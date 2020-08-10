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
import tech.pegasys.teku.storage.server.rocksdb.schema.V4SchemaHot;

public class V4HotRocksDbDao implements RocksDbHotDao, RocksDbEth1Dao, RocksDbProtoArrayDao {
  // Persistent data
  private final RocksDbAccessor db;

  public V4HotRocksDbDao(final RocksDbAccessor db) {
    this.db = db;
  }

  @Override
  public Optional<UInt64> getGenesisTime() {
    return db.get(V4SchemaHot.GENESIS_TIME);
  }

  @Override
  public Optional<Checkpoint> getJustifiedCheckpoint() {
    return db.get(V4SchemaHot.JUSTIFIED_CHECKPOINT);
  }

  @Override
  public Optional<Checkpoint> getBestJustifiedCheckpoint() {
    return db.get(V4SchemaHot.BEST_JUSTIFIED_CHECKPOINT);
  }

  @Override
  public Optional<Checkpoint> getFinalizedCheckpoint() {
    return db.get(V4SchemaHot.FINALIZED_CHECKPOINT);
  }

  @Override
  public Optional<SignedBeaconBlock> getHotBlock(final Bytes32 root) {
    return db.get(V4SchemaHot.HOT_BLOCKS_BY_ROOT, root);
  }

  @Override
  @MustBeClosed
  public Stream<SignedBeaconBlock> streamHotBlocks() {
    return db.stream(V4SchemaHot.HOT_BLOCKS_BY_ROOT).map(ColumnEntry::getValue);
  }

  @Override
  public Optional<BeaconState> getLatestFinalizedState() {
    return db.get(V4SchemaHot.LATEST_FINALIZED_STATE);
  }

  @Override
  public Map<Bytes32, SignedBeaconBlock> getHotBlocks() {
    return db.getAll(V4SchemaHot.HOT_BLOCKS_BY_ROOT);
  }

  @Override
  public List<Bytes32> getStateRootsBeforeSlot(final UInt64 slot) {
    try (Stream<ColumnEntry<Bytes32, SlotAndBlockRoot>> stream =
        db.stream(V4SchemaHot.STATE_ROOT_TO_SLOT_AND_BLOCK_ROOT)) {
      return stream
          .filter((column) -> column.getValue().getSlot().compareTo(slot) < 0)
          .map(ColumnEntry::getKey)
          .collect(Collectors.toList());
    }
  }

  @Override
  public Optional<SlotAndBlockRoot> getSlotAndBlockRootFromStateRoot(final Bytes32 stateRoot) {
    return db.get(V4SchemaHot.STATE_ROOT_TO_SLOT_AND_BLOCK_ROOT, stateRoot);
  }

  @Override
  public Map<UInt64, VoteTracker> getVotes() {
    return db.getAll(V4SchemaHot.VOTES);
  }

  @Override
  @MustBeClosed
  public Stream<DepositsFromBlockEvent> streamDepositsFromBlocks() {
    return db.stream(V4SchemaHot.DEPOSITS_FROM_BLOCK_EVENTS).map(ColumnEntry::getValue);
  }

  @Override
  public Optional<MinGenesisTimeBlockEvent> getMinGenesisTimeBlock() {
    return db.get(V4SchemaHot.MIN_GENESIS_TIME_BLOCK);
  }

  @Override
  public Optional<ProtoArraySnapshot> getProtoArraySnapshot() {
    return db.get(V4SchemaHot.PROTO_ARRAY_SNAPSHOT);
  }

  @Override
  @MustBeClosed
  public HotUpdater hotUpdater() {
    return new V4HotUpdater(db);
  }

  @Override
  @MustBeClosed
  public Eth1Updater eth1Updater() {
    return new V4HotUpdater(db);
  }

  @Override
  @MustBeClosed
  public ProtoArrayUpdater protoArrayUpdater() {
    return new V4HotUpdater(db);
  }

  @Override
  public void close() throws Exception {
    db.close();
  }

  private static class V4HotUpdater implements HotUpdater, Eth1Updater, ProtoArrayUpdater {

    private final RocksDbTransaction transaction;

    V4HotUpdater(final RocksDbAccessor db) {
      this.transaction = db.startTransaction();
    }

    @Override
    public void setGenesisTime(final UInt64 genesisTime) {
      transaction.put(V4SchemaHot.GENESIS_TIME, genesisTime);
    }

    @Override
    public void setJustifiedCheckpoint(final Checkpoint checkpoint) {
      transaction.put(V4SchemaHot.JUSTIFIED_CHECKPOINT, checkpoint);
    }

    @Override
    public void setBestJustifiedCheckpoint(final Checkpoint checkpoint) {
      transaction.put(V4SchemaHot.BEST_JUSTIFIED_CHECKPOINT, checkpoint);
    }

    @Override
    public void setFinalizedCheckpoint(final Checkpoint checkpoint) {
      transaction.put(V4SchemaHot.FINALIZED_CHECKPOINT, checkpoint);
    }

    @Override
    public void setLatestFinalizedState(final BeaconState state) {
      transaction.put(V4SchemaHot.LATEST_FINALIZED_STATE, state);
    }

    @Override
    public void addHotBlock(final SignedBeaconBlock block) {
      final Bytes32 blockRoot = block.getRoot();
      transaction.put(V4SchemaHot.HOT_BLOCKS_BY_ROOT, blockRoot, block);
    }

    @Override
    public void addHotBlocks(final Map<Bytes32, SignedBeaconBlock> blocks) {
      blocks.values().forEach(this::addHotBlock);
    }

    @Override
    public void addHotStateRoots(
        final Map<Bytes32, SlotAndBlockRoot> stateRootToSlotAndBlockRootMap) {
      stateRootToSlotAndBlockRootMap.forEach(
          (stateRoot, slotAndBlockRoot) ->
              transaction.put(
                  V4SchemaHot.STATE_ROOT_TO_SLOT_AND_BLOCK_ROOT, stateRoot, slotAndBlockRoot));
    }

    @Override
    public void pruneHotStateRoots(final List<Bytes32> stateRoots) {
      stateRoots.stream()
          .forEach(
              (root) -> transaction.delete(V4SchemaHot.STATE_ROOT_TO_SLOT_AND_BLOCK_ROOT, root));
    }

    @Override
    public void addVotes(final Map<UInt64, VoteTracker> votes) {
      votes.forEach(
          (validatorIndex, vote) -> transaction.put(V4SchemaHot.VOTES, validatorIndex, vote));
    }

    @Override
    public void deleteHotBlock(final Bytes32 blockRoot) {
      transaction.delete(V4SchemaHot.HOT_BLOCKS_BY_ROOT, blockRoot);
    }

    @Override
    public void addMinGenesisTimeBlock(final MinGenesisTimeBlockEvent event) {
      transaction.put(V4SchemaHot.MIN_GENESIS_TIME_BLOCK, event);
    }

    @Override
    public void addDepositsFromBlockEvent(final DepositsFromBlockEvent event) {
      transaction.put(V4SchemaHot.DEPOSITS_FROM_BLOCK_EVENTS, event.getBlockNumber(), event);
    }

    @Override
    public void putProtoArraySnapshot(ProtoArraySnapshot newProtoArray) {
      transaction.put(V4SchemaHot.PROTO_ARRAY_SNAPSHOT, newProtoArray);
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
