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

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.infrastructure.logging.P2PLogger.P2P_LOG;
import static tech.pegasys.teku.infrastructure.time.TimeUtilities.secondsToMillis;
import static tech.pegasys.teku.spec.constants.NetworkConstants.INTERVALS_PER_SLOT;
import static tech.pegasys.teku.statetransition.forkchoice.StateRootCollector.addParentStateRoots;

import com.google.common.base.Throwables;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.ExceptionThrowingRunnable;
import tech.pegasys.teku.infrastructure.async.ExceptionThrowingSupplier;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.eventthread.EventThread;
import tech.pegasys.teku.infrastructure.exceptions.ExceptionUtil;
import tech.pegasys.teku.infrastructure.exceptions.FatalServiceFailureException;
import tech.pegasys.teku.infrastructure.subscribers.Subscribers;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.cache.CapturingIndexedAttestationCache;
import tech.pegasys.teku.spec.cache.IndexedAttestationCache;
import tech.pegasys.teku.spec.datastructures.attestation.ValidateableAttestation;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.forkchoice.InvalidCheckpointException;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyForkChoiceStrategy;
import tech.pegasys.teku.spec.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.spec.datastructures.forkchoice.VoteUpdater;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.IndexedAttestation;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.util.AttestationProcessingResult;
import tech.pegasys.teku.spec.datastructures.util.AttestationProcessingResult.Status;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannel;
import tech.pegasys.teku.spec.executionlayer.PayloadStatus;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.StateTransitionException;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult.FailureReason;
import tech.pegasys.teku.spec.logic.common.util.ForkChoiceUtil;
import tech.pegasys.teku.statetransition.attestation.DeferredAttestations;
import tech.pegasys.teku.statetransition.block.BlockImportPerformance;
import tech.pegasys.teku.statetransition.validation.AttestationStateSelector;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.protoarray.DeferredVotes;
import tech.pegasys.teku.storage.protoarray.ForkChoiceStrategy;
import tech.pegasys.teku.storage.store.UpdatableStore;
import tech.pegasys.teku.storage.store.UpdatableStore.StoreTransaction;

public class ForkChoice implements ForkChoiceUpdatedResultSubscriber {

  public static final int BLOCK_CREATION_TOLERANCE_MS = 500;
  private static final Logger LOG = LogManager.getLogger();

  private final Spec spec;
  private final EventThread forkChoiceExecutor;
  private final ForkChoiceStateProvider forkChoiceStateProvider;
  private final RecentChainData recentChainData;
  private final ForkChoiceNotifier forkChoiceNotifier;
  private final MergeTransitionBlockValidator transitionBlockValidator;
  private final boolean forkChoiceUpdateHeadOnBlockImportEnabled;
  private final AttestationStateSelector attestationStateSelector;
  private final DeferredAttestations deferredAttestations = new DeferredAttestations();
  private final PandaPrinter pandaPrinter;

  private final Subscribers<OptimisticHeadSubscriber> optimisticSyncSubscribers =
      Subscribers.create(true);
  private final TickProcessor tickProcessor;
  private Optional<Boolean> optimisticSyncing = Optional.empty();

  public ForkChoice(
      final Spec spec,
      final EventThread forkChoiceExecutor,
      final RecentChainData recentChainData,
      final ForkChoiceNotifier forkChoiceNotifier,
      final ForkChoiceStateProvider forkChoiceStateProvider,
      final TickProcessor tickProcessor,
      final MergeTransitionBlockValidator transitionBlockValidator,
      final PandaPrinter pandaPrinter,
      final boolean forkChoiceUpdateHeadOnBlockImportEnabled) {
    this.spec = spec;
    this.forkChoiceExecutor = forkChoiceExecutor;
    this.forkChoiceStateProvider = forkChoiceStateProvider;
    this.recentChainData = recentChainData;
    this.forkChoiceNotifier = forkChoiceNotifier;
    this.transitionBlockValidator = transitionBlockValidator;
    this.pandaPrinter = pandaPrinter;
    this.attestationStateSelector = new AttestationStateSelector(spec, recentChainData);
    this.tickProcessor = tickProcessor;
    this.forkChoiceUpdateHeadOnBlockImportEnabled = forkChoiceUpdateHeadOnBlockImportEnabled;
    recentChainData.subscribeStoreInitialized(this::initializeProtoArrayForkChoice);
    forkChoiceNotifier.subscribeToForkChoiceUpdatedResult(this);
  }

