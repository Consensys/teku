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

import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;

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
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.datastructures.hashtree.HashTree;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.storage.events.FinalizedChainData;
import tech.pegasys.teku.storage.store.Store.Transaction;

class StoreTransactionUpdatesFactory {
  private final Store baseStore;
  private final Store.Transaction tx;

  private final Map<Bytes32, SignedBeaconBlock> hotBlocks;
  private final Map<Bytes32, BeaconState> hotStates;
  private final Map<Bytes32, SlotAndBlockRoot> stateRoots;
  private final Set<Bytes32> prunedHotBlockRoots =
      Collections.newSetFromMap(new ConcurrentHashMap<>());

  private volatile Optional<BlockTree> updatedBlockTree = Optional.empty();

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
    final Checkpoint prevFinalizedCheckpoint = baseStore.getFinalizedCheckpoint();
    final Optional<Checkpoint> newFinalizedCheckpoint =
        Optional.of(tx.getFinalizedCheckpoint())
            .filter(c -> c.getEpoch().compareTo(prevFinalizedCheckpoint.getEpoch()) > 0);

    // Calculate new tree structure
    if (newFinalizedCheckpoint.isPresent() || hotBlocks.size() > 0) {
      final Bytes32 updatedRoot =
          newFinalizedCheckpoint.map(Checkpoint::getRoot).orElse(baseStore.blockTree.getRootHash());
      updatedBlockTree = Optional.of(baseStore.blockTree.updated(updatedRoot, hotBlocks.values()));
    }

    if (newFinalizedCheckpoint.isPresent()) {
      return buildFinalizedUpdates(newFinalizedCheckpoint.get());
    } else {
      return SafeFuture.completedFuture(createStoreTransactionUpdates(Optional.empty()));
    }
  }

  private SafeFuture<StoreTransactionUpdates> buildFinalizedUpdates(
      final Checkpoint finalizedCheckpoint) {

    return tx.retrieveBlockAndState(finalizedCheckpoint.getRoot())
        .thenApply(
            maybeLatestFinalized -> {
              final SignedBlockAndState latestFinalized =
                  maybeLatestFinalized.orElseThrow(
                      () -> new IllegalStateException("Missing latest finalized block and state"));

              final Map<Bytes32, Bytes32> finalizedChildToParent =
                  collectFinalizedRoots(baseStore, latestFinalized.getRoot(), hotBlocks.values());
              Set<SignedBeaconBlock> finalizedBlocks =
                  collectFinalizedBlocks(tx, finalizedChildToParent);
              Map<Bytes32, BeaconState> finalizedStates =
                  collectFinalizedStates(tx, finalizedChildToParent);

              // Prune collections
              calculatePrunedHotBlockRoots(tx, updatedBlockTree.orElseThrow());
              prunedHotBlockRoots.forEach(hotBlocks::remove);
              prunedHotBlockRoots.forEach(hotStates::remove);

              final Optional<FinalizedChainData> finalizedChainData =
                  Optional.of(
                      FinalizedChainData.builder()
                          .finalizedCheckpoint(finalizedCheckpoint)
                          .latestFinalizedBlockAndState(latestFinalized)
                          .finalizedChildAndParent(finalizedChildToParent)
                          .finalizedBlocks(finalizedBlocks)
                          .finalizedStates(finalizedStates)
                          .build());

              return createStoreTransactionUpdates(finalizedChainData);
            });
  }

  /**
   * Pull subset of hot states that sit at epoch boundaries to persist
   *
   * @return
   */
  private Map<Bytes32, BeaconState> getHotStatesToPersist() {
    final BlockTree blockTree = updatedBlockTree.orElse(baseStore.blockTree);
    return hotStates.entrySet().stream()
        .filter(e -> blockTree.isRootAtEpochBoundary(e.getKey()))
        .filter(
            e ->
                compute_epoch_at_slot(e.getValue().getSlot())
                    .mod(Store.HOT_STATE_CHECKPOINT_FREQUENCY_IN_EPOCHS)
                    .equals(UInt64.ZERO))
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  private static Map<Bytes32, Bytes32> collectFinalizedRoots(
      final Store baseStore,
      final Bytes32 newlyFinalizedBlockRoot,
      final Collection<SignedBeaconBlock> newBlocks) {

    final HashTree finalizedTree =
        baseStore.blockTree.contains(newlyFinalizedBlockRoot)
            ? baseStore.blockTree.getHashTree()
            : baseStore.blockTree.getHashTree().updater().blocks(newBlocks).build();

    final HashMap<Bytes32, Bytes32> childToParent = new HashMap<>();
    finalizedTree.processHashesInChain(newlyFinalizedBlockRoot, childToParent::put);
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

  private void calculatePrunedHotBlockRoots(Transaction tx, final BlockTree prunedTree) {
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
        getHotStatesToPersist(),
        prunedHotBlockRoots,
        updatedBlockTree,
        stateRoots);
  };
}
