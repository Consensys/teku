/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.sync.events;

import static tech.pegasys.teku.infrastructure.logging.LogFormatter.formatHashRoot;

import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.storage.api.ChainHeadChannel;
import tech.pegasys.teku.storage.api.ReorgContext;
import tech.pegasys.teku.sync.forward.ForwardSync;
import tech.pegasys.teku.sync.forward.ForwardSync.SyncSubscriber;

public class CoalescingChainHeadChannel implements ChainHeadChannel, SyncSubscriber {
  private static final Logger LOG = LogManager.getLogger();

  private final ChainHeadChannel delegate;
  private boolean syncing = false;

  private Optional<PendingEvent> pendingEvent = Optional.empty();

  public CoalescingChainHeadChannel(final ChainHeadChannel delegate) {
    this.delegate = delegate;
  }

  public static ChainHeadChannel create(
      final ChainHeadChannel delegate, final ForwardSync syncService) {
    final CoalescingChainHeadChannel channel = new CoalescingChainHeadChannel(delegate);
    syncService.subscribeToSyncChanges(channel);
    return channel;
  }

  @Override
  public synchronized void chainHeadUpdated(
      final UInt64 slot,
      final Bytes32 stateRoot,
      final Bytes32 bestBlockRoot,
      final boolean epochTransition,
      final Bytes32 proposerShufflingPivotRoot,
      final Bytes32 attesterShufflingPivotRoot,
      final Optional<ReorgContext> optionalReorgContext) {
    if (!syncing) {
      optionalReorgContext.ifPresent(
          reorg ->
              LOG.info(
                  "Chain reorg at slot {} from {} to {}. Common ancestor at slot {}",
                  slot,
                  formatHashRoot(reorg.getOldBestBlockRoot()),
                  formatHashRoot(bestBlockRoot),
                  reorg.getCommonAncestorSlot()));
      delegate.chainHeadUpdated(
          slot,
          stateRoot,
          bestBlockRoot,
          epochTransition,
          proposerShufflingPivotRoot,
          attesterShufflingPivotRoot,
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
                          proposerShufflingPivotRoot,
                          attesterShufflingPivotRoot,
                          optionalReorgContext))
              .or(
                  () ->
                      Optional.of(
                          new PendingEvent(
                              slot,
                              stateRoot,
                              bestBlockRoot,
                              epochTransition,
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
    private Bytes32 proposerShufflingPivotRoot;
    private Bytes32 attesterShufflingPivotRoot;
    private Optional<ReorgContext> reorgContext;

    private PendingEvent(
        final UInt64 slot,
        final Bytes32 stateRoot,
        final Bytes32 bestBlockRoot,
        final boolean epochTransition,
        final Optional<ReorgContext> reorgContext) {
      this.slot = slot;
      this.stateRoot = stateRoot;
      this.bestBlockRoot = bestBlockRoot;
      this.epochTransition = epochTransition;
      this.reorgContext = reorgContext;
    }

    public void send() {
      delegate.chainHeadUpdated(
          slot,
          stateRoot,
          bestBlockRoot,
          epochTransition,
          proposerShufflingPivotRoot,
          attesterShufflingPivotRoot,
          reorgContext);
    }

    public PendingEvent update(
        final UInt64 slot,
        final Bytes32 stateRoot,
        final Bytes32 bestBlockRoot,
        final boolean epochTransition,
        final Bytes32 proposerShufflingPivotRoot,
        final Bytes32 attesterShufflingPivotRoot,
        final Optional<ReorgContext> reorgContext) {
      this.slot = slot;
      this.stateRoot = stateRoot;
      this.bestBlockRoot = bestBlockRoot;
      if (epochTransition) {
        this.epochTransition = true;
      }
      this.proposerShufflingPivotRoot = proposerShufflingPivotRoot;
      this.attesterShufflingPivotRoot = attesterShufflingPivotRoot;
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