  /**
   * @deprecated Provided only to avoid having to hard code forkChoiceUpdateHeadOnBlockImportEnabled
   *     in lots of tests. Will be removed when the feature toggle is removed.
   */
  @Deprecated
  public ForkChoice(
      final Spec spec,
      final EventThread forkChoiceExecutor,
      final RecentChainData recentChainData,
      final ForkChoiceNotifier forkChoiceNotifier,
      final MergeTransitionBlockValidator transitionBlockValidator) {
    this(
        spec,
        forkChoiceExecutor,
        recentChainData,
        forkChoiceNotifier,
        new ForkChoiceStateProvider(forkChoiceExecutor, recentChainData),
        new TickProcessor(spec, recentChainData),
        transitionBlockValidator,
        PandaPrinter.NOOP,
        true);
  }

  @Override
  public void onForkChoiceUpdatedResult(
      final ForkChoiceUpdatedResultNotification forkChoiceUpdatedResultNotification) {
    forkChoiceUpdatedResultNotification
        .getForkChoiceUpdatedResult()
        .thenAccept(
            maybeForkChoiceUpdatedResult ->
                maybeForkChoiceUpdatedResult.ifPresent(
                    forkChoiceUpdatedResult -> {
                      if (forkChoiceUpdatedResultNotification.isTerminalBlockCall()
                          && forkChoiceUpdatedResult.getPayloadStatus().hasInvalidStatus()) {
                        LOG.error(
                            "Execution engine considers INVALID recently provided terminal block {}",
                            forkChoiceUpdatedResultNotification
                                .getForkChoiceState()
                                .getHeadExecutionBlockHash());
                        return;
                      }
                      onExecutionPayloadResult(
                          forkChoiceUpdatedResultNotification
                              .getForkChoiceState()
                              .getHeadBlockRoot(),
                          forkChoiceUpdatedResult.getPayloadStatus());
                    }))
        .finish(error -> LOG.error("Failed to update fork choice", error));
  }

  private void initializeProtoArrayForkChoice() {
    processHead().join();
  }

  public SafeFuture<Boolean> processHead() {
    return processHead(Optional.empty(), false);
  }

  public SafeFuture<Boolean> processHead(final UInt64 nodeSlot) {
    return processHead(Optional.of(nodeSlot), false);
  }

  public SafeFuture<Boolean> processHead(final UInt64 nodeSlot, final boolean isPreProposal) {
    return processHead(Optional.of(nodeSlot), isPreProposal);
  }

