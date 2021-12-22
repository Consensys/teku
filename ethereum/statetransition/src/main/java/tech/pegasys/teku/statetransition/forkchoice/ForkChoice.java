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

package tech.pegasys.teku.statetransition.forkchoice;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.infrastructure.logging.P2PLogger.P2P_LOG;
import static tech.pegasys.teku.spec.constants.NetworkConstants.INTERVALS_PER_SLOT;
import static tech.pegasys.teku.statetransition.forkchoice.StateRootCollector.addParentStateRoots;

import com.google.common.base.Throwables;
import java.util.List;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.ExceptionThrowingRunnable;
import tech.pegasys.teku.infrastructure.async.ExceptionThrowingSupplier;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.eventthread.EventThread;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.protoarray.ForkChoiceStrategy;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.cache.CapturingIndexedAttestationCache;
import tech.pegasys.teku.spec.cache.IndexedAttestationCache;
import tech.pegasys.teku.spec.datastructures.attestation.ValidateableAttestation;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.forkchoice.InvalidCheckpointException;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyForkChoiceStrategy;
import tech.pegasys.teku.spec.datastructures.forkchoice.VoteUpdater;
import tech.pegasys.teku.spec.datastructures.operations.IndexedAttestation;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.util.AttestationProcessingResult;
import tech.pegasys.teku.spec.executionengine.ExecutePayloadResult;
import tech.pegasys.teku.spec.executionengine.ExecutionEngineChannel;
import tech.pegasys.teku.spec.executionengine.ExecutionPayloadStatus;
import tech.pegasys.teku.spec.executionengine.ForkChoiceState;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.StateTransitionException;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult.FailureReason;
import tech.pegasys.teku.spec.logic.common.util.ForkChoiceUtil;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.store.UpdatableStore;
import tech.pegasys.teku.storage.store.UpdatableStore.StoreTransaction;

public class ForkChoice {
  private static final Logger LOG = LogManager.getLogger();

  private final Spec spec;
  private final EventThread forkChoiceExecutor;
  private final RecentChainData recentChainData;
  private final ForkChoiceNotifier forkChoiceNotifier;
  private final boolean proposerBoostEnabled;

  private ForkChoice(
      final Spec spec,
      final EventThread forkChoiceExecutor,
      final RecentChainData recentChainData,
      final ForkChoiceNotifier forkChoiceNotifier,
      final boolean proposerBoostEnabled) {
    this.spec = spec;
    this.forkChoiceExecutor = forkChoiceExecutor;
    this.recentChainData = recentChainData;
    this.forkChoiceNotifier = forkChoiceNotifier;
    this.proposerBoostEnabled = proposerBoostEnabled;
    recentChainData.subscribeStoreInitialized(this::initializeProtoArrayForkChoice);
  }

  public static ForkChoice create(
      final Spec spec,
      final EventThread forkChoiceExecutor,
      final RecentChainData recentChainData,
      final ForkChoiceNotifier forkChoiceNotifier,
      final boolean proposerBoostEnabled) {
    return new ForkChoice(
        spec, forkChoiceExecutor, recentChainData, forkChoiceNotifier, proposerBoostEnabled);
  }

  /**
   * @deprecated Provided only to avoid having to hard code proposerBoostEnabled in lots of tests.
   *     Will be removed when the feature toggle is removed.
   */
  @Deprecated
  public static ForkChoice create(
      final Spec spec,
      final EventThread forkChoiceExecutor,
      final RecentChainData recentChainData,
      final ForkChoiceNotifier forkChoiceNotifier) {
    return create(spec, forkChoiceExecutor, recentChainData, forkChoiceNotifier, false);
  }

  private void initializeProtoArrayForkChoice() {
    processHead().join();
  }

  public SafeFuture<Boolean> processHead() {
    return processHead(Optional.empty());
  }

  public SafeFuture<Boolean> processHead(UInt64 nodeSlot) {
    return processHead(Optional.of(nodeSlot));
  }

