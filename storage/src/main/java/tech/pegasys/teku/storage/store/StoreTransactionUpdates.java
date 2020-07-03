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

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.datastructures.hashtree.HashTree;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.datastructures.state.CheckpointAndBlock;
import tech.pegasys.teku.storage.events.FinalizedChainData;
import tech.pegasys.teku.storage.events.StorageUpdate;
import tech.pegasys.teku.storage.store.Store.Transaction;

class StoreTransactionUpdates {
  private final Store.Transaction tx;

  private final Optional<FinalizedChainData> finalizedChainData;
  private final Map<Bytes32, SignedBeaconBlock> hotBlocks;
  private final Map<Bytes32, BeaconState> hotStates;
  private final Set<Bytes32> prunedHotBlockRoots;
  private final Optional<HashTree> updatedBlockTree;

  private StoreTransactionUpdates(
      final Transaction tx,
      final Optional<FinalizedChainData> finalizedChainData,
      final Map<Bytes32, SignedBeaconBlock> hotBlocks,
      final Map<Bytes32, BeaconState> hotStates,
      final Set<Bytes32> prunedHotBlockRoots,
      final Optional<HashTree> updatedBlockTree) {
    this.tx = tx;
    this.finalizedChainData = finalizedChainData;
    this.hotBlocks = hotBlocks;
    this.hotStates = hotStates;
    this.prunedHotBlockRoots = prunedHotBlockRoots;
    this.updatedBlockTree = updatedBlockTree;
  }

  public static StoreTransactionUpdates calculate(final Store baseStore, final Transaction tx) {
    // Save copy of tx data that may be pruned
    final Map<Bytes32, SignedBeaconBlock> hotBlocks = new HashMap<>(tx.blocks);
    final Map<Bytes32, BeaconState> hotStates = new HashMap<>(tx.block_states);

    // If a new checkpoint has been finalized, calculated what to finalize and what to prune
    final CheckpointAndBlock prevFinalizedCheckpoint = baseStore.getFinalizedCheckpointAndBlock();
    final Optional<CheckpointAndBlock> newFinalizedCheckpoint =
        Optional.of(tx.getFinalizedCheckpointAndBlock())
            .filter(c -> c.getEpoch().compareTo(prevFinalizedCheckpoint.getEpoch()) > 0);

    // Calculate new tree structure
    final Optional<HashTree> updatedBlockTree;
    if (newFinalizedCheckpoint.isPresent() || hotBlocks.size() > 0) {
      final Bytes32 updatedRoot =
          newFinalizedCheckpoint
              .map(CheckpointAndBlock::getRoot)
              .orElse(baseStore.blockTree.getRootHash());
      HashTree.Builder blockTreeUpdater =
          baseStore.blockTree.withRoot(updatedRoot).blocks(hotBlocks.values());
      newFinalizedCheckpoint.ifPresent(finalized -> blockTreeUpdater.block(finalized.getBlock()));
      updatedBlockTree = Optional.of(blockTreeUpdater.build());
    } else {
      updatedBlockTree = Optional.empty();
    }

    // Calculate finalized chain data
    final Optional<FinalizedChainData> finalizedChainData;
    final Set<Bytes32> prunedHotBlockRoots;
    if (newFinalizedCheckpoint.isPresent()) {
      final CheckpointAndBlock finalizedCheckpoint = newFinalizedCheckpoint.get();
      final SignedBeaconBlock finalizedBlock = finalizedCheckpoint.getBlock();
      final Map<Bytes32, Bytes32> finalizedChildToParent =
          collectFinalizedRoots(baseStore, finalizedBlock, hotBlocks.values());
      Set<SignedBeaconBlock> finalizedBlocks = collectFinalizedBlocks(tx, finalizedChildToParent);
      Map<Bytes32, BeaconState> finalizedStates =
          collectFinalizedStates(tx, finalizedChildToParent);

      finalizedChainData =
          Optional.of(
              FinalizedChainData.builder()
                  .finalizedCheckpoint(finalizedCheckpoint.getCheckpoint())
                  .finalizedBlock(finalizedCheckpoint.getBlock())
                  .latestFinalizedState(tx.getBlockState(finalizedBlock.getRoot()))
                  .finalizedChildAndParent(finalizedChildToParent)
                  .finalizedBlocks(finalizedBlocks)
                  .finalizedStates(finalizedStates)
                  .build());

      prunedHotBlockRoots =
          calculatePrunedHotBlockRoots(baseStore, tx, updatedBlockTree.orElseThrow());

      // Prune transaction collections
      prunedHotBlockRoots.forEach(hotBlocks::remove);
      prunedHotBlockRoots.forEach(hotStates::remove);
    } else {
      finalizedChainData = Optional.empty();
      prunedHotBlockRoots = Collections.emptySet();
    }

    return new StoreTransactionUpdates(
        tx, finalizedChainData, hotBlocks, hotStates, prunedHotBlockRoots, updatedBlockTree);
  }