  private SafeFuture<Boolean> processHead(
      final Optional<UInt64> nodeSlot, final boolean isPreProposal) {
    final Checkpoint retrievedJustifiedCheckpoint =
        recentChainData.getStore().getJustifiedCheckpoint();
    return recentChainData
        .retrieveCheckpointState(retrievedJustifiedCheckpoint)
        .thenCompose(
            justifiedCheckpointState ->
                onForkChoiceThread(
                    () -> {
                      final Checkpoint finalizedCheckpoint =
                          recentChainData.getStore().getFinalizedCheckpoint();
                      final Checkpoint justifiedCheckpoint =
                          recentChainData.getStore().getJustifiedCheckpoint();
                      if (!justifiedCheckpoint.equals(retrievedJustifiedCheckpoint)) {
                        LOG.debug(
                            "Skipping head block update as justified checkpoint was updated while loading checkpoint state. Was {} ({}) but now {} ({})",
                            retrievedJustifiedCheckpoint.getEpoch(),
                            retrievedJustifiedCheckpoint.getRoot(),
                            justifiedCheckpoint.getEpoch(),
                            justifiedCheckpoint.getRoot());
                        return false;
                      }
                      final VoteUpdater transaction = recentChainData.startVoteUpdate();
                      final ReadOnlyForkChoiceStrategy forkChoiceStrategy = getForkChoiceStrategy();
                      final BeaconState justifiedState = justifiedCheckpointState.orElseThrow();
                      final List<UInt64> justifiedEffectiveBalances =
                          spec.getBeaconStateUtil(justifiedState.getSlot())
                              .getEffectiveBalances(justifiedState);

                      Bytes32 headBlockRoot =
                          transaction.applyForkChoiceScoreChanges(
                              recentChainData.getCurrentEpoch().orElseThrow(),
                              finalizedCheckpoint,
                              justifiedCheckpoint,
                              justifiedEffectiveBalances,
                              recentChainData.getStore().getProposerBoostRoot(),
                              spec.getProposerBoostAmount(justifiedState));

                      recentChainData.updateHead(
                          headBlockRoot,
                          nodeSlot.orElse(
                              forkChoiceStrategy
                                  .blockSlot(headBlockRoot)
                                  .orElseThrow(
                                      () ->
                                          new IllegalStateException(
                                              "Unable to retrieve the slot of fork choice head: "
                                                  + headBlockRoot))));

                      transaction.commit();
                      notifyForkChoiceUpdatedAndOptimisticSyncingChanged(
                          isPreProposal ? nodeSlot : Optional.empty());

                      return true;
                    }));
  }

  /** Import a block to the store. */
  public SafeFuture<BlockImportResult> onBlock(
      final SignedBeaconBlock block,
      final Optional<BlockImportPerformance> blockImportPerformance,
      final ExecutionLayerChannel executionLayer) {
    return recentChainData
        .retrieveStateAtSlot(new SlotAndBlockRoot(block.getSlot(), block.getParentRoot()))
        .thenPeek(__ -> blockImportPerformance.ifPresent(BlockImportPerformance::preStateRetrieved))
        .thenCompose(
            blockSlotState ->
                onBlock(block, blockSlotState, blockImportPerformance, executionLayer));
  }

  /**
   * Import a block to the store. The supplied blockSlotState must already have empty slots
   * processed to the same slot as the block.
   */
  private SafeFuture<BlockImportResult> onBlock(
      final SignedBeaconBlock block,
      final Optional<BeaconState> blockSlotState,
      final Optional<BlockImportPerformance> blockImportPerformance,
      final ExecutionLayerChannel executionLayer) {
    if (blockSlotState.isEmpty()) {
      return SafeFuture.completedFuture(BlockImportResult.FAILED_UNKNOWN_PARENT);
    }
    checkArgument(
        block.getSlot().equals(blockSlotState.get().getSlot()),
        "State must have processed slots up to the block slot. Block slot %s, state slot %s",
        block.getSlot(),
        blockSlotState.get().getSlot());

    final ForkChoicePayloadExecutor payloadExecutor =
        ForkChoicePayloadExecutor.create(spec, recentChainData, block, executionLayer);
    final ForkChoiceUtil forkChoiceUtil = spec.atSlot(block.getSlot()).getForkChoiceUtil();
    final BlockImportResult preconditionCheckResult =
        forkChoiceUtil.checkOnBlockConditions(
            block, blockSlotState.get(), recentChainData.getStore());
    if (!preconditionCheckResult.isSuccessful()) {
      reportInvalidBlock(block, preconditionCheckResult);
      return SafeFuture.completedFuture(preconditionCheckResult);
    }

    final CapturingIndexedAttestationCache indexedAttestationCache =
        IndexedAttestationCache.capturing();

    final BeaconState postState;
    try {
      postState =
          spec.getBlockProcessor(block.getSlot())
              .processAndValidateBlock(
                  block,
                  blockSlotState.get(),
                  indexedAttestationCache,
                  Optional.of(payloadExecutor));
    } catch (final StateTransitionException e) {
      final BlockImportResult result = BlockImportResult.failedStateTransition(e);
      reportInvalidBlock(block, result);
      return SafeFuture.completedFuture(result);
    }
    blockImportPerformance.ifPresent(BlockImportPerformance::postStateCreated);

    return payloadExecutor
        .getExecutionResult()
        .thenPeek(
            __ -> blockImportPerformance.ifPresent(BlockImportPerformance::executionResultReceived))
        .thenApplyAsync(
            payloadResult ->
                importBlockAndState(
                    block,
                    blockSlotState.get(),
                    blockImportPerformance,
                    forkChoiceUtil,
                    indexedAttestationCache,
                    postState,
                    payloadResult),
            forkChoiceExecutor)
        .thenPeek(
            result -> {
              if (result.isSuccessful()) {
                blockSlotState.ifPresent(state -> pandaPrinter.onBlockImported(state, block));
              }
            });
  }

