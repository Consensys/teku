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
import tech.pegasys.teku.protoarray.ProtoArraySnapshot;

/**
 * Provides an abstract "data access object" interface for working with hot data (non-finalized)
 * data from the underlying database.
 */
public interface RocksDbHotDao extends AutoCloseable {

  Optional<UnsignedLong> getGenesisTime();

  Optional<Checkpoint> getJustifiedCheckpoint();

  Optional<Checkpoint> getBestJustifiedCheckpoint();

  Optional<Checkpoint> getFinalizedCheckpoint();

  // In hot dao because it must be in sync with the finalized checkpoint
  Optional<BeaconState> getLatestFinalizedState();

  Optional<SignedBeaconBlock> getHotBlock(final Bytes32 root);

  Map<Bytes32, SignedBeaconBlock> getHotBlocks();

  @MustBeClosed
  Stream<SignedBeaconBlock> streamHotBlocks();

  Map<UnsignedLong, VoteTracker> getVotes();

  HotUpdater hotUpdater();

  interface HotUpdater extends AutoCloseable {

    void setGenesisTime(final UnsignedLong genesisTime);

    void setJustifiedCheckpoint(final Checkpoint checkpoint);

    void setBestJustifiedCheckpoint(final Checkpoint checkpoint);

    void setFinalizedCheckpoint(final Checkpoint checkpoint);

    void setLatestFinalizedState(final BeaconState state);

    void addHotBlock(final SignedBeaconBlock block);

    void addVotes(final Map<UnsignedLong, VoteTracker> states);

    void addHotBlocks(final Map<Bytes32, SignedBeaconBlock> blocks);

    void deleteHotBlock(final Bytes32 blockRoot);

    void putProtoArraySnapshot(final ProtoArraySnapshot protoArraySnapshot);

    void commit();

    void cancel();

    @Override
    void close();
  }
}
