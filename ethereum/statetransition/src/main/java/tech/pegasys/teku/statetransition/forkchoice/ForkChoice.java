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

import static tech.pegasys.teku.core.ForkChoiceUtil.on_attestation;
import static tech.pegasys.teku.core.ForkChoiceUtil.on_block;

import com.google.common.base.Throwables;
import java.util.List;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.core.StateTransition;
import tech.pegasys.teku.core.lookup.CapturingIndexedAttestationProvider;
import tech.pegasys.teku.core.results.BlockImportResult;
import tech.pegasys.teku.datastructures.attestation.ValidateableAttestation;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.datastructures.forkchoice.InvalidCheckpointException;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.datastructures.util.AttestationProcessingResult;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.protoarray.ForkChoiceStrategy;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoiceExecutor.ForkChoiceTask;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.store.UpdatableStore.StoreTransaction;

public class ForkChoice {
  private static final Logger LOG = LogManager.getLogger();

  private final ForkChoiceExecutor forkChoiceExecutor;
  private final RecentChainData recentChainData;
  private final StateTransition stateTransition;

  public ForkChoice(
      final ForkChoiceExecutor forkChoiceExecutor,
      final RecentChainData recentChainData,
      final StateTransition stateTransition) {
    this.forkChoiceExecutor = forkChoiceExecutor;
    this.recentChainData = recentChainData;
    this.stateTransition = stateTransition;
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
                        return SafeFuture.COMPLETE;
                      }
                      final StoreTransaction transaction = recentChainData.startStoreTransaction();
                      final ForkChoiceStrategy forkChoiceStrategy = getForkChoiceStrategy();
                      Bytes32 headBlockRoot =
                          forkChoiceStrategy.findHead(
                              transaction,
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
                      return transaction.commit();
                    }))
        .join();
  }

  public SafeFuture<BlockImportResult> onBlock(
      final SignedBeaconBlock block, Optional<BeaconState> preState) {
    return onForkChoiceThread(
        () -> {
          final ForkChoiceStrategy forkChoiceStrategy = getForkChoiceStrategy();
          final StoreTransaction transaction = recentChainData.startStoreTransaction();
          final CapturingIndexedAttestationProvider indexedAttestationProvider =
              new CapturingIndexedAttestationProvider();
          final BlockImportResult result =
              on_block(
                  transaction,
                  block,
                  preState,
                  stateTransition,
                  forkChoiceStrategy,
                  beaconState ->
                      transaction.putStateRoot(
                          beaconState.hash_tree_root(),
                          new SlotAndBlockRoot(
                              beaconState.getSlot(),
                              beaconState.getLatest_block_header().hash_tree_root())),
                  indexedAttestationProvider);

          if (!result.isSuccessful()) {
            return SafeFuture.completedFuture(result);
          }
          indexedAttestationProvider.getIndexedAttestations().stream()
              .filter(
                  attestation ->
                      forkChoiceStrategy.contains(attestation.getData().getBeacon_block_root()))
              .forEach(
                  indexedAttestation ->
                      forkChoiceStrategy.onAttestation(transaction, indexedAttestation));
          return transaction
              .commit()
              .thenRun(() -> updateForkChoiceForImportedBlock(block, forkChoiceStrategy, result))
              .thenApply(__ -> result);
        });
  }

  private void updateForkChoiceForImportedBlock(
      final SignedBeaconBlock block,
      final ForkChoiceStrategy forkChoiceStrategy,
      final BlockImportResult result) {
    result
        .getBlockProcessingRecord()
        .ifPresent(
            record -> {
              forkChoiceStrategy.onBlock(block.getMessage(), record.getPostState());
              // If the new block builds on our current chain head immediately make it the new head
              // Since fork choice works by walking down the tree selecting the child block with
              // the greatest weight, when a block has only one child it will automatically become
              // a better choice than the block itself.  So the first block we receive that is a
              // child of our current chain head, must be the new chain head. If we'd had any other
              // child of the current chain head we'd have already selected it as head.
              if (recentChainData
                  .getHeadBlock()
                  .map(currentHead -> currentHead.getRoot().equals(block.getParent_root()))
                  .orElse(false)) {
                recentChainData.updateHead(block.getRoot(), block.getSlot());
                result.markAsCanonical();
              }
            });
  }

  public SafeFuture<AttestationProcessingResult> onAttestation(
      final ValidateableAttestation attestation) {
    return recentChainData
        .retrieveCheckpointState(attestation.getData().getTarget())
        .thenCompose(
            targetBlockState ->
                onForkChoiceThread(
                    () -> {
                      final StoreTransaction transaction = recentChainData.startStoreTransaction();
                      final AttestationProcessingResult result =
                          on_attestation(
                              transaction, attestation, targetBlockState, getForkChoiceStrategy());
                      return result.isSuccessful()
                          ? transaction.commit().thenApply(__ -> result)
                          : SafeFuture.completedFuture(result);
                    }))
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
              final StoreTransaction transaction = recentChainData.startStoreTransaction();
              final ForkChoiceStrategy forkChoiceStrategy = getForkChoiceStrategy();
              attestations.stream()
                  .map(
                      a ->
                          a.getIndexedAttestation()
                              .orElseThrow(
                                  () ->
                                      new UnsupportedOperationException(
                                          "ValidateableAttestation does not have an IndexedAttestation.")))
                  .forEach(
                      attestation -> forkChoiceStrategy.onAttestation(transaction, attestation));
              return transaction.commit();
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

  private <T> SafeFuture<T> onForkChoiceThread(final ForkChoiceTask<T> task) {
    return forkChoiceExecutor.performTask(task);
  }
}
