/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.beacon.sync.events;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.beacon.sync.forward.ForwardSync.SyncSubscriber;
import tech.pegasys.teku.infrastructure.logging.EventLogger;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.storage.api.ChainHeadChannel;
import tech.pegasys.teku.storage.api.ReorgContext;

public class CoalescingChainHeadChannel implements ChainHeadChannel, SyncSubscriber {

  private final ChainHeadChannel delegate;
  private boolean syncing = false;

  private Optional<PendingEvent> pendingEvent = Optional.empty();
  private final EventLogger eventLogger;

  public CoalescingChainHeadChannel(
      final ChainHeadChannel delegate, final EventLogger eventLogger) {
    this.delegate = delegate;
    this.eventLogger = eventLogger;
  }

  @Override
  public synchronized void chainHeadUpdated(
      final UInt64 slot,
      final Bytes32 stateRoot,
      final Bytes32 bestBlockRoot,
      final boolean epochTransition,
      final boolean executionOptimistic,
      final Bytes32 previousDutyDependentRoot,
      final Bytes32 currentDutyDependentRoot,
      final Optional<ReorgContext> optionalReorgContext) {
    if (!syncing) {
      optionalReorgContext.ifPresent(
          reorg ->
              eventLogger.reorgEvent(
                  reorg.getOldBestBlockRoot(),
                  reorg.getOldBestBlockSlot(),
                  bestBlockRoot,
                  slot,
                  reorg.getCommonAncestorRoot(),
                  reorg.getCommonAncestorSlot()));
      delegate.chainHeadUpdated(
          slot,
          stateRoot,
          bestBlockRoot,
          epochTransition,
          executionOptimistic,
          previousDutyDependentRoot,
          currentDutyDependentRoot,
          optionalReorgContext);
    } else {
      pendingEvent =
          pendingEvent
              .map(
                  current ->
                      current.update(
                          slot,
                          stateRoot,
                          bestBlockRoot,
                          epochTransition,
                          executionOptimistic,
                          previousDutyDependentRoot,
                          currentDutyDependentRoot,
                          optionalReorgContext))
              .or(
                  () ->
                      Optional.of(
                          new PendingEvent(
                              slot,
                              stateRoot,
                              bestBlockRoot,
                              epochTransition,
                              executionOptimistic,
                              previousDutyDependentRoot,
                              currentDutyDependentRoot,
                              optionalReorgContext)));
    }
  }

  @Override
  public synchronized void onSyncingChange(final boolean isSyncing) {
    syncing = isSyncing;
    if (!syncing) {
      pendingEvent.ifPresent(PendingEvent::send);
      pendingEvent = Optional.empty();
    }
  }

  private class PendingEvent {
    private UInt64 slot;
    private Bytes32 stateRoot;
    private Bytes32 bestBlockRoot;
    private boolean epochTransition;
    private boolean executionOptimistic;
    private Bytes32 previousDutyDependentRoot;
    private Bytes32 currentDutyDependentRoot;
    private Optional<ReorgContext> reorgContext;

    private PendingEvent(
        final UInt64 slot,
        final Bytes32 stateRoot,
        final Bytes32 bestBlockRoot,
        final boolean epochTransition,
        final boolean executionOptimistic,
        final Bytes32 previousDutyDependentRoot,
        final Bytes32 currentDutyDependentRoot,
        final Optional<ReorgContext> reorgContext) {
      this.slot = slot;
      this.stateRoot = stateRoot;
      this.bestBlockRoot = bestBlockRoot;
      this.epochTransition = epochTransition;
      this.executionOptimistic = executionOptimistic;
      this.previousDutyDependentRoot = previousDutyDependentRoot;
      this.currentDutyDependentRoot = currentDutyDependentRoot;
      this.reorgContext = reorgContext;
    }

    public void send() {
      delegate.chainHeadUpdated(
          slot,
          stateRoot,
          bestBlockRoot,
          epochTransition,
          executionOptimistic,
          previousDutyDependentRoot,
          currentDutyDependentRoot,
          reorgContext);
    }

    public PendingEvent update(
        final UInt64 slot,
        final Bytes32 stateRoot,
        final Bytes32 bestBlockRoot,
        final boolean epochTransition,
        final boolean executionOptimistic,
        final Bytes32 previousDutyDependentRoot,
        final Bytes32 currentDutyDependentRoot,
        final Optional<ReorgContext> reorgContext) {
      this.slot = slot;
      this.stateRoot = stateRoot;
      this.bestBlockRoot = bestBlockRoot;
      if (epochTransition) {
        this.epochTransition = true;
      }
      this.executionOptimistic = executionOptimistic;
      this.previousDutyDependentRoot = previousDutyDependentRoot;
      this.currentDutyDependentRoot = currentDutyDependentRoot;
      if (reorgContext.isPresent() && hasEarlierCommonAncestor(reorgContext)) {
        this.reorgContext = reorgContext;
      }
      return this;
    }

    private boolean hasEarlierCommonAncestor(final Optional<ReorgContext> reorgContext) {
      return this.reorgContext.isEmpty()
          || reorgContext
              .orElseThrow()
              .getCommonAncestorSlot()
              .isLessThan(this.reorgContext.orElseThrow().getCommonAncestorSlot());
    }
  }
}
