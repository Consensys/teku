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

package tech.pegasys.teku.storage.client;

import static tech.pegasys.teku.storage.client.Store.indexBlockRootsBySlot;
import static tech.pegasys.teku.storage.client.Store.removeBlockRootFromSlotIndex;

import com.google.common.primitives.UnsignedLong;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.datastructures.state.CheckpointAndBlock;
import tech.pegasys.teku.storage.client.Store.Transaction;
import tech.pegasys.teku.storage.events.StorageUpdate;

class StoreTransactionUpdates {
  private final Store.Transaction tx;

  private final Map<Bytes32, SignedBeaconBlock> hotBlocks;
  private final Map<Bytes32, SignedBlockAndState> finalizedChainData;
  private final Map<UnsignedLong, Set<Bytes32>> prunedHotBlockRoots;
  private final Map<Checkpoint, BeaconState> checkpointStates;
  private final Set<Checkpoint> staleCheckpointStates;
  private final Optional<CheckpointAndBlock> newFinalizedCheckpoint;
  private final Map<Bytes32, BeaconState> hotStates;

  private StoreTransactionUpdates(
      final Transaction tx,
      final Map<Bytes32, SignedBeaconBlock> hotBlocks,
      final Map<Bytes32, BeaconState> hotStates,
      final Map<Bytes32, SignedBlockAndState> finalizedChainData,
      final Map<UnsignedLong, Set<Bytes32>> prunedHotBlockRoots,
      final Map<Checkpoint, BeaconState> checkpointStates,
      final Set<Checkpoint> staleCheckpointStates,
      final Optional<CheckpointAndBlock> newFinalizedCheckpoint) {
    this.tx = tx;
    this.hotBlocks = hotBlocks;
    this.hotStates = hotStates;
    this.finalizedChainData = finalizedChainData;
    this.prunedHotBlockRoots = prunedHotBlockRoots;
    this.checkpointStates = checkpointStates;
    this.staleCheckpointStates = staleCheckpointStates;
    this.newFinalizedCheckpoint = newFinalizedCheckpoint;
  }

  public static StoreTransactionUpdates calculate(final Store baseStore, final Transaction tx) {
    // Save copy of tx data that may be pruned
    final Map<Bytes32, SignedBeaconBlock> hotBlocks = new HashMap<>(tx.blocks);
    final Map<Bytes32, BeaconState> hotStates = new HashMap<>(tx.block_states);
    final Map<Checkpoint, BeaconState> checkpointStates = new HashMap<>(tx.checkpoint_states);

    // If a new checkpoint has been finalized, calculated what to finalize and what to prune
    final CheckpointAndBlock prevFinalizedCheckpoint = baseStore.getFinalizedCheckpointAndBlock();
    final Optional<CheckpointAndBlock> newFinalizedCheckpoint =
        Optional.of(tx.getFinalizedCheckpointAndBlock())
            .filter(c -> c.getEpoch().compareTo(prevFinalizedCheckpoint.getEpoch()) > 0);

    // Calculate finalized chain data
    final Map<Bytes32, SignedBlockAndState> finalizedChainData;
    final Map<UnsignedLong, Set<Bytes32>> prunedHotBlockRoots;
    final Set<Checkpoint> prunedCheckpoints;
    if (newFinalizedCheckpoint.isPresent()) {
      final CheckpointAndBlock finalizedCheckpoint = newFinalizedCheckpoint.get();
      final BeaconState finalizedState = tx.getBlockState(finalizedCheckpoint.getRoot());
      final SignedBlockAndState finalizedBlockAndState =
          new SignedBlockAndState(finalizedCheckpoint.getBlock(), finalizedState);

      finalizedChainData =
          calculateFinalizedChainData(tx, prevFinalizedCheckpoint, finalizedBlockAndState);
      prunedHotBlockRoots =
          calculatePrunedHotBlockRoots(
              baseStore, tx, finalizedCheckpoint, finalizedBlockAndState.getBlock(), hotBlocks);
      prunedCheckpoints =
          calculatePrunedCheckpoints(baseStore, tx, finalizedCheckpoint.getCheckpoint());

      // Prune transaction collections
      prunedCheckpoints.forEach(checkpointStates::remove);
      prunedHotBlockRoots.forEach((slot, roots) -> roots.forEach(hotBlocks::remove));
      prunedHotBlockRoots.forEach((slot, roots) -> roots.forEach(hotStates::remove));
    } else {
      finalizedChainData = Collections.emptyMap();
      prunedHotBlockRoots = Collections.emptyMap();
      prunedCheckpoints = Collections.emptySet();
    }

    return new StoreTransactionUpdates(
        tx,
        hotBlocks,
        hotStates,
        finalizedChainData,
        prunedHotBlockRoots,
        checkpointStates,
        prunedCheckpoints,
        newFinalizedCheckpoint);
  }

  private static Set<Checkpoint> calculatePrunedCheckpoints(
      final Store baseStore, final Transaction tx, final Checkpoint finalizedCheckpoint) {
    final Set<Checkpoint> allCheckpoints = new HashSet<>(baseStore.checkpoint_states.keySet());
    allCheckpoints.addAll(tx.checkpoint_states.keySet());
    return allCheckpoints.stream()
        .filter(c -> c.getEpoch().compareTo(finalizedCheckpoint.getEpoch()) < 0)
        .collect(Collectors.toSet());
  }

