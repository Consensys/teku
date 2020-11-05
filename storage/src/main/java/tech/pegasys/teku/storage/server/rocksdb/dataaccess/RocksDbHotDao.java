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

package tech.pegasys.teku.storage.server.rocksdb.dataaccess;

import com.google.errorprone.annotations.MustBeClosed;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.blocks.BlockAndCheckpointEpochs;
import tech.pegasys.teku.datastructures.blocks.CheckpointEpochs;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

/**
 * Provides an abstract "data access object" interface for working with hot data (non-finalized)
 * data from the underlying database.
 */
public interface RocksDbHotDao extends AutoCloseable {

  Optional<UInt64> getGenesisTime();

  Optional<Checkpoint> getAnchor();

  Optional<Checkpoint> getJustifiedCheckpoint();

  Optional<Checkpoint> getBestJustifiedCheckpoint();

  Optional<Checkpoint> getFinalizedCheckpoint();

  // In hot dao because it must be in sync with the finalized checkpoint
  Optional<BeaconState> getLatestFinalizedState();

  Optional<Checkpoint> getWeakSubjectivityCheckpoint();

  Optional<SignedBeaconBlock> getHotBlock(Bytes32 root);

  Optional<CheckpointEpochs> getHotBlockCheckpointEpochs(Bytes32 root);

  Optional<BeaconState> getHotState(Bytes32 root);

  List<Bytes32> getStateRootsBeforeSlot(UInt64 slot);

  Optional<SlotAndBlockRoot> getSlotAndBlockRootFromStateRoot(Bytes32 stateRoot);

  @MustBeClosed
  Stream<SignedBeaconBlock> streamHotBlocks();

  Map<UInt64, VoteTracker> getVotes();

  HotUpdater hotUpdater();

  interface HotUpdater extends AutoCloseable {

    void setGenesisTime(UInt64 genesisTime);

    void setAnchor(Checkpoint anchorPoint);

    void setJustifiedCheckpoint(Checkpoint checkpoint);

    void setBestJustifiedCheckpoint(Checkpoint checkpoint);

    void setFinalizedCheckpoint(Checkpoint checkpoint);

    void setWeakSubjectivityCheckpoint(Checkpoint checkpoint);

    void clearWeakSubjectivityCheckpoint();

    void setLatestFinalizedState(BeaconState state);

    void addHotBlock(BlockAndCheckpointEpochs blockAndCheckpointEpochs);

    void addHotBlockCheckpointEpochs(Bytes32 blockRoot, CheckpointEpochs checkpointEpochs);

    void addHotState(Bytes32 blockRoot, BeaconState state);

    default void addHotStates(final Map<Bytes32, BeaconState> states) {
      states.forEach(this::addHotState);
    }

    void addVotes(Map<UInt64, VoteTracker> states);

    default void addHotBlocks(final Map<Bytes32, BlockAndCheckpointEpochs> blocks) {
      blocks.values().forEach(this::addHotBlock);
    }

    void addHotStateRoots(Map<Bytes32, SlotAndBlockRoot> stateRootToSlotAndBlockRootMap);

    void pruneHotStateRoots(List<Bytes32> stateRoots);

    void deleteHotBlock(Bytes32 blockRoot);

    void deleteHotState(Bytes32 blockRoot);

    void commit();

    void cancel();

    @Override
    void close();
  }
}
