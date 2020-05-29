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

import com.google.common.primitives.UnsignedLong;
import com.google.errorprone.annotations.MustBeClosed;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.pow.event.DepositsFromBlockEvent;
import tech.pegasys.teku.pow.event.MinGenesisTimeBlockEvent;

/**
 * A RocksDB "data access object" interface to abstract interactions with underlying database.
 *
 * @see <a href="https://en.wikipedia.org/wiki/Data_access_object">DAO</a>
 */
public interface RocksDbDao extends AutoCloseable {

  Optional<UnsignedLong> getGenesisTime();

  Optional<Checkpoint> getJustifiedCheckpoint();

  Optional<Checkpoint> getBestJustifiedCheckpoint();

  Optional<Checkpoint> getFinalizedCheckpoint();

  Optional<Bytes32> getFinalizedRootAtSlot(final UnsignedLong slot);

  Optional<Bytes32> getLatestFinalizedRootAtSlot(final UnsignedLong slot);

  Optional<SignedBeaconBlock> getHotBlock(final Bytes32 root);

  Optional<SignedBeaconBlock> getFinalizedBlock(final Bytes32 root);

  Optional<BeaconState> getFinalizedState(final Bytes32 root);

  Map<Bytes32, SignedBeaconBlock> getHotBlocks();

  Map<Checkpoint, BeaconState> getCheckpointStates();

  Map<UnsignedLong, VoteTracker> getVotes();

  @MustBeClosed
  Stream<DepositsFromBlockEvent> streamDepositsFromBlocks();

  Optional<MinGenesisTimeBlockEvent> getMinGenesisTimeBlock();

  Updater updater();

  interface Updater extends AutoCloseable {

    void setGenesisTime(final UnsignedLong genesisTime);

    void setJustifiedCheckpoint(final Checkpoint checkpoint);

    void setBestJustifiedCheckpoint(final Checkpoint checkpoint);

    void setFinalizedCheckpoint(final Checkpoint checkpoint);

    void addCheckpointState(final Checkpoint checkpoint, final BeaconState state);

    void addCheckpointStates(Map<Checkpoint, BeaconState> checkpointStates);

    void addHotBlock(final SignedBeaconBlock block);

    void addFinalizedBlock(final SignedBeaconBlock block);

    void addFinalizedState(final Bytes32 blockRoot, final BeaconState state);

    void addVotes(final Map<UnsignedLong, VoteTracker> states);

    void addHotBlocks(final Map<Bytes32, SignedBeaconBlock> blocks);

    void deleteCheckpointState(final Checkpoint checkpoint);

    void deleteHotBlock(final Bytes32 blockRoot);

    void addMinGenesisTimeBlock(final MinGenesisTimeBlockEvent event);

    void addDepositsFromBlockEvent(final DepositsFromBlockEvent event);

    void commit();

    void cancel();

    @Override
    void close();
  }
}
