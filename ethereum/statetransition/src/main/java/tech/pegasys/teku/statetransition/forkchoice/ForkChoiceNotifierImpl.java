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

package tech.pegasys.teku.statetransition.forkchoice;

import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.eventthread.EventThread;
import tech.pegasys.teku.infrastructure.subscribers.Subscribers;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadContext;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannel;
import tech.pegasys.teku.spec.executionlayer.ForkChoiceState;
import tech.pegasys.teku.spec.executionlayer.ForkChoiceUpdatedResult;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoiceUpdatedResultSubscriber.ForkChoiceUpdatedResultNotification;
import tech.pegasys.teku.statetransition.forkchoice.ProposersDataManager.ProposersDataManagerSubscriber;
import tech.pegasys.teku.storage.client.RecentChainData;

public class ForkChoiceNotifierImpl implements ForkChoiceNotifier, ProposersDataManagerSubscriber {
  private static final Logger LOG = LogManager.getLogger();

  private final EventThread eventThread;
  private final ForkChoiceStateProvider forkChoiceStateProvider;
  private final ExecutionLayerChannel executionLayerChannel;
  private final RecentChainData recentChainData;
  private final ProposersDataManager proposersDataManager;
  private final Spec spec;
  private final TimeProvider timeProvider;

  private final Subscribers<ForkChoiceUpdatedResultSubscriber> subscribers =
      Subscribers.create(true);

  private ForkChoiceUpdateData forkChoiceUpdateData = new ForkChoiceUpdateData();

  private boolean inSync = false; // Assume we are not in sync at startup.

  public ForkChoiceNotifierImpl(
      final ForkChoiceStateProvider forkChoiceStateProvider,
      final EventThread eventThread,
      final TimeProvider timeProvider,
      final Spec spec,
      final ExecutionLayerChannel executionLayerChannel,
      final RecentChainData recentChainData,
      final ProposersDataManager proposersDataManager) {
    this.forkChoiceStateProvider = forkChoiceStateProvider;
    this.eventThread = eventThread;
    this.spec = spec;
    this.executionLayerChannel = executionLayerChannel;
    this.recentChainData = recentChainData;
    this.proposersDataManager = proposersDataManager;
    this.timeProvider = timeProvider;
    proposersDataManager.subscribeToProposersDataChanges(this);
  }

  @Override
  public long subscribeToForkChoiceUpdatedResult(ForkChoiceUpdatedResultSubscriber subscriber) {
    return subscribers.subscribe(subscriber);
  }

  @Override
  public boolean unsubscribeFromForkChoiceUpdatedResult(long subscriberId) {
    return subscribers.unsubscribe(subscriberId);
  }

  @Override
  public void onForkChoiceUpdated(
      final ForkChoiceState forkChoiceState, final Optional<UInt64> proposingSlot) {
    eventThread.execute(() -> internalForkChoiceUpdated(forkChoiceState, proposingSlot));
  }

  @Override
  public void onAttestationsDue(final UInt64 slot) {
    eventThread.execute(() -> internalAttestationsDue(slot));
  }

  @Override
  public void onSyncingStatusChanged(final boolean inSync) {
    eventThread.execute(
        () -> {
          this.inSync = inSync;
        });
  }

  @Override
  public SafeFuture<Optional<ExecutionPayloadContext>> getPayloadId(
      final Bytes32 parentBeaconBlockRoot, final UInt64 blockSlot) {
    return eventThread.executeFuture(() -> internalGetPayloadId(parentBeaconBlockRoot, blockSlot));
  }

  @Override
  public void onTerminalBlockReached(Bytes32 executionBlockHash) {
    eventThread.execute(() -> internalTerminalBlockReached(executionBlockHash));
  }

  @Override
  public void onPreparedProposersUpdated() {
    eventThread.execute(this::internalUpdatePreparableProposers);
  }

  @Override
  public void onValidatorRegistrationsUpdated() {}

  private void internalTerminalBlockReached(Bytes32 executionBlockHash) {
    eventThread.checkOnEventThread();
    LOG.debug("internalTerminalBlockReached executionBlockHash {}", executionBlockHash);
    forkChoiceUpdateData = forkChoiceUpdateData.withTerminalBlockHash(executionBlockHash);
    LOG.debug("internalTerminalBlockReached forkChoiceUpdateData {}", forkChoiceUpdateData);
  }

