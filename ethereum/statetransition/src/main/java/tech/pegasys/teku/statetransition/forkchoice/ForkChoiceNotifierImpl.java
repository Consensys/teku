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
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoiceNode;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannel;
import tech.pegasys.teku.spec.executionlayer.ForkChoiceState;
import tech.pegasys.teku.spec.executionlayer.PayloadBuildingAttributes;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoiceUpdatedResultSubscriber.ForkChoiceUpdatedResultNotification;
import tech.pegasys.teku.storage.client.RecentChainData;

public class ForkChoiceNotifierImpl implements ForkChoiceNotifier {
  private static final Logger LOG = LogManager.getLogger();

  private final EventThread eventThread;
  private final ExecutionLayerChannel executionLayerChannel;
  private final RecentChainData recentChainData;
  private final ProposersDataManager proposersDataManager;
  private final Spec spec;
  private final TimeProvider timeProvider;
  private final boolean forkChoiceLateBlockReorgEnabled;

  private final Subscribers<ForkChoiceUpdatedResultSubscriber> forkChoiceUpdatedSubscribers =
      Subscribers.create(true);

  private ForkChoiceUpdateContext forkChoiceUpdateContext =
      ForkChoiceUpdateContext.plain(new ForkChoiceUpdateData());

  private boolean inSync = false; // Assume we are not in sync at startup.

  public ForkChoiceNotifierImpl(
      final EventThread eventThread,
      final TimeProvider timeProvider,
      final Spec spec,
      final ExecutionLayerChannel executionLayerChannel,
      final RecentChainData recentChainData,
      final ProposersDataManager proposersDataManager,
      final boolean forkChoiceLateBlockReorgEnabled) {
    this.eventThread = eventThread;
    this.spec = spec;
    this.executionLayerChannel = executionLayerChannel;
    this.recentChainData = recentChainData;
    this.proposersDataManager = proposersDataManager;
    this.timeProvider = timeProvider;
    this.forkChoiceLateBlockReorgEnabled = forkChoiceLateBlockReorgEnabled;
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
      final ForkChoiceNode parentBeaconBlock, final UInt64 blockSlot) {
    return eventThread.executeFuture(() -> internalGetPayloadId(parentBeaconBlock, blockSlot));
  }

  @Override
  public void onTerminalBlockReached(final Bytes32 executionBlockHash) {
    eventThread.execute(() -> internalTerminalBlockReached(executionBlockHash));
  }

  private void internalTerminalBlockReached(final Bytes32 executionBlockHash) {
    eventThread.checkOnEventThread();
    LOG.debug("internalTerminalBlockReached executionBlockHash {}", executionBlockHash);
    forkChoiceUpdateContext = forkChoiceUpdateContext.withTerminalBlockHash(executionBlockHash);
    LOG.debug(
        "internalTerminalBlockReached forkChoiceUpdateData {}",
        forkChoiceUpdateContext.getForkChoiceUpdateData());
  }

