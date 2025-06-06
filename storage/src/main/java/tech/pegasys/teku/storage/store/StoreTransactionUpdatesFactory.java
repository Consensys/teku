/*
 * Copyright Consensys Software Inc., 2025
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

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockSummary;
import tech.pegasys.teku.spec.datastructures.blocks.BlockAndCheckpoints;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.state.AnchorPoint;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.storage.api.FinalizedChainData;

class StoreTransactionUpdatesFactory {
  private static final Logger LOG = LogManager.getLogger();

  private final Spec spec;
  private final Store baseStore;
  private final StoreTransaction tx;

  private final Map<Bytes32, BlockAndCheckpoints> hotBlocks;
  private final Map<Bytes32, SignedBlockAndState> hotBlockAndStates;
  private final Map<SlotAndBlockRoot, List<BlobSidecar>> blobSidecars;
  private final Optional<UInt64> maybeEarliestBlobSidecarSlot;
  private final Optional<Bytes32> maybeLatestCanonicalBlockRoot;
  private final Map<Bytes32, SlotAndBlockRoot> stateRoots;
  private final AnchorPoint latestFinalized;
  private final Map<Bytes32, UInt64> prunedHotBlockRoots = new ConcurrentHashMap<>();

  public StoreTransactionUpdatesFactory(
      final Spec spec,
      final Store baseStore,
      final StoreTransaction tx,
      final AnchorPoint latestFinalized) {
    this.spec = spec;
    this.baseStore = baseStore;
    this.tx = tx;
    this.latestFinalized = latestFinalized;
    // Save copy of tx data that may be pruned
    hotBlocks =
        tx.blockData.entrySet().stream()
            .collect(
                Collectors.toMap(
                    Map.Entry::getKey, entry -> entry.getValue().toBlockAndCheckpoints()));
    hotBlockAndStates = new ConcurrentHashMap<>(tx.blockData);
    stateRoots = new ConcurrentHashMap<>(tx.stateRoots);
    blobSidecars = new ConcurrentHashMap<>(tx.blobSidecars);
    maybeEarliestBlobSidecarSlot = tx.maybeEarliestBlobSidecarTransactionSlot;
    maybeLatestCanonicalBlockRoot = tx.maybeLatestCanonicalBlockRoot;
  }

  public static StoreTransactionUpdates create(
      final Spec spec,
      final Store baseStore,
      final StoreTransaction tx,
      final AnchorPoint latestFinalized) {
    return new StoreTransactionUpdatesFactory(spec, baseStore, tx, latestFinalized).build();
  }

  public StoreTransactionUpdates build() {
    // If a new checkpoint has been finalized, calculated what to finalize and what to prune
    final Checkpoint prevFinalizedCheckpoint = baseStore.getFinalizedCheckpoint();
    final Optional<Checkpoint> newFinalizedCheckpoint =
        Optional.of(tx.getFinalizedCheckpoint())
            .filter(c -> c.getEpoch().compareTo(prevFinalizedCheckpoint.getEpoch()) > 0);

    return newFinalizedCheckpoint
        .map(this::buildFinalizedUpdates)
        .orElseGet(
            () ->
                createStoreTransactionUpdates(
                    Optional.empty(),
                    tx.clearFinalizedOptimisticTransitionPayload,
                    Optional.empty()));
  }

  private StoreTransactionUpdates buildFinalizedUpdates(final Checkpoint finalizedCheckpoint) {
    final Map<Bytes32, Bytes32> finalizedChildToParent =
        collectFinalizedRoots(latestFinalized.getRoot());
    final Set<SignedBeaconBlock> finalizedBlocks =
        collectFinalizedBlocks(tx, finalizedChildToParent);
    final Map<Bytes32, BeaconState> finalizedStates =
        collectFinalizedStates(tx, finalizedChildToParent);

    final FinalizedChainData.Builder finalizedChainDataBuilder = FinalizedChainData.builder();
    final boolean optimisticTransitionBlockRootSet;
    final Optional<Bytes32> optimisticTransitionBlockRoot;
    if (tx.clearFinalizedOptimisticTransitionPayload) {
      optimisticTransitionBlockRootSet = true;
      optimisticTransitionBlockRoot = Optional.empty();
    } else if (!spec.isMergeTransitionComplete(baseStore.getLatestFinalized().getState())
        && spec.isMergeTransitionComplete(latestFinalized.getState())) {
      // Transition block was finalized by this transaction
      optimisticTransitionBlockRootSet = true;
      optimisticTransitionBlockRoot =
          baseStore
              .getForkChoiceStrategy()
              .getOptimisticallySyncedTransitionBlockRoot(latestFinalized.getRoot());
    } else {
      optimisticTransitionBlockRootSet = false;
      optimisticTransitionBlockRoot = Optional.empty();
    }

    // Prune collections
    calculatePrunedHotBlockRoots();
    prunedHotBlockRoots.forEach(hotBlocks::remove);
    prunedHotBlockRoots.forEach(hotBlockAndStates::remove);

    final Optional<FinalizedChainData> finalizedChainData =
        Optional.of(
            finalizedChainDataBuilder
                .latestFinalized(latestFinalized)
                .finalizedChildAndParent(finalizedChildToParent)
                .finalizedBlocks(finalizedBlocks)
                .finalizedStates(finalizedStates)
                .build());

    return createStoreTransactionUpdates(
        finalizedChainData, optimisticTransitionBlockRootSet, optimisticTransitionBlockRoot);
  }

  /** Pull subset of hot states that sit at epoch boundaries to persist */
  private Map<Bytes32, BeaconState> getHotStatesToPersist() {
    final Map<Bytes32, BeaconState> statesToPersist =
        hotBlockAndStates.entrySet().stream()
            .filter(
                e ->
                    baseStore.shouldPersistState(
                        e.getValue().getSlot(), blockSlot(e.getValue().getParentRoot())))
            .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().getState()));
    if (statesToPersist.size() > 0) {
      LOG.trace("Persist {} hot states", statesToPersist.size());
    }
    return statesToPersist;
  }

  private Optional<UInt64> blockSlot(final Bytes32 root) {
    return Optional.ofNullable(hotBlockAndStates.get(root))
        .map(SignedBlockAndState::getSlot)
        .or(() -> baseStore.getForkChoiceStrategy().blockSlot(root));
  }

  private Map<Bytes32, Bytes32> collectFinalizedRoots(final Bytes32 newlyFinalizedBlockRoot) {
    final HashMap<Bytes32, Bytes32> childToParent = new HashMap<>();
    // Add any new blocks that were immediately finalized
    Bytes32 finalizedChainHeadRoot = newlyFinalizedBlockRoot;
    while (!baseStore.containsBlock(finalizedChainHeadRoot)) {
      // Blocks from the new transaction must all be in memory as they haven't been stored yet
      final SignedBeaconBlock block = hotBlocks.get(finalizedChainHeadRoot).getBlock();
      childToParent.put(finalizedChainHeadRoot, block.getParentRoot());
      finalizedChainHeadRoot = block.getParentRoot();
    }

    // Add existing hot blocks that are now finalized
    if (baseStore.getForkChoiceStrategy().contains(finalizedChainHeadRoot)) {
      baseStore
          .getForkChoiceStrategy()
          .processHashesInChain(
              finalizedChainHeadRoot,
              (blockRoot, slot, parentRoot) -> childToParent.put(blockRoot, parentRoot));
    }
    return childToParent;
  }

  private Set<SignedBeaconBlock> collectFinalizedBlocks(
      final StoreTransaction tx, final Map<Bytes32, Bytes32> finalizedChildToParent) {
    return finalizedChildToParent.keySet().stream()
        .flatMap(root -> tx.getBlockIfAvailable(root).stream())
        .collect(Collectors.toSet());
  }

  private Map<Bytes32, BeaconState> collectFinalizedStates(
      final StoreTransaction tx, final Map<Bytes32, Bytes32> finalizedChildToParent) {
    final Map<Bytes32, BeaconState> states = new HashMap<>();
    for (Bytes32 finalizedRoot : finalizedChildToParent.keySet()) {
      tx.getBlockStateIfAvailable(finalizedRoot)
          .ifPresent(state -> states.put(finalizedRoot, state));
    }
    return states;
  }

  private boolean shouldPrune(
      final BeaconBlockSummary finalizedBlock,
      final Bytes32 blockRoot,
      final UInt64 slot,
      final Bytes32 parentRoot) {
    return (slot.isLessThanOrEqualTo(finalizedBlock.getSlot())
            || prunedHotBlockRoots.containsKey(parentRoot))
        // Keep the actual finalized block
        && !blockRoot.equals(finalizedBlock.getRoot());
  }

  private void calculatePrunedHotBlockRoots() {
    final BeaconBlockSummary finalizedBlock = tx.getLatestFinalized().getBlockSummary();
    baseStore
        .getForkChoiceStrategy()
        .processAllInOrder(
            (blockRoot, slot, parentRoot) -> {
              if (shouldPrune(finalizedBlock, blockRoot, slot, parentRoot)) {
                prunedHotBlockRoots.put(blockRoot, slot);
              }
            });

    tx.blockData.values().stream()
        // Iterate new blocks in slot order to guarantee we see parents first
        .sorted(Comparator.comparing(SignedBlockAndState::getSlot))
        .filter(
            newBlockAndState ->
                shouldPrune(
                    finalizedBlock,
                    newBlockAndState.getRoot(),
                    newBlockAndState.getSlot(),
                    newBlockAndState.getParentRoot()))
        .forEach(
            newBlockAndState ->
                prunedHotBlockRoots.put(newBlockAndState.getRoot(), newBlockAndState.getSlot()));
  }

  private StoreTransactionUpdates createStoreTransactionUpdates(
      final Optional<FinalizedChainData> finalizedChainData,
      final boolean optimisticTransitionBlockRootSet,
      final Optional<Bytes32> optimisticTransitionBlockRoot) {
    return new StoreTransactionUpdates(
        tx,
        finalizedChainData,
        hotBlocks,
        hotBlockAndStates,
        getHotStatesToPersist(),
        blobSidecars,
        maybeEarliestBlobSidecarSlot,
        prunedHotBlockRoots,
        stateRoots,
        optimisticTransitionBlockRootSet,
        optimisticTransitionBlockRoot,
        maybeLatestCanonicalBlockRoot,
        spec.isMilestoneSupported(SpecMilestone.DENEB),
        spec.isMilestoneSupported(SpecMilestone.FULU));
  }
}
