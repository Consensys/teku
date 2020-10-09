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
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.pow.event.DepositsFromBlockEvent;
import tech.pegasys.teku.pow.event.MinGenesisTimeBlockEvent;
import tech.pegasys.teku.protoarray.ProtoArraySnapshot;
import tech.pegasys.teku.storage.server.rocksdb.core.ColumnEntry;
import tech.pegasys.teku.storage.server.rocksdb.core.RocksDbAccessor;
import tech.pegasys.teku.storage.server.rocksdb.core.RocksDbAccessor.RocksDbTransaction;
import tech.pegasys.teku.storage.server.rocksdb.schema.SchemaHot;

public class V4HotRocksDbDao implements RocksDbHotDao, RocksDbEth1Dao, RocksDbProtoArrayDao {
  // Persistent data
  private final RocksDbAccessor db;
  private final SchemaHot schema;

  public V4HotRocksDbDao(final RocksDbAccessor db, final SchemaHot schema) {
    this.db = db;
    this.schema = schema;
  }

  @Override
  public Optional<UInt64> getGenesisTime() {
    return db.get(schema.variable_GENESIS_TIME());
  }

  @Override
  public Optional<Checkpoint> getJustifiedCheckpoint() {
    return db.get(schema.variable_JUSTIFIED_CHECKPOINT());
  }

  @Override
  public Optional<Checkpoint> getBestJustifiedCheckpoint() {
    return db.get(schema.variable_BEST_JUSTIFIED_CHECKPOINT());
  }

  @Override
  public Optional<Checkpoint> getFinalizedCheckpoint() {
    return db.get(schema.variable_FINALIZED_CHECKPOINT());
  }

  @Override
  public Optional<SignedBeaconBlock> getHotBlock(final Bytes32 root) {
    return db.get(schema.column_HOT_BLOCKS_BY_ROOT(), root);
  }

  @Override
  public Optional<BeaconState> getHotState(final Bytes32 root) {
    return db.get(schema.column_HOT_STATES_BY_ROOT(), root);
  }

  @Override
  @MustBeClosed
  public Stream<SignedBeaconBlock> streamHotBlocks() {
    return db.stream(schema.column_HOT_BLOCKS_BY_ROOT()).map(ColumnEntry::getValue);
  }

  @Override
  public Optional<BeaconState> getLatestFinalizedState() {
    return db.get(schema.variable_LATEST_FINALIZED_STATE());
  }

  @Override
  public Optional<Checkpoint> getWeakSubjectivityCheckpoint() {
    return db.get(schema.variable_WEAK_SUBJECTIVITY_CHECKPOINT());
  }

  @Override
  public Map<Bytes32, SignedBeaconBlock> getHotBlocks() {
    return db.getAll(schema.column_HOT_BLOCKS_BY_ROOT());
  }

  @Override
  public List<Bytes32> getStateRootsBeforeSlot(final UInt64 slot) {
    try (Stream<ColumnEntry<Bytes32, SlotAndBlockRoot>> stream =
        db.stream(schema.column_STATE_ROOT_TO_SLOT_AND_BLOCK_ROOT())) {
      return stream
          .filter((column) -> column.getValue().getSlot().compareTo(slot) < 0)
          .map(ColumnEntry::getKey)
          .collect(Collectors.toList());
    }
  }

  @Override
  public Optional<SlotAndBlockRoot> getSlotAndBlockRootFromStateRoot(final Bytes32 stateRoot) {
    return db.get(schema.column_STATE_ROOT_TO_SLOT_AND_BLOCK_ROOT(), stateRoot);
  }

  @Override
  public Map<UInt64, VoteTracker> getVotes() {
    return db.getAll(schema.column_VOTES());
  }

  @Override
  @MustBeClosed
  public Stream<DepositsFromBlockEvent> streamDepositsFromBlocks() {
    return db.stream(schema.column_DEPOSITS_FROM_BLOCK_EVENTS()).map(ColumnEntry::getValue);
  }

  @Override
  public Optional<MinGenesisTimeBlockEvent> getMinGenesisTimeBlock() {
    return db.get(schema.variable_MIN_GENESIS_TIME_BLOCK());
  }

  @Override
  public Optional<ProtoArraySnapshot> getProtoArraySnapshot() {
    return db.get(schema.variable_PROTO_ARRAY_SNAPSHOT());
  }

  @Override
  @MustBeClosed
  public HotUpdater hotUpdater() {
    return new V4HotUpdater(db, schema);
  }