  /**
   * @param parentBeaconBlock fork choice node of the beacon block the new block will be built on
   * @param blockSlot slot of the block being produced, for which the payloadId has been requested
   * @return must return a Future resolving to:
   *     <p>Optional.empty() only when is safe to produce a block with an empty execution payload
   *     (after the bellatrix fork and before Terminal Block arrival)
   *     <p>Optional.of(executionPayloadContext) when one of the following:
   *     <p>1. builds on top of execution head of parentBeaconBlock
   *     <p>2. builds on top of the terminal block
   *     <p>in all other cases it must Throw to avoid block production
   */
  private SafeFuture<Optional<ExecutionPayloadContext>> internalGetPayloadId(
      final ForkChoiceNode parentBeaconBlock, final UInt64 blockSlot) {
    eventThread.checkOnEventThread();

    LOG.debug(
        "internalGetPayloadId parentBeaconBlock {} blockSlot {}", parentBeaconBlock, blockSlot);

    final Bytes32 parentExecutionHash =
        recentChainData
            .getExecutionBlockHashForBlock(parentBeaconBlock)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Failed to retrieve execution payload hash from beacon block root"));

    final UInt64 timestamp = spec.computeTimeAtSlot(blockSlot, recentChainData.getGenesisTime());

    final ForkChoiceUpdateData localForkChoiceUpdateData =
        getForkChoiceUpdateDataForPayloadId(blockSlot);

    validateForkChoiceHeadMatchesParent(
        localForkChoiceUpdateData, parentBeaconBlock, parentExecutionHash);

    if (parentExecutionHash.isZero() && !localForkChoiceUpdateData.hasTerminalBlockHash()) {
      // Pre-merge so ok to use default payload
      return SafeFuture.completedFuture(Optional.empty());
    }

    final Optional<PayloadBuildSession> productionSession =
        forkChoiceUpdateContext.getProductionSessionFor(blockSlot);
    if (productionSession.isPresent()
        && !hasPayloadAttributesForProduction(
            localForkChoiceUpdateData, parentBeaconBlock, blockSlot)) {
      final PayloadBuildSession session = productionSession.orElseThrow();
      // Production may arrive while the payload attributes calculation is still in flight. In that
      // case, wait for the calculation so the payload id is requested with attributes pinned to the
      // requested parent and slot.
      if (!session.arePayloadAttributesApplied()) {
        LOG.debug(
            "Waiting for pending payload attributes for block production at slot {}", blockSlot);
        return session
            .getPayloadAttributesApplied()
            .thenCompose(
                __ ->
                    eventThread.executeFuture(
                        () -> internalGetPayloadId(parentBeaconBlock, blockSlot)));
      }
      // If the calculation has already completed but still did not produce matching attributes,
      // do not wait again. Fall through to the validation below so we fail fast instead of
      // recursively re-entering this branch.
    }

    validatePayloadAttributesMatchBlockProductionRequest(
        localForkChoiceUpdateData, parentBeaconBlock, blockSlot);

    if (!localForkChoiceUpdateData.isPayloadIdSuitable(parentExecutionHash, timestamp)) {
      throw new IllegalStateException(
          String.format(
              "No suitable payloadId for block production at slot %s using fork choice update data %s",
              blockSlot, localForkChoiceUpdateData));
    }

    forkChoiceUpdateContext
        .getPayloadBuildSession()
        .ifPresent(session -> session.markPayloadIdRequested(blockSlot));

    return localForkChoiceUpdateData
        .getExecutionPayloadContext()
        .thenApply(
            maybeExecutionPayloadContext -> {
              if (maybeExecutionPayloadContext.isEmpty()) {
                throw new IllegalStateException("Unable to obtain an executionPayloadContext");
              }
              return maybeExecutionPayloadContext;
            });
  }

  private ForkChoiceUpdateData getForkChoiceUpdateDataForPayloadId(final UInt64 blockSlot) {
    return forkChoiceUpdateContext.getForkChoiceUpdateDataForPayloadId(blockSlot);
  }

  private ForkChoiceUpdateData getCurrentForkChoiceUpdateData() {
    return forkChoiceUpdateContext.getForkChoiceUpdateData();
  }

  private void validateForkChoiceHeadMatchesParent(
      final ForkChoiceUpdateData forkChoiceUpdateData,
      final ForkChoiceNode parentBeaconBlock,
      final Bytes32 parentExecutionHash) {
    if (parentExecutionHash.isZero()) {
      return;
    }

    checkState(
        forkChoiceUpdateData.getForkChoiceState().headBlock().equals(parentBeaconBlock),
        "Fork choice update head %s does not match requested block production parent %s",
        forkChoiceUpdateData.getForkChoiceState().headBlock(),
        parentBeaconBlock);
  }

