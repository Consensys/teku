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

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.core.StateTransition;
import tech.pegasys.teku.core.results.BlockImportResult;
import tech.pegasys.teku.datastructures.attestation.ValidateableAttestation;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.datastructures.forkchoice.MutableStore;
import tech.pegasys.teku.datastructures.operations.IndexedAttestation;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.datastructures.util.AttestationProcessingResult;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.protoarray.ForkChoiceStrategy;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.store.UpdatableStore.StoreTransaction;

public class ForkChoice {
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

  public synchronized void processHead() {
    processHead(Optional.empty());
  }

  public synchronized void processHead(UInt64 nodeSlot) {
    processHead(Optional.of(nodeSlot));
  }

  private synchronized void processHead(Optional<UInt64> nodeSlot) {
    final Checkpoint finalizedCheckpoint = recentChainData.getStore().getFinalizedCheckpoint();
    final Checkpoint justifiedCheckpoint = recentChainData.getStore().getJustifiedCheckpoint();
    recentChainData
        .retrieveCheckpointState(justifiedCheckpoint)
        .thenAccept(
            justifiedCheckpointState -> {
              StoreTransaction transaction = recentChainData.startStoreTransaction();
              final ForkChoiceStrategy forkChoiceStrategy = getForkChoiceStrategy();
              Bytes32 headBlockRoot =
                  forkChoiceStrategy.findHead(
                      transaction,
                      finalizedCheckpoint,
                      justifiedCheckpoint,
                      justifiedCheckpointState.orElseThrow());
              transaction.commit(() -> {}, "Failed to persist validator vote changes.");

              recentChainData.updateBestBlock(
                  headBlockRoot,
                  nodeSlot.orElse(
                      forkChoiceStrategy
                          .blockSlot(headBlockRoot)
                          .orElseThrow(
                              () ->
                                  new IllegalStateException(
                                      "Unable to retrieve the slot of fork choice head"))));
            })
        .join();
  }

  public synchronized BlockImportResult onBlock(
      final SignedBeaconBlock block, Optional<BeaconState> preState) {
    final ForkChoiceStrategy forkChoiceStrategy = getForkChoiceStrategy();
    StoreTransaction transaction = recentChainData.startStoreTransaction();
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
      return result;
    }

    transaction.commit().join();
    result
        .getBlockProcessingRecord()
        .ifPresent(record -> forkChoiceStrategy.onBlock(block.getMessage(), record.getPostState()));

    return result;
  }

  public SafeFuture<AttestationProcessingResult> onAttestation(
      final ValidateableAttestation attestation) {
    return recentChainData
        .retrieveCheckpointState(attestation.getData().getTarget())
        .thenApply(
            targetState -> {
              StoreTransaction transaction = recentChainData.startStoreTransaction();
              final AttestationProcessingResult result =
                  on_attestation(transaction, attestation, targetState, getForkChoiceStrategy());
              if (result.isSuccessful()) {
                transaction.commit(() -> {}, "Failed to persist attestation result");
              }
              return result;
            });
  }

  public void save() {
    getForkChoiceStrategy().save();
  }

  public void applyIndexedAttestation(
      final MutableStore store, final IndexedAttestation indexedAttestation) {
    getForkChoiceStrategy().onAttestation(store, indexedAttestation);
  }

  private ForkChoiceStrategy getForkChoiceStrategy() {
    return recentChainData
        .getForkChoiceStrategy()
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Attempting to perform fork choice operations before store has been initialized"));
  }
}
