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
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ethereum.pow.api.DepositsFromBlockEvent;
import tech.pegasys.teku.ethereum.pow.api.MinGenesisTimeBlockEvent;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.BlockCheckpoints;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

public interface KvStoreCombinedDaoCommon extends AutoCloseable {

  void ingest(KvStoreCombinedDaoCommon dao, int batchSize, Consumer<String> logger);

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

  Set<Bytes32> getNonCanonicalBlockRootsAtSlot(UInt64 slot);

  long countNonCanonicalSlots();

  Optional<UInt64> getOptimisticTransitionBlockSlot();

  Map<String, Long> getColumnCounts();

  @MustBeClosed
  Stream<UInt64> streamFinalizedStateSlots(final UInt64 startSlot, final UInt64 endSlot);

  interface CombinedUpdaterCommon extends HotUpdaterCommon, FinalizedUpdaterCommon {}

  interface HotUpdaterCommon extends AutoCloseable {

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

    void commit();

    void cancel();

    @Override
    void close();

    void addMinGenesisTimeBlock(final MinGenesisTimeBlockEvent event);

    void addDepositsFromBlockEvent(final DepositsFromBlockEvent event);
  }

  interface FinalizedUpdaterCommon extends AutoCloseable {

    void addFinalizedState(final Bytes32 blockRoot, final BeaconState state);

    void addFinalizedStateRoot(final Bytes32 stateRoot, final UInt64 slot);

    void setOptimisticTransitionBlockSlot(final Optional<UInt64> transitionBlockSlot);

    void addNonCanonicalRootAtSlot(final UInt64 slot, final Set<Bytes32> blockRoots);

    void commit();

    void cancel();

    @Override
    void close();
  }
}
