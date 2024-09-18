/*
 * Copyright Consensys Software Inc., 2022
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

package tech.pegasys.teku.storage.server;

import com.google.errorprone.annotations.MustBeClosed;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ethereum.pow.api.DepositTreeSnapshot;
import tech.pegasys.teku.ethereum.pow.api.DepositsFromBlockEvent;
import tech.pegasys.teku.ethereum.pow.api.MinGenesisTimeBlockEvent;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.BlockCheckpoints;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.spec.datastructures.state.AnchorPoint;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.spec.datastructures.util.SlotAndBlockRootAndBlobIndex;
import tech.pegasys.teku.storage.api.OnDiskStoreData;
import tech.pegasys.teku.storage.api.StorageUpdate;
import tech.pegasys.teku.storage.api.UpdateResult;
import tech.pegasys.teku.storage.api.WeakSubjectivityState;
import tech.pegasys.teku.storage.api.WeakSubjectivityUpdate;

public interface Database extends AutoCloseable {

  void storeInitialAnchor(AnchorPoint initialAnchor);

  UpdateResult update(StorageUpdate event);

  void storeFinalizedBlocks(
      Collection<SignedBeaconBlock> blocks,
      Map<SlotAndBlockRoot, List<BlobSidecar>> blobSidecarsBySlot,
      Optional<UInt64> maybeEarliestBlobSidecarSlot);

  void storeReconstructedFinalizedState(BeaconState state, Bytes32 blockRoot);

  void updateWeakSubjectivityState(WeakSubjectivityUpdate weakSubjectivityUpdate);

  void storeBlobSidecar(BlobSidecar blobSidecar);

  void storeNonCanonicalBlobSidecar(BlobSidecar blobSidecar);

  Optional<BlobSidecar> getBlobSidecar(SlotAndBlockRootAndBlobIndex key);

  Optional<BlobSidecar> getNonCanonicalBlobSidecar(SlotAndBlockRootAndBlobIndex key);

  /**
   * This prune method will delete BlobSidecars starting from the oldest BlobSidecars (by slot) up
   * to BlobSidecars at {@code lastSlotToPrune} (inclusive). The pruning process will be stopped if
   * {@code pruneLimit} reached completing the current slot. So, if pruneLimit happened at index#0
   * BlobSidecar and there are 2 BlobSidecars in a slot, index#1 will be removed too. Main purpose
   * of pruneLimit is to softly cap DB operation time.
   *
   * @param lastSlotToPrune inclusive, not reached if limit happens first
   * @param pruneLimit soft BlobSidecars (not slots) limit
   * @return true if number of pruned blobs reached the pruneLimit, false otherwise
   */
  boolean pruneOldestBlobSidecars(UInt64 lastSlotToPrune, int pruneLimit);

  boolean pruneOldestNonCanonicalBlobSidecars(UInt64 lastSlotToPrune, int pruneLimit);

  @MustBeClosed
  Stream<SlotAndBlockRootAndBlobIndex> streamBlobSidecarKeys(UInt64 startSlot, UInt64 endSlot);

  @MustBeClosed
  Stream<SlotAndBlockRootAndBlobIndex> streamNonCanonicalBlobSidecarKeys(
      UInt64 startSlot, UInt64 endSlot);

  @MustBeClosed
  default Stream<SlotAndBlockRootAndBlobIndex> streamNonCanonicalBlobSidecarKeys(
      final UInt64 slot) {
    return streamNonCanonicalBlobSidecarKeys(slot, slot);
  }

  @MustBeClosed
  default Stream<SlotAndBlockRootAndBlobIndex> streamBlobSidecarKeys(final UInt64 slot) {
    return streamBlobSidecarKeys(slot, slot);
  }

  @MustBeClosed
  Stream<BlobSidecar> streamBlobSidecars(SlotAndBlockRoot slotAndBlockRoot);

  List<SlotAndBlockRootAndBlobIndex> getBlobSidecarKeys(SlotAndBlockRoot slotAndBlockRoot);

  Optional<UInt64> getEarliestBlobSidecarSlot();

  Optional<OnDiskStoreData> createMemoryStore();

  WeakSubjectivityState getWeakSubjectivityState();

  Map<UInt64, VoteTracker> getVotes();

  Optional<UInt64> getSlotForFinalizedBlockRoot(Bytes32 blockRoot);

  Optional<UInt64> getSlotForFinalizedStateRoot(Bytes32 stateRoot);

  /**
   * Return the finalized block at this slot if such a block exists.
   *
   * @param slot The slot to query
   * @return Returns the finalized block proposed at this slot, if such a block exists
   */
  Optional<SignedBeaconBlock> getFinalizedBlockAtSlot(UInt64 slot);

  /**
   * @return The earliest available finalized block's slot
   */
  Optional<UInt64> getEarliestAvailableBlockSlot();

  /** Return the earliest available finalized block */
  Optional<SignedBeaconBlock> getEarliestAvailableBlock();

  Optional<SignedBeaconBlock> getLastAvailableFinalizedBlock();

  Optional<Checkpoint> getFinalizedCheckpoint();

  Optional<Bytes32> getFinalizedBlockRootBySlot(UInt64 slot);

  /**
   * Returns the latest finalized block at or prior to the given slot
   *
   * @param slot The slot to query
   * @return Returns the latest finalized block proposed at or prior to the given slot
   */
  Optional<SignedBeaconBlock> getLatestFinalizedBlockAtSlot(UInt64 slot);

  Optional<SignedBeaconBlock> getSignedBlock(Bytes32 root);

  Optional<BeaconState> getHotState(Bytes32 root);

  Optional<UInt64> getGenesisTime();

  /**
   * Returns latest finalized block or any known blocks that descend from the latest finalized block
   *
   * @param blockRoots The roots of blocks to look up
   * @return A map from root too block of any found blocks
   */
  Map<Bytes32, SignedBeaconBlock> getHotBlocks(final Set<Bytes32> blockRoots);

  Optional<SignedBeaconBlock> getHotBlock(final Bytes32 blockRoot);

  @MustBeClosed
  Stream<Map.Entry<Bytes, Bytes>> streamHotBlocksAsSsz();

  /**
   * Return a {@link Stream} of blocks beginning at startSlot and ending at endSlot, both inclusive.
   *
   * @param startSlot the slot of the first block to return
   * @param endSlot the slot of the last block to return
   * @return a Stream of blocks in the range startSlot to endSlot (both inclusive).
   */
  @MustBeClosed
  Stream<SignedBeaconBlock> streamFinalizedBlocks(UInt64 startSlot, UInt64 endSlot);

  @MustBeClosed
  Stream<Map.Entry<Bytes32, BlockCheckpoints>> streamBlockCheckpoints();

  List<Bytes32> getStateRootsBeforeSlot(final UInt64 slot);

  void addHotStateRoots(final Map<Bytes32, SlotAndBlockRoot> stateRootToSlotAndBlockRootMap);

  Optional<SlotAndBlockRoot> getSlotAndBlockRootFromStateRoot(final Bytes32 stateRoot);

  void pruneHotStateRoots(final List<Bytes32> stateRoots);

  Optional<BeaconState> getLatestAvailableFinalizedState(UInt64 maxSlot);

  @MustBeClosed
  Stream<Map.Entry<Bytes32, UInt64>> getFinalizedStateRoots();

  Optional<MinGenesisTimeBlockEvent> getMinGenesisTimeBlock();

  List<SignedBeaconBlock> getNonCanonicalBlocksAtSlot(final UInt64 slot);

  @MustBeClosed
  Stream<DepositsFromBlockEvent> streamDepositsFromBlocks();

  @MustBeClosed
  Stream<UInt64> streamFinalizedStateSlots(final UInt64 startSlot, final UInt64 endSlot);

  void addMinGenesisTimeBlock(final MinGenesisTimeBlockEvent event);

  void addDepositsFromBlockEvent(final DepositsFromBlockEvent event);

  void removeDepositsFromBlockEvents(List<UInt64> blockNumbers);

  void storeVotes(Map<UInt64, VoteTracker> votes);

  Map<String, Long> getColumnCounts(final Optional<String> maybeColumnFilter);

  long getBlobSidecarColumnCount();

  long getNonCanonicalBlobSidecarColumnCount();

  void migrate();

  Optional<Checkpoint> getAnchor();

  Optional<Checkpoint> getJustifiedCheckpoint();

  void deleteHotBlocks(Set<Bytes32> blockRootsToDelete);

  Optional<DepositTreeSnapshot> getFinalizedDepositSnapshot();

  void setFinalizedDepositSnapshot(DepositTreeSnapshot finalizedDepositSnapshot);

  /**
   * This prune method will delete finalized blocks starting from the oldest (by slot) up to block
   * at {@code lastSlotToPrune} (inclusive). The pruning process will be stopped if {@code
   * pruneLimit} reached completing the current slot. Main purpose of pruneLimit is to softly cap DB
   * operation time.
   *
   * @param lastSlotToPrune inclusive, not reached if limit happens first
   * @param pruneLimit slots limit
   * @return actual last pruned slot
   */
  UInt64 pruneFinalizedBlocks(UInt64 lastSlotToPrune, int pruneLimit);

  // Sidecars
  Optional<UInt64> getFirstCustodyIncompleteSlot();

  Optional<UInt64> getFirstSamplerIncompleteSlot();

  Optional<DataColumnSidecar> getSidecar(DataColumnSlotAndIdentifier identifier);

  @MustBeClosed
  Stream<DataColumnSlotAndIdentifier> streamDataColumnIdentifiers(
      UInt64 firstSlot, UInt64 lastSlot);

  @MustBeClosed
  default Stream<DataColumnSlotAndIdentifier> streamDataColumnIdentifiers(final UInt64 slot) {
    return streamDataColumnIdentifiers(slot, slot);
  }

  Optional<UInt64> getEarliestDataColumnSidecarSlot();

  void setFirstCustodyIncompleteSlot(UInt64 slot);

  void setFirstSamplerIncompleteSlot(UInt64 slot);

  void addSidecar(DataColumnSidecar sidecar);

  void pruneAllSidecars(UInt64 tillSlotInclusive);
}