  private BlockImportResult importBlockAndState(
      final SignedBeaconBlock block,
      final BeaconState blockSlotState,
      final Optional<BlockImportPerformance> blockImportPerformance,
      final ForkChoiceUtil forkChoiceUtil,
      final CapturingIndexedAttestationCache indexedAttestationCache,
      final BeaconState postState,
      final PayloadValidationResult payloadValidationResult) {
    blockImportPerformance.ifPresent(BlockImportPerformance::beginImporting);
    final PayloadStatus payloadResult = payloadValidationResult.getStatus();
    if (payloadResult.hasInvalidStatus()) {
      final BlockImportResult result =
          BlockImportResult.failedStateTransition(
              new IllegalStateException(
                  "Invalid ExecutionPayload: "
                      + payloadResult.getValidationError().orElse("No reason provided")));
      reportInvalidBlock(block, result);
      payloadValidationResult
          .getInvalidTransitionBlockRoot()
          .ifPresentOrElse(
              invalidTransitionBlockRoot ->
                  getForkChoiceStrategy()
                      .onExecutionPayloadResult(invalidTransitionBlockRoot, payloadResult, true),
              () ->
                  getForkChoiceStrategy()
                      .onExecutionPayloadResult(block.getParentRoot(), payloadResult, false));
      return result;
    }

    if (payloadResult.hasNotValidatedStatus()
        && !spec.atSlot(block.getSlot())
            .getForkChoiceUtil()
            .canOptimisticallyImport(recentChainData.getStore(), block)) {
      return BlockImportResult.FAILED_EXECUTION_PAYLOAD_EXECUTION_SYNCING;
    }

    if (payloadResult.hasFailedExecution()) {
      return BlockImportResult.failedExecutionPayloadExecution(
          payloadResult.getFailureCause().orElseThrow());
    }

    final ForkChoiceStrategy forkChoiceStrategy = getForkChoiceStrategy();

    // Now that we're on the fork choice thread, make sure the block still descends from finalized
    // (which may have changed while we were processing the block)
    if (!forkChoiceUtil.blockDescendsFromLatestFinalizedBlock(
        block, recentChainData.getStore(), forkChoiceStrategy)) {
      return BlockImportResult.FAILED_INVALID_ANCESTRY;
    }

    final StoreTransaction transaction = recentChainData.startStoreTransaction();
    addParentStateRoots(spec, blockSlotState, transaction);
    forkChoiceUtil.applyBlockToStore(
        transaction, block, postState, payloadResult.hasNotValidatedStatus());

    if (spec.getCurrentSlot(transaction).equals(block.getSlot())) {
      final UInt64 millisPerSlot = spec.getMillisPerSlot(block.getSlot());
      final UInt64 timeIntoSlotMillis = getMillisIntoSlot(transaction, millisPerSlot);

      if (isBeforeAttestingInterval(millisPerSlot, timeIntoSlotMillis)) {
        transaction.setProposerBoostRoot(block.getRoot());
      }
    }

    blockImportPerformance.ifPresent(BlockImportPerformance::transactionReady);
    // Note: not using thenRun here because we want to ensure each step is on the event thread
    transaction.commit().join();
    blockImportPerformance.ifPresent(BlockImportPerformance::transactionCommitted);
    forkChoiceStrategy.onExecutionPayloadResult(block.getRoot(), payloadResult, true);

    final UInt64 currentEpoch = spec.computeEpochAtSlot(spec.getCurrentSlot(transaction));

    // We only need to apply attestations from the current or previous epoch. If the block is from
    // before that, none of the attestations will be applicable so just skip the whole step.
    if (spec.computeEpochAtSlot(block.getSlot())
        .isGreaterThanOrEqualTo(currentEpoch.minusMinZero(1))) {
      final VoteUpdater voteUpdater = recentChainData.startVoteUpdate();
      // We also need to handle recent AttesterSlashings to update equivocating validator indices
      // We don't need any epochs older than previous as it doesn't affect ForkChoice
      applyAttesterSlashingsFromBlock(block, voteUpdater);
      applyVotesFromBlock(forkChoiceStrategy, currentEpoch, indexedAttestationCache, voteUpdater);
      voteUpdater.commit();
    }

    final BlockImportResult result;
    if (payloadResult.hasValidStatus()) {
      result = BlockImportResult.successful(block);
    } else {
      result = BlockImportResult.optimisticallySuccessful(block);
    }
    if (forkChoiceUpdateHeadOnBlockImportEnabled) {
      updateForkChoiceForImportedBlock(block, result, forkChoiceStrategy);
    }
    notifyForkChoiceUpdatedAndOptimisticSyncingChanged(Optional.empty());
    return result;
  }

