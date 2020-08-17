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
import tech.pegasys.teku.storage.server.rocksdb.schema.V3Schema;

public class V3RocksDbDao
    implements RocksDbHotDao, RocksDbFinalizedDao, RocksDbEth1Dao, RocksDbProtoArrayDao {

  private final RocksDbAccessor db;

  public V3RocksDbDao(final RocksDbAccessor db) {
    this.db = db;
  }

  @Override
  public Optional<UInt64> getGenesisTime() {
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
  public Optional<SignedBeaconBlock> getFinalizedBlockAtSlot(final UInt64 slot) {
    return db.get(V3Schema.FINALIZED_ROOTS_BY_SLOT, slot).flatMap(this::getFinalizedBlock);
  }

  @Override
  public Optional<SignedBeaconBlock> getLatestFinalizedBlockAtSlot(final UInt64 slot) {
    return db.getFloorEntry(V3Schema.FINALIZED_ROOTS_BY_SLOT, slot)
        .map(ColumnEntry::getValue)
        .flatMap(this::getFinalizedBlock);
  }

  @Override
  public Optional<BeaconState> getLatestAvailableFinalizedState(final UInt64 maxSlot) {
    return db.getFloorEntry(V3Schema.FINALIZED_ROOTS_BY_SLOT, maxSlot)
        .map(ColumnEntry::getValue)
        .flatMap(root -> db.get(V3Schema.FINALIZED_STATES_BY_ROOT, root));
  }

  @Override
  @MustBeClosed
  public Stream<SignedBeaconBlock> streamFinalizedBlocks(
      final UInt64 startSlot, final UInt64 endSlot) {
    return db.stream(V3Schema.FINALIZED_ROOTS_BY_SLOT, startSlot, endSlot)
        .map(ColumnEntry::getValue)
        .flatMap(root -> getFinalizedBlock(root).stream());
  }

  @Override
  public Optional<UInt64> getSlotForFinalizedBlockRoot(final Bytes32 blockRoot) {
    return getFinalizedBlock(blockRoot).map(SignedBeaconBlock::getSlot);
  }

  @Override
  public Optional<UInt64> getSlotForFinalizedStateRoot(final Bytes32 stateRoot) {
    return db.get(V3Schema.SLOTS_BY_FINALIZED_STATE_ROOT, stateRoot);
  }

  @Override
  public Optional<SlotAndBlockRoot> getSlotAndBlockRootForFinalizedStateRoot(
      final Bytes32 stateRoot) {
    Optional<UInt64> maybeSlot = db.get(V3Schema.SLOTS_BY_FINALIZED_STATE_ROOT, stateRoot);
    return maybeSlot.flatMap(
        slot ->
            getFinalizedBlockAtSlot(slot)
                .map(block -> new SlotAndBlockRoot(slot, block.getRoot())));
  }

  @Override
  public Optional<SignedBeaconBlock> getHotBlock(final Bytes32 root) {
    return db.get(V3Schema.HOT_BLOCKS_BY_ROOT, root);
  }

  @Override
  public Optional<BeaconState> getHotState(final Bytes32 root) {
    // Not supported
    return Optional.empty();
  }

  @Override
  public Optional<SignedBeaconBlock> getFinalizedBlock(final Bytes32 root) {
    return db.get(V3Schema.FINALIZED_BLOCKS_BY_ROOT, root);
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
  public List<Bytes32> getStateRootsBeforeSlot(final UInt64 slot) {
    try (Stream<ColumnEntry<Bytes32, SlotAndBlockRoot>> stream =
        db.stream(V3Schema.STATE_ROOT_TO_SLOT_AND_BLOCK_ROOT)) {
      return stream
          .filter((column) -> column.getValue().getSlot().compareTo(slot) < 0)
          .map(ColumnEntry::getKey)
          .collect(Collectors.toList());
    }
  }

  @Override
  public Optional<SlotAndBlockRoot> getSlotAndBlockRootFromStateRoot(final Bytes32 stateRoot) {
    return db.get(V3Schema.STATE_ROOT_TO_SLOT_AND_BLOCK_ROOT, stateRoot);
  }

  @Override
  @MustBeClosed
  public Stream<SignedBeaconBlock> streamHotBlocks() {
    return db.stream(V3Schema.HOT_BLOCKS_BY_ROOT).map(ColumnEntry::getValue);
  }

  @Override
  public Map<UInt64, VoteTracker> getVotes() {
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
  public Optional<ProtoArraySnapshot> getProtoArraySnapshot() {
    return db.get(V3Schema.PROTO_ARRAY_SNAPSHOT);
  }

  @Override
  @MustBeClosed
  public HotUpdater hotUpdater() {
    return new V3Updater(db);
  }

  @Override
  @MustBeClosed
  public FinalizedUpdater finalizedUpdater() {
    return new V3Updater(db);
  }

  @Override
  @MustBeClosed
  public Eth1Updater eth1Updater() {
    return new V3Updater(db);
  }

  @Override
  @MustBeClosed
  public ProtoArrayUpdater protoArrayUpdater() {
    return new V3Updater(db);
  }

  @Override
  public void close() throws Exception {
    db.close();
  }

  private static class V3Updater
      implements HotUpdater, FinalizedUpdater, Eth1Updater, ProtoArrayUpdater {

    private final RocksDbTransaction transaction;

    V3Updater(final RocksDbAccessor db) {
      this.transaction = db.startTransaction();
    }

    @Override
    public void setGenesisTime(final UInt64 genesisTime) {
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
    public void addHotBlock(final SignedBeaconBlock block) {
      final Bytes32 blockRoot = block.getRoot();
      transaction.put(V3Schema.HOT_BLOCKS_BY_ROOT, blockRoot, block);
    }

    @Override
    public void addHotState(final Bytes32 blockRoot, final BeaconState state) {
      // No-op for this version
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
    public void addFinalizedStateRoot(final Bytes32 stateRoot, final UInt64 slot) {
      transaction.put(V3Schema.SLOTS_BY_FINALIZED_STATE_ROOT, stateRoot, slot);
    }

    @Override
    public void addHotStateRoots(
        final Map<Bytes32, SlotAndBlockRoot> stateRootToSlotAndBlockRootMap) {
      stateRootToSlotAndBlockRootMap.forEach(
          (stateRoot, slotAndBlockRoot) ->
              transaction.put(
                  V3Schema.STATE_ROOT_TO_SLOT_AND_BLOCK_ROOT, stateRoot, slotAndBlockRoot));
    }

    @Override
    public void pruneHotStateRoots(final List<Bytes32> stateRoots) {
      stateRoots.stream()
          .forEach((root) -> transaction.delete(V3Schema.STATE_ROOT_TO_SLOT_AND_BLOCK_ROOT, root));
    }

    @Override
    public void addVotes(final Map<UInt64, VoteTracker> votes) {
      votes.forEach(
          (validatorIndex, vote) -> transaction.put(V3Schema.VOTES, validatorIndex, vote));
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
    public void putProtoArraySnapshot(ProtoArraySnapshot newProtoArray) {
      transaction.put(V3Schema.PROTO_ARRAY_SNAPSHOT, newProtoArray);
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
