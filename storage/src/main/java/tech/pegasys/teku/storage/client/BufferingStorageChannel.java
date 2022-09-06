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

package tech.pegasys.teku.storage.client;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockSummary;
import tech.pegasys.teku.spec.datastructures.blocks.BlockAndCheckpoints;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.blocks.StateAndBlockSummary;
import tech.pegasys.teku.spec.datastructures.state.AnchorPoint;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.storage.api.OnDiskStoreData;
import tech.pegasys.teku.storage.api.StorageQueryChannel;
import tech.pegasys.teku.storage.api.StorageUpdate;
import tech.pegasys.teku.storage.api.StorageUpdateChannel;
import tech.pegasys.teku.storage.api.UpdateResult;
import tech.pegasys.teku.storage.api.WeakSubjectivityState;
import tech.pegasys.teku.storage.api.WeakSubjectivityUpdate;

/**
 * Buffers updates sent to the storage service so that queries that are performed after the update
 * call is completed but before the update is actually processed by the backing storage are still
 * included in queries.
 *
 * <p>The threading model requires that any content to be buffered is added to the storage in this
 * class before the update method returns, and not removed until the database update has been
 * applied.
 */
public class BufferingStorageChannel implements StorageQueryChannel, StorageUpdateChannel {
  private final AtomicInteger inflightUpdateCount = new AtomicInteger();
  private final StorageUpdateChannel updateChannel;
  private final StorageQueryChannel queryChannel;

  private final ConcurrentMap<Bytes32, BlockAndCheckpoints> hotBlocks = new ConcurrentHashMap<>();
  private final ConcurrentMap<Bytes32, BeaconState> hotStates = new ConcurrentHashMap<>();
  private final ConcurrentMap<Bytes32, SlotAndBlockRoot> stateRoots = new ConcurrentHashMap<>();

  private final ConcurrentMap<Bytes32, SignedBeaconBlock> finalizedBlocksByRoot =
      new ConcurrentHashMap<>();
  private final ConcurrentNavigableMap<UInt64, SignedBeaconBlock> finalizedBlocksBySlot =
      new ConcurrentSkipListMap<>();
  private final ConcurrentMap<Bytes32, BeaconState> finalizedStatesByBlockRoot =
      new ConcurrentHashMap<>();
  private final ConcurrentNavigableMap<UInt64, BeaconState> finalizedStatesBySlot =
      new ConcurrentSkipListMap<>();

  public BufferingStorageChannel(
      final MetricsSystem metricsSystem,
      final StorageUpdateChannel updateChannel,
      final StorageQueryChannel queryChannel) {
    this.updateChannel = updateChannel;
    this.queryChannel = queryChannel;
    metricsSystem.createIntegerGauge(
        TekuMetricCategory.STORAGE,
        "in_flight_storage_updates_current",
        "Total number of storage updates currently queued for asynchronous processing",
        inflightUpdateCount::get);
  }

  @VisibleForTesting
  int getBufferedItemCount() {
    return hotBlocks.size()
        + hotStates.size()
        + stateRoots.size()
        + finalizedBlocksByRoot.size()
        + finalizedBlocksBySlot.size()
        + finalizedStatesBySlot.size()
        + finalizedStatesByBlockRoot.size();
  }

  @Override
  public SafeFuture<UpdateResult> onStorageUpdate(final StorageUpdate event) {
    // TODO: Need to handle shutdown properly. The actual updateChannel will ignore new events
    // This class probably should stop trying to buffer them.
    inflightUpdateCount.incrementAndGet();
    hotBlocks.putAll(event.getHotBlocks());
    hotStates.putAll(event.getHotStates());
    stateRoots.putAll(event.getStateRoots());

    // TODO: Should we track deleted hot blocks and make sure they aren't available?
    event
        .getDeletedHotBlocks()
        .forEach(
            key -> {
              hotBlocks.remove(key);
              hotStates.remove(key);
            });
    event
        .getFinalizedBlocks()
        .forEach(
            (root, block) -> {
              finalizedBlocksByRoot.put(root, block);
              finalizedBlocksBySlot.put(block.getSlot(), block);
            });
    event
        .getFinalizedStates()
        .forEach(
            (blockRoot, state) -> {
              finalizedStatesByBlockRoot.put(blockRoot, state);
              finalizedStatesBySlot.put(state.getSlot(), state);
            });
    return updateChannel
        .onStorageUpdate(event)
        .thenPeek(__ -> removeBufferedData(event))
        .catchAndRethrow(__ -> removeBufferedData(event));
  }

