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
 * Replicates the fork choice behaviour prior to HF1 changes where processHead ran synchronously
 * when attestations were due (or on slot during sync) regardless of whether it had been run for
 * that slot previously.
 */
class OriginalForkChoiceTrigger implements ForkChoiceTrigger {
  private static final Logger LOG = LogManager.getLogger();

  private final AtomicReference<ForkChoiceUpdate> latestCompletedForkChoice =
      new AtomicReference<>();

  private final ForkChoice forkChoice;

  OriginalForkChoiceTrigger(final ForkChoice forkChoice) {
    this.forkChoice = forkChoice;
  }

  @Override
  public void onSlotStartedWhileSyncing(final UInt64 nodeSlot) {
    processHead(nodeSlot).result.join();
  }

  @Override
  public void onSlotStarted(final UInt64 nodeSlot) {}

  @Override
  public void onAttestationsDueForSlot(final UInt64 nodeSlot) {
    processHead(nodeSlot).result.join();
  }

  @Override
  public SafeFuture<Void> prepareForBlockProduction(final UInt64 slot) {
    return SafeFuture.COMPLETE;
  }

  @Override
  public SafeFuture<Void> ensureForkChoiceCompleteForSlot(final UInt64 slot) {
    return processHead(slot).result;
  }

  protected ForkChoiceUpdate processHead(final UInt64 nodeSlot) {
    // Keep trying to get our slot processed until we or someone else gets it done
    while (true) {
      final ForkChoiceUpdate previousUpdate = latestCompletedForkChoice.get();
      if (previousUpdate != null && previousUpdate.nodeSlot.isGreaterThanOrEqualTo(nodeSlot)) {
        LOG.debug(
            "Skipping fork choice update for slot {} as high water mark is already {}",
            nodeSlot,
            previousUpdate.nodeSlot);
        return previousUpdate;
      }
      final ForkChoiceUpdate newUpdate = new ForkChoiceUpdate(nodeSlot);
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
    private final SafeFuture<Void> result = new SafeFuture<>();

    private ForkChoiceUpdate(final UInt64 nodeSlot) {
      this.nodeSlot = nodeSlot;
    }
  }
}
