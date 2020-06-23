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
import tech.pegasys.teku.storage.server.rocksdb.core.ColumnEntry;
import tech.pegasys.teku.storage.server.rocksdb.core.RocksDbAccessor;
import tech.pegasys.teku.storage.server.rocksdb.core.RocksDbAccessor.RocksDbTransaction;
import tech.pegasys.teku.storage.server.rocksdb.schema.V3Schema;

public class V3RocksDbDao implements RocksDbHotDao, RocksDbFinalizedDao, RocksDbEth1Dao {
  private final RocksDbAccessor db;

  public V3RocksDbDao(final RocksDbAccessor db) {
    this.db = db;
  }

  @Override
  public Optional<UnsignedLong> getGenesisTime() {
    return db.get(V3Schema.GENESIS_TIME);
  }

  @Override
  public Optional<Checkpoint> getJustifiedCheckpoint() {
    return db.get(V3Schema.JUSTIFIED_CHECKPOINT);
  }

  @Override
  public Optional<Checkpoint> getBestJustifiedCheckpoint() {
    return db.get(V3Schema.BEST_JUSTIFIED_CHECKPOINT);
  }

  @Override
  public Optional<Checkpoint> getFinalizedCheckpoint() {
    return db.get(V3Schema.FINALIZED_CHECKPOINT);
  }

  @Override
  public Optional<Bytes32> getFinalizedRootAtSlot(final UnsignedLong slot) {
    return db.get(V3Schema.FINALIZED_ROOTS_BY_SLOT, slot);
  }

  @Override
  public Optional<Bytes32> getLatestFinalizedRootAtSlot(final UnsignedLong slot) {
    return db.getFloorEntry(V3Schema.FINALIZED_ROOTS_BY_SLOT, slot).map(ColumnEntry::getValue);
  }

  @Override
  public Optional<SignedBeaconBlock> getHotBlock(final Bytes32 root) {
    return db.get(V3Schema.HOT_BLOCKS_BY_ROOT, root);
  }

  @Override
  public Optional<SignedBeaconBlock> getFinalizedBlock(final Bytes32 root) {
    return db.get(V3Schema.FINALIZED_BLOCKS_BY_ROOT, root);
  }

  @Override
  public Optional<BeaconState> getFinalizedState(final Bytes32 root) {
    return db.get(V3Schema.FINALIZED_STATES_BY_ROOT, root);
  }

  @Override
  public Optional<BeaconState> getLatestFinalizedState() {
    return db.get(V3Schema.LATEST_FINALIZED_STATE);
  }

  @Override
  public Map<Bytes32, SignedBeaconBlock> getHotBlocks() {
    return db.getAll(V3Schema.HOT_BLOCKS_BY_ROOT);
  }

  @Override
  public Map<Checkpoint, BeaconState> getCheckpointStates() {
    return db.getAll(V3Schema.CHECKPOINT_STATES);
  }

  @Override
  public Map<UnsignedLong, VoteTracker> getVotes() {
    return db.getAll(V3Schema.VOTES);
  }

  @Override
  @MustBeClosed
  public Stream<DepositsFromBlockEvent> streamDepositsFromBlocks() {
    return db.stream(V3Schema.DEPOSITS_FROM_BLOCK_EVENTS).map(ColumnEntry::getValue);
  }

  @Override
  public Optional<MinGenesisTimeBlockEvent> getMinGenesisTimeBlock() {
    return db.get(V3Schema.MIN_GENESIS_TIME_BLOCK);
  }

  @Override
  public HotUpdater hotUpdater() {
    return new V3Updater(db);
  }

  @Override
  public FinalizedUpdater finalizedUpdater() {
    return new V3Updater(db);
  }

  @Override
  public Eth1Updater eth1Updater() {
    return new V3Updater(db);
  }

  @Override
  public void close() throws Exception {
    db.close();
  }

  private static class V3Updater implements HotUpdater, FinalizedUpdater, Eth1Updater {

    private final RocksDbTransaction transaction;

    V3Updater(final RocksDbAccessor db) {
      this.transaction = db.startTransaction();
    }

    @Override
    public void setGenesisTime(final UnsignedLong genesisTime) {
      transaction.put(V3Schema.GENESIS_TIME, genesisTime);
    }

    @Override
    public void setJustifiedCheckpoint(final Checkpoint checkpoint) {
      transaction.put(V3Schema.JUSTIFIED_CHECKPOINT, checkpoint);
    }

    @Override
    public void setBestJustifiedCheckpoint(final Checkpoint checkpoint) {
      transaction.put(V3Schema.BEST_JUSTIFIED_CHECKPOINT, checkpoint);
    }

    @Override
    public void setFinalizedCheckpoint(final Checkpoint checkpoint) {
      transaction.put(V3Schema.FINALIZED_CHECKPOINT, checkpoint);
    }

    @Override
    public void setLatestFinalizedState(final BeaconState state) {
      transaction.put(V3Schema.LATEST_FINALIZED_STATE, state);
    }

    @Override
    public void addCheckpointState(final Checkpoint checkpoint, final BeaconState state) {
      transaction.put(V3Schema.CHECKPOINT_STATES, checkpoint, state);
    }

    @Override
    public void addCheckpointStates(final Map<Checkpoint, BeaconState> checkpointStates) {
      checkpointStates.forEach(this::addCheckpointState);
    }

    @Override
    public void addHotBlock(final SignedBeaconBlock block) {
      final Bytes32 blockRoot = block.getRoot();
      transaction.put(V3Schema.HOT_BLOCKS_BY_ROOT, blockRoot, block);
    }

    @Override
    public void addFinalizedBlock(final SignedBeaconBlock block) {
      final Bytes32 root = block.getRoot();
      transaction.put(V3Schema.FINALIZED_ROOTS_BY_SLOT, block.getSlot(), root);
      transaction.put(V3Schema.FINALIZED_BLOCKS_BY_ROOT, root, block);
    }

    @Override
    public void addFinalizedState(final Bytes32 blockRoot, final BeaconState state) {
      transaction.put(V3Schema.FINALIZED_STATES_BY_ROOT, blockRoot, state);
    }

    @Override
    public void addHotBlocks(final Map<Bytes32, SignedBeaconBlock> blocks) {
      blocks.values().forEach(this::addHotBlock);
    }

    @Override
    public void addVotes(final Map<UnsignedLong, VoteTracker> votes) {
      votes.forEach(
          (validatorIndex, vote) -> transaction.put(V3Schema.VOTES, validatorIndex, vote));
    }

    @Override
    public void deleteCheckpointState(final Checkpoint checkpoint) {
      transaction.delete(V3Schema.CHECKPOINT_STATES, checkpoint);
    }

    @Override
    public void deleteHotBlock(final Bytes32 blockRoot) {
      transaction.delete(V3Schema.HOT_BLOCKS_BY_ROOT, blockRoot);
    }

    @Override
    public void addMinGenesisTimeBlock(final MinGenesisTimeBlockEvent event) {
      transaction.put(V3Schema.MIN_GENESIS_TIME_BLOCK, event);
    }

    @Override
    public void addDepositsFromBlockEvent(final DepositsFromBlockEvent event) {
      transaction.put(V3Schema.DEPOSITS_FROM_BLOCK_EVENTS, event.getBlockNumber(), event);
    }

    @Override
    public void commit() {
      // Commit db updates
      transaction.commit();
      close();
    }

    @Override
    public void cancel() {
      transaction.rollback();
      close();
    }

    @Override
    public void close() {
      transaction.close();
    }
  }
}