  private void removeBufferedData(final StorageUpdate event) {
    event.getHotBlocks().forEach(hotBlocks::remove);
    event.getHotStates().forEach(hotStates::remove);
    event.getStateRoots().forEach(stateRoots::remove);
    event
        .getFinalizedBlocks()
        .forEach(
            (root, block) -> {
              finalizedBlocksByRoot.remove(root, block);
              finalizedBlocksBySlot.remove(block.getSlot(), block);
            });
    event
        .getFinalizedStates()
        .forEach(
            (blockRoot, state) -> {
              finalizedStatesByBlockRoot.remove(blockRoot, state);
              finalizedStatesBySlot.remove(state.getSlot(), state);
            });
    inflightUpdateCount.decrementAndGet();
  }

  @Override
  public SafeFuture<Void> onFinalizedBlocks(final Collection<SignedBeaconBlock> finalizedBlocks) {
    // This is used for block backfill so no need to buffer.
    // getEarliestFinalizedBlock will only report what's in the database so it's always consistent
    return updateChannel.onFinalizedBlocks(finalizedBlocks);
  }

  @Override
  public SafeFuture<Void> onFinalizedState(
      final BeaconState finalizedState, final Bytes32 blockRoot) {
    // This is used for historic regeneration so no need to buffer.
    // We may just have to apply more blocks to regenerate than we might have had to
    return updateChannel.onFinalizedState(finalizedState, blockRoot);
  }

  @Override
  public SafeFuture<Void> onWeakSubjectivityUpdate(
      final WeakSubjectivityUpdate weakSubjectivityUpdate) {
    return updateChannel.onWeakSubjectivityUpdate(weakSubjectivityUpdate);
  }

  @Override
  public void onChainInitialized(final AnchorPoint initialAnchor) {
    updateChannel.onChainInitialized(initialAnchor);
  }

  // Query Functions

  @Override
  public SafeFuture<Optional<OnDiskStoreData>> onStoreRequest() {
    // Only called at startup, no buffering required
    return queryChannel.onStoreRequest();
  }

  @Override
  public SafeFuture<WeakSubjectivityState> getWeakSubjectivityState() {
    // Only called at startup, no buffering required
    return queryChannel.getWeakSubjectivityState();
  }

  @Override
  public SafeFuture<Optional<UInt64>> getEarliestAvailableBlockSlot() {
    // Allow earlier blocks to only become available when the tx completes, no need to buffer
    return queryChannel.getEarliestAvailableBlockSlot();
  }

  @Override
  public SafeFuture<Optional<SignedBeaconBlock>> getEarliestAvailableBlock() {
    // Allow earlier blocks to only become available when the tx completes, no need to buffer
    return queryChannel.getEarliestAvailableBlock();
  }

  @Override
  public SafeFuture<Optional<SignedBeaconBlock>> getFinalizedBlockAtSlot(final UInt64 slot) {
    final SignedBeaconBlock bufferedBlock = finalizedBlocksBySlot.get(slot);
    if (bufferedBlock != null) {
      return SafeFuture.completedFuture(Optional.of(bufferedBlock));
    }
    return queryChannel.getFinalizedBlockAtSlot(slot);
  }

  @Override
  public SafeFuture<Optional<SignedBeaconBlock>> getLatestFinalizedBlockAtSlot(final UInt64 slot) {
    final Entry<UInt64, SignedBeaconBlock> bestBufferedEntry =
        finalizedBlocksBySlot.floorEntry(slot);
    // Blocks are finalized in order so if the slot we're requesting is after a finalized block we
    // have, then it must be the latest one. We only need to go to the DB if the slot is before
    // the finalized blocks we have buffered.
    if (bestBufferedEntry != null) {
      return SafeFuture.completedFuture(Optional.of(bestBufferedEntry.getValue()));
    }
    return queryChannel.getLatestFinalizedBlockAtSlot(slot);
  }

  @Override
  public SafeFuture<Optional<SignedBeaconBlock>> getBlockByBlockRoot(final Bytes32 blockRoot) {
    final BlockAndCheckpoints bufferedHot = hotBlocks.get(blockRoot);
    if (bufferedHot != null) {
      return SafeFuture.completedFuture(Optional.of(bufferedHot.getBlock()));
    }
    final SignedBeaconBlock bufferedFinalized = finalizedBlocksByRoot.get(blockRoot);
    if (bufferedFinalized != null) {
      return SafeFuture.completedFuture(Optional.of(bufferedFinalized));
    }
    return queryChannel.getBlockByBlockRoot(blockRoot);
  }

  @Override
  public SafeFuture<Optional<SignedBlockAndState>> getHotBlockAndStateByBlockRoot(
      final Bytes32 blockRoot) {
    final BeaconState bufferedState = hotStates.get(blockRoot);
    if (bufferedState != null) {
      return getBlockByBlockRoot(blockRoot)
          .thenApply(
              maybeBlock -> maybeBlock.map(block -> new SignedBlockAndState(block, bufferedState)));
    }
    return queryChannel.getHotBlockAndStateByBlockRoot(blockRoot);
  }