  @Override
  @MustBeClosed
  public Eth1Updater eth1Updater() {
    return new V4HotUpdater(db, schema);
  }

  @Override
  @MustBeClosed
  public ProtoArrayUpdater protoArrayUpdater() {
    return new V4HotUpdater(db, schema);
  }

  @Override
  public void close() throws Exception {
    db.close();
  }

  private static class V4HotUpdater implements HotUpdater, Eth1Updater, ProtoArrayUpdater {

    private final RocksDbTransaction transaction;
    private final SchemaHot schema;

    V4HotUpdater(final RocksDbAccessor db, final SchemaHot schema) {
      this.transaction = db.startTransaction();
      this.schema = schema;
    }

    @Override
    public void setGenesisTime(final UInt64 genesisTime) {
      transaction.put(schema.variable_GENESIS_TIME(), genesisTime);
    }

    @Override
    public void setJustifiedCheckpoint(final Checkpoint checkpoint) {
      transaction.put(schema.variable_JUSTIFIED_CHECKPOINT(), checkpoint);
    }

    @Override
    public void setBestJustifiedCheckpoint(final Checkpoint checkpoint) {
      transaction.put(schema.variable_BEST_JUSTIFIED_CHECKPOINT(), checkpoint);
    }

    @Override
    public void setFinalizedCheckpoint(final Checkpoint checkpoint) {
      transaction.put(schema.variable_FINALIZED_CHECKPOINT(), checkpoint);
    }

    @Override
    public void setWeakSubjectivityCheckpoint(Checkpoint checkpoint) {
      transaction.put(schema.variable_WEAK_SUBJECTIVITY_CHECKPOINT(), checkpoint);
    }

    @Override
    public void clearWeakSubjectivityCheckpoint() {
      transaction.delete(schema.variable_WEAK_SUBJECTIVITY_CHECKPOINT());
    }

    @Override
    public void setLatestFinalizedState(final BeaconState state) {
      transaction.put(schema.variable_LATEST_FINALIZED_STATE(), state);
    }

    @Override
    public void addHotBlock(final SignedBeaconBlock block) {
      final Bytes32 blockRoot = block.getRoot();
      transaction.put(schema.column_HOT_BLOCKS_BY_ROOT(), blockRoot, block);
    }

    @Override
    public void addHotState(final Bytes32 blockRoot, final BeaconState state) {
      transaction.put(schema.column_HOT_STATES_BY_ROOT(), blockRoot, state);
    }

    @Override
    public void addHotStateRoots(
        final Map<Bytes32, SlotAndBlockRoot> stateRootToSlotAndBlockRootMap) {
      stateRootToSlotAndBlockRootMap.forEach(
          (stateRoot, slotAndBlockRoot) ->
              transaction.put(
                  schema.column_STATE_ROOT_TO_SLOT_AND_BLOCK_ROOT(), stateRoot, slotAndBlockRoot));
    }

    @Override
    public void pruneHotStateRoots(final List<Bytes32> stateRoots) {
      stateRoots.stream()
          .forEach(
              (root) -> transaction.delete(schema.column_STATE_ROOT_TO_SLOT_AND_BLOCK_ROOT(), root));
    }

    @Override
    public void addVotes(final Map<UInt64, VoteTracker> votes) {
      votes.forEach(
          (validatorIndex, vote) -> transaction.put(schema.column_VOTES(), validatorIndex, vote));
    }

    @Override
    public void deleteHotBlock(final Bytes32 blockRoot) {
      transaction.delete(schema.column_HOT_BLOCKS_BY_ROOT(), blockRoot);
      deleteHotState(blockRoot);
    }

    @Override
    public void deleteHotState(final Bytes32 blockRoot) {
      transaction.delete(schema.column_HOT_STATES_BY_ROOT(), blockRoot);
    }

    @Override
    public void addMinGenesisTimeBlock(final MinGenesisTimeBlockEvent event) {
      transaction.put(schema.variable_MIN_GENESIS_TIME_BLOCK(), event);
    }

    @Override
    public void addDepositsFromBlockEvent(final DepositsFromBlockEvent event) {
      transaction.put(schema.column_DEPOSITS_FROM_BLOCK_EVENTS(), event.getBlockNumber(), event);
    }

    @Override
    public void putProtoArraySnapshot(ProtoArraySnapshot newProtoArray) {
      transaction.put(schema.variable_PROTO_ARRAY_SNAPSHOT(), newProtoArray);
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
