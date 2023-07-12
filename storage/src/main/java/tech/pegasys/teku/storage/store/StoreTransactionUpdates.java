/*
 * Copyright ConsenSys Software Inc., 2022
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.BlockAndCheckpoints;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.blocks.StateAndBlockSummary;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.storage.api.FinalizedChainData;
import tech.pegasys.teku.storage.api.StorageUpdate;
import tech.pegasys.teku.storage.api.UpdateResult;

class StoreTransactionUpdates {
  private final StoreTransaction tx;

  private final Optional<FinalizedChainData> finalizedChainData;
  private final Map<Bytes32, BlockAndCheckpoints> hotBlocks;
  private final Map<Bytes32, SignedBlockAndState> hotBlockAndStates;
  // A subset of hot states to be persisted to disk
  private final Map<Bytes32, BeaconState> hotStatesToPersist;
  private final Map<SlotAndBlockRoot, List<BlobSidecar>> blobSidecars;
  private final Optional<UInt64> maybeEarliestBlobSidecarSlot;
  private final Map<Bytes32, SlotAndBlockRoot> stateRoots;
  private final Map<Bytes32, UInt64> prunedHotBlockRoots;
  private final boolean optimisticTransitionBlockRootSet;
  private final Optional<Bytes32> optimisticTransitionBlockRoot;
  private final boolean blobSidecarsEnabled;

  StoreTransactionUpdates(
      final StoreTransaction tx,
      final Optional<FinalizedChainData> finalizedChainData,
      final Map<Bytes32, BlockAndCheckpoints> hotBlocks,
      final Map<Bytes32, SignedBlockAndState> hotBlockAndStates,
      final Map<Bytes32, BeaconState> hotStatesToPersist,
      final Map<SlotAndBlockRoot, List<BlobSidecar>> blobSidecars,
      final Optional<UInt64> maybeEarliestBlobSidecarSlot,
      final Map<Bytes32, UInt64> prunedHotBlockRoots,
      final Map<Bytes32, SlotAndBlockRoot> stateRoots,
      final boolean optimisticTransitionBlockRootSet,
      final Optional<Bytes32> optimisticTransitionBlockRoot,
      final boolean blobSidecarsEnabled) {
    checkNotNull(tx, "Transaction is required");
    checkNotNull(finalizedChainData, "Finalized data is required");
    checkNotNull(hotBlocks, "Hot blocks are required");
    checkNotNull(hotBlockAndStates, "Hot states are required");
    checkNotNull(hotStatesToPersist, "Hot states to persist are required");
    checkNotNull(blobSidecars, "BlobSidecars are required");
    checkNotNull(maybeEarliestBlobSidecarSlot, "Hot maybe earliest blobSidecar slot is required");
    checkNotNull(prunedHotBlockRoots, "Pruned roots are required");
    checkNotNull(stateRoots, "State roots are required");

    this.tx = tx;
    this.finalizedChainData = finalizedChainData;
    this.hotBlocks = hotBlocks;
    this.hotBlockAndStates = hotBlockAndStates;
    this.hotStatesToPersist = hotStatesToPersist;
    this.blobSidecars = blobSidecars;
    this.maybeEarliestBlobSidecarSlot = maybeEarliestBlobSidecarSlot;
    this.prunedHotBlockRoots = prunedHotBlockRoots;
    this.stateRoots = stateRoots;
    this.optimisticTransitionBlockRootSet = optimisticTransitionBlockRootSet;
    this.optimisticTransitionBlockRoot = optimisticTransitionBlockRoot;
    this.blobSidecarsEnabled = blobSidecarsEnabled;
  }

  public StorageUpdate createStorageUpdate() {
    return new StorageUpdate(
        tx.genesisTime,
        finalizedChainData,
        tx.justifiedCheckpoint,
        tx.bestJustifiedCheckpoint,
        hotBlocks,
        hotStatesToPersist,
        blobSidecars,
        maybeEarliestBlobSidecarSlot,
        prunedHotBlockRoots,
        stateRoots,
        optimisticTransitionBlockRootSet,
        optimisticTransitionBlockRoot,
        blobSidecarsEnabled);
  }

  public void applyToStore(final Store store, final UpdateResult updateResult) {
    // Add new data
    tx.timeMillis
        .filter(t -> t.isGreaterThan(store.getTimeMillis()))
        .ifPresent(value -> store.timeMillis = value);
    tx.genesisTime.ifPresent(value -> store.genesisTime = value);
    tx.justifiedCheckpoint.ifPresent(value -> store.justifiedCheckpoint = value);
    tx.bestJustifiedCheckpoint.ifPresent(value -> store.bestJustifiedCheckpoint = value);
    hotBlocks.forEach((root, value) -> store.blocks.put(root, value.getBlock()));
    store.states.cacheAll(Maps.transformValues(hotBlockAndStates, this::blockAndStateAsSummary));
    if (optimisticTransitionBlockRootSet) {
      store.finalizedOptimisticTransitionPayload =
          updateResult.getFinalizedOptimisticTransitionPayload();
    }

    // Update finalized data
    finalizedChainData.ifPresent(
        finalizedData -> store.finalizedAnchor = finalizedData.getLatestFinalized());

    // Prune blocks and states
    prunedHotBlockRoots
        .keySet()
        .forEach(
            (root) -> {
              store.blocks.remove(root);
              store.states.remove(root);
            });

    store.checkpointStates.removeIf(
        slotAndBlockRoot -> prunedHotBlockRoots.containsKey(slotAndBlockRoot.getBlockRoot()));

    if (tx.proposerBoostRootSet) {
      store.proposerBoostRoot = tx.proposerBoostRoot;
    }

    store.forkChoiceStrategy.applyUpdate(
        hotBlocks.values(),
        tx.pulledUpBlockCheckpoints,
        prunedHotBlockRoots,
        store.getFinalizedCheckpoint());
  }

  private StateAndBlockSummary blockAndStateAsSummary(final SignedBlockAndState blockAndState) {
    return blockAndState;
  }
}
