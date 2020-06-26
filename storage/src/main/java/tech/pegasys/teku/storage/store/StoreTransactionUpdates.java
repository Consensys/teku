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
import java.util.HashSet;
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
  private final Map<Checkpoint, BeaconState> checkpointStates;
  private final Set<Checkpoint> prunedCheckpointStates;
  private final HashTree updatedBlockTree;

  private StoreTransactionUpdates(
      final Transaction tx,
      final Optional<FinalizedChainData> finalizedChainData,
      final Map<Bytes32, SignedBeaconBlock> hotBlocks,
      final Map<Bytes32, BeaconState> hotStates,
      final Set<Bytes32> prunedHotBlockRoots,
      final Map<Checkpoint, BeaconState> checkpointStates,
      final Set<Checkpoint> prunedCheckpointStates,
      final HashTree updatedBlockTree) {
    this.tx = tx;
    this.finalizedChainData = finalizedChainData;
    this.hotBlocks = hotBlocks;
    this.hotStates = hotStates;
    this.prunedHotBlockRoots = prunedHotBlockRoots;
    this.checkpointStates = checkpointStates;
    this.prunedCheckpointStates = prunedCheckpointStates;
    this.updatedBlockTree = updatedBlockTree;
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

    // Calculate new tree structure
    final Bytes32 updatedRoot =
        newFinalizedCheckpoint
            .map(CheckpointAndBlock::getRoot)
            .orElse(baseStore.blockTree.getRootHash());
    HashTree.Builder blockTreeUpdater =
        baseStore.blockTree.withRoot(updatedRoot).blocks(hotBlocks.values());
    newFinalizedCheckpoint.ifPresent(finalized -> blockTreeUpdater.block(finalized.getBlock()));
    final HashTree updatedBlockTree = blockTreeUpdater.build();

    // Calculate finalized chain data
    final Optional<FinalizedChainData> finalizedChainData;
    final Set<Bytes32> prunedHotBlockRoots;
    final Set<Checkpoint> prunedCheckpoints;
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

      prunedHotBlockRoots = calculatePrunedHotBlockRoots(baseStore, tx, updatedBlockTree);
      prunedCheckpoints =
          calculatePrunedCheckpoints(baseStore, tx, finalizedCheckpoint.getCheckpoint());

      // Prune transaction collections
      prunedCheckpoints.forEach(checkpointStates::remove);
      prunedHotBlockRoots.forEach(hotBlocks::remove);
      prunedHotBlockRoots.forEach(hotStates::remove);
    } else {
      finalizedChainData = Optional.empty();
      prunedHotBlockRoots = Collections.emptySet();
      prunedCheckpoints = Collections.emptySet();
    }

    return new StoreTransactionUpdates(
        tx,
        finalizedChainData,
        hotBlocks,
        hotStates,
        prunedHotBlockRoots,
        checkpointStates,
        prunedCheckpoints,
        updatedBlockTree);
  }

  private static Set<Checkpoint> calculatePrunedCheckpoints(
      final Store baseStore, final Transaction tx, final Checkpoint finalizedCheckpoint) {
    final Set<Checkpoint> allCheckpoints = new HashSet<>(baseStore.checkpoint_states.keySet());
    allCheckpoints.addAll(tx.checkpoint_states.keySet());
    return allCheckpoints.stream()
        .filter(c -> c.getEpoch().compareTo(finalizedCheckpoint.getEpoch()) < 0)
        .collect(Collectors.toSet());
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
    store.blockTree = updatedBlockTree;

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

    // Prune stale checkpoint states
    prunedCheckpointStates.forEach(store.checkpoint_states::remove);
    // Prune blocks and states
    prunedHotBlockRoots.forEach(
        (root) -> {
          store.blocks.remove(root);
          store.block_states.remove(root);
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
}