  @Override
  public SafeFuture<Optional<StateAndBlockSummary>> getHotStateAndBlockSummaryByBlockRoot(
      final Bytes32 blockRoot) {
    final BeaconState bufferedState = hotStates.get(blockRoot);
    if (bufferedState != null) {
      return getBlockByBlockRoot(blockRoot)
          .thenApply(
              maybeBlock -> {
                final BeaconBlockSummary header =
                    maybeBlock
                        .map(b -> (BeaconBlockSummary) b)
                        .orElseGet(() -> BeaconBlockHeader.fromState(bufferedState));
                return Optional.of(StateAndBlockSummary.create(header, bufferedState));
              });
    }
    return queryChannel.getHotStateAndBlockSummaryByBlockRoot(blockRoot);
  }

  @Override
  public SafeFuture<Map<Bytes32, SignedBeaconBlock>> getHotBlocksByRoot(
      final Set<Bytes32> blockRoots) {
    final Map<Bytes32, SignedBeaconBlock> bufferedBlocks = new HashMap<>();
    for (Bytes32 blockRoot : blockRoots) {
      final BlockAndCheckpoints blockAndCheckpoints = hotBlocks.get(blockRoot);
      if (blockAndCheckpoints != null) {
        bufferedBlocks.put(blockRoot, blockAndCheckpoints.getBlock());
      }
    }
    if (bufferedBlocks.isEmpty()) {
      // Didn't find any blocks, request them all from the database
      return queryChannel.getHotBlocksByRoot(blockRoots);
    } else if (bufferedBlocks.size() == blockRoots.size()) {
      // Found all the blocks, just return them
      return SafeFuture.completedFuture(bufferedBlocks);
    } else {
      // Need to request the remaining blocks from the database
      return queryChannel
          .getHotBlocksByRoot(Sets.symmetricDifference(blockRoots, bufferedBlocks.keySet()))
          .thenApply(
              loadedBlocks -> {
                bufferedBlocks.putAll(loadedBlocks);
                return bufferedBlocks;
              });
    }
  }

  @Override
  public SafeFuture<Optional<SlotAndBlockRoot>> getSlotAndBlockRootByStateRoot(
      final Bytes32 stateRoot) {
    final SlotAndBlockRoot bufferedResult = stateRoots.get(stateRoot);
    if (bufferedResult != null) {
      return SafeFuture.completedFuture(Optional.of(bufferedResult));
    }
    return queryChannel.getSlotAndBlockRootByStateRoot(stateRoot);
  }

  @Override
  public SafeFuture<Optional<BeaconState>> getLatestFinalizedStateAtSlot(final UInt64 slot) {
    final Entry<UInt64, BeaconState> bufferedState = finalizedStatesBySlot.floorEntry(slot);
    // Blocks are finalized in order so if the slot we're requesting is after a finalized state we
    // have, then it must be the latest one. We only need to go to the DB if the slot is before
    // the finalized states we have buffered.
    if (bufferedState != null) {
      return SafeFuture.completedFuture(Optional.of(bufferedState.getValue()));
    }
    return queryChannel.getLatestFinalizedStateAtSlot(slot);
  }

  @Override
  public SafeFuture<Optional<BeaconState>> getFinalizedStateByBlockRoot(final Bytes32 blockRoot) {
    final BeaconState bufferedState = finalizedStatesByBlockRoot.get(blockRoot);
    if (bufferedState != null) {
      return SafeFuture.completedFuture(Optional.of(bufferedState));
    }
    return queryChannel.getFinalizedStateByBlockRoot(blockRoot);
  }

  @Override
  public SafeFuture<Optional<UInt64>> getFinalizedSlotByStateRoot(final Bytes32 stateRoot) {
    final SlotAndBlockRoot bufferedResult = stateRoots.get(stateRoot);
    if (bufferedResult != null) {
      return SafeFuture.completedFuture(Optional.of(bufferedResult.getSlot()));
    }
    return queryChannel.getFinalizedSlotByStateRoot(stateRoot);
  }

  @Override
  public SafeFuture<List<SignedBeaconBlock>> getNonCanonicalBlocksBySlot(final UInt64 slot) {
    // TODO: Needs buffering (and possibly rename to include finalized)
    return queryChannel.getNonCanonicalBlocksBySlot(slot);
  }

  @Override
  public SafeFuture<Optional<Checkpoint>> getAnchor() {
    // Anchor updates have always been async so no need to buffer
    return queryChannel.getAnchor();
  }
}
