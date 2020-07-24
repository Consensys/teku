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

package tech.pegasys.teku.storage.store;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.datastructures.hashtree.HashTree;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.storage.events.FinalizedChainData;
import tech.pegasys.teku.storage.events.StorageUpdate;
import tech.pegasys.teku.storage.store.Store.Transaction;

class StoreTransactionUpdates {
  private final Store.Transaction tx;

  private final Optional<FinalizedChainData> finalizedChainData;
  private final Map<Bytes32, SignedBeaconBlock> hotBlocks;
  private final Map<Bytes32, BeaconState> hotStates;
  private final Map<Bytes32, SlotAndBlockRoot> stateRoots;
  private final Set<Bytes32> prunedHotBlockRoots;
  private final Optional<HashTree> updatedBlockTree;

  StoreTransactionUpdates(
      final Transaction tx,
      final Optional<FinalizedChainData> finalizedChainData,
      final Map<Bytes32, SignedBeaconBlock> hotBlocks,
      final Map<Bytes32, BeaconState> hotStates,
      final Set<Bytes32> prunedHotBlockRoots,
      final Optional<HashTree> updatedBlockTree,
      final Map<Bytes32, SlotAndBlockRoot> stateRoots) {
    checkNotNull(tx, "Transaction is required");
    checkNotNull(finalizedChainData, "Finalized data is required");
    checkNotNull(hotBlocks, "Hot blocks are required");
    checkNotNull(hotStates, "Hot states are required");
    checkNotNull(prunedHotBlockRoots, "Pruned roots are required");
    checkNotNull(updatedBlockTree, "Update tree is required");
    checkNotNull(stateRoots, "State roots are required");

    this.tx = tx;
    this.finalizedChainData = finalizedChainData;
    this.hotBlocks = hotBlocks;
    this.hotStates = hotStates;
    this.prunedHotBlockRoots = prunedHotBlockRoots;
    this.updatedBlockTree = updatedBlockTree;
    this.stateRoots = stateRoots;
  }

  public StorageUpdate createStorageUpdate() {
    return new StorageUpdate(
        tx.genesis_time,
        finalizedChainData,
        tx.justified_checkpoint,
        tx.best_justified_checkpoint,
        hotBlocks,
        prunedHotBlockRoots,
        tx.votes,
        stateRoots);
  }

  public void applyToStore(final Store store) {
    // Add new data
    tx.time.ifPresent(value -> store.time = value);
    tx.genesis_time.ifPresent(value -> store.genesis_time = value);
    tx.justified_checkpoint.ifPresent(value -> store.justified_checkpoint = value);
    tx.best_justified_checkpoint.ifPresent(value -> store.best_justified_checkpoint = value);
    store.blocks.putAll(hotBlocks);
    store.block_states.putAll(hotStates);
    updatedBlockTree.ifPresent(updated -> store.blockTree = updated);
    store.votes.putAll(tx.votes);

    // Update finalized data
    finalizedChainData.ifPresent(
        finalizedData -> {
          store.finalized_checkpoint = finalizedData.getFinalizedCheckpoint();
          store.finalizedBlockAndState = finalizedData.getLatestFinalizedBlockAndState();
        });

    // Prune blocks and states
    prunedHotBlockRoots.forEach(
        (root) -> {
          store.blocks.remove(root);
          store.block_states.remove(root);
        });
  }
}
