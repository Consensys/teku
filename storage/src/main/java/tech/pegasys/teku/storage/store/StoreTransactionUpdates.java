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

import static tech.pegasys.teku.storage.store.Store.indexBlockRootsBySlot;
import static tech.pegasys.teku.storage.store.Store.removeBlockRootFromSlotIndex;

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
import tech.pegasys.teku.storage.events.FinalizedChainData;
import tech.pegasys.teku.storage.events.StorageUpdate;
import tech.pegasys.teku.storage.store.Store.Transaction;
import tech.pegasys.teku.storage.store.UpdatableStore.StoreTransaction;

class StoreTransactionUpdates {
  private final Store.Transaction tx;

  private final Optional<FinalizedChainData> finalizedChainData;
  private final Map<Bytes32, SignedBeaconBlock> hotBlocks;
  private final Map<Bytes32, BeaconState> hotStates;
  private final Map<UnsignedLong, Set<Bytes32>> prunedHotBlockRoots;
  private final Map<Checkpoint, BeaconState> checkpointStates;
  private final Set<Checkpoint> prunedCheckpointStates;

  private StoreTransactionUpdates(
      final Transaction tx,
      final Optional<FinalizedChainData> finalizedChainData,
      final Map<Bytes32, SignedBeaconBlock> hotBlocks,
      final Map<Bytes32, BeaconState> hotStates,
      final Map<UnsignedLong, Set<Bytes32>> prunedHotBlockRoots,
      final Map<Checkpoint, BeaconState> checkpointStates,
      final Set<Checkpoint> prunedCheckpointStates) {
    this.tx = tx;
    this.finalizedChainData = finalizedChainData;
    this.hotBlocks = hotBlocks;
    this.hotStates = hotStates;
    this.prunedHotBlockRoots = prunedHotBlockRoots;
    this.checkpointStates = checkpointStates;
    this.prunedCheckpointStates = prunedCheckpointStates;
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
    final Optional<FinalizedChainData> finalizedChainData;
    final Map<UnsignedLong, Set<Bytes32>> prunedHotBlockRoots;
    final Set<Checkpoint> prunedCheckpoints;
    if (newFinalizedCheckpoint.isPresent()) {
      final CheckpointAndBlock finalizedCheckpoint = newFinalizedCheckpoint.get();
      final SignedBeaconBlock finalizedBlock = finalizedCheckpoint.getBlock();
      Set<SignedBeaconBlock> finalizedBlocks =
          collectFinalizedBlocks(tx, prevFinalizedCheckpoint, finalizedBlock);
      Map<Bytes32, BeaconState> finalizedStates = collectFinalizedStates(tx, finalizedBlocks);

      finalizedChainData =
          Optional.of(
              FinalizedChainData.builder()
                  .finalizedCheckpoint(finalizedCheckpoint.getCheckpoint())
                  .latestFinalizedState(tx.getBlockState(finalizedBlock.getRoot()))
                  .finalizedBlock(finalizedCheckpoint.getBlock())
                  .finalizedBlocks(finalizedBlocks)
                  .finalizedStates(finalizedStates)
                  .build());

      prunedHotBlockRoots =
          calculatePrunedHotBlockRoots(
              baseStore, tx, finalizedCheckpoint, finalizedBlock, hotBlocks);
      prunedCheckpoints =
          calculatePrunedCheckpoints(baseStore, tx, finalizedCheckpoint.getCheckpoint());

      // Prune transaction collections
      prunedCheckpoints.forEach(checkpointStates::remove);
      prunedHotBlockRoots.forEach((slot, roots) -> roots.forEach(hotBlocks::remove));
      prunedHotBlockRoots.forEach((slot, roots) -> roots.forEach(hotStates::remove));
    } else {
      finalizedChainData = Optional.empty();
      prunedHotBlockRoots = Collections.emptyMap();
      prunedCheckpoints = Collections.emptySet();
    }

    return new StoreTransactionUpdates(
        tx,
        finalizedChainData,
        hotBlocks,
        hotStates,
        prunedHotBlockRoots,
        checkpointStates,
        prunedCheckpoints);
  }

  private static Set<Checkpoint> calculatePrunedCheckpoints(
      final Store baseStore, final Transaction tx, final Checkpoint finalizedCheckpoint) {
    final Set<Checkpoint> allCheckpoints = new HashSet<>(baseStore.checkpoint_states.keySet());
    allCheckpoints.addAll(tx.checkpoint_states.keySet());
    return allCheckpoints.stream()
        .filter(c -> c.getEpoch().compareTo(finalizedCheckpoint.getEpoch()) < 0)
        .collect(Collectors.toSet());
  }

