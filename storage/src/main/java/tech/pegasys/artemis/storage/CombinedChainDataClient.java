/*
 * Copyright 2019 ConsenSys AG.
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

package tech.pegasys.artemis.storage;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.primitives.UnsignedLong.ONE;
import static com.google.common.primitives.UnsignedLong.ZERO;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_block_root_at_slot;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_committee_count_at_slot;
import static tech.pegasys.artemis.util.async.SafeFuture.completedFuture;
import static tech.pegasys.artemis.util.config.Constants.SLOTS_PER_EPOCH;
import static tech.pegasys.artemis.util.config.Constants.SLOTS_PER_HISTORICAL_ROOT;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.CommitteeAssignment;
import tech.pegasys.artemis.datastructures.util.BeaconStateUtil;
import tech.pegasys.artemis.datastructures.util.CommitteeUtil;
import tech.pegasys.artemis.util.async.SafeFuture;

public class CombinedChainDataClient {
  private static final Logger LOG = LogManager.getLogger();

  private static final SafeFuture<Optional<SignedBeaconBlock>> BLOCK_NOT_AVAILABLE =
      completedFuture(Optional.empty());
  private static final SafeFuture<Optional<BeaconState>> STATE_NOT_AVAILABLE =
      completedFuture(Optional.empty());
  private final ChainStorageClient recentChainData;
  private final HistoricalChainData historicalChainData;

  public CombinedChainDataClient(
      final ChainStorageClient recentChainData, final HistoricalChainData historicalChainData) {
    this.recentChainData = recentChainData;
    this.historicalChainData = historicalChainData;
  }

  /**
   * Returns the block proposed for the requested slot on the chain identified by <code>
   * headBlockRoot</code>. If the slot was empty, no block is returned.
   *
   * @param slot the slot to get the block for
   * @param headBlockRoot the block root of the head of the chain
   * @return the block at the requested slot or empty if the slot was empty
   */
  public SafeFuture<Optional<SignedBeaconBlock>> getBlockAtSlotExact(
      final UnsignedLong slot, final Bytes32 headBlockRoot) {
    return getBlockInEffectAtSlot(slot, headBlockRoot)
        .thenApply(maybeBlock -> maybeBlock.filter(block -> block.getSlot().equals(slot)));
  }

  /**
   * Returns the block which was proposed in or most recently before the requested slot on the chain
   * specified by <code>headBlockRoot</code>. If the slot was empty, the block at the last filled
   * slot is returned.
   *
   * @param slot the slot to get the effective block for
   * @param headBlockRoot the block root of the head of the chain
   * @return the block at slot or the closest previous slot if empty
   */
  public SafeFuture<Optional<SignedBeaconBlock>> getBlockInEffectAtSlot(
      final UnsignedLong slot, final Bytes32 headBlockRoot) {
    final Store store = getStore();
    if (store == null) {
      LOG.trace("No block at slot {} because the store is not set", slot);
      return BLOCK_NOT_AVAILABLE;
    }

    final BeaconState headState = store.getBlockState(headBlockRoot);
    if (headState == null) {
      LOG.trace("No block at slot {} because head block root {} is unknown", slot, headBlockRoot);
      return BLOCK_NOT_AVAILABLE;
    }
    if (headState.getSlot().compareTo(slot) < 0) {
      LOG.trace(
          "No block at slot {} because it is after the referenced head state slot {}",
          slot,
          headState.getSlot());
      return BLOCK_NOT_AVAILABLE;
    }
    if (headState.getSlot().equals(slot)) {
      LOG.trace("Block root at slot {} is the specified head block root", slot);
      return getBlockByBlockRoot(headBlockRoot);
    }
    if (isFinalized(slot)) {
      LOG.trace("Block at slot {} is in a finalized epoch. Retrieving from historical data", slot);
      return historicalChainData.getLatestFinalizedBlockAtSlot(slot);
    }

    return getBlockAtSlotFormHistoricalBlockRoots(slot, store, headState);
  }

  private SafeFuture<Optional<SignedBeaconBlock>> getBlockAtSlotFormHistoricalBlockRoots(
      final UnsignedLong slot, final Store store, final BeaconState headState) {
    final UnsignedLong slotsPerHistoricalRoot = UnsignedLong.valueOf(SLOTS_PER_HISTORICAL_ROOT);
    BeaconState state = headState;
    while (state != null && !BeaconStateUtil.isBlockRootAvailableFromState(state, slot)) {
      checkState(
          state.getSlot().compareTo(slotsPerHistoricalRoot) >= 0,
          "Can't get earlier state because the historical roots already extends to genesis");
      final UnsignedLong earliestAvailableSlot = state.getSlot().minus(slotsPerHistoricalRoot);
      LOG.trace(
          "Slot {} is before the current historical root. Retrieving state from slot {}",
          slot,
          earliestAvailableSlot);
      state = store.getBlockState(get_block_root_at_slot(state, earliestAvailableSlot));
    }
    return getBlockByBlockRoot(get_block_root_at_slot(state, slot));
  }

  public boolean isFinalized(final UnsignedLong slot) {
    final UnsignedLong finalizedEpoch = recentChainData.getFinalizedEpoch();
    final UnsignedLong finalizedSlot = compute_start_slot_at_epoch(finalizedEpoch);
    return finalizedSlot.compareTo(slot) >= 0;
  }

  public Optional<BeaconState> getNonfinalizedBlockState(final Bytes32 blockRoot) {
    return recentChainData.getBlockState(blockRoot);
  }

  /**
   * Returns the state at the given slot on the current canonical chain.
   *
   * @param slot the slot to get the state for
   * @return the State at slot
   */
  public SafeFuture<Optional<BeaconState>> getStateAtSlot(final UnsignedLong slot) {
    final Optional<Bytes32> headRoot = getBestBlockRoot();
    return headRoot.map(root -> getStateAtSlot(slot, root)).orElse(STATE_NOT_AVAILABLE);
  }

  /**
   * Returns the state on the chain specified by <code>headBlockRoot</code>.
   *
   * @param slot the slot to get the state for
   * @return the State at slot
   */
  public SafeFuture<Optional<BeaconState>> getStateAtSlot(
      final UnsignedLong slot, final Bytes32 headBlockRoot) {
    checkNotNull(headBlockRoot);
    final Store store = getStore();
    if (store == null) {
      LOG.trace("No state at slot {} because the store is not set", slot);
      return STATE_NOT_AVAILABLE;
    }

    if (isFinalized(slot)) {
      return historicalChainData.getFinalizedStateAtSlot(slot);
    }

    final BeaconState headState = store.getBlockState(headBlockRoot);
    if (headState.getSlot().equals(slot)) {
      return completedFuture(Optional.ofNullable(headState));
    }

    return completedFuture(recentChainData.getStateBySlot(slot));
  }

  public SafeFuture<Optional<BeaconState>> getStateByBlockRoot(final Bytes32 blockRoot) {
    final Store store = getStore();
    if (store == null) {
      LOG.trace("No state at blockRoot {} because the store is not set", blockRoot);
      return STATE_NOT_AVAILABLE;
    }
    final BeaconState state = store.getBlockState(blockRoot);
    if (state != null) {
      return completedFuture(Optional.of(state));
    }

    return historicalChainData.getFinalizedStateByBlockRoot(blockRoot);
  }

  public Optional<BeaconState> getHeadStateFromStore() {
    final Store store = getStore();
    if (store == null) {
      LOG.trace("No state at head because the store is not set");
      return Optional.empty();
    }
    return getBestBlockRoot().map(store::getBlockState);
  }

  public Optional<Bytes32> getBestBlockRoot() {
    return recentChainData.getBestBlockRoot();
  }

  public boolean isStoreAvailable() {
    return recentChainData != null && getStore() != null;
  }

  /**
   * Gets a list of CommitteeAssignments for a specified epoch.
   *
   * <p>These committee assignments are calculated at the epoch prior to the specified epoch, unless
   * specifying epoch 0, in which case the committee assignments are correct.
   *
   * @param epoch - the current or historic epoch
   * @return list of CommitteeAssignments
   */
  public SafeFuture<List<CommitteeAssignment>> getCommitteeAssignmentAtEpoch(UnsignedLong epoch) {
    final UnsignedLong committeesCalculatedAtEpoch = epoch.equals(ZERO) ? ZERO : epoch.minus(ONE);
    final UnsignedLong startingSlot = compute_start_slot_at_epoch(committeesCalculatedAtEpoch);

    SafeFuture<Optional<BeaconState>> future = getStateAtSlot(startingSlot);

    return future
        .thenApply(
            optionalState -> getCommitteesFromState(optionalState.orElseThrow(), startingSlot))
        .exceptionally(err -> List.of());
  }

  public List<CommitteeAssignment> getCommitteesFromState(
      BeaconState state, UnsignedLong startingSlot) {
    List<CommitteeAssignment> result = new ArrayList<>();
    for (int i = 0; i < SLOTS_PER_EPOCH; i++) {
      UnsignedLong slot = startingSlot.plus(UnsignedLong.valueOf(i));
      int committeeCount = get_committee_count_at_slot(state, slot).intValue();
      for (int j = 0; j < committeeCount; j++) {
        UnsignedLong idx = UnsignedLong.valueOf(j);
        List<Integer> committee = CommitteeUtil.get_beacon_committee(state, slot, idx);
        result.add(new CommitteeAssignment(committee, idx, slot));
      }
    }
    return result;
  }

  public UnsignedLong getBestSlot() {
    return this.recentChainData.getBestSlot();
  }

  @VisibleForTesting
  public Store getStore() {
    return recentChainData.getStore();
  }

  public Optional<Bytes32> getBlockRootBySlot(final UnsignedLong slot) {
    return recentChainData.getBlockRootBySlot(slot);
  }

  public SafeFuture<Optional<SignedBeaconBlock>> getBlockBySlot(final UnsignedLong slot) {
    final Optional<Bytes32> blockRootBySlot = getBlockRootBySlot(slot);
    final Optional<Bytes32> bestBlockRoot = getBestBlockRoot();

    if (blockRootBySlot.isPresent()) {
      return getBlockByBlockRoot(blockRootBySlot.get());
    } else if (bestBlockRoot.isPresent()) {
      return getBlockAtSlotExact(slot, bestBlockRoot.get());
    } else {
      return SafeFuture.completedFuture(Optional.empty());
    }
  }

  @VisibleForTesting
  public Optional<SignedBeaconBlock> getBlockFromStore(final Bytes32 blockRoot) {
    return isStoreAvailable()
        ? Optional.ofNullable(recentChainData.getStore().getSignedBlock(blockRoot))
        : Optional.empty();
  }

  public SafeFuture<Optional<SignedBeaconBlock>> getBlockByBlockRoot(final Bytes32 blockRoot) {
    return getBlockFromStore(blockRoot)
        .map(value -> SafeFuture.completedFuture(Optional.of(value)))
        .orElseGet(() -> historicalChainData.getBlockByBlockRoot(blockRoot));
  }
}
