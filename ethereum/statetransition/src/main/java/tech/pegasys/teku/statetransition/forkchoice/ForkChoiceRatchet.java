/*
 * Copyright 2021 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package tech.pegasys.teku.statetransition.forkchoice;

import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

/**
 * Provides a ratchet-like functionality for running fork choice. Callers request that fork choice
 * be run for a specific slot and phase and this class tracks which slot fork choice has been run
 * for, only running it again when the slot or phase needs to ratchet up. Thus the slot we run fork
 * choice for only ever moves up and fork choice is never run for old slots, avoiding unnecessary
 * work.
 *
 * <p>The phase allows fork choice to be run twice per slot - once at the start of the slot to
 * prepare for producing blocks, and once to prepare for creating attestations.
 */
class ForkChoiceRatchet {

  private static final Logger LOG = LogManager.getLogger();
  private final AtomicReference<ForkChoiceUpdate> latestCompletedForkChoice =
      new AtomicReference<>();

  private final ForkChoice forkChoice;

  ForkChoiceRatchet(final ForkChoice forkChoice) {
    this.forkChoice = forkChoice;
  }

  void scheduleForkChoiceForSlot(final UInt64 slot, final ForkChoicePhase phase) {
    processHead(slot, phase);
  }

  SafeFuture<Void> ensureForkChoiceCompleteForSlot(final UInt64 slot, final ForkChoicePhase phase) {
    final ForkChoiceUpdate forkChoiceUpdate = processHead(slot, phase);
    if (forkChoiceUpdate.nodeSlot.isGreaterThan(slot)) {
      return SafeFuture.COMPLETE;
    } else if (forkChoiceUpdate.nodeSlot.equals(slot)) {
      return forkChoiceUpdate.result;
    } else {
      // Only possible if processHead messed up somehow
      return SafeFuture.failedFuture(
          new IllegalStateException(
              "Requested fork choice be processed for slot "
                  + slot
                  + " but result indicates fork choice was only up to "
                  + forkChoiceUpdate.nodeSlot));
    }
  }

  private ForkChoiceUpdate processHead(final UInt64 nodeSlot, final ForkChoicePhase phase) {
    // Keep trying to get our slot processed until we or someone else gets it done
    while (true) {
      final ForkChoiceUpdate previousUpdate = latestCompletedForkChoice.get();
      if (previousUpdate != null && previousUpdate.isSufficientFor(nodeSlot, phase)) {
        LOG.debug(
            "Skipping fork choice update for slot {} as high water mark is already {}",
            nodeSlot,
            previousUpdate.nodeSlot);
        return previousUpdate;
      }
      final ForkChoiceUpdate newUpdate = new ForkChoiceUpdate(nodeSlot, phase);
      if (latestCompletedForkChoice.compareAndSet(previousUpdate, newUpdate)) {
        forkChoice
            .processHead(nodeSlot)
            // We handle errors in fork choice by logging them but then continuing
            // as we don't want to fail to produce a block because fork choice failed.
            .handleException(error -> LOG.error("Fork choice process head failed", error))
            .propagateTo(newUpdate.result);
        return newUpdate;
      }
    }
  }

  private static class ForkChoiceUpdate {

    private final UInt64 nodeSlot;
    private final ForkChoicePhase phase;
    private final SafeFuture<Void> result = new SafeFuture<>();

    private ForkChoiceUpdate(final UInt64 nodeSlot, final ForkChoicePhase phase) {
      this.nodeSlot = nodeSlot;
      this.phase = phase;
    }

    boolean isSufficientFor(final UInt64 requiredSlot, final ForkChoicePhase requiredPhase) {
      if (nodeSlot.isGreaterThan(requiredSlot)) {
        return true;
      } else if (nodeSlot.isLessThan(requiredSlot)) {
        return false;
      } else {
        return phase.compareTo(requiredPhase) >= 0;
      }
    }
  }

  public enum ForkChoicePhase {
    BLOCK,
    ATTESTATION
  }
}