  private static Set<SignedBeaconBlock> collectFinalizedBlocks(
      final StoreTransaction tx,
      final CheckpointAndBlock prevFinalizedCheckpoint,
      final SignedBeaconBlock newlyFinalizedBlock) {
    // Collect blocks
    final Set<SignedBeaconBlock> blocks = new HashSet<>();
    SignedBeaconBlock oldestFinalizedBlock = newlyFinalizedBlock;
    SignedBeaconBlock currentBlock = newlyFinalizedBlock;
    while (currentBlock != null
        && currentBlock.getSlot().compareTo(prevFinalizedCheckpoint.getBlockSlot()) > 0) {
      blocks.add(currentBlock);
      oldestFinalizedBlock = currentBlock;
      currentBlock = tx.getSignedBlock(currentBlock.getParent_root());
    }

    // Make sure we capture all finalized blocks
    if (!oldestFinalizedBlock.getParent_root().equals(prevFinalizedCheckpoint.getRoot())) {
      throw new IllegalStateException("Unable to retrieve all finalized blocks");
    }

    return blocks;
  }

  private static Map<Bytes32, BeaconState> collectFinalizedStates(
      final Store.Transaction tx, final Set<SignedBeaconBlock> finalizedBlocks) {
    final Map<Bytes32, BeaconState> states = new HashMap<>();
    for (SignedBeaconBlock finalizedBlock : finalizedBlocks) {
      final Bytes32 blockRoot = finalizedBlock.getRoot();
      tx.getBlockStateIfAvailable(finalizedBlock.getRoot())
          .ifPresent(state -> states.put(blockRoot, state));
    }
    return states;
  }

  private static Map<UnsignedLong, Set<Bytes32>> calculatePrunedHotBlockRoots(
      final Store baseStore,
      final StoreTransaction tx,
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
        finalizedChainData,
        tx.justified_checkpoint,
        tx.best_justified_checkpoint,
        hotBlocks,
        getPrunedRoots(),
        checkpointStates,
        prunedCheckpointStates,
        tx.getForkChoiceVotes());
  }

  public void applyToStore(final Store store) {
    // Add new data
    tx.time.ifPresent(value -> store.time = value);
    tx.genesis_time.ifPresent(value -> store.genesis_time = value);
    tx.justified_checkpoint.ifPresent(value -> store.justified_checkpoint = value);
    tx.best_justified_checkpoint.ifPresent(value -> store.best_justified_checkpoint = value);
    store.blocks.putAll(hotBlocks);
    store.block_states.putAll(hotStates);
    store.checkpoint_states.putAll(checkpointStates);

    // Update finalized data
    finalizedChainData.ifPresent(
        finalizedData -> {
          final Checkpoint finalizedCheckpoint = finalizedData.getFinalizedCheckpoint();
          final Bytes32 finalizedRoot = finalizedCheckpoint.getRoot();
          store.finalized_checkpoint = finalizedCheckpoint;
          final SignedBeaconBlock finalizedBlock = finalizedData.getBlocks().get(finalizedRoot);
          final BeaconState finalizedState = finalizedData.getLatestFinalizedState();
          store.finalizedBlockAndState = new SignedBlockAndState(finalizedBlock, finalizedState);
        });

    // Track roots by slot
    indexBlockRootsBySlot(store.rootsBySlotLookup, hotBlocks.values());

    // Prune stale checkpoint states
    prunedCheckpointStates.forEach(store.checkpoint_states::remove);
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

    // Update forkchoice
    tx.forkChoiceUpdater.commit();
  }

  public void invokeUpdateHandler(final Store store, StoreUpdateHandler storeUpdateHandler) {
    // Process new finalized block
    finalizedChainData.ifPresent(
        data -> storeUpdateHandler.onNewFinalizedCheckpoint(data.getFinalizedCheckpoint()));

    // Process new head
    if (tx.headUpdated) {
      storeUpdateHandler.onNewHeadBlock(store.forkChoiceState.getHead());
    }
  }

  private Set<Bytes32> getPrunedRoots() {
    return prunedHotBlockRoots.values().stream()
        .flatMap(Collection::stream)
        .collect(Collectors.toSet());
  }
}