  private UInt64 getMillisIntoSlot(StoreTransaction transaction, UInt64 millisPerSlot) {
    return transaction
        .getTimeMillis()
        .minus(secondsToMillis(transaction.getGenesisTime()))
        .mod(millisPerSlot);
  }

  private boolean isBeforeAttestingInterval(
      final UInt64 millisPerSlot, final UInt64 timeIntoSlotMillis) {
    UInt64 oneThirdSlot = millisPerSlot.dividedBy(INTERVALS_PER_SLOT);
    return timeIntoSlotMillis.isLessThan(oneThirdSlot);
  }

  private void onExecutionPayloadResult(
      final Bytes32 blockRoot, final PayloadStatus payloadResult) {
    final SafeFuture<PayloadValidationResult> transitionValidatedStatus;
    if (payloadResult.hasValidStatus()) {
      transitionValidatedStatus = transitionBlockValidator.verifyAncestorTransitionBlock(blockRoot);
    } else {
      transitionValidatedStatus =
          SafeFuture.completedFuture(new PayloadValidationResult(payloadResult));
    }
    transitionValidatedStatus.finishAsync(
        result -> {
          final PayloadStatus resultStatus = result.getStatus();
          final Bytes32 validatedBlockRoot =
              result.getInvalidTransitionBlockRoot().orElse(blockRoot);

          getForkChoiceStrategy().onExecutionPayloadResult(validatedBlockRoot, resultStatus, true);

          if (resultStatus.hasInvalidStatus()) {
            LOG.warn("Will run fork choice because head block {} was invalid", validatedBlockRoot);
            processHead().finish(error -> LOG.error("Fork choice updating head failed", error));
          }
        },
        error -> {
          // Pass FatalServiceFailureException up to the uncaught exception handler.
          // This will cause teku to exit because the error is unrecoverable.
          // We specifically do this here because a FatalServiceFailureException will be thrown if
          // a justified or finalized block is found to be invalid.
          if (ExceptionUtil.hasCause(error, FatalServiceFailureException.class)) {
            Thread.currentThread()
                .getUncaughtExceptionHandler()
                .uncaughtException(Thread.currentThread(), error);
          } else {
            LOG.error("Failed to apply payload result for block {}", blockRoot, error);
          }
        },
        forkChoiceExecutor);
  }