  /**
   * @param parentBeaconBlockRoot root of the beacon block the new block will be built on
   * @param blockSlot slot of the block being produced, for which the payloadId has been requested
   * @return must return a Future resolving to:
   *     <p>Optional.empty() only when is safe to produce a block with an empty execution payload
   *     (after the bellatrix fork and before Terminal Block arrival)
   *     <p>Optional.of(executionPayloadContext) when one of the following:
   *     <p>1. builds on top of execution head of parentBeaconBlockRoot
   *     <p>2. builds on top of the terminal block
   *     <p>in all other cases it must Throw to avoid block production
   */
  private SafeFuture<Optional<ExecutionPayloadContext>> internalGetPayloadId(
      final Bytes32 parentBeaconBlockRoot, final UInt64 blockSlot) {
    eventThread.checkOnEventThread();

    LOG.debug(
        "internalGetPayloadId parentBeaconBlockRoot {} blockSlot {}",
        parentBeaconBlockRoot,
        blockSlot);

    final Bytes32 parentExecutionHash =
        recentChainData
            .getExecutionBlockHashForBlockRoot(parentBeaconBlockRoot)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Failed to retrieve execution payload hash from beacon block root"));

    final UInt64 timestamp = spec.getSlotStartTime(blockSlot, recentChainData.getGenesisTime());
    if (forkChoiceUpdateData.isPayloadIdSuitable(parentExecutionHash, timestamp)) {
      return forkChoiceUpdateData.getExecutionPayloadContext();
    } else if (parentExecutionHash.isZero() && !forkChoiceUpdateData.hasTerminalBlockHash()) {
      // Pre-merge so ok to use default payload
      return SafeFuture.completedFuture(Optional.empty());
    } else {
      // Request a new payload with refreshed forkChoiceState and payloadBuildingAttributes

      LOG.warn(
          "No suitable payloadId for block production at slot {}, requesting a new one to the EL",
          blockSlot);

      // to make sure that we deal with the same data when calculatePayloadAttributes asynchronously
      // returns, we save locally the current class reference.
      final ForkChoiceUpdateData localForkChoiceUpdateData = forkChoiceUpdateData;

      return forkChoiceStateProvider
          .getForkChoiceState()
          .thenCombine(
              proposersDataManager.calculatePayloadBuildingAttributes(
                  blockSlot, inSync, localForkChoiceUpdateData, true),
              (forkChoiceState, payloadBuildingAttributes) -> {
                forkChoiceUpdateData =
                    localForkChoiceUpdateData
                        .withForkChoiceState(forkChoiceState)
                        .withPayloadBuildingAttributes(payloadBuildingAttributes);

                sendForkChoiceUpdated();

                if (!forkChoiceUpdateData.isPayloadIdSuitable(parentExecutionHash, timestamp)) {
                  throw new IllegalStateException(
                      "recalculated forkChoiceUpdateData still not suitable");
                }

                return forkChoiceUpdateData.getExecutionPayloadContext();
              })
          .thenCompose(
              executionPayloadContextFuture ->
                  executionPayloadContextFuture.thenApply(
                      maybeExecutionPayloadContext -> {
                        if (maybeExecutionPayloadContext.isEmpty()) {
                          throw new IllegalStateException(
                              "Unable to obtain an executionPayloadContext");
                        }
                        return maybeExecutionPayloadContext;
                      }));
    }
  }

  private void internalUpdatePreparableProposers() {
    eventThread.checkOnEventThread();

    LOG.debug("internalUpdatePreparableProposers");

    // Default to the genesis slot if we're pre-genesis.
    final UInt64 currentSlot = recentChainData.getCurrentSlot().orElse(SpecConfig.GENESIS_SLOT);

    // Update payload attributes in case we now need to propose the next block
    updatePayloadAttributes(currentSlot.plus(1));
  }

  private void internalForkChoiceUpdated(
      final ForkChoiceState forkChoiceState, final Optional<UInt64> proposingSlot) {
    eventThread.checkOnEventThread();

    LOG.debug("internalForkChoiceUpdated forkChoiceState {}", forkChoiceState);

    this.forkChoiceUpdateData = this.forkChoiceUpdateData.withForkChoiceState(forkChoiceState);

    LOG.debug("internalForkChoiceUpdated forkChoiceUpdateData {}", forkChoiceUpdateData);

    final Optional<UInt64> attributesSlot =
        proposingSlot.or(() -> recentChainData.getCurrentSlot().map(UInt64::increment));

    attributesSlot.ifPresent(this::updatePayloadAttributes);

    sendForkChoiceUpdated();
  }

  private void internalAttestationsDue(final UInt64 slot) {
    eventThread.checkOnEventThread();

    LOG.debug("internalAttestationsDue slot {}", slot);

    // Assume `slot` is empty and check if we need to prepare to propose in the next slot
    updatePayloadAttributes(slot.plus(1));
  }

  private void sendForkChoiceUpdated() {
    final SafeFuture<Optional<ForkChoiceUpdatedResult>> forkChoiceUpdatedResult =
        forkChoiceUpdateData.send(executionLayerChannel, timeProvider.getTimeInMillis());
    subscribers.deliver(
        ForkChoiceUpdatedResultSubscriber::onForkChoiceUpdatedResult,
        new ForkChoiceUpdatedResultNotification(
            forkChoiceUpdateData.getForkChoiceState(),
            forkChoiceUpdateData.hasTerminalBlockHash(),
            forkChoiceUpdatedResult));
  }

  private void updatePayloadAttributes(final UInt64 blockSlot) {
    LOG.debug("updatePayloadAttributes blockSlot {}", blockSlot);

    forkChoiceUpdateData
        .withPayloadBuildingAttributesAsync(
            () ->
                proposersDataManager.calculatePayloadBuildingAttributes(
                    blockSlot, inSync, forkChoiceUpdateData, false),
            eventThread)
        .thenAccept(
            newForkChoiceUpdateData -> {
              if (newForkChoiceUpdateData.isPresent()) {
                forkChoiceUpdateData = newForkChoiceUpdateData.get();
                sendForkChoiceUpdated();
              }
            })
        .finish(
            error ->
                LOG.error("Failed to calculate payload attributes for slot {}", blockSlot, error));
  }
}
