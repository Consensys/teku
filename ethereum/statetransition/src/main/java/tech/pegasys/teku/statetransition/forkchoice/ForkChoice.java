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
import static tech.pegasys.teku.statetransition.forkchoice.StateRootCollector.addParentStateRoots;

import com.google.common.base.Throwables;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
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
import tech.pegasys.teku.spec.datastructures.forkchoice.ProposerWeighting;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyForkChoiceStrategy;
import tech.pegasys.teku.spec.datastructures.forkchoice.VoteUpdater;
import tech.pegasys.teku.spec.datastructures.operations.IndexedAttestation;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.util.AttestationProcessingResult;
import tech.pegasys.teku.spec.executionengine.ExecutionEngineChannel;
import tech.pegasys.teku.spec.executionengine.ForkChoiceState;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult.FailureReason;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.store.UpdatableStore;
import tech.pegasys.teku.storage.store.UpdatableStore.StoreTransaction;

public class ForkChoice {
  private static final Logger LOG = LogManager.getLogger();

  private final Spec spec;
  private final EventThread forkChoiceExecutor;
  private final RecentChainData recentChainData;
  private final ProposerWeightings proposerWeightings;
  private final ForkChoiceNotifier forkChoiceNotifier;

  private ForkChoice(
      final Spec spec,
      final EventThread forkChoiceExecutor,
      final RecentChainData recentChainData,
      final ForkChoiceNotifier forkChoiceNotifier,
      final ProposerWeightings proposerWeightings) {
    this.spec = spec;
    this.forkChoiceExecutor = forkChoiceExecutor;
    this.recentChainData = recentChainData;
    this.proposerWeightings = proposerWeightings;
    this.forkChoiceNotifier = forkChoiceNotifier;
    recentChainData.subscribeStoreInitialized(this::initializeProtoArrayForkChoice);
  }

  public static ForkChoice create(
      final Spec spec,
      final EventThread forkChoiceExecutor,
      final RecentChainData recentChainData,
      final ForkChoiceNotifier forkChoiceNotifier,
      final boolean balanceAttackMitigationEnabled) {
    final ProposerWeightings proposerWeightings =
        balanceAttackMitigationEnabled
            ? new ActiveProposerWeightings(forkChoiceExecutor, spec)
            : new InactiveProposerWeightings();
    return new ForkChoice(
        spec, forkChoiceExecutor, recentChainData, forkChoiceNotifier, proposerWeightings);
  }

  /**
   * @deprecated Provided only to avoid having to hard code balanceAttackMitigationEnabled in lots
   *     of tests. Will be removed when the feature toggle is removed.
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

                      final List<ProposerWeighting> removedProposerWeightings =
                          proposerWeightings.clearProposerWeightings();
                      Bytes32 headBlockRoot =
                          transaction.applyForkChoiceScoreChanges(
                              finalizedCheckpoint,
                              justifiedCheckpoint,
                              justifiedEffectiveBalances,
                              removedProposerWeightings);

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
        new ForkChoicePayloadExecutor(
            spec, recentChainData, forkChoiceExecutor, block, executionEngine);

    return onForkChoiceThread(
            () -> {
              final ForkChoiceStrategy forkChoiceStrategy = getForkChoiceStrategy();
              final StoreTransaction transaction = recentChainData.startStoreTransaction();
              final CapturingIndexedAttestationCache indexedAttestationCache =
                  IndexedAttestationCache.capturing();

              addParentStateRoots(blockSlotState.get(), transaction);
              final BlockImportResult result =
                  spec.onBlock(
                      transaction,
                      block,
                      blockSlotState.get(),
                      indexedAttestationCache,
                      payloadExecutor);

              if (!result.isSuccessful()) {
                if (result.getFailureReason() != FailureReason.BLOCK_IS_FROM_FUTURE) {
                  // Blocks from the future are not invalid, just not ready for processing yet
                  P2P_LOG.onInvalidBlock(
                      block.getSlot(),
                      block.getRoot(),
                      block.sszSerialize(),
                      result.getFailureReason().name(),
                      result.getFailureCause());
                }
                return SafeFuture.completedFuture(result);
              }
              // Note: not using thenRun here because we want to ensure each step is on the event
              // thread
              transaction.commit().join();

              proposerWeightings.onBlockReceived(block, blockSlotState.get(), forkChoiceStrategy);

              final UInt64 currentEpoch = spec.computeEpochAtSlot(spec.getCurrentSlot(transaction));

              // We only need to apply attestations from the current or previous epoch
              // If the block is from before that, none of the attestations will be applicable so
              // just
              // skip the whole step.
              if (spec.computeEpochAtSlot(block.getSlot())
                  .isGreaterThanOrEqualTo(currentEpoch.minusMinZero(1))) {
                applyVotesFromBlock(forkChoiceStrategy, currentEpoch, indexedAttestationCache);
              }

              // Do the combine while still on the fork choice thread unless we really do have to
              // wait for the payload execution to complete, so call this here rather than in
              // .thenCompose below even though it means having to unwrap the SafeFuture
              return payloadExecutor.combine(result);
            })
        .thenApplyAsync(
            result -> {
              if (result.isSuccessful()) {
                notifyForkChoiceUpdated();
              }
              return result;
            },
            forkChoiceExecutor);
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

  public void onBlocksDueForSlot(final UInt64 slot) {
    onForkChoiceThread(() -> proposerWeightings.onBlockDueForSlot(slot)).reportExceptions();
  }

  public void onTick(final UInt64 currentTime) {
    final StoreTransaction transaction = recentChainData.startStoreTransaction();
    spec.onTick(transaction, currentTime);
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
        (ExceptionThrowingSupplier<Void>)
            () -> {
              task.run();
              return null;
            });
  }

  private <T> SafeFuture<T> onForkChoiceThread(final ExceptionThrowingSupplier<T> task) {
    return forkChoiceExecutor.execute(task);
  }

  // Errorprone thinks we're ignoring return values because the execute() call winds up returning a
  // nested SafeFuture<SafeFuture<?>> but we are unwrapping it so if either future fails we'll
  // still handle the result
  @SuppressWarnings("FutureReturnValueIgnored")
  private <T> SafeFuture<T> onForkChoiceThread(final Supplier<SafeFuture<T>> task) {
    return forkChoiceExecutor.execute(task::get).thenCompose(Function.identity());
  }
}
