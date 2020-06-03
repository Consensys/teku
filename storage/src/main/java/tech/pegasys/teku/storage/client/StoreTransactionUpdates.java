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
import tech.pegasys.teku.storage.client.Store.Transaction;
import tech.pegasys.teku.storage.events.StorageUpdate;

class StoreTransactionUpdates {
  private final Store.Transaction tx;

  private final Map<Bytes32, SignedBeaconBlock> hotBlocks;
  private final Map<Bytes32, SignedBlockAndState> finalizedChainData;
  private final Map<UnsignedLong, Set<Bytes32>> prunedHotBlockRoots;
  private final Map<Checkpoint, BeaconState> checkpointStates;
  private final Set<Checkpoint> staleCheckpointStates;
  private final Optional<SignedBeaconBlock> newlyFinalizedBlock;

  private StoreTransactionUpdates(
      final Transaction tx,
      final Map<Bytes32, SignedBeaconBlock> hotBlocks,
      final Map<Bytes32, SignedBlockAndState> finalizedChainData,
      final Map<UnsignedLong, Set<Bytes32>> prunedHotBlockRoots,
      final Map<Checkpoint, BeaconState> checkpointStates,
      final Set<Checkpoint> staleCheckpointStates,
      final Optional<SignedBeaconBlock> newlyFinalizedBlock) {
    this.tx = tx;
    this.hotBlocks = hotBlocks;
    this.finalizedChainData = finalizedChainData;
    this.prunedHotBlockRoots = prunedHotBlockRoots;
    this.checkpointStates = checkpointStates;
    this.staleCheckpointStates = staleCheckpointStates;
    this.newlyFinalizedBlock = newlyFinalizedBlock;
  }

  public static StoreTransactionUpdates calculate(final Store baseStore, final Transaction tx) {
    final Map<Bytes32, SignedBeaconBlock> hotBlocks = new HashMap<>(tx.blocks);

    // If a new checkpoint has been finalized, calculated what to finalize and what to prune
    final UnsignedLong previouslyFinalizedEpoch = baseStore.finalized_checkpoint.getEpoch();
    final Optional<UnsignedLong> newlyFinalizedEpoch =
        tx.finalized_checkpoint
            .map(Checkpoint::getEpoch)
            .filter(epoch -> epoch.compareTo(previouslyFinalizedEpoch) > 0);

    // Calculate finalized chain data
    final Map<Bytes32, SignedBlockAndState> finalizedChainData;
    final Map<UnsignedLong, Set<Bytes32>> prunedHotBlockRoots;
    final Map<Checkpoint, BeaconState> checkpointStates = new HashMap<>(tx.checkpoint_states);
    final Set<Checkpoint> staleCheckpointStates;
    final Optional<SignedBeaconBlock> newlyFinalizedBlock;
    if (newlyFinalizedEpoch.isPresent()) {
      final SignedBlockAndState previouslyFinalizedBlock =
          tx.getBlockAndState(baseStore.finalized_checkpoint.getRoot())
              .orElseThrow(() -> new IllegalStateException("Finalized block is missing"));
      final SignedBlockAndState newlyFinalizedBlockAndState =
          tx.getBlockAndState(tx.finalized_checkpoint.get().getRoot())
              .orElseThrow(() -> new IllegalStateException("Newly finalized block is missing"));
      newlyFinalizedBlock = Optional.of(newlyFinalizedBlockAndState.getBlock());

      finalizedChainData =
          calculateFinalizedChainData(tx, previouslyFinalizedBlock, newlyFinalizedBlockAndState);
      prunedHotBlockRoots =
          calculatePrunedHotBlockRoots(
              baseStore, tx, tx.finalized_checkpoint.get(), newlyFinalizedBlock.get(), hotBlocks);
      // Collect stale checkpoint states to be deleted
      staleCheckpointStates =
          baseStore.checkpoint_states.keySet().stream()
              .filter(c -> c.getEpoch().compareTo(newlyFinalizedEpoch.get()) < 0)
              .collect(Collectors.toSet());

      // Make sure we save the checkpoint state for the new finalized checkpoint
      checkpointStates.put(tx.finalized_checkpoint.get(), newlyFinalizedBlockAndState.getState());

      // Remove pruned blocks from hot blocks
      prunedHotBlockRoots.forEach((slot, roots) -> roots.forEach(hotBlocks::remove));
    } else {
      newlyFinalizedBlock = Optional.empty();
      finalizedChainData = Collections.emptyMap();
      prunedHotBlockRoots = Collections.emptyMap();
      staleCheckpointStates = Collections.emptySet();
    }

    return new StoreTransactionUpdates(
        tx,
        hotBlocks,
        finalizedChainData,
        prunedHotBlockRoots,
        checkpointStates,
        staleCheckpointStates,
        newlyFinalizedBlock);
  }

  private static Map<Bytes32, SignedBlockAndState> calculateFinalizedChainData(
      final Transaction tx,
      final SignedBlockAndState previouslyFinalizedBlock,
      final SignedBlockAndState newlyFinalizedBlock) {
    final Map<Bytes32, SignedBlockAndState> finalizedChainData = new HashMap<>();

    SignedBlockAndState oldestFinalizedBlock = newlyFinalizedBlock;
    SignedBlockAndState currentBlock = newlyFinalizedBlock;
    while (currentBlock != null
        && currentBlock.getSlot().compareTo(previouslyFinalizedBlock.getSlot()) > 0) {
      finalizedChainData.put(currentBlock.getRoot(), currentBlock);
      oldestFinalizedBlock = currentBlock;
      currentBlock = tx.getBlockAndState(currentBlock.getParentRoot()).orElse(null);
    }

    // Make sure we capture all finalized blocks
    if (!oldestFinalizedBlock.getParentRoot().equals(previouslyFinalizedBlock.getRoot())) {
      throw new IllegalStateException("Unable to retrieve all finalized blocks");
    }

    return finalizedChainData;
  }

  private static Map<UnsignedLong, Set<Bytes32>> calculatePrunedHotBlockRoots(
      final Store baseStore,
      final Transaction tx,
      final Checkpoint newFinalizedCheckpoint,
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

  public Map<Bytes32, SignedBeaconBlock> getHotBlocks() {
    return hotBlocks;
  }

  public Map<UnsignedLong, Set<Bytes32>> getPrunedHotBlockRoots() {
    return prunedHotBlockRoots;
  }

  public Set<Checkpoint> getStaleCheckpointStates() {
    return staleCheckpointStates;
  }

  public Optional<SignedBeaconBlock> getNewlyFinalizedBlock() {
    return newlyFinalizedBlock;
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

  private Set<Bytes32> getPrunedRoots() {
    return prunedHotBlockRoots.values().stream()
        .flatMap(Collection::stream)
        .collect(Collectors.toSet());
  }
}
