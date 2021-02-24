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
import static tech.pegasys.teku.statetransition.forkchoice.StateRootCollector.addParentStateRoots;

import com.google.common.base.Throwables;
import java.util.List;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.core.results.BlockImportResult;
import tech.pegasys.teku.datastructures.attestation.ValidateableAttestation;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.forkchoice.InvalidCheckpointException;
import tech.pegasys.teku.datastructures.forkchoice.ReadOnlyForkChoiceStrategy;
import tech.pegasys.teku.datastructures.forkchoice.VoteUpdater;
import tech.pegasys.teku.datastructures.operations.IndexedAttestation;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.datastructures.util.AttestationProcessingResult;
import tech.pegasys.teku.infrastructure.async.ExceptionThrowingRunnable;
import tech.pegasys.teku.infrastructure.async.ExceptionThrowingSupplier;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.eventthread.EventThread;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.protoarray.ForkChoiceStrategy;
import tech.pegasys.teku.spec.SpecProvider;
import tech.pegasys.teku.spec.cache.CapturingIndexedAttestationCache;
import tech.pegasys.teku.spec.cache.IndexedAttestationCache;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.store.UpdatableStore;
import tech.pegasys.teku.storage.store.UpdatableStore.StoreTransaction;

public class ForkChoice {
  private static final Logger LOG = LogManager.getLogger();

  private final SpecProvider specProvider;
  private final EventThread forkChoiceExecutor;
  private final RecentChainData recentChainData;

  public ForkChoice(
      final SpecProvider specProvider,
      final EventThread forkChoiceExecutor,
      final RecentChainData recentChainData) {
    this.specProvider = specProvider;
    this.forkChoiceExecutor = forkChoiceExecutor;
    this.recentChainData = recentChainData;
    recentChainData.subscribeStoreInitialized(this::initializeProtoArrayForkChoice);
  }

  private void initializeProtoArrayForkChoice() {
    processHead();
  }

  public void processHead() {
    processHead(Optional.empty());
  }

  public void processHead(UInt64 nodeSlot) {
    processHead(Optional.of(nodeSlot));
  }

  private void processHead(Optional<UInt64> nodeSlot) {
    final Checkpoint retrievedJustifiedCheckpoint =
        recentChainData.getStore().getJustifiedCheckpoint();
    recentChainData
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
                        LOG.info(
                            "Skipping head block update as justified checkpoint was updated while loading checkpoint state. Was {} ({}) but now {} ({})",
                            retrievedJustifiedCheckpoint.getEpoch(),
                            retrievedJustifiedCheckpoint.getRoot(),
                            justifiedCheckpoint.getEpoch(),
                            justifiedCheckpoint.getRoot());
                        return;
                      }
                      final VoteUpdater transaction = recentChainData.startVoteUpdate();
                      final ReadOnlyForkChoiceStrategy forkChoiceStrategy = getForkChoiceStrategy();
                      Bytes32 headBlockRoot =
                          transaction.applyForkChoiceScoreChanges(
                              finalizedCheckpoint,
                              justifiedCheckpoint,
                              justifiedCheckpointState.orElseThrow());

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
                    }))
        .join();
  }

  /**
   * Import a block to the store. The supplied blockSlotState must already have empty slots
   * processed to the same slot as the block.
   */
  public SafeFuture<BlockImportResult> onBlock(
      final SignedBeaconBlock block, Optional<BeaconState> blockSlotState) {
    if (blockSlotState.isEmpty()) {
      return SafeFuture.completedFuture(BlockImportResult.FAILED_UNKNOWN_PARENT);
    }
    checkArgument(
        block.getSlot().equals(blockSlotState.get().getSlot()),
        "State must have processed slots up to the block slot. Block slot %s, state slot %s",
        block.getSlot(),
        blockSlotState.get().getSlot());
    return onForkChoiceThread(
        () -> {
          final ForkChoiceStrategy forkChoiceStrategy = getForkChoiceStrategy();
          final StoreTransaction transaction = recentChainData.startStoreTransaction();
          final CapturingIndexedAttestationCache indexedAttestationCache =
              IndexedAttestationCache.capturing();

          addParentStateRoots(blockSlotState.get(), transaction);

          final BlockImportResult result =
              specProvider.onBlock(
                  transaction, block, blockSlotState.get(), indexedAttestationCache);

          if (!result.isSuccessful()) {
            return result;
          }
          // Note: not using thenRun here because we want to ensure each step is on the event thread
          transaction.commit().join();
          updateForkChoiceForImportedBlock(block, result);
          applyVotesFromBlock(forkChoiceStrategy, indexedAttestationCache);
          return result;
        });
  }

  private void applyVotesFromBlock(
      final ForkChoiceStrategy forkChoiceStrategy,
      final CapturingIndexedAttestationCache indexedAttestationProvider) {
    final VoteUpdater voteUpdater = recentChainData.startVoteUpdate();
    indexedAttestationProvider.getIndexedAttestations().stream()
        .filter(
            attestation ->
                forkChoiceStrategy.contains(attestation.getData().getBeacon_block_root()))
        .forEach(
            indexedAttestation ->
                forkChoiceStrategy.onAttestation(voteUpdater, indexedAttestation));
    voteUpdater.commit();
  }

  private void updateForkChoiceForImportedBlock(
      final SignedBeaconBlock block, final BlockImportResult result) {
    if (result.isSuccessful()) {
      // If the new block builds on our current chain head immediately make it the new head
      // Since fork choice works by walking down the tree selecting the child block with
      // the greatest weight, when a block has only one child it will automatically become
      // a better choice than the block itself.  So the first block we receive that is a
      // child of our current chain head, must be the new chain head. If we'd had any other
      // child of the current chain head we'd have already selected it as head.
      if (recentChainData
          .getChainHead()
          .map(currentHead -> currentHead.getRoot().equals(block.getParentRoot()))
          .orElse(false)) {
        recentChainData.updateHead(block.getRoot(), block.getSlot());
        result.markAsCanonical();
      }
    }
  }

  public SafeFuture<AttestationProcessingResult> onAttestation(
      final ValidateableAttestation attestation) {
    return recentChainData
        .retrieveCheckpointState(attestation.getData().getTarget())
        .thenCompose(
            maybeTargetState -> {
              final UpdatableStore store = recentChainData.getStore();
              final AttestationProcessingResult validationResult =
                  specProvider.validateAttestation(store, attestation, maybeTargetState);

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

  private ForkChoiceStrategy getForkChoiceStrategy() {
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