  private static Map<Bytes32, SignedBlockAndState> calculateFinalizedChainData(
      final Transaction tx,
      final CheckpointAndBlock prevFinalizedCheckpoint,
      final SignedBlockAndState newlyFinalizedBlock) {
    final Map<Bytes32, SignedBlockAndState> finalizedChainData = new HashMap<>();

    SignedBlockAndState oldestFinalizedBlock = newlyFinalizedBlock;
    SignedBlockAndState currentBlock = newlyFinalizedBlock;
    while (currentBlock != null
        && currentBlock.getSlot().compareTo(prevFinalizedCheckpoint.getBlockSlot()) > 0) {
      finalizedChainData.put(currentBlock.getRoot(), currentBlock);
      oldestFinalizedBlock = currentBlock;
      currentBlock = tx.getBlockAndState(currentBlock.getParentRoot()).orElse(null);
    }

    // Make sure we capture all finalized blocks
    if (!oldestFinalizedBlock.getParentRoot().equals(prevFinalizedCheckpoint.getRoot())) {
      throw new IllegalStateException("Unable to retrieve all finalized blocks");
    }

    return finalizedChainData;
  }

  private static Map<UnsignedLong, Set<Bytes32>> calculatePrunedHotBlockRoots(
      final Store baseStore,
      final Transaction tx,
      final CheckpointAndBlock newFinalizedCheckpoint,
      final SignedBeaconBlock finalizedBlock,
      final Map<Bytes32, SignedBeaconBlock> newBlocks) {
    final UnsignedLong finalizedSlot = newFinalizedCheckpoint.getEpochStartSlot();
    Map<UnsignedLong, Set<Bytes32>> prunedBlockRoots = new HashMap<>();

    // Build combined index from slot to block root for new and existing blocks
    final NavigableMap<UnsignedLong, Set<Bytes32>> slotToHotBlockRootIndex =
        new TreeMap<>(baseStore.rootsBySlotLookup);
    indexBlockRootsBySlot(slotToHotBlockRootIndex, newBlocks.values());

    // Prune historical blocks
    slotToHotBlockRootIndex
        .headMap(finalizedSlot, true)
        .forEach(
            (slot, roots) ->
                prunedBlockRoots.computeIfAbsent(slot, __ -> new HashSet<>()).addAll(roots));

    // Prune any hot blocks that do not descend from the latest finalized block
    final Set<Bytes32> canonicalBlocks = new HashSet<>();
    canonicalBlocks.add(finalizedBlock.getRoot());
    slotToHotBlockRootIndex
        .tailMap(finalizedSlot, true)
        .forEach(
            (slot, roots) -> {
              for (Bytes32 root : roots) {
                final BeaconBlock block = tx.getBlock(root);
                final Bytes32 blockRoot = block.hash_tree_root();
                if (canonicalBlocks.contains(block.getParent_root())) {
                  canonicalBlocks.add(blockRoot);
                } else {
                  // Prune block
                  prunedBlockRoots
                      .computeIfAbsent(block.getSlot(), __ -> new HashSet<>())
                      .add(blockRoot);
                }
              }
            });

    // Don't prune the latest finalized block
    removeBlockRootFromSlotIndex(prunedBlockRoots, finalizedBlock);

    return prunedBlockRoots;
  }

  public StorageUpdate createStorageUpdate() {
    return new StorageUpdate(
        tx.genesis_time,
        tx.justified_checkpoint,
        tx.finalized_checkpoint,
        tx.best_justified_checkpoint,
        hotBlocks,
        getPrunedRoots(),
        finalizedChainData,
        checkpointStates,
        staleCheckpointStates,
        tx.votes);
  }

  public void applyToStore(final Store store) {
    // Add new data
    tx.time.ifPresent(value -> store.time = value);
    tx.genesis_time.ifPresent(value -> store.genesis_time = value);
    tx.justified_checkpoint.ifPresent(value -> store.justified_checkpoint = value);
    tx.finalized_checkpoint.ifPresent(value -> store.finalized_checkpoint = value);
    tx.best_justified_checkpoint.ifPresent(value -> store.best_justified_checkpoint = value);
    store.blocks.putAll(hotBlocks);
    store.block_states.putAll(hotStates);
    store.checkpoint_states.putAll(checkpointStates);
    store.votes.putAll(tx.votes);
    // Track roots by slot
    indexBlockRootsBySlot(store.rootsBySlotLookup, hotBlocks.values());

    // Prune old data if we updated our finalized epoch
    if (newFinalizedCheckpoint.isPresent()) {
      // Prune stale checkpoint states
      staleCheckpointStates.forEach(store.checkpoint_states::remove);
      // Prune blocks and states
      prunedHotBlockRoots.forEach(
          (slot, roots) -> {
            roots.forEach(
                (root) -> {
                  store.blocks.remove(root);
                  store.block_states.remove(root);
                  removeBlockRootFromSlotIndex(store.rootsBySlotLookup, slot, root);
                });
          });
    }
  }

  private Set<Bytes32> getPrunedRoots() {
    return prunedHotBlockRoots.values().stream()
        .flatMap(Collection::stream)
        .collect(Collectors.toSet());
  }
}