  private SafeFuture<Boolean> processHead(Optional<UInt64> nodeSlot) {
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
                      notifyForkChoiceUpdated();
                      return true;
                    }));
  }

  /** Import a block to the store. */
  public SafeFuture<BlockImportResult> onBlock(
      final SignedBeaconBlock block, final ExecutionEngineChannel executionEngine) {
    return recentChainData
        .retrieveStateAtSlot(new SlotAndBlockRoot(block.getSlot(), block.getParentRoot()))
        .thenCompose(blockSlotState -> onBlock(block, blockSlotState, executionEngine));
  }

  /**
   * Import a block to the store. The supplied blockSlotState must already have empty slots
   * processed to the same slot as the block.
   */
  private SafeFuture<BlockImportResult> onBlock(
      final SignedBeaconBlock block,
      final Optional<BeaconState> blockSlotState,
      final ExecutionEngineChannel executionEngine) {
    if (blockSlotState.isEmpty()) {
      return SafeFuture.completedFuture(BlockImportResult.FAILED_UNKNOWN_PARENT);
    }
    checkArgument(
        block.getSlot().equals(blockSlotState.get().getSlot()),
        "State must have processed slots up to the block slot. Block slot %s, state slot %s",
        block.getSlot(),
        blockSlotState.get().getSlot());

    final ForkChoicePayloadExecutor payloadExecutor =
        new ForkChoicePayloadExecutor(spec, block, executionEngine);
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
                  block, blockSlotState.get(), indexedAttestationCache, payloadExecutor);
    } catch (final StateTransitionException e) {
      final BlockImportResult result = BlockImportResult.failedStateTransition(e);
      reportInvalidBlock(block, result);
      return SafeFuture.completedFuture(result);
    }

    return payloadExecutor
        .getExecutionResult()
        .thenApplyAsync(
            payloadResult ->
                importBlockAndState(
                    block,
                    blockSlotState.get(),
                    forkChoiceUtil,
                    indexedAttestationCache,
                    postState,
                    payloadResult),
            forkChoiceExecutor);
  }

  private BlockImportResult importBlockAndState(
      final SignedBeaconBlock block,
      final BeaconState blockSlotState,
      final ForkChoiceUtil forkChoiceUtil,
      final CapturingIndexedAttestationCache indexedAttestationCache,
      final BeaconState postState,
      final ExecutePayloadResult payloadResult) {
    if (payloadResult.hasStatus(ExecutionPayloadStatus.INVALID)) {
      final BlockImportResult result =
          BlockImportResult.failedStateTransition(
              new IllegalStateException(
                  "Invalid ExecutionPayload: "
                      + payloadResult.getValidationError().orElse("No reason provided")));
      reportInvalidBlock(block, result);
      return result;
    }

    if (payloadResult.hasStatus(ExecutionPayloadStatus.SYNCING)
        && !recentChainData.isOptimisticSyncPossible()) {
      return BlockImportResult.FAILED_EXECUTION_PAYLOAD_EXECUTION_SYNCING;
    }

    if (payloadResult.hasFailedExecution()) {
      return BlockImportResult.failedExecutionPayloadExecution(
          payloadResult.getFailureCause().orElseThrow());
    }

    final ExecutionPayloadStatus payloadResultStatus = payloadResult.getStatus().orElseThrow();

    final ForkChoiceStrategy forkChoiceStrategy = getForkChoiceStrategy();

    // Now that we're on the fork choice thread, make sure the block still descends from finalized
    // (which may have changed while we were processing the block)
    if (!forkChoiceUtil.blockDescendsFromLatestFinalizedBlock(
        block, recentChainData.getStore(), forkChoiceStrategy)) {
      return BlockImportResult.FAILED_INVALID_ANCESTRY;
    }

    final StoreTransaction transaction = recentChainData.startStoreTransaction();
    addParentStateRoots(blockSlotState, transaction);
    forkChoiceUtil.applyBlockToStore(transaction, block, postState);

    if (proposerBoostEnabled && spec.getCurrentSlot(transaction).equals(block.getSlot())) {
      final int secondsPerSlot = spec.getSecondsPerSlot(block.getSlot());
      final UInt64 timeIntoSlot =
          transaction.getTime().minus(transaction.getGenesisTime()).mod(secondsPerSlot);
      final boolean isBeforeAttestingInterval =
          timeIntoSlot.isLessThan(secondsPerSlot / INTERVALS_PER_SLOT);
      if (isBeforeAttestingInterval) {
        transaction.setProposerBoostRoot(block.getRoot());
      }
    }

    if (payloadResult.hasStatus(ExecutionPayloadStatus.VALID)) {
      UInt64 latestValidFinalizedSlot = transaction.getLatestFinalized().getSlot();
      if (latestValidFinalizedSlot.isGreaterThan(transaction.getLatestValidFinalizedSlot())) {
        transaction.setLatestValidFinalizedSlot(latestValidFinalizedSlot);
      }
    }

    // Note: not using thenRun here because we want to ensure each step is on the event thread
    transaction.commit().join();
    forkChoiceStrategy.onExecutionPayloadResult(block.getRoot(), payloadResultStatus);

    final UInt64 currentEpoch = spec.computeEpochAtSlot(spec.getCurrentSlot(transaction));

    // We only need to apply attestations from the current or previous epoch. If the block is from
    // before that, none of the attestations will be applicable so just skip the whole step.
    if (spec.computeEpochAtSlot(block.getSlot())
        .isGreaterThanOrEqualTo(currentEpoch.minusMinZero(1))) {
      applyVotesFromBlock(forkChoiceStrategy, currentEpoch, indexedAttestationCache);
    }

    final BlockImportResult result = BlockImportResult.successful(block);
    if (payloadResultStatus == ExecutionPayloadStatus.VALID) {
      updateForkChoiceForImportedBlock(block, result, forkChoiceStrategy);
    }
    notifyForkChoiceUpdated();
    return result;
  }

  public void onExecutionPayloadResult(
      final Bytes32 blockRoot,
      final ExecutePayloadResult result,
      final UInt64 latestFinalizedBlockSlot) {
    onForkChoiceThread(
            () -> {
              if (!result.hasStatus(ExecutionPayloadStatus.VALID)) {
                return;
              }
              UInt64 latestValidFinalizedSlotInStore =
                  recentChainData.getLatestValidFinalizedSlot();

              if (latestFinalizedBlockSlot.isGreaterThan(latestValidFinalizedSlotInStore)) {
                final StoreTransaction transaction = recentChainData.startStoreTransaction();
                transaction.setLatestValidFinalizedSlot(latestFinalizedBlockSlot);
                transaction.commit().join();
              }

              recentChainData
                  .getForkChoiceStrategy()
                  .orElseThrow()
                  .onExecutionPayloadResult(blockRoot, result.getStatus().orElseThrow());
            })
        .reportExceptions();
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
        .getChainHead()
        .map(currentHead -> currentHead.getRoot().equals(block.getParentRoot()))
        .orElse(false)) {
      return new SlotAndBlockRoot(block.getSlot(), block.getRoot());
    }

    // Otherwise, use fork choice to find the new chain head as if this block is on time the
    // proposer weighting may cause us to reorg.
    // During sync, this may be noticeably slower than just comparing the chain head due to the way
    // ProtoArray skips updating all ancestors when adding a new block but it's cheap when in sync.
    final Checkpoint justifiedCheckpoint = recentChainData.getJustifiedCheckpoint().orElseThrow();
    final Checkpoint finalizedCheckpoint = recentChainData.getFinalizedCheckpoint().orElseThrow();
    return forkChoiceStrategy.findHead(justifiedCheckpoint, finalizedCheckpoint);
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

  private void notifyForkChoiceUpdated() {
    final ForkChoiceState forkChoiceState =
        getForkChoiceStrategy()
            .getForkChoiceState(
                recentChainData.getJustifiedCheckpoint().orElseThrow(),
                recentChainData.getFinalizedCheckpoint().orElseThrow());
    forkChoiceNotifier.onForkChoiceUpdated(forkChoiceState);
  }

  private void applyVotesFromBlock(
      final ForkChoiceStrategy forkChoiceStrategy,
      final UInt64 currentEpoch,
      final CapturingIndexedAttestationCache indexedAttestationProvider) {
    final VoteUpdater voteUpdater = recentChainData.startVoteUpdate();
    indexedAttestationProvider.getIndexedAttestations().stream()
        .filter(
            attestation -> validateBlockAttestation(forkChoiceStrategy, currentEpoch, attestation))
        .forEach(attestation -> forkChoiceStrategy.onAttestation(voteUpdater, attestation));
    voteUpdater.commit();
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
    return recentChainData
        .retrieveCheckpointState(attestation.getData().getTarget())
        .thenCompose(
            maybeTargetState -> {
              final UpdatableStore store = recentChainData.getStore();
              final AttestationProcessingResult validationResult =
                  spec.validateAttestation(store, attestation, maybeTargetState);

              if (!validationResult.isSuccessful()) {
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
        .reportExceptions();
  }

  public void onTick(final UInt64 currentTime) {
    final StoreTransaction transaction = recentChainData.startStoreTransaction();
    final UInt64 previousSlot = spec.getCurrentSlot(transaction);
    spec.onTick(transaction, currentTime);
    if (spec.getCurrentSlot(transaction).isGreaterThan(previousSlot)) {
      transaction.removeProposerBoostRoot();
    }
    transaction.commit().join();
  }

  private ForkChoiceStrategy getForkChoiceStrategy() {
    forkChoiceExecutor.checkOnEventThread();
    return recentChainData
        .getForkChoiceStrategy()
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
}
