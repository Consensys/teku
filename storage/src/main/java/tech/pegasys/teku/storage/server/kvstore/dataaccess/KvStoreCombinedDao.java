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

package tech.pegasys.teku.storage.server.kvstore.dataaccess;

import com.google.errorprone.annotations.MustBeClosed;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ethereum.pow.api.DepositTreeSnapshot;
import tech.pegasys.teku.ethereum.pow.api.DepositsFromBlockEvent;
import tech.pegasys.teku.ethereum.pow.api.MinGenesisTimeBlockEvent;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobsSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.BlockAndCheckpoints;
import tech.pegasys.teku.spec.datastructures.blocks.BlockCheckpoints;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.util.SlotAndBlockRootAndBlobIndex;

public interface KvStoreCombinedDao extends AutoCloseable {

  Bytes32 MIN_BLOCK_ROOT = Bytes32.ZERO;
  Bytes32 MAX_BLOCK_ROOT = Bytes32.ZERO.not();

  @MustBeClosed
  HotUpdater hotUpdater();

  @MustBeClosed
  FinalizedUpdater finalizedUpdater();

  @MustBeClosed
  CombinedUpdater combinedUpdater();

  Optional<SignedBeaconBlock> getHotBlock(Bytes32 root);

  @MustBeClosed
  Stream<SignedBeaconBlock> streamHotBlocks();

  Stream<Map.Entry<Bytes, Bytes>> streamHotBlocksAsSsz();

  Optional<SignedBeaconBlock> getFinalizedBlock(final Bytes32 root);

  Optional<SignedBeaconBlock> getFinalizedBlockAtSlot(UInt64 slot);

  Optional<UInt64> getEarliestFinalizedBlockSlot();

  Optional<SignedBeaconBlock> getEarliestFinalizedBlock();

  Optional<SignedBeaconBlock> getLatestFinalizedBlockAtSlot(UInt64 slot);

  List<SignedBeaconBlock> getNonCanonicalBlocksAtSlot(UInt64 slot);

  @MustBeClosed
  Stream<SignedBeaconBlock> streamFinalizedBlocks(UInt64 startSlot, UInt64 endSlot);

  Optional<UInt64> getSlotForFinalizedBlockRoot(Bytes32 blockRoot);

  Optional<UInt64> getSlotForFinalizedStateRoot(Bytes32 stateRoot);

  Optional<? extends SignedBeaconBlock> getNonCanonicalBlock(Bytes32 root);

  void ingest(KvStoreCombinedDao dao, int batchSize, Consumer<String> logger);

  Optional<UInt64> getGenesisTime();

  Optional<Checkpoint> getAnchor();

  Optional<Checkpoint> getJustifiedCheckpoint();

  Optional<Checkpoint> getBestJustifiedCheckpoint();

  Optional<Checkpoint> getFinalizedCheckpoint();

  // In hot dao because it must be in sync with the finalized checkpoint
  Optional<BeaconState> getLatestFinalizedState();

  Optional<Checkpoint> getWeakSubjectivityCheckpoint();

  Optional<BlockCheckpoints> getHotBlockCheckpointEpochs(Bytes32 root);

  Optional<BeaconState> getHotState(Bytes32 root);

  List<Bytes32> getStateRootsBeforeSlot(UInt64 slot);

  Optional<SlotAndBlockRoot> getSlotAndBlockRootFromStateRoot(Bytes32 stateRoot);

  Optional<SlotAndBlockRoot> getSlotAndBlockRootForFinalizedStateRoot(Bytes32 stateRoot);

  Map<UInt64, VoteTracker> getVotes();

  @MustBeClosed
  Stream<DepositsFromBlockEvent> streamDepositsFromBlocks();

  @MustBeClosed
  Stream<Map.Entry<Bytes32, BlockCheckpoints>> streamBlockCheckpoints();

  Optional<MinGenesisTimeBlockEvent> getMinGenesisTimeBlock();

  Optional<BeaconState> getLatestAvailableFinalizedState(UInt64 maxSlot);

  @MustBeClosed
  Stream<Map.Entry<Bytes32, UInt64>> getFinalizedStateRoots();

  @MustBeClosed
  Stream<Map.Entry<Bytes32, UInt64>> getFinalizedBlockRoots();

  Set<Bytes32> getNonCanonicalBlockRootsAtSlot(UInt64 slot);

  Optional<UInt64> getOptimisticTransitionBlockSlot();

  Optional<Bytes> getBlobSidecar(SlotAndBlockRootAndBlobIndex key);

  Optional<Bytes> getBlobsSidecar(SlotAndBlockRoot slotAndBlockRoot);

