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
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.datastructures.hashtree.HashTree;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.CheckpointAndBlock;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.storage.events.FinalizedChainData;
import tech.pegasys.teku.storage.store.Store.Transaction;

public class StoreTransactionUpdatesFactory {
  private final Store baseStore;
  private final Store.Transaction tx;

  private final Map<Bytes32, SignedBeaconBlock> hotBlocks;
  private final Map<Bytes32, BeaconState> hotStates;
  private final Map<Bytes32, SlotAndBlockRoot> stateRoots;
  private final Set<Bytes32> prunedHotBlockRoots =
      Collections.newSetFromMap(new ConcurrentHashMap<>());

  private volatile Optional<HashTree> updatedBlockTree;

  private StoreTransactionUpdatesFactory(final Store baseStore, final Transaction tx) {
    this.baseStore = baseStore;
    this.tx = tx;
    // Save copy of tx data that may be pruned
    hotBlocks = new ConcurrentHashMap<>(tx.blocks);
    hotStates = new ConcurrentHashMap<>(tx.block_states);
    stateRoots = new ConcurrentHashMap<>(tx.stateRoots);
  }

  public static SafeFuture<StoreTransactionUpdates> create(
      final Store baseStore, final Transaction tx) {
    return new StoreTransactionUpdatesFactory(baseStore, tx).build();
  }

  private SafeFuture<StoreTransactionUpdates> build() {
    // If a new checkpoint has been finalized, calculated what to finalize and what to prune
    final CheckpointAndBlock prevFinalizedCheckpoint = baseStore.getFinalizedCheckpointAndBlock();
    final Optional<CheckpointAndBlock> newFinalizedCheckpoint =
        Optional.of(tx.getFinalizedCheckpointAndBlock())
            .filter(c -> c.getEpoch().compareTo(prevFinalizedCheckpoint.getEpoch()) > 0);

    // Calculate new tree structure
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

    if (newFinalizedCheckpoint.isPresent()) {
      return buildFinalizedUpdates(newFinalizedCheckpoint.get());
    } else {
      return SafeFuture.completedFuture(createStoreTransactionUpdates(Optional.empty()));
    }
  }

  private SafeFuture<StoreTransactionUpdates> buildFinalizedUpdates(
      final CheckpointAndBlock finalizedCheckpoint) {
    final SignedBeaconBlock finalizedBlock = finalizedCheckpoint.getBlock();
    final Map<Bytes32, Bytes32> finalizedChildToParent =
        collectFinalizedRoots(baseStore, finalizedBlock, hotBlocks.values());
    Set<SignedBeaconBlock> finalizedBlocks = collectFinalizedBlocks(tx, finalizedChildToParent);
    Map<Bytes32, BeaconState> finalizedStates = collectFinalizedStates(tx, finalizedChildToParent);

    calculatePrunedHotBlockRoots(baseStore, tx, updatedBlockTree.orElseThrow());

    // Prune transaction collections
    prunedHotBlockRoots.forEach(hotBlocks::remove);
    prunedHotBlockRoots.forEach(hotStates::remove);

    return tx.retrieveBlockState(finalizedBlock.getRoot())
        .thenApply(
            maybeLatestFinalizedState -> {
              final BeaconState latestFinalizedState =
                  maybeLatestFinalizedState.orElseThrow(
                      () -> new IllegalStateException("Missing latest finalized state"));
              final Optional<FinalizedChainData> finalizedChainData =
                  Optional.of(
                      FinalizedChainData.builder()
                          .finalizedCheckpoint(finalizedCheckpoint.getCheckpoint())
                          .finalizedBlock(finalizedCheckpoint.getBlock())
                          .latestFinalizedState(latestFinalizedState)
                          .finalizedChildAndParent(finalizedChildToParent)
                          .finalizedBlocks(finalizedBlocks)
                          .finalizedStates(finalizedStates)
                          .build());

              return createStoreTransactionUpdates(finalizedChainData);
            });
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

  private Set<SignedBeaconBlock> collectFinalizedBlocks(
      final Store.Transaction tx, final Map<Bytes32, Bytes32> finalizedChildToParent) {
    return finalizedChildToParent.keySet().stream()
        .map(root -> tx.getBlockIfAvailable(root).orElse(null))
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());
  }

  private Map<Bytes32, BeaconState> collectFinalizedStates(
      final Store.Transaction tx, final Map<Bytes32, Bytes32> finalizedChildToParent) {
    final Map<Bytes32, BeaconState> states = new HashMap<>();
    for (Bytes32 finalizedRoot : finalizedChildToParent.keySet()) {
      tx.getBlockStateIfAvailable(finalizedRoot)
          .ifPresent(state -> states.put(finalizedRoot, state));
    }
    return states;
  }

  private void calculatePrunedHotBlockRoots(
      final Store baseStore, Transaction tx, final HashTree prunedTree) {

    baseStore.blockTree.getAllRoots().stream()
        .filter(root -> !prunedTree.contains(root))
        .forEach(prunedHotBlockRoots::add);

    tx.getBlockRoots().stream()
        .filter(root -> !prunedTree.contains(root))
        .forEach(prunedHotBlockRoots::add);
  }

  private StoreTransactionUpdates createStoreTransactionUpdates(
      final Optional<FinalizedChainData> finalizedChainData) {
    return new StoreTransactionUpdates(
        tx,
        finalizedChainData,
        hotBlocks,
        hotStates,
        prunedHotBlockRoots,
        updatedBlockTree,
        stateRoots);
  };
}