  private void updateForkChoiceForImportedBlock(
      final SignedBeaconBlock block,
      final BlockImportResult result,
      final ForkChoiceStrategy forkChoiceStrategy) {

    final SlotAndBlockRoot bestHeadBlock = findNewChainHead(block, forkChoiceStrategy);
    if (!bestHeadBlock.getBlockRoot().equals(recentChainData.getBestBlockRoot().orElseThrow())) {
      recentChainData.updateHead(bestHeadBlock.getBlockRoot(), bestHeadBlock.getSlot());
      if (bestHeadBlock.getBlockRoot().equals(block.getRoot())) {
        result.markAsCanonical();
      }
    }
  }

  private SlotAndBlockRoot findNewChainHead(
      final SignedBeaconBlock block, final ForkChoiceStrategy forkChoiceStrategy) {
    // If the new block builds on our current chain head it must be the new chain head.
    // Since fork choice works by walking down the tree selecting the child block with
    // the greatest weight, when a block has only one child it will automatically become
    // a better choice than the block itself.  So the first block we receive that is a
    // child of our current chain head, must be the new chain head. If we'd had any other
    // child of the current chain head we'd have already selected it as head.
    if (recentChainData
        .getBestBlockRoot()
        .map(chainHeadRoot -> chainHeadRoot.equals(block.getParentRoot()))
        .orElse(false)) {
      return new SlotAndBlockRoot(block.getSlot(), block.getRoot());
    }

    // Otherwise, use fork choice to find the new chain head as if this block is on time the
    // proposer weighting may cause us to reorg.
    // During sync, this may be noticeably slower than just comparing the chain head due to the way
    // ProtoArray skips updating all ancestors when adding a new block but it's cheap when in sync.
    final Checkpoint justifiedCheckpoint = recentChainData.getJustifiedCheckpoint().orElseThrow();
    final Checkpoint finalizedCheckpoint = recentChainData.getFinalizedCheckpoint().orElseThrow();
    return forkChoiceStrategy.findHead(
        recentChainData.getCurrentEpoch().orElseThrow(), justifiedCheckpoint, finalizedCheckpoint);
  }

  private void reportInvalidBlock(final SignedBeaconBlock block, final BlockImportResult result) {
    if (result.getFailureReason() == FailureReason.BLOCK_IS_FROM_FUTURE) {
      return;
    }
    P2P_LOG.onInvalidBlock(
        block.getSlot(),
        block.getRoot(),
        block.sszSerialize(),
        result.getFailureReason().name(),
        result.getFailureCause());
  }

  private void notifyForkChoiceUpdatedAndOptimisticSyncingChanged(
          final Optional<UInt64> proposingSlot) {
    forkChoiceStateProvider
        .getForkChoiceState()
        .thenAccept(
            forkChoiceState -> {
              forkChoiceNotifier.onForkChoiceUpdated(forkChoiceState, proposingSlot);

              if (optimisticSyncing
                  .map(oldValue -> !oldValue.equals(forkChoiceState.isHeadOptimistic()))
                  .orElse(true)) {
                optimisticSyncing = Optional.of(forkChoiceState.isHeadOptimistic());
                optimisticSyncSubscribers.deliver(
                    OptimisticHeadSubscriber::onOptimisticHeadChanged,
                    forkChoiceState.isHeadOptimistic());
              }
            })
        .ifExceptionGetsHereRaiseABug();
  }

  private void applyVotesFromBlock(
      final ForkChoiceStrategy forkChoiceStrategy,
      final UInt64 currentEpoch,
      final CapturingIndexedAttestationCache indexedAttestationProvider,
      final VoteUpdater voteUpdater) {
    indexedAttestationProvider.getIndexedAttestations().stream()
        .filter(
            attestation -> validateBlockAttestation(forkChoiceStrategy, currentEpoch, attestation))
        .forEach(attestation -> forkChoiceStrategy.onAttestation(voteUpdater, attestation));
  }