  private void validatePayloadAttributesMatchBlockProductionRequest(
      final ForkChoiceUpdateData forkChoiceUpdateData,
      final ForkChoiceNode parentBeaconBlock,
      final UInt64 blockSlot) {
    final PayloadBuildingAttributes payloadBuildingAttributes =
        forkChoiceUpdateData
            .getPayloadBuildingAttributes()
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        String.format(
                            "Payload building attributes are missing for block production at slot %s",
                            blockSlot)));

    checkState(
        payloadBuildingAttributes.proposalSlot().equals(blockSlot),
        "Payload building attributes slot %s does not match requested block production slot %s",
        payloadBuildingAttributes.proposalSlot(),
        blockSlot);
    checkState(
        payloadBuildingAttributes.parentBeaconBlock().equals(parentBeaconBlock),
        "Payload building attributes parent %s does not match requested block production parent %s",
        payloadBuildingAttributes.parentBeaconBlock(),
        parentBeaconBlock);
  }

  private boolean hasPayloadAttributesForProduction(
      final ForkChoiceUpdateData forkChoiceUpdateData,
      final ForkChoiceNode parentBeaconBlock,
      final UInt64 blockSlot) {
    return forkChoiceUpdateData
        .getPayloadBuildingAttributes()
        .filter(payloadAttributes -> payloadAttributes.proposalSlot().equals(blockSlot))
        .filter(
            payloadAttributes -> payloadAttributes.parentBeaconBlock().equals(parentBeaconBlock))
        .isPresent();
  }

  private void internalForkChoiceUpdated(
      final ForkChoiceState forkChoiceState, final Optional<UInt64> requestedBlockProductionSlot) {
    eventThread.checkOnEventThread();

    LOG.debug("internalForkChoiceUpdated forkChoiceState {}", forkChoiceState);

    clearProductionSessionIfHeadAdvanced(forkChoiceState);

    if (requestedBlockProductionSlot.isPresent()) {
      startOrPromoteProductionSessionAndSendForkChoiceUpdated(
          forkChoiceState, requestedBlockProductionSlot.get());
      return;
    }

    // this is the slot for which we should calculate payload attributes for, in case we are a
    // proposer for that slot. it will be empty only in pre-genesis.
    final Optional<UInt64> payloadAttributesSlot =
        requestedBlockProductionSlot.or(() -> calculatePayloadAttributesSlot(forkChoiceState));

    if (shouldSkipForkChoiceUpdateDueToLateBlockReorg(forkChoiceState, payloadAttributesSlot)) {
      return;
    }

    if (forkChoiceUpdateContext.isBlockingForkChoiceUpdates()) {
      final PayloadBuildSession blockingSession =
          forkChoiceUpdateContext.getPayloadBuildSession().orElseThrow();
      LOG.debug(
          "internalForkChoiceUpdated skipped — pinned for slot {} until payloadId is requested",
          blockingSession.getProposalSlot());
      return;
    }

    final Optional<PayloadBuildSession> sameSlotSession =
        payloadAttributesSlot
            .flatMap(forkChoiceUpdateContext::getSessionFor)
            .filter(session -> session.hasForkChoiceState(forkChoiceState));
    if (sameSlotSession.isPresent()) {
      LOG.debug("nothing changed, send FCU again");
      sendForkChoiceUpdated(sameSlotSession.orElseThrow().getForkChoiceUpdateData());
      return;
    }

    forkChoiceUpdateContext =
        ForkChoiceUpdateContext.plain(
            forkChoiceUpdateContext.getForkChoiceUpdateData().withForkChoiceState(forkChoiceState));

    LOG.debug(
        "internalForkChoiceUpdated forkChoiceUpdateData {}",
        forkChoiceUpdateContext.getForkChoiceUpdateData());

    payloadAttributesSlot.ifPresentOrElse(
        slot -> setupPayloadBuildSessionAndSendForkChoiceUpdated(forkChoiceState, slot, false),
        this::sendForkChoiceUpdated);
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
  private Optional<UInt64> calculatePayloadAttributesSlot(final ForkChoiceState forkChoiceState) {

    final Optional<UInt64> currentSlot = recentChainData.getCurrentSlot();
    if (currentSlot.isEmpty()) {
      // We are pre-genesis, so we don't care about proposing slots
      return Optional.empty();
    }

    final Optional<UInt64> maybeCurrentPayloadAttributesSlot =
        getCurrentForkChoiceUpdateData()
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

  private boolean shouldSkipForkChoiceUpdateDueToLateBlockReorg(
      final ForkChoiceState forkChoiceState, final Optional<UInt64> proposingSlot) {
    if (!forkChoiceLateBlockReorgEnabled || proposingSlot.isEmpty()) {
      return false;
    }
    final boolean shouldOverrideForkChoiceUpdate =
        recentChainData.shouldOverrideForkChoiceUpdate(
            forkChoiceState.headBlock().blockRoot(), forkChoiceState.headBlockSlot());
    if (!shouldOverrideForkChoiceUpdate) {
      return false;
    }
    LOG.debug(
        "internalForkChoiceUpdated skipped due to late block reorg override producing block at slot {}",
        proposingSlot.orElseThrow());
    return true;
  }

  private void prepareNextSlotProposal(final UInt64 slot) {
    // Assume `slot` is empty and check if we need to prepare to propose in the next slot
    final UInt64 proposalSlot = slot.plus(1);
    if (forkChoiceUpdateContext.isBlockingForkChoiceUpdates()) {
      final PayloadBuildSession blockingSession =
          forkChoiceUpdateContext.getPayloadBuildSession().orElseThrow();
      LOG.debug(
          "Skipping payload attributes update for slot {} while pinned for slot {}",
          proposalSlot,
          blockingSession.getProposalSlot());
      return;
    }
    final Optional<PayloadBuildSession> existingSession =
        forkChoiceUpdateContext.getSessionFor(proposalSlot);
    if (existingSession.isPresent()) {
      sendForkChoiceUpdated(existingSession.orElseThrow().getForkChoiceUpdateData());
      return;
    }
    setupPayloadBuildSessionAndSendForkChoiceUpdated(
        getCurrentForkChoiceUpdateData().getForkChoiceState(), proposalSlot, false);
  }

  private void sendForkChoiceUpdated() {
    sendForkChoiceUpdated(forkChoiceUpdateContext.getForkChoiceUpdateData());
  }

  private void sendForkChoiceUpdated(final ForkChoiceUpdateData forkChoiceUpdateData) {
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

  private void setupPayloadBuildSessionAndSendForkChoiceUpdated(
      final ForkChoiceState forkChoiceState,
      final UInt64 payloadAttributesSlot,
      final boolean production) {
    final ForkChoiceUpdateData baseForkChoiceUpdateData = getCurrentForkChoiceUpdateData();
    final ForkChoiceUpdateData sessionForkChoiceUpdateData =
        production
            ? baseForkChoiceUpdateData.withFreshForkChoiceState(forkChoiceState)
            : baseForkChoiceUpdateData.withForkChoiceState(forkChoiceState);
    final PayloadBuildSession session =
        new PayloadBuildSession(payloadAttributesSlot, sessionForkChoiceUpdateData, production);
    forkChoiceUpdateContext = ForkChoiceUpdateContext.preparing(session);

    final SafeFuture<Optional<PayloadBuildingAttributes>> payloadBuildingAttributesFuture =
        proposersDataManager.calculatePayloadBuildingAttributes(
            payloadAttributesSlot, inSync, session.getForkChoiceUpdateData(), production);

    payloadBuildingAttributesFuture
        .thenAcceptAsync(
            payloadBuildingAttributes ->
                completePayloadBuildSessionAndSendForkChoiceUpdated(
                    session, payloadBuildingAttributes),
            eventThread)
        .finish(
            error -> {
              if (production
                  && !session.getForkChoiceUpdateData().getExecutionPayloadContext().isDone()) {
                session
                    .getForkChoiceUpdateData()
                    .getExecutionPayloadContext()
                    .completeExceptionally(error);
              }
              session.completePayloadAttributesExceptionally(error);
              LOG.error(
                  "Failed to calculate payload attributes for slot {}",
                  payloadAttributesSlot,
                  error);
            });
  }

  private void startOrPromoteProductionSessionAndSendForkChoiceUpdated(
      final ForkChoiceState forkChoiceState, final UInt64 proposalSlot) {
    final Optional<PayloadBuildSession> existingSession =
        forkChoiceUpdateContext
            .getSessionFor(proposalSlot)
            .filter(session -> session.hasForkChoiceState(forkChoiceState));
    if (existingSession.isPresent()) {
      final PayloadBuildSession session = existingSession.orElseThrow();
      if (hasPayloadAttributesForProduction(
          session.getForkChoiceUpdateData(), forkChoiceState.headBlock(), proposalSlot)) {
        LOG.debug("promoting to production session for slot {}", proposalSlot);
        session.promoteToProduction();
        sendForkChoiceUpdated(session.getForkChoiceUpdateData());
        return;
      }
    }
    LOG.debug("start building session for slot {}", proposalSlot);
    setupPayloadBuildSessionAndSendForkChoiceUpdated(forkChoiceState, proposalSlot, true);
  }

  private void completePayloadBuildSessionAndSendForkChoiceUpdated(
      final PayloadBuildSession session,
      final Optional<PayloadBuildingAttributes> payloadBuildingAttributes) {
    if (!isCurrentPayloadBuildSession(session)) {
      if (session.isProduction()) {
        session.getForkChoiceUpdateData().getExecutionPayloadContext().complete(Optional.empty());
      }
      session.completePayloadAttributesApplied();
      LOG.debug(
          "Ignoring stale payload attributes for slot {} because payload build session has changed",
          session.getProposalSlot());
      return;
    }

    session.withPayloadBuildingAttributes(payloadBuildingAttributes);
    sendForkChoiceUpdated(session.getForkChoiceUpdateData());
    session.completePayloadAttributesApplied();

    LOG.debug(
        "completePayloadBuildSession for slot {}: {}",
        session.getProposalSlot(),
        payloadBuildingAttributes);
  }

  @SuppressWarnings("ReferenceComparison")
  private boolean isCurrentPayloadBuildSession(final PayloadBuildSession session) {
    return forkChoiceUpdateContext
        .getPayloadBuildSession()
        .filter(currentSession -> currentSession == session)
        .isPresent();
  }

  private void clearProductionSessionIfHeadAdvanced(final ForkChoiceState forkChoiceState) {
    final Optional<PayloadBuildSession> advancedProductionSession =
        forkChoiceUpdateContext
            .getPayloadBuildSession()
            .filter(PayloadBuildSession::isProduction)
            .filter(
                session ->
                    forkChoiceState
                        .headBlockSlot()
                        .isGreaterThanOrEqualTo(session.getProposalSlot()));
    if (advancedProductionSession.isEmpty()) {
      return;
    }
    LOG.debug(
        "internalForkChoiceUpdated clearing pin for slot {} (head advanced to slot {})",
        advancedProductionSession.orElseThrow().getProposalSlot(),
        forkChoiceState.headBlockSlot());
    forkChoiceUpdateContext =
        ForkChoiceUpdateContext.plain(forkChoiceUpdateContext.getForkChoiceUpdateData());
  }
}
