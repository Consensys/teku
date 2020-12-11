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

import com.google.common.collect.Maps;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.blocks.BlockAndCheckpointEpochs;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.datastructures.blocks.StateAndBlockSummary;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.storage.events.FinalizedChainData;
import tech.pegasys.teku.storage.events.StorageUpdate;

class StoreTransactionUpdates {
  private final StoreTransaction tx;

  private final Optional<FinalizedChainData> finalizedChainData;
  private final Map<Bytes32, BlockAndCheckpointEpochs> hotBlocks;
  private final Map<Bytes32, SignedBlockAndState> hotBlockAndStates;
  // A subset of hot states to be persisted to disk
  private final Map<Bytes32, BeaconState> hotStatesToPersist;
  private final Map<Bytes32, SlotAndBlockRoot> stateRoots;
  private final Set<Bytes32> prunedHotBlockRoots;

  StoreTransactionUpdates(
      final StoreTransaction tx,
      final Optional<FinalizedChainData> finalizedChainData,
      final Map<Bytes32, BlockAndCheckpointEpochs> hotBlocks,
      final Map<Bytes32, SignedBlockAndState> hotBlockAndStates,
      final Map<Bytes32, BeaconState> hotStatesToPersist,
      final Set<Bytes32> prunedHotBlockRoots,
      final Map<Bytes32, SlotAndBlockRoot> stateRoots) {
    checkNotNull(tx, "Transaction is required");
    checkNotNull(finalizedChainData, "Finalized data is required");
    checkNotNull(hotBlocks, "Hot blocks are required");
    checkNotNull(hotBlockAndStates, "Hot states are required");
    checkNotNull(hotStatesToPersist, "Hot states to persist are required");
    checkNotNull(prunedHotBlockRoots, "Pruned roots are required");
    checkNotNull(stateRoots, "State roots are required");

    this.tx = tx;
    this.finalizedChainData = finalizedChainData;
    this.hotBlocks = hotBlocks;
    this.hotBlockAndStates = hotBlockAndStates;
    this.hotStatesToPersist = hotStatesToPersist;
    this.prunedHotBlockRoots = prunedHotBlockRoots;
    this.stateRoots = stateRoots;
  }

  public StorageUpdate createStorageUpdate() {
    return new StorageUpdate(
        tx.genesis_time,
        finalizedChainData,
        tx.justified_checkpoint,
        tx.best_justified_checkpoint,
        hotBlocks,
        hotStatesToPersist,
        prunedHotBlockRoots,
        tx.votes,
        stateRoots);
  }

  public void applyToStore(final Store store) {
    // Add new data
    tx.time.filter(t -> t.isGreaterThan(store.getTime())).ifPresent(value -> store.time = value);
    tx.genesis_time.ifPresent(value -> store.genesis_time = value);
    tx.justified_checkpoint.ifPresent(value -> store.justified_checkpoint = value);
    tx.best_justified_checkpoint.ifPresent(value -> store.best_justified_checkpoint = value);
    hotBlocks.forEach((root, value) -> store.blocks.put(root, value.getBlock()));
    store.states.cacheAll(Maps.transformValues(hotBlockAndStates, this::blockAndStateAsSummary));
    store.votes.putAll(tx.votes);

    // Update finalized data
    finalizedChainData.ifPresent(
        finalizedData -> {
          store.finalizedAnchor = finalizedData.getLatestFinalized();
        });

    // Prune blocks and states
    prunedHotBlockRoots.forEach(
        (root) -> {
          store.blocks.remove(root);
          store.states.remove(root);
          store.checkpointStates.removeIf(
              slotAndBlockRoot -> slotAndBlockRoot.getBlockRoot().equals(root));
        });

    store.blockMetadata =
        store.blockMetadata.applyUpdate(
            hotBlocks.values(), prunedHotBlockRoots, store.getFinalizedCheckpoint());
  }

  private StateAndBlockSummary blockAndStateAsSummary(final SignedBlockAndState blockAndState) {
    return blockAndState;
  }
}