  private boolean validateBlockAttestation(
      final ForkChoiceStrategy forkChoiceStrategy,
      final UInt64 currentEpoch,
      final IndexedAttestation attestation) {
    return spec.atSlot(attestation.getData().getSlot())
        .getForkChoiceUtil()
        .validateOnAttestation(forkChoiceStrategy, currentEpoch, attestation.getData())
        .isSuccessful();
  }

  public SafeFuture<AttestationProcessingResult> onAttestation(
      final ValidateableAttestation attestation) {
    return attestationStateSelector
        .getStateToValidate(attestation.getData())
        .thenCompose(
            maybeState -> {
              final UpdatableStore store = recentChainData.getStore();
              final AttestationProcessingResult validationResult =
                  spec.validateAttestation(store, attestation, maybeState);

              if (!validationResult.isSuccessful()) {
                if (validationResult.getStatus() == Status.DEFER_FORK_CHOICE_PROCESSING) {
                  deferredAttestations.addAttestation(getIndexedAttestation(attestation));
                }
                return SafeFuture.completedFuture(validationResult);
              }
              return onForkChoiceThread(
                      () -> {
                        final VoteUpdater transaction = recentChainData.startVoteUpdate();
                        getForkChoiceStrategy()
                            .onAttestation(transaction, getIndexedAttestation(attestation));
                        transaction.commit();
                      })
                  .thenApply(__ -> validationResult);
            })
        .exceptionallyCompose(
            error -> {
              final Throwable rootCause = Throwables.getRootCause(error);
              if (rootCause instanceof InvalidCheckpointException) {
                return SafeFuture.completedFuture(
                    AttestationProcessingResult.invalid(rootCause.getMessage()));
              }
              return SafeFuture.failedFuture(error);
            });
  }

  public void applyIndexedAttestations(final List<ValidateableAttestation> attestations) {
    onForkChoiceThread(
            () -> {
              final VoteUpdater transaction = recentChainData.startVoteUpdate();
              final ForkChoiceStrategy forkChoiceStrategy = getForkChoiceStrategy();
              attestations.stream()
                  .map(this::getIndexedAttestation)
                  .forEach(
                      attestation -> forkChoiceStrategy.onAttestation(transaction, attestation));
              transaction.commit();
            })
        .ifExceptionGetsHereRaiseABug();
  }

  private SafeFuture<Void> applyDeferredAttestations(
      final Collection<DeferredVotes> deferredVoteUpdates) {
    return onForkChoiceThread(
        () -> {
          final VoteUpdater transaction = recentChainData.startVoteUpdate();
          final ForkChoiceStrategy forkChoiceStrategy = getForkChoiceStrategy();
          deferredVoteUpdates.forEach(
              update -> forkChoiceStrategy.applyDeferredAttestations(transaction, update));
          transaction.commit();
        });
  }

  private void applyAttesterSlashingsFromBlock(
      final SignedBeaconBlock signedBeaconBlock, final VoteUpdater voteUpdater) {
    signedBeaconBlock
        .getMessage()
        .getBody()
        .getAttesterSlashings()
        .forEach(attesterSlashing -> storeEquivocatingIndices(attesterSlashing, voteUpdater));
  }

  public void onAttesterSlashing(
      final AttesterSlashing slashing,
      InternalValidationResult validationStatus,
      boolean fromNetwork) {
    if (!validationStatus.isAccept()) {
      return;
    }
    onForkChoiceThread(
            () -> {
              final VoteUpdater transaction = recentChainData.startVoteUpdate();
              storeEquivocatingIndices(slashing, transaction);
              transaction.commit();
            })
        .ifExceptionGetsHereRaiseABug();
  }

