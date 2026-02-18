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

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.infrastructure.logging.P2PLogger.P2P_LOG;
import static tech.pegasys.teku.statetransition.forkchoice.StateRootCollector.addParentStateRoots;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Throwables;
import java.net.ConnectException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import tech.pegasys.teku.ethereum.performance.trackers.BlockProductionPerformance;
import tech.pegasys.teku.infrastructure.async.ExceptionThrowingRunnable;
import tech.pegasys.teku.infrastructure.async.ExceptionThrowingSupplier;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.eventthread.EventThread;
import tech.pegasys.teku.infrastructure.exceptions.ExceptionUtil;
import tech.pegasys.teku.infrastructure.exceptions.FatalServiceFailureException;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.subscribers.Subscribers;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.cache.CapturingIndexedAttestationCache;
import tech.pegasys.teku.spec.cache.IndexedAttestationCache;
import tech.pegasys.teku.spec.datastructures.attestation.ValidatableAttestation;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.forkchoice.InvalidCheckpointException;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyForkChoiceStrategy;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyStore;
import tech.pegasys.teku.spec.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.spec.datastructures.forkchoice.VoteUpdater;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.IndexedAttestation;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.util.AttestationProcessingResult;
import tech.pegasys.teku.spec.datastructures.util.AttestationProcessingResult.Status;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannel;
import tech.pegasys.teku.spec.executionlayer.ForkChoiceState;
import tech.pegasys.teku.spec.executionlayer.PayloadStatus;
import tech.pegasys.teku.spec.logic.common.statetransition.availability.AvailabilityChecker;
import tech.pegasys.teku.spec.logic.common.statetransition.availability.DataAndValidationResult;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.EpochProcessingException;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.SlotProcessingException;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.StateTransitionException;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult.FailureReason;
import tech.pegasys.teku.spec.logic.common.statetransition.results.ExecutionPayloadImportResult;
import tech.pegasys.teku.spec.logic.common.util.ForkChoiceUtil;
import tech.pegasys.teku.statetransition.attestation.DeferredAttestations;
import tech.pegasys.teku.statetransition.block.BlockImportPerformance;
import tech.pegasys.teku.statetransition.util.DebugDataDumper;
import tech.pegasys.teku.statetransition.validation.AttestationStateSelector;
import tech.pegasys.teku.statetransition.validation.BlockBroadcastValidator;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.storage.client.ChainHead;
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
  private final AttestationStateSelector attestationStateSelector;
  private final DeferredAttestations deferredAttestations = new DeferredAttestations();

  private final Subscribers<OptimisticHeadSubscriber> optimisticSyncSubscribers =
      Subscribers.create(true);
  private final TickProcessor tickProcessor;
  private final boolean forkChoiceLateBlockReorgEnabled;
  private Optional<Boolean> optimisticSyncing = Optional.empty();

  private final AtomicReference<UInt64> lastProcessHeadSlot = new AtomicReference<>();

  private final LabelledMetric<Counter> getProposerHeadSelectedCounter;

  private final DebugDataDumper debugDataDumper;

  public ForkChoice(
      final Spec spec,
      final EventThread forkChoiceExecutor,
      final RecentChainData recentChainData,
      final ForkChoiceNotifier forkChoiceNotifier,
      final ForkChoiceStateProvider forkChoiceStateProvider,
      final TickProcessor tickProcessor,
      final MergeTransitionBlockValidator transitionBlockValidator,
      final boolean forkChoiceLateBlockReorgEnabled,
      final DebugDataDumper debugDataDumper,
      final MetricsSystem metricsSystem) {
    this.spec = spec;
    this.forkChoiceExecutor = forkChoiceExecutor;
    this.forkChoiceStateProvider = forkChoiceStateProvider;
    this.recentChainData = recentChainData;
    this.forkChoiceNotifier = forkChoiceNotifier;
    this.transitionBlockValidator = transitionBlockValidator;
    this.attestationStateSelector =
        new AttestationStateSelector(spec, recentChainData, metricsSystem);
    this.tickProcessor = tickProcessor;
    this.forkChoiceLateBlockReorgEnabled = forkChoiceLateBlockReorgEnabled;
    this.lastProcessHeadSlot.set(UInt64.ZERO);
    LOG.debug("forkChoiceLateBlockReorgEnabled is set to {}", forkChoiceLateBlockReorgEnabled);
    this.debugDataDumper = debugDataDumper;
    getProposerHeadSelectedCounter =
        metricsSystem.createLabelledCounter(
            TekuMetricCategory.BEACON,
            "get_proposer_head_selection_total",
            "when late_block_reorg is enabled, counts based on the proposer parent being based on fork choice, head, or parent of head.",
            "selected_source");
    recentChainData.subscribeStoreInitialized(this::initializeProtoArrayForkChoice);
    forkChoiceNotifier.subscribeToForkChoiceUpdatedResult(this);
  }

  @VisibleForTesting
  public ForkChoice(
      final Spec spec,
      final EventThread forkChoiceExecutor,
      final RecentChainData recentChainData,
      final ForkChoiceNotifier forkChoiceNotifier,
      final MergeTransitionBlockValidator transitionBlockValidator,
      final MetricsSystem metricsSystem) {
    this(
        spec,
        forkChoiceExecutor,
        recentChainData,
        forkChoiceNotifier,
        new ForkChoiceStateProvider(forkChoiceExecutor, recentChainData),
        new TickProcessor(spec, recentChainData),
        transitionBlockValidator,
        false,
        DebugDataDumper.NOOP,
        metricsSystem);
  }

  @Override
  public void onForkChoiceUpdatedResult(
      final ForkChoiceUpdatedResultNotification forkChoiceUpdatedResultNotification) {
    forkChoiceUpdatedResultNotification
        .forkChoiceUpdatedResultFuture()
        .thenAccept(
            forkChoiceUpdatedResult -> {
              if (forkChoiceUpdatedResultNotification.isTerminalBlockCall()
                  && forkChoiceUpdatedResult.getPayloadStatus().hasInvalidStatus()) {
                LOG.error(
                    "Execution engine considers INVALID recently provided terminal block {}",
                    forkChoiceUpdatedResultNotification
                        .forkChoiceState()
                        .getHeadExecutionBlockHash());
                return;
              }
              onExecutionPayloadResult(
                  forkChoiceUpdatedResultNotification.forkChoiceState().getHeadBlockRoot(),
                  forkChoiceUpdatedResult.getPayloadStatus());
            })
        .finish(
            error -> {
              final String errorMessage = "Failed to update fork choice. ";
              if (ExceptionUtil.hasCause(error, ConnectException.class)) {
                LOG.error(errorMessage + error.getMessage());
              } else {
                LOG.error(errorMessage, error);
              }
            });
  }

  public SafeFuture<Boolean> processHead() {
    return processHead(Optional.empty(), false);
  }

  public boolean isForkChoiceLateBlockReorgEnabled() {
    return forkChoiceLateBlockReorgEnabled;
  }

  /** Import a block to the store. */
  public SafeFuture<BlockImportResult> onBlock(
      final SignedBeaconBlock block,
      final Optional<BlockImportPerformance> blockImportPerformance,
      final BlockBroadcastValidator blockBroadcastValidator,
      final ExecutionLayerChannel executionLayer) {
    recentChainData.setBlockTimelinessIfEmpty(block);
    final ForkChoiceUtil forkChoiceUtil = spec.atSlot(block.getSlot()).getForkChoiceUtil();
    return forkChoiceUtil
        .retrievePreStateRequiredOnBlock(recentChainData.getStore(), block)
        .thenPeek(__ -> blockImportPerformance.ifPresent(BlockImportPerformance::preStateRetrieved))
        .thenCompose(
            blockSlotState ->
                onBlock(
                    block,
                    blockSlotState,
                    blockImportPerformance,
                    blockBroadcastValidator,
                    executionLayer,
                    forkChoiceUtil));
  }

  /** Import an execution payload to the store. */
  public SafeFuture<ExecutionPayloadImportResult> onExecutionPayload(
      final SignedExecutionPayloadEnvelope signedEnvelope,
      final ExecutionLayerChannel executionLayer) {
    return recentChainData
        .retrieveBlockState(signedEnvelope.getSlotAndBlockRoot())
        .thenCompose(blockState -> onExecutionPayload(signedEnvelope, blockState, executionLayer));
  }

  public SafeFuture<AttestationProcessingResult> onAttestation(
      final ValidatableAttestation attestation) {
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

  public void applyIndexedAttestations(final List<ValidatableAttestation> attestations) {
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
        .finishStackTrace();
  }

  public void onAttesterSlashing(
      final AttesterSlashing slashing,
      final InternalValidationResult validationStatus,
      final boolean fromNetwork) {
    if (!validationStatus.isAccept()) {
      return;
    }
    onForkChoiceThread(
            () -> {
              final VoteUpdater transaction = recentChainData.startVoteUpdate();
              storeEquivocatingIndices(slashing, transaction);
              transaction.commit();
            })
        .finishStackTrace();
  }

  public void subscribeToOptimisticHeadChangesAndUpdate(final OptimisticHeadSubscriber subscriber) {
    optimisticSyncSubscribers.subscribe(subscriber);
    getOptimisticSyncing().ifPresent(subscriber::onOptimisticHeadChanged);
  }

  public void onTick(
      final UInt64 currentTimeMillis, final Optional<TickProcessingPerformance> performanceRecord) {
    final UpdatableStore store = recentChainData.getStore();
    final UInt64 slotAtStartOfTick = spec.getCurrentSlot(store);
    tickProcessor.onTick(currentTimeMillis).join();
    performanceRecord.ifPresent(TickProcessingPerformance::tickProcessorComplete);
    final UInt64 currentSlot = spec.getCurrentSlot(store);
    if (currentSlot.isGreaterThan(slotAtStartOfTick)) {
      applyDeferredAttestations(currentSlot).finishStackTrace();
    }
    performanceRecord.ifPresent(TickProcessingPerformance::deferredAttestationsApplied);
  }

  private void initializeProtoArrayForkChoice() {
    processHead().join();
  }

  SafeFuture<Boolean> processHead(final UInt64 nodeSlot) {
    return processHead(Optional.of(nodeSlot), false);
  }

  private SafeFuture<Boolean> processHead(
      final Optional<UInt64> nodeSlot, final boolean isPreProposal) {
    final Checkpoint retrievedJustifiedCheckpoint =
        recentChainData.getStore().getJustifiedCheckpoint();
    return recentChainData
        .retrieveCheckpointState(retrievedJustifiedCheckpoint)
        .thenCompose(
            maybeJustifiedCheckpointState ->
                onForkChoiceThread(
                    () -> {
                      final Checkpoint finalizedCheckpoint =
                          recentChainData.getStore().getFinalizedCheckpoint();
                      final Checkpoint justifiedCheckpoint =
                          recentChainData.getStore().getJustifiedCheckpoint();
                      if (!justifiedCheckpoint.equals(retrievedJustifiedCheckpoint)) {
                        LOG.debug(
                            "Skipping head block update as justified checkpoint was updated while loading checkpoint state. Was {} ({}) but now {} ({})",
                            retrievedJustifiedCheckpoint::getEpoch,
                            retrievedJustifiedCheckpoint::getRoot,
                            justifiedCheckpoint::getEpoch,
                            justifiedCheckpoint::getRoot);
                        return false;
                      }
                      if (maybeJustifiedCheckpointState.isEmpty()) {
                        LOG.debug(
                            "Retrieved justified checkpoint state for {} was empty, cannot update head given current information.",
                            justifiedCheckpoint::getRoot);
                        return false;
                      }
                      updateHeadTransaction(
                          nodeSlot,
                          maybeJustifiedCheckpointState.orElseThrow(),
                          finalizedCheckpoint,
                          justifiedCheckpoint);
                      nodeSlot.ifPresent(lastProcessHeadSlot::set);
                      notifyForkChoiceUpdatedAndOptimisticSyncingChanged(
                          isPreProposal ? nodeSlot : Optional.empty());
                      return true;
                    }));
  }

  private void updateHeadTransaction(
      final Optional<UInt64> nodeSlot,
      final BeaconState justifiedState,
      final Checkpoint finalizedCheckpoint,
      final Checkpoint justifiedCheckpoint) {
    if (forkChoiceLateBlockReorgEnabled) {
      recentChainData.getStore().computeBalanceThresholds(justifiedState);
    }
    final VoteUpdater transaction = recentChainData.startVoteUpdate();
    final ReadOnlyForkChoiceStrategy forkChoiceStrategy = getForkChoiceStrategy();
    final List<UInt64> justifiedEffectiveBalances =
        spec.getBeaconStateUtil(justifiedState.getSlot())
            .getEffectiveActiveUnslashedBalances(justifiedState);

    // If a runtime exception occurs while updating protoarray, we could skip the transaction
    // commit.
    // There is no clean way to solve it unless we move to a fully transactional protoarray update.
    // Currently, the assumption is that any exception thrown by design is happening before any
    // update to protoarray, so it is correct to skip the transaction commit.
    final Bytes32 headBlockRoot =
        transaction.applyForkChoiceScoreChanges(
            recentChainData.getCurrentEpoch().orElseThrow(),
            finalizedCheckpoint,
            justifiedCheckpoint,
            justifiedEffectiveBalances,
            recentChainData.getStore().getProposerBoostRoot(),
            spec.getProposerBoostAmount(justifiedState));

    try {
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
    } finally {
      // here we just make sure to commit, because protoarray has been updated. We just had an
      // exception while updating recentChainData which will become consistent again on the next
      // successful updateHead call
      transaction.commit();
    }
  }

  /**
   * Import a block to the store. The supplied blockSlotState must already have empty slots
   * processed to the same slot as the block.
   */
  private SafeFuture<BlockImportResult> onBlock(
      final SignedBeaconBlock block,
      final Optional<BeaconState> blockSlotState,
      final Optional<BlockImportPerformance> blockImportPerformance,
      final BlockBroadcastValidator blockBroadcastValidator,
      final ExecutionLayerChannel executionLayer,
      final ForkChoiceUtil forkChoiceUtil) {
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
    final BlockImportResult preconditionCheckResult =
        forkChoiceUtil.checkOnBlockConditions(
            block, blockSlotState.get(), recentChainData.getStore());
    if (!preconditionCheckResult.isSuccessful()) {
      reportInvalidBlock(block, preconditionCheckResult);
      return SafeFuture.completedFuture(preconditionCheckResult);
    }

    final CapturingIndexedAttestationCache indexedAttestationCache =
        IndexedAttestationCache.capturing();

    final AvailabilityChecker<?> availabilityChecker =
        forkChoiceUtil.createAvailabilityChecker(block);

    availabilityChecker.initiateDataAvailabilityCheck();

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

    final SafeFuture<? extends DataAndValidationResult<?>> dataAndValidationResultSafeFuture =
        availabilityChecker
            .getAvailabilityCheckResult()
            .thenPeek(
                result -> {
                  LOG.debug(
                      "Data availability check for slot: {}, block_root: {} result: {}",
                      block.getSlot(),
                      block.getRoot(),
                      result.toLogString());
                  blockImportPerformance.ifPresent(BlockImportPerformance::dataAvailabilityChecked);
                  // consensus validation is completed when DA check is completed
                  if (result.isSuccess()) {
                    blockBroadcastValidator.onConsensusValidationSucceeded();
                  }
                });

    final SafeFuture<PayloadValidationResult> payloadValidationFuture =
        payloadExecutor
            .getExecutionResult()
            .thenPeek(
                __ ->
                    blockImportPerformance.ifPresent(
                        BlockImportPerformance::executionResultReceived));

    return blockBroadcastValidator
        .getResult()
        .thenCompose(
            broadcastValidationResult -> {
              if (broadcastValidationResult.isFailure()) {
                return SafeFuture.completedFuture(BlockImportResult.FAILED_BROADCAST_VALIDATION);
              }

              return payloadValidationFuture.thenCombineAsync(
                  dataAndValidationResultSafeFuture,
                  (payloadResult, dataAndValidationResult) ->
                      importBlockAndState(
                          block,
                          blockSlotState.get(),
                          blockImportPerformance,
                          forkChoiceUtil,
                          indexedAttestationCache,
                          postState,
                          payloadResult,
                          dataAndValidationResult),
                  forkChoiceExecutor);
            });
  }

  /**
   * Import an execution payload to the store. The supplied {@code blockState} must be the
   * post-state after processing the block whose root is the beacon block root of the execution
   * payload
   */
  private SafeFuture<ExecutionPayloadImportResult> onExecutionPayload(
      final SignedExecutionPayloadEnvelope signedEnvelope,
      final Optional<BeaconState> blockState,
      final ExecutionLayerChannel executionLayer) {
    if (blockState.isEmpty()) {
      return SafeFuture.completedFuture(
          ExecutionPayloadImportResult.FAILED_UNKNOWN_BEACON_BLOCK_ROOT);
    }

    final ForkChoiceUtil forkChoiceUtil = spec.atSlot(signedEnvelope.getSlot()).getForkChoiceUtil();

    final AvailabilityChecker<?> availabilityChecker =
        forkChoiceUtil.createAvailabilityChecker(signedEnvelope);

    availabilityChecker.initiateDataAvailabilityCheck();

    final ForkChoicePayloadExecutorGloas payloadExecutor =
        ForkChoicePayloadExecutorGloas.create(signedEnvelope, executionLayer);

    final BeaconState postState;
    try {
      postState =
          spec.getExecutionPayloadProcessor(signedEnvelope.getSlot())
              .processAndVerifyExecutionPayload(
                  signedEnvelope, blockState.get(), Optional.of(payloadExecutor));
    } catch (final StateTransitionException ex) {
      final ExecutionPayloadImportResult result =
          ExecutionPayloadImportResult.failedStateTransition(ex);
      reportInvalidExecutionPayload(signedEnvelope, result);
      return SafeFuture.completedFuture(result);
    }

    final SafeFuture<? extends DataAndValidationResult<?>> dataAndValidationResultFuture =
        availabilityChecker
            .getAvailabilityCheckResult()
            .thenPeek(
                result ->
                    LOG.debug(
                        "Data availability check for slot: {}, builder: {}, block_root: {} result: {}",
                        signedEnvelope.getSlot(),
                        signedEnvelope.getMessage().getBuilderIndex(),
                        signedEnvelope.getBeaconBlockRoot(),
                        result.toLogString()));

    return payloadExecutor
        .getExecutionResult()
        .thenCombineAsync(
            dataAndValidationResultFuture,
            (payloadResult, dataAndValidationResult) ->
                importExecutionPayloadAndState(
                    signedEnvelope,
                    forkChoiceUtil,
                    postState,
                    payloadResult,
                    dataAndValidationResult),
            forkChoiceExecutor);
  }

  private BlockImportResult importBlockAndState(
      final SignedBeaconBlock block,
      final BeaconState blockSlotState,
      final Optional<BlockImportPerformance> blockImportPerformance,
      final ForkChoiceUtil forkChoiceUtil,
      final CapturingIndexedAttestationCache indexedAttestationCache,
      final BeaconState postState,
      final PayloadValidationResult payloadValidationResult,
      final DataAndValidationResult<?> dataAndValidationResult) {
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

    switch (dataAndValidationResult.validationResult()) {
      case NOT_AVAILABLE -> {
        return BlockImportResult.failedDataAvailabilityCheckNotAvailable(
            dataAndValidationResult.cause());
      }
      case INVALID -> {
        debugDataDumper.saveInvalidSidecars(dataAndValidationResult.data(), block);
        return BlockImportResult.failedDataAvailabilityCheckInvalid(
            dataAndValidationResult.cause());
      }
      default -> {}
    }

    final ForkChoiceStrategy forkChoiceStrategy = getForkChoiceStrategy();

    // Now that we're on the fork choice thread, make sure the block still descends from finalized
    // (which may have changed while we were processing the block)
    if (!forkChoiceUtil.blockDescendsFromLatestFinalizedBlock(
        block.getSlot(), block.getParentRoot(), recentChainData.getStore(), forkChoiceStrategy)) {
      return BlockImportResult.FAILED_INVALID_ANCESTRY;
    }

    final StoreTransaction transaction = recentChainData.startStoreTransaction();
    addParentStateRoots(spec, blockSlotState, transaction);

    final Optional<List<BlobSidecar>> blobSidecars =
        extractBlobSidecarsFromValidationResults(dataAndValidationResult, block);
    final Optional<UInt64> earliestBlobSidecarsSlot =
        computeEarliestBlobSidecarsSlot(
            recentChainData.getStore(), dataAndValidationResult, block.getMessage());

    forkChoiceUtil.applyBlockToStore(
        transaction,
        block,
        postState,
        payloadResult.hasNotValidatedStatus(),
        blobSidecars,
        earliestBlobSidecarsSlot);

    final boolean shouldUpdateProposerBoostRoot = shouldUpdateProposerBoostRoot(block, transaction);
    if (shouldUpdateProposerBoostRoot) {
      transaction.setProposerBoostRoot(block.getRoot());
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
    updateForkChoiceForImportedBlock(
        block, shouldUpdateProposerBoostRoot, result, forkChoiceStrategy);
    notifyForkChoiceUpdatedAndOptimisticSyncingChanged(Optional.empty());
    return result;
  }

  // TODO-GLOAS: https://github.com/Consensys/teku/issues/9878 it requires potentially more
  // validations and more interactions with the store (e.g. onExecutionPayloadResult)
  private ExecutionPayloadImportResult importExecutionPayloadAndState(
      final SignedExecutionPayloadEnvelope signedEnvelope,
      final ForkChoiceUtil forkChoiceUtil,
      final BeaconState postState,
      final PayloadValidationResult payloadResult,
      final DataAndValidationResult<?> dataAndValidationResult) {
    final PayloadStatus payloadStatus = payloadResult.getStatus();

    if (payloadStatus.hasInvalidStatus()) {
      final ExecutionPayloadImportResult result =
          ExecutionPayloadImportResult.failedStateTransition(
              new IllegalStateException(
                  "Invalid ExecutionPayload: "
                      + payloadStatus.getValidationError().orElse("No reason provided")));
      reportInvalidExecutionPayload(signedEnvelope, result);
      return result;
    }

    if (payloadStatus.hasFailedExecution()) {
      return ExecutionPayloadImportResult.failedExecution(
          payloadStatus.getFailureCause().orElseThrow());
    }

    switch (dataAndValidationResult.validationResult()) {
      case NOT_AVAILABLE -> {
        return ExecutionPayloadImportResult.failedDataAvailabilityCheckNotAvailable(
            dataAndValidationResult.cause());
      }
      case INVALID -> {
        return ExecutionPayloadImportResult.failedDataAvailabilityCheckInvalid(
            dataAndValidationResult.cause());
      }
      default -> {}
    }

    final StoreTransaction transaction = recentChainData.startStoreTransaction();

    forkChoiceUtil.applyExecutionPayloadToStore(transaction, signedEnvelope, postState);

    // Note: not using thenRun here because we want to ensure each step is on the event thread
    transaction.commit().join();

    return ExecutionPayloadImportResult.successful(signedEnvelope);
  }

  // from consensus-specs/fork-choice:
  private boolean shouldUpdateProposerBoostRoot(
      final SignedBeaconBlock block, final StoreTransaction transaction) {
    // is_first_block
    if (transaction.getProposerBoostRoot().isPresent()) {
      return false;
    }
    // is_timely
    if (recentChainData.isBlockLate(block.getRoot())) {
      return false;
    }

    // in the edge cases when the chain head is not present or the head state future is not
    // immediately available, the proposer boost root will be set to maintain backwards
    // compatibility (see: https://github.com/ethereum/consensus-specs/pull/4807/)
    return recentChainData
        .getChainHead()
        .map(
            chainHead -> {
              final SafeFuture<BeaconState> headStateFuture = chainHead.getState();
              if (!headStateFuture.isCompletedNormally()) {
                return true;
              }
              final BeaconState headState = headStateFuture.join();
              final UInt64 currentSlot = spec.getCurrentSlot(transaction);
              try {
                return block.getProposerIndex().intValue()
                    == spec.getProposerIndexAtSlot(headState, currentSlot);
              } catch (final SlotProcessingException | EpochProcessingException ex) {
                throw new RuntimeException(
                    String.format(
                        "Can't progress head state from slot %s to slot %s",
                        headState.getSlot(), currentSlot),
                    ex);
              }
            })
        .orElse(true);
  }

  private Optional<List<BlobSidecar>> extractBlobSidecarsFromValidationResults(
      final DataAndValidationResult<?> dataAndValidationResult, final SignedBeaconBlock block) {
    if (dataAndValidationResult.isNotRequired()) {
      // Outside availability window or pre-Deneb
      return Optional.empty();
    } else if (dataAndValidationResult.isValid()) {
      return dataAndValidationResult.getDataAsBlobSidecars();
    }
    throw new IllegalStateException(
        String.format(
            "Unexpected attempt to store invalid sidecars (%s) for block: %s",
            dataAndValidationResult.data().size(), block.toLogString()));
  }

  /**
   * In order to keep track of DataAvailability Window, we need to compute the earliest slot we can
   * consider data available for the given block. It needs to take in account possible empty slots
   * so the actual slot should be parent block slot + 1. Moreover, we need to manage the case in
   * which the calculated slot is before Deneb activation, so it should never be before the first
   * deneb slot.
   *
   * @param store requires for data availability window calculation
   * @param dataAndValidationResult If it's not required or invalid, we could skip calculation
   * @param block Beacon block
   * @return the slot we could mark as earliestBlobSidecarsSlot if any, otherwise Optional.empty()
   */
  private Optional<UInt64> computeEarliestBlobSidecarsSlot(
      final ReadOnlyStore store,
      final DataAndValidationResult<?> dataAndValidationResult,
      final BeaconBlock block) {
    if (!dataAndValidationResult.isValid()) {
      return Optional.empty();
    }
    final UInt64 earliestAffectedSlot =
        recentChainData
            .getSlotForBlockRoot(block.getParentRoot())
            .map(UInt64::increment)
            .orElse(block.getSlot());

    final Optional<UInt64> maybeEarliestAvailabilityWindowSlotBeforeBlock =
        spec.atSlot(block.getSlot())
            .getForkChoiceUtil()
            .getEarliestAvailabilityWindowSlotBeforeBlock(spec, store, block.getSlot());

    return maybeEarliestAvailabilityWindowSlotBeforeBlock.map(
        earliestAvailabilityWindowSlotBeforeBlock ->
            earliestAvailabilityWindowSlotBeforeBlock.max(earliestAffectedSlot));
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
      final boolean shouldUpdateProposerBoostRoot,
      final BlockImportResult result,
      final ForkChoiceStrategy forkChoiceStrategy) {

    final ChainHead currentHead = recentChainData.getChainHead().orElseThrow();

    final SlotAndBlockRoot bestHeadBlock = findNewChainHead(forkChoiceStrategy);
    if (!bestHeadBlock.getBlockRoot().equals(currentHead.getRoot())) {
      recentChainData.updateHead(bestHeadBlock.getBlockRoot(), bestHeadBlock.getSlot());
      if (bestHeadBlock.getBlockRoot().equals(block.getRoot())) {
        result.markAsCanonical();
      }
    }

    if (!result.isBlockOnCanonicalChain() && shouldUpdateProposerBoostRoot) {
      // This is likely a reorging block that requires a full processHead to update the head.
      // Running processHead here will ensure:
      //
      // - node producing a reorging block will attest and produce sync committee for the reorging
      // block
      // - node just importing (not producing it) a reorging block will produce sync committee for
      // the new head
      //
      // It wouldn't be enough to trigger processHead at attestationDue because it will only
      // cover attestation generation and not sync committee message, since it is generated
      // VC side and requires head event to be sent before attestationDue.
      processHead().finish(error -> LOG.error("Fork choice updating head failed", error));
    }
  }

  private SlotAndBlockRoot findNewChainHead(final ForkChoiceStrategy forkChoiceStrategy) {
    // use fork choice to find the new chain head as if this block is on time the proposer weighting
    // may cause us to reorg.
    final Checkpoint justifiedCheckpoint = recentChainData.getJustifiedCheckpoint().orElseThrow();
    final Checkpoint finalizedCheckpoint = recentChainData.getFinalizedCheckpoint().orElseThrow();
    return forkChoiceStrategy.findHead(
        recentChainData.getCurrentEpoch().orElseThrow(), justifiedCheckpoint, finalizedCheckpoint);
  }

  private void reportInvalidBlock(final SignedBeaconBlock block, final BlockImportResult result) {
    if (result.getFailureReason() == FailureReason.BLOCK_IS_FROM_FUTURE) {
      return;
    }
    debugDataDumper.saveInvalidBlock(
        block, result.getFailureReason().name(), result.getFailureCause());
    P2P_LOG.onInvalidBlock(
        block.getSlot(),
        block.getRoot(),
        block.sszSerialize(),
        result.getFailureReason().name(),
        result.getFailureCause());
  }

  private void reportInvalidExecutionPayload(
      final SignedExecutionPayloadEnvelope signedEnvelope,
      final ExecutionPayloadImportResult result) {
    debugDataDumper.saveInvalidExecutionPayload(
        signedEnvelope, result.getFailureReason().name(), result.getFailureCause());
    P2P_LOG.onInvalidExecutionPayload(
        signedEnvelope.getSlot(),
        signedEnvelope.getMessage().getBuilderIndex(),
        signedEnvelope.getBeaconBlockRoot(),
        signedEnvelope.sszSerialize(),
        result.getFailureReason().name(),
        result.getFailureCause());
  }

  private void notifyForkChoiceUpdatedAndOptimisticSyncingChanged(
      final Optional<UInt64> proposingSlot) {
    final ForkChoiceState forkChoiceState = forkChoiceStateProvider.getForkChoiceStateSync();

    forkChoiceNotifier.onForkChoiceUpdated(forkChoiceState, proposingSlot);
    getProposerHeadSelectedCounter.labels("fork_choice").inc();

    if (optimisticSyncing
        .map(oldValue -> !oldValue.equals(forkChoiceState.isHeadOptimistic()))
        .orElse(true)) {
      optimisticSyncing = Optional.of(forkChoiceState.isHeadOptimistic());
      optimisticSyncSubscribers.deliver(
          OptimisticHeadSubscriber::onOptimisticHeadChanged, forkChoiceState.isHeadOptimistic());
    }
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

  private void storeEquivocatingIndices(
      final AttesterSlashing attesterSlashing, final VoteUpdater transaction) {
    attesterSlashing
        .getIntersectingValidatorIndices()
        .forEach(
            validatorIndex -> {
              final VoteTracker voteTracker = transaction.getVote(validatorIndex);
              if (!voteTracker.isEquivocating()) {
                transaction.putVote(validatorIndex, voteTracker.createNextEquivocating());
              }
            });
  }

  public UInt64 getLastProcessHeadSlot() {
    return lastProcessHeadSlot.get();
  }

  SafeFuture<Void> prepareForBlockProduction(
      final UInt64 slot, final BlockProductionPerformance blockProductionPerformance) {
    final UInt64 slotStartTimeMillis =
        spec.computeTimeMillisAtSlot(slot, recentChainData.getGenesisTimeMillis());
    final UInt64 currentTime = recentChainData.getStore().getTimeInMillis();
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
            tickProcessor
                .onTick(slotStartTimeMillis)
                .thenPeek(__ -> blockProductionPerformance.prepareOnTick()),
            applyDeferredAttestations(slot)
                .thenPeek(__ -> blockProductionPerformance.prepareApplyDeferredAttestations()))
        .thenCompose(__ -> processHead(Optional.of(slot), true))
        .thenRun(blockProductionPerformance::prepareProcessHead);
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

  private IndexedAttestation getIndexedAttestation(final ValidatableAttestation attestation) {
    return attestation
        .getIndexedAttestation()
        .orElseThrow(
            () ->
                new UnsupportedOperationException(
                    "ValidatableAttestation does not have an IndexedAttestation."));
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

  private Optional<Boolean> getOptimisticSyncing() {
    return optimisticSyncing;
  }

  public interface OptimisticHeadSubscriber {
    void onOptimisticHeadChanged(boolean isHeadOptimistic);
  }
}
