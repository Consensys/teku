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

import com.google.common.primitives.UnsignedLong;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.core.StateTransition;
import tech.pegasys.teku.core.results.BlockImportResult;
import tech.pegasys.teku.datastructures.attestation.ValidateableAttestation;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.forkchoice.MutableStore;
import tech.pegasys.teku.datastructures.operations.IndexedAttestation;
import tech.pegasys.teku.datastructures.util.AttestationProcessingResult;
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

  public synchronized Bytes32 processHead() {
    return processHead(Optional.empty());
  }

  public synchronized Bytes32 processHead(UnsignedLong nodeSlot) {
    return processHead(Optional.of(nodeSlot));
  }

  public synchronized Bytes32 processHead(Optional<UnsignedLong> nodeSlot) {
    StoreTransaction transaction = recentChainData.startStoreTransaction();
    final ForkChoiceStrategy forkChoiceStrategy = getForkChoiceStrategy();
    Bytes32 headBlockRoot = forkChoiceStrategy.findHead(transaction);
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
    return headBlockRoot;
  }

  public synchronized BlockImportResult onBlock(final SignedBeaconBlock block) {
    final ForkChoiceStrategy forkChoiceStrategy = getForkChoiceStrategy();
    StoreTransaction transaction = recentChainData.startStoreTransaction();
    final BlockImportResult result =
        on_block(transaction, block, stateTransition, forkChoiceStrategy);

    if (!result.isSuccessful()) {
      return result;
    }

    transaction.commit().join();
    result
        .getBlockProcessingRecord()
        .ifPresent(record -> forkChoiceStrategy.onBlock(block.getMessage(), record.getPostState()));

    return result;
  }

  public AttestationProcessingResult onAttestation(
      final MutableStore store, final ValidateableAttestation attestation) {
    return on_attestation(store, attestation, stateTransition, getForkChoiceStrategy());
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