  private void storeEquivocatingIndices(
      final AttesterSlashing attesterSlashing, final VoteUpdater transaction) {
    attesterSlashing
        .getIntersectingValidatorIndices()
        .forEach(
            validatorIndex -> {
              final VoteTracker voteTracker = transaction.getVote(validatorIndex);
              transaction.putVote(validatorIndex, voteTracker.createNextEquivocating());
            });
  }

  public void onTick(
      final UInt64 currentTimeMillis, final Optional<TickProcessingPerformance> performanceRecord) {
    final UInt64 previousSlot = spec.getCurrentSlot(recentChainData.getStore());
    tickProcessor.onTick(currentTimeMillis).join();
    performanceRecord.ifPresent(TickProcessingPerformance::tickProcessorComplete);
    final UInt64 currentSlot = spec.getCurrentSlot(recentChainData.getStore());
    if (currentSlot.isGreaterThan(previousSlot)) {
      applyDeferredAttestations(currentSlot).ifExceptionGetsHereRaiseABug();
    }
    performanceRecord.ifPresent(TickProcessingPerformance::deferredAttestationsApplied);
  }

  public SafeFuture<Void> prepareForBlockProduction(final UInt64 slot) {
    final UInt64 slotStartTimeMillis =
        spec.getSlotStartTimeMillis(slot, recentChainData.getGenesisTimeMillis());
    final UInt64 currentTime = recentChainData.getStore().getTimeMillis();
    // We haven't yet transitioned into this slot but will do very soon, so make it happen now
    if (!currentTime
        .plus(BLOCK_CREATION_TOLERANCE_MS)
        .isGreaterThanOrEqualTo(slotStartTimeMillis)) {
      // Creating a block too far before the slot start, don't run fork choice at all.
      LOG.warn(
          "Block creation requested more than {}ms before the start of slot {}",
          BLOCK_CREATION_TOLERANCE_MS,
          slot);
      return SafeFuture.COMPLETE;
    }

    return SafeFuture.allOf(
            tickProcessor.onTick(slotStartTimeMillis), applyDeferredAttestations(slot))
        .thenCompose(__ -> processHead(slot, true))
        .toVoid();
  }

  private SafeFuture<Void> applyDeferredAttestations(final UInt64 slot) {
    final Collection<DeferredVotes> deferredVoteUpdates = deferredAttestations.prune(slot);
    if (deferredVoteUpdates.isEmpty()) {
      return SafeFuture.COMPLETE;
    }
    return applyDeferredAttestations(deferredVoteUpdates);
  }

  private ForkChoiceStrategy getForkChoiceStrategy() {
    forkChoiceExecutor.checkOnEventThread();
    return recentChainData
        .getUpdatableForkChoiceStrategy()
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Attempting to perform fork choice operations before store has been initialized"));
  }

  private IndexedAttestation getIndexedAttestation(final ValidateableAttestation attestation) {
    return attestation
        .getIndexedAttestation()
        .orElseThrow(
            () ->
                new UnsupportedOperationException(
                    "ValidateableAttestation does not have an IndexedAttestation."));
  }

  private SafeFuture<Void> onForkChoiceThread(final ExceptionThrowingRunnable task) {
    return onForkChoiceThread(
        () -> {
          task.run();
          return null;
        });
  }

  private <T> SafeFuture<T> onForkChoiceThread(final ExceptionThrowingSupplier<T> task) {
    return forkChoiceExecutor.execute(task);
  }

  public Optional<Boolean> getOptimisticSyncing() {
    return optimisticSyncing;
  }

  public void subscribeToOptimisticHeadChangesAndUpdate(OptimisticHeadSubscriber subscriber) {
    optimisticSyncSubscribers.subscribe(subscriber);
    getOptimisticSyncing().ifPresent(subscriber::onOptimisticHeadChanged);
  }

  public interface OptimisticHeadSubscriber {
    void onOptimisticHeadChanged(boolean isHeadOptimistic);
  }
}
