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

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.core.StateTransition;
import tech.pegasys.teku.core.results.BlockImportResult;
import tech.pegasys.teku.datastructures.attestation.ValidateableAttestation;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.datastructures.util.AttestationProcessingResult;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.protoarray.ForkChoiceStrategy;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.server.ShuttingDownException;
import tech.pegasys.teku.storage.store.UpdatableStore.StoreTransaction;

public class ForkChoice {
  private static final Logger LOG = LogManager.getLogger();
  private final Semaphore lock = new Semaphore(1);
  private final RecentChainData recentChainData;
  private final StateTransition stateTransition;

  public ForkChoice(final RecentChainData recentChainData, final StateTransition stateTransition) {
    this.recentChainData = recentChainData;
    this.stateTransition = stateTransition;
    recentChainData.subscribeStoreInitialized(this::initializeProtoArrayForkChoice);
  }

  private void initializeProtoArrayForkChoice() {
    processHead();
  }

  private void processHead() {
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
                withLock(
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
    return withLock(
        () -> {
          final ForkChoiceStrategy forkChoiceStrategy = getForkChoiceStrategy();
          final StoreTransaction transaction = recentChainData.startStoreTransaction();
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
                              beaconState.getLatest_block_header().hash_tree_root())));

          if (!result.isSuccessful()) {
            return SafeFuture.completedFuture(result);
          }
          return transaction
              .commit()
              .thenRun(
                  () ->
                      result
                          .getBlockProcessingRecord()
                          .ifPresent(
                              record ->
                                  forkChoiceStrategy.onBlock(
                                      block.getMessage(), record.getPostState())))
              .thenApply(__ -> result);
        });
  }

  public SafeFuture<AttestationProcessingResult> onAttestation(
      final ValidateableAttestation attestation) {
    return recentChainData
        .retrieveCheckpointState(attestation.getData().getTarget())
        .thenCompose(
            targetBlockState ->
                withLock(
                    () -> {
                      final StoreTransaction transaction = recentChainData.startStoreTransaction();
                      final AttestationProcessingResult result =
                          on_attestation(
                              transaction, attestation, targetBlockState, getForkChoiceStrategy());
                      return result.isSuccessful()
                          ? transaction.commit().thenApply(__ -> result)
                          : SafeFuture.completedFuture(result);
                    }));
  }

  public void save() {
    getForkChoiceStrategy().save();
  }

  public void applyIndexedAttestations(final List<ValidateableAttestation> attestations) {
    withLock(
            () -> {
              final StoreTransaction transaction = recentChainData.startStoreTransaction();
              final ForkChoiceStrategy forkChoiceStrategy = getForkChoiceStrategy();
              attestations.stream()
                  .map(ValidateableAttestation::getIndexedAttestation)
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

  private <T> SafeFuture<T> withLock(final Supplier<SafeFuture<T>> action) {
    try {
      lock.acquire();
    } catch (InterruptedException e) {
      throw new ShuttingDownException();
    }
    return SafeFuture.ofComposed(action::get).alwaysRun(lock::release);
  }
}
