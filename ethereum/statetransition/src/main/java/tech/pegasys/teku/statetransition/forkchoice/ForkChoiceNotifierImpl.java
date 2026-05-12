/*
 * Copyright Consensys Software Inc., 2026
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

import static com.google.common.base.Preconditions.checkState;

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
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadContext;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannel;
import tech.pegasys.teku.spec.executionlayer.ForkChoiceState;
import tech.pegasys.teku.spec.executionlayer.PayloadBuildingAttributes;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoiceUpdatedResultSubscriber.ForkChoiceUpdatedResultNotification;
import tech.pegasys.teku.storage.client.RecentChainData;

public class ForkChoiceNotifierImpl implements ForkChoiceNotifier {
  private static final Logger LOG = LogManager.getLogger();

  private final EventThread eventThread;
  private final ForkChoiceStateProvider forkChoiceStateProvider;
  private final ExecutionLayerChannel executionLayerChannel;
  private final RecentChainData recentChainData;
  private final ProposersDataManager proposersDataManager;
  private final Spec spec;
  private final TimeProvider timeProvider;

  private final Subscribers<ForkChoiceUpdatedResultSubscriber> forkChoiceUpdatedSubscribers =
      Subscribers.create(true);

  private ForkChoiceUpdateData forkChoiceUpdateData = new ForkChoiceUpdateData();

  // Pinned proposing slot for which forkChoiceUpdateData must remain stable. Set when block
  // production starts (proposingSlot is supplied). Cleared as soon as the produced block is
  // imported (head advances past it).
  private Optional<UInt64> lastProposingSlot = Optional.empty();

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
  }

  @Override
  public void subscribeToForkChoiceUpdatedResult(
      final ForkChoiceUpdatedResultSubscriber subscriber) {
    forkChoiceUpdatedSubscribers.subscribe(subscriber);
  }

  @Override
  public void onForkChoiceUpdated(
      final ForkChoiceState forkChoiceState, final Optional<UInt64> proposingSlot) {
    eventThread.execute(() -> internalForkChoiceUpdated(forkChoiceState, proposingSlot));
  }

  @Override
  public void onAttestationsDue(final UInt64 slot) {
    eventThread.execute(
        () -> {
          eventThread.checkOnEventThread();
          LOG.debug("onAttestationsDue slot {}", slot);
          // when we don't need to notify fCu when we have imported a beacon block (post-Gloas),
          // there is no need to prepare next slot proposals when attestations are due
          if (spec.atSlot(slot).getForkChoiceUtil().shouldNotifyForkChoiceUpdatedOnBlock()) {
            prepareNextSlotProposal(slot);
          }
        });
  }

  @Override
  public void onPayloadAttestationsDue(final UInt64 slot) {
    eventThread.execute(
        () -> {
          eventThread.checkOnEventThread();
          LOG.debug("onPayloadAttestationsDue slot {}", slot);
          prepareNextSlotProposal(slot);
        });
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
  public void onTerminalBlockReached(final Bytes32 executionBlockHash) {
    eventThread.execute(() -> internalTerminalBlockReached(executionBlockHash));
  }

  private void internalTerminalBlockReached(final Bytes32 executionBlockHash) {
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

    final UInt64 timestamp = spec.computeTimeAtSlot(blockSlot, recentChainData.getGenesisTime());

    validateForkChoiceHeadMatchesParent(parentBeaconBlockRoot, parentExecutionHash);

    if (forkChoiceUpdateData.isPayloadIdSuitable(parentExecutionHash, timestamp)) {
      return forkChoiceUpdateData.getExecutionPayloadContext();
    } else if (parentExecutionHash.isZero() && !forkChoiceUpdateData.hasTerminalBlockHash()) {
      // Pre-merge so ok to use default payload
      return SafeFuture.completedFuture(Optional.empty());
    } else {
      // TODO: with the pinned forkChoiceUpdateData there is no point in trying to refresh.
      // Now we should have a stable forkChoiceUpdateData which must be compatible with
      // parentBeaconBlockRoot and slot by blockSlot. So we can just throw here.

      // Request a new payload with refreshed forkChoiceState and payloadBuildingAttributes

      LOG.warn(
          "No suitable payloadId for block production at slot {}, requesting a new one to the EL",
          blockSlot);

      // to make sure that we deal with the same data when calculatePayloadAttributes asynchronously
      // returns, we save locally the current class reference.
      final ForkChoiceUpdateData localForkChoiceUpdateData = forkChoiceUpdateData;

      return forkChoiceStateProvider
          .getForkChoiceStateAsync()
          .thenCombine(
              proposersDataManager.calculatePayloadBuildingAttributes(
                  blockSlot, inSync, localForkChoiceUpdateData, true),
              (forkChoiceState, payloadBuildingAttributes) -> {
                forkChoiceUpdateData =
                    localForkChoiceUpdateData
                        .withForkChoiceState(forkChoiceState)
                        .withPayloadBuildingAttributes(payloadBuildingAttributes);

                sendForkChoiceUpdated();

                validateForkChoiceHeadMatchesParent(parentBeaconBlockRoot, parentExecutionHash);

                if (!forkChoiceUpdateData.isPayloadIdSuitable(parentExecutionHash, timestamp)) {
                  throw new IllegalStateException(
                      "payloadId still not suitable after requesting a new one via FcU with recalculated data");
                }

                return forkChoiceUpdateData;
              })
          .thenCompose(ForkChoiceUpdateData::getExecutionPayloadContext)
          .thenApply(
              maybeExecutionPayloadContext -> {
                if (maybeExecutionPayloadContext.isEmpty()) {
                  throw new IllegalStateException("Unable to obtain an executionPayloadContext");
                }
                return maybeExecutionPayloadContext;
              });
    }
  }

  private void validateForkChoiceHeadMatchesParent(
      final Bytes32 parentBeaconBlockRoot, final Bytes32 parentExecutionHash) {
    if (parentExecutionHash.isZero()) {
      return;
    }

    checkState(
        forkChoiceUpdateData
            .getForkChoiceState()
            .headBlock()
            .blockRoot()
            .equals(parentBeaconBlockRoot),
        "Fork choice update head %s does not match requested block production parent %s",
        forkChoiceUpdateData.getForkChoiceState().headBlock().blockRoot(),
        parentBeaconBlockRoot);
  }

  private void internalForkChoiceUpdated(
      final ForkChoiceState forkChoiceState, final Optional<UInt64> proposingSlot) {
    eventThread.checkOnEventThread();

    LOG.debug("internalForkChoiceUpdated forkChoiceState {}", forkChoiceState);

    final Optional<UInt64> localProposingSlot =
        calculatePayloadAttributesSlot(forkChoiceState, proposingSlot);

    // Reset rule: if our produced block (or a later block) has been imported, clear the pin and
    // let this fcU through so the EL learns the new head immediately. The fall-through path below
    // refreshes forkChoiceUpdateData and broadcasts on the same event-thread tick.
    if (lastProposingSlot.isPresent()
        && forkChoiceState.headBlockSlot().isGreaterThanOrEqualTo(lastProposingSlot.get())) {
      LOG.debug(
          "internalForkChoiceUpdated clearing pin for slot {} (head advanced to slot {})",
          lastProposingSlot.get(),
          forkChoiceState.headBlockSlot());
      lastProposingSlot = Optional.empty();
    }

    if (proposingSlot.isPresent()) {
      // Production trigger: refresh forkChoiceUpdateData and (re)set the pin to the proposing slot
      // regardless of any cached value. ForkChoice has already substituted the proposer head
      // (incl. late-block reorg) so the broadcast is correct without an override gate here.
      this.forkChoiceUpdateData = this.forkChoiceUpdateData.withForkChoiceState(forkChoiceState);
      lastProposingSlot = localProposingSlot;
      LOG.debug(
          "internalForkChoiceUpdated forkChoiceUpdateData {} pinned for slot {}",
          forkChoiceUpdateData,
          lastProposingSlot);
      localProposingSlot.ifPresent(this::updatePayloadAttributes);
      sendForkChoiceUpdated();
      return;
    }

    if (localProposingSlot.isPresent()
        && lastProposingSlot.isPresent()
        && localProposingSlot.get().equals(lastProposingSlot.get())) {
      // Pin-hold: a background fcU arrived during the active proposing window. Skip the swap
      // (which would also wipe payloadBuildingAttributes via withForkChoiceState) so the
      // proposing-slot state stays stable.
      LOG.debug("internalForkChoiceUpdated skipped — pinned for slot {}", lastProposingSlot.get());
      return;
    }

    this.forkChoiceUpdateData = this.forkChoiceUpdateData.withForkChoiceState(forkChoiceState);

    LOG.debug("internalForkChoiceUpdated forkChoiceUpdateData {}", forkChoiceUpdateData);

    localProposingSlot.ifPresent(this::updatePayloadAttributes);

    sendForkChoiceUpdated();
  }

  /**
   * Determine for which slot we should calculate payload attributes (block proposal)
   *
   * <pre>
   * this will guarantee that whenever we calculate a payload attributes for a slot, it will remain stable until:
   * 1. next slot attestation due is reached (internalAttestationsDue forcing attributes calculation for next slot)
   * OR
   * 2. we imported the block for current slot and has become the head
   * </pre>
   */
  private Optional<UInt64> calculatePayloadAttributesSlot(
      final ForkChoiceState forkChoiceState, final Optional<UInt64> proposingSlot) {
    if (proposingSlot.isPresent()) {
      // We are in the context of a block production, so we should use the proposing slot
      return proposingSlot;
    }

    final Optional<UInt64> currentSlot = recentChainData.getCurrentSlot();
    if (currentSlot.isEmpty()) {
      // We are pre-genesis, so we don't care about proposing slots
      return Optional.empty();
    }

    final Optional<UInt64> maybeCurrentPayloadAttributesSlot =
        forkChoiceUpdateData
            .getPayloadBuildingAttributes()
            .map(PayloadBuildingAttributes::proposalSlot);

    if (maybeCurrentPayloadAttributesSlot.isPresent()
        // we are still in the same slot as the last proposing slot
        && currentSlot.get().equals(maybeCurrentPayloadAttributesSlot.get())
        // we have not yet imported our own produced block
        && forkChoiceState.headBlockSlot().isLessThan(maybeCurrentPayloadAttributesSlot.get())) {

      LOG.debug(
          "current payload attributes slot has been chosen for payload attributes calculation: {}",
          currentSlot.get());

      // in case we propose two blocks in a row and we fail producing the first block,
      // we won't keep using the same first slot because internalAttestationsDue will
      // update the payload attributes for the second block slot
      return currentSlot;
    }

    // chain advanced since last proposing slot, we should consider attributes for the next slot
    return currentSlot.map(UInt64::increment);
  }

  private void prepareNextSlotProposal(final UInt64 slot) {
    // Assume `slot` is empty and check if we need to prepare to propose in the next slot
    updatePayloadAttributes(slot.plus(1));
  }

  private void sendForkChoiceUpdated() {
    forkChoiceUpdateData
        .send(executionLayerChannel, timeProvider.getTimeInMillis())
        .ifPresent(
            forkChoiceUpdatedResultFuture ->
                forkChoiceUpdatedSubscribers.deliver(
                    ForkChoiceUpdatedResultSubscriber::onForkChoiceUpdatedResult,
                    new ForkChoiceUpdatedResultNotification(
                        forkChoiceUpdateData.getForkChoiceState(),
                        forkChoiceUpdateData.getPayloadBuildingAttributes(),
                        forkChoiceUpdateData.hasTerminalBlockHash(),
                        forkChoiceUpdatedResultFuture)));
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