  @MustBeClosed
  Stream<SlotAndBlockRootAndBlobIndex> streamBlobSidecarKeys(UInt64 startSlot, UInt64 endSlot);

  @MustBeClosed
  Stream<Entry<SlotAndBlockRoot, Bytes>> streamBlobsSidecar(UInt64 startSlot, UInt64 endSlot);

  @MustBeClosed
  Stream<SlotAndBlockRoot> streamBlobsSidecarKeys(UInt64 startSlot, UInt64 endSlot);

  @MustBeClosed
  Stream<SlotAndBlockRoot> streamUnconfirmedBlobsSidecar(UInt64 startSlot, UInt64 endSlot);

  Optional<UInt64> getEarliestBlobSidecarSlot();

  Optional<UInt64> getEarliestBlobsSidecarSlot();

  Map<String, Long> getColumnCounts();

  Map<String, Long> getBlobsSidecarColumnCounts();

  @MustBeClosed
  Stream<UInt64> streamFinalizedStateSlots(final UInt64 startSlot, final UInt64 endSlot);

  Optional<DepositTreeSnapshot> getFinalizedDepositSnapshot();

  interface CombinedUpdater extends HotUpdater, FinalizedUpdater {}

  interface HotUpdater extends AutoCloseable {
    void addHotBlock(BlockAndCheckpoints blockAndCheckpointEpochs);

    default void addHotBlocks(final Map<Bytes32, BlockAndCheckpoints> blocks) {
      blocks.values().forEach(this::addHotBlock);
    }

    void deleteHotBlock(Bytes32 blockRoot);

    void deleteHotBlockOnly(Bytes32 blockRoot);

    void setGenesisTime(UInt64 genesisTime);

    void setAnchor(Checkpoint anchorPoint);

    void setJustifiedCheckpoint(Checkpoint checkpoint);

    void setBestJustifiedCheckpoint(Checkpoint checkpoint);

    void setFinalizedCheckpoint(Checkpoint checkpoint);

    void setWeakSubjectivityCheckpoint(Checkpoint checkpoint);

    void clearWeakSubjectivityCheckpoint();

    void setLatestFinalizedState(BeaconState state);

    void addHotState(Bytes32 blockRoot, BeaconState state);

    default void addHotStates(final Map<Bytes32, BeaconState> states) {
      states.forEach(this::addHotState);
    }

    void addVotes(Map<UInt64, VoteTracker> states);

    void addHotStateRoots(Map<Bytes32, SlotAndBlockRoot> stateRootToSlotAndBlockRootMap);

    void pruneHotStateRoots(List<Bytes32> stateRoots);

    void deleteHotState(Bytes32 blockRoot);

    void setFinalizedDepositSnapshot(DepositTreeSnapshot finalizedDepositSnapshot);

    void commit();

    void cancel();

    @Override
    void close();

    void addMinGenesisTimeBlock(final MinGenesisTimeBlockEvent event);

    void addDepositsFromBlockEvent(final DepositsFromBlockEvent event);

    void removeDepositsFromBlockEvent(UInt64 blockNumber);
  }

  interface FinalizedUpdater extends AutoCloseable {

    void addFinalizedBlock(final SignedBeaconBlock block);

    void addNonCanonicalBlock(final SignedBeaconBlock block);

    void deleteFinalizedBlock(final UInt64 slot, final Bytes32 blockRoot);

    void deleteNonCanonicalBlockOnly(final Bytes32 blockRoot);

    void addFinalizedState(final Bytes32 blockRoot, final BeaconState state);

    void addReconstructedFinalizedState(final Bytes32 blockRoot, final BeaconState state);

    void addFinalizedStateRoot(final Bytes32 stateRoot, final UInt64 slot);

    void setOptimisticTransitionBlockSlot(final Optional<UInt64> transitionBlockSlot);

    void addNonCanonicalRootAtSlot(final UInt64 slot, final Set<Bytes32> blockRoots);

    void addBlobSidecar(BlobSidecar blobsSidecar);

    void addNoBlobsSlot(SlotAndBlockRoot slotAndBlockRoot);

    void addBlobsSidecar(BlobsSidecar blobsSidecar);

    void addUnconfirmedBlobsSidecar(BlobsSidecar blobsSidecar);

    void removeBlobSidecar(SlotAndBlockRootAndBlobIndex key);

    void removeBlobsSidecar(SlotAndBlockRoot slotAndBlockRoot);

    void confirmBlobsSidecar(SlotAndBlockRoot slotAndBlockRoot);

    void commit();

    void cancel();

    @Override
    void close();
  }
}