  private static Map<Bytes32, Bytes32> collectFinalizedRoots(
      final Store baseStore,
      final SignedBeaconBlock newlyFinalizedBlock,
      final Collection<SignedBeaconBlock> newBlocks) {

    final HashTree finalizedTree =
        baseStore.blockTree.contains(newlyFinalizedBlock.getRoot())
            ? baseStore.blockTree
            : baseStore.blockTree.updater().blocks(newBlocks).build();

    final HashMap<Bytes32, Bytes32> childToParent = new HashMap<>();
    finalizedTree.processHashesInChain(newlyFinalizedBlock.getRoot(), childToParent::put);
    return childToParent;
  }

  private static Set<SignedBeaconBlock> collectFinalizedBlocks(
      final Store.Transaction tx, final Map<Bytes32, Bytes32> finalizedChildToParent) {
    return finalizedChildToParent.keySet().stream()
        .map(root -> tx.getBlockIfAvailable(root).orElse(null))
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());
  }

  private static Map<Bytes32, BeaconState> collectFinalizedStates(
      final Store.Transaction tx, final Map<Bytes32, Bytes32> finalizedChildToParent) {
    final Map<Bytes32, BeaconState> states = new HashMap<>();
    for (Bytes32 finalizedRoot : finalizedChildToParent.keySet()) {
      tx.getBlockStateIfAvailable(finalizedRoot)
          .ifPresent(state -> states.put(finalizedRoot, state));
    }
    return states;
  }

  private static Set<Bytes32> calculatePrunedHotBlockRoots(
      final Store baseStore, Transaction tx, final HashTree prunedTree) {

    Set<Bytes32> roots =
        baseStore.blockTree.getAllRoots().stream()
            .filter(root -> !prunedTree.contains(root))
            .collect(Collectors.toSet());

    tx.getBlockRoots().stream().filter(root -> !prunedTree.contains(root)).forEach(roots::add);
    return roots;
  }

  public StorageUpdate createStorageUpdate() {
    return new StorageUpdate(
        tx.genesis_time,
        finalizedChainData,
        tx.justified_checkpoint,
        tx.best_justified_checkpoint,
        hotBlocks,
        prunedHotBlockRoots,
        tx.votes);
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
          final Checkpoint finalizedCheckpoint = finalizedData.getFinalizedCheckpoint();
          final Bytes32 finalizedRoot = finalizedCheckpoint.getRoot();
          store.finalized_checkpoint = finalizedCheckpoint;
          final SignedBeaconBlock finalizedBlock = tx.getSignedBlock(finalizedRoot);
          final BeaconState finalizedState = finalizedData.getLatestFinalizedState();
          store.finalizedBlockAndState = new SignedBlockAndState(finalizedBlock, finalizedState);
        });

    // Prune blocks and states
    prunedHotBlockRoots.forEach(
        (root) -> {
          store.blocks.remove(root);
          store.block_states.remove(root);
        });
  }
}
