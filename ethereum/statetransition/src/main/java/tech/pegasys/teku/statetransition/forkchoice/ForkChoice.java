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

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.core.StateTransition;
import tech.pegasys.teku.core.results.AttestationProcessingResult;
import tech.pegasys.teku.core.results.BlockImportResult;
import tech.pegasys.teku.datastructures.attestation.ValidateableAttestation;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.forkchoice.MutableStore;
import tech.pegasys.teku.datastructures.operations.IndexedAttestation;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.protoarray.ProtoArrayForkChoiceStrategy;
import tech.pegasys.teku.storage.api.FinalizedCheckpointChannel;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.store.UpdatableStore.StoreTransaction;

public class ForkChoice implements FinalizedCheckpointChannel {

  private final RecentChainData recentChainData;
  private final StateTransition stateTransition;

  private ProtoArrayForkChoiceStrategy protoArrayForkChoiceStrategy;

  public ForkChoice(final RecentChainData recentChainData, final StateTransition stateTransition) {
    this.recentChainData = recentChainData;
    this.stateTransition = stateTransition;
    recentChainData.subscribeStoreInitialized(this::initializeProtoArrayForkChoice);
  }

  private void initializeProtoArrayForkChoice() {
    protoArrayForkChoiceStrategy = ProtoArrayForkChoiceStrategy.create(recentChainData.getStore());
    processHead();
  }

  public synchronized Bytes32 processHead() {
    StoreTransaction transaction = recentChainData.startStoreTransaction();
    Bytes32 headBlockRoot = protoArrayForkChoiceStrategy.findHead(transaction);
    transaction.commit(() -> {}, "Failed to persist validator vote changes.");
    recentChainData.updateBestBlock(
        headBlockRoot,
        protoArrayForkChoiceStrategy
            .blockSlot(headBlockRoot)
            .orElseThrow(
                () ->
                    new IllegalStateException("Unable to retrieve the slot of fork choice head")));
    return headBlockRoot;
  }

  public synchronized BlockImportResult onBlock(final SignedBeaconBlock block) {
    StoreTransaction transaction = recentChainData.startStoreTransaction();
    final BlockImportResult result =
        on_block(transaction, block, stateTransition, protoArrayForkChoiceStrategy);

    if (!result.isSuccessful()) {
      return result;
    }

    transaction.commit().join();
    protoArrayForkChoiceStrategy.onBlock(recentChainData.getStore(), block.getMessage());
    return result;
  }

  public AttestationProcessingResult onAttestation(
      final MutableStore store, final ValidateableAttestation attestation) {
    return on_attestation(store, attestation, stateTransition, protoArrayForkChoiceStrategy);
  }

  public void applyIndexedAttestation(
      final MutableStore store, final IndexedAttestation indexedAttestation) {
    protoArrayForkChoiceStrategy.onAttestation(store, indexedAttestation);
  }

  @Override
  public void onNewFinalizedCheckpoint(final Checkpoint checkpoint) {
    protoArrayForkChoiceStrategy.maybePrune(checkpoint.getRoot());
  }
}
