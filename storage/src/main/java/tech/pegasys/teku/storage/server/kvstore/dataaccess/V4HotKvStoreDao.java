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

package tech.pegasys.teku.storage.server.kvstore.dataaccess;

import com.google.common.base.Preconditions;
import com.google.errorprone.annotations.MustBeClosed;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ethereum.pow.api.DepositsFromBlockEvent;
import tech.pegasys.teku.ethereum.pow.api.MinGenesisTimeBlockEvent;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.BlockAndCheckpointEpochs;
import tech.pegasys.teku.spec.datastructures.blocks.CheckpointEpochs;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.storage.server.kvstore.ColumnEntry;
import tech.pegasys.teku.storage.server.kvstore.KvStoreAccessor;
import tech.pegasys.teku.storage.server.kvstore.KvStoreAccessor.KvStoreTransaction;
import tech.pegasys.teku.storage.server.kvstore.schema.KvStoreColumn;
import tech.pegasys.teku.storage.server.kvstore.schema.KvStoreVariable;
import tech.pegasys.teku.storage.server.kvstore.schema.SchemaHot;

public class V4HotKvStoreDao implements KvStoreHotDao, KvStoreEth1Dao {
  // Persistent data
  private final KvStoreAccessor db;
  private final SchemaHot schema;

  public V4HotKvStoreDao(final KvStoreAccessor db, final SchemaHot schema) {
    this.db = db;
    this.schema = schema;
  }

  @Override
  public Optional<UInt64> getGenesisTime() {
    return db.get(schema.getVariableGenesisTime());
  }

  @Override
  public Optional<Checkpoint> getAnchor() {
    return db.get(schema.getVariableAnchorCheckpoint());
  }

  @Override
  public Optional<Checkpoint> getJustifiedCheckpoint() {
    return db.get(schema.getVariableJustifiedCheckpoint());
  }

  @Override
  public Optional<Checkpoint> getBestJustifiedCheckpoint() {
    return db.get(schema.getVariableBestJustifiedCheckpoint());
  }

  @Override
  public Optional<Checkpoint> getFinalizedCheckpoint() {
    return db.get(schema.getVariableFinalizedCheckpoint());
  }

  @Override
  public Optional<SignedBeaconBlock> getHotBlock(final Bytes32 root) {
    return db.get(schema.getColumnHotBlocksByRoot(), root);
  }

  @Override
  public Optional<CheckpointEpochs> getHotBlockCheckpointEpochs(final Bytes32 root) {
    return db.get(schema.getColumnHotBlockCheckpointEpochsByRoot(), root);
  }

  @Override
  public Optional<BeaconState> getHotState(final Bytes32 root) {
    return db.get(schema.getColumnHotStatesByRoot(), root);
  }

  @Override
  @MustBeClosed
  public Stream<SignedBeaconBlock> streamHotBlocks() {
    return db.stream(schema.getColumnHotBlocksByRoot()).map(ColumnEntry::getValue);
  }

  @Override
  public Optional<BeaconState> getLatestFinalizedState() {
    return db.get(schema.getVariableLatestFinalizedState());
  }

  @Override
  public Optional<Checkpoint> getWeakSubjectivityCheckpoint() {
    return db.get(schema.getVariableWeakSubjectivityCheckpoint());
  }

  @Override
  public List<Bytes32> getStateRootsBeforeSlot(final UInt64 slot) {
    try (Stream<ColumnEntry<Bytes32, SlotAndBlockRoot>> stream =
        db.stream(schema.getColumnStateRootToSlotAndBlockRoot())) {
      return stream
          .filter((column) -> column.getValue().getSlot().compareTo(slot) < 0)
          .map(ColumnEntry::getKey)
          .collect(Collectors.toList());
    }
  }

  @Override
  public Optional<SlotAndBlockRoot> getSlotAndBlockRootFromStateRoot(final Bytes32 stateRoot) {
    return db.get(schema.getColumnStateRootToSlotAndBlockRoot(), stateRoot);
  }

  @Override
  public Map<UInt64, VoteTracker> getVotes() {
    return db.getAll(schema.getColumnVotes());
  }

  @Override
  @MustBeClosed
  public Stream<DepositsFromBlockEvent> streamDepositsFromBlocks() {
    return db.stream(schema.getColumnDepositsFromBlockEvents()).map(ColumnEntry::getValue);
  }

  @Override
  public Optional<MinGenesisTimeBlockEvent> getMinGenesisTimeBlock() {
    return db.get(schema.getVariableMinGenesisTimeBlock());
  }

  @Override
  @MustBeClosed
  public HotUpdater hotUpdater() {
    return new V4HotUpdater(db, schema);
  }

  @Override
  public void ingest(
      final KvStoreHotDao hotDao, final int batchSize, final Consumer<String> logger) {
    Preconditions.checkArgument(batchSize > 0, "Batch size must be at least 1 (MB)");
    Preconditions.checkArgument(
        hotDao instanceof V4HotKvStoreDao, "Expected instance of V4HotKvStoreDao");
    final V4HotKvStoreDao dao = (V4HotKvStoreDao) hotDao;

    final Map<String, KvStoreVariable<?>> newVariables = schema.getVariableMap();
    if (newVariables.size() > 0) {
      final Map<String, KvStoreVariable<?>> oldVariables = dao.schema.getVariableMap();
      try (final KvStoreTransaction transaction = db.startTransaction()) {
        for (String key : newVariables.keySet()) {
          logger.accept(String.format("Copy variable %s", key));
          dao.getRawVariable(oldVariables.get(key))
              .ifPresent(value -> transaction.putRaw(newVariables.get(key), value));
        }
        transaction.commit();
      }
    } else {
      logger.accept("No variables to copy from hot store.");
    }
    final Map<String, KvStoreColumn<?, ?>> newColumns = schema.getColumnMap();
    if (newColumns.size() > 0) {
      final Map<String, KvStoreColumn<?, ?>> oldColumns = dao.schema.getColumnMap();
      for (String key : newColumns.keySet()) {
        logger.accept(String.format("copy column %s", key));
        try (final Stream<ColumnEntry<Bytes, Bytes>> oldEntryStream =
                dao.streamRawColumn(oldColumns.get(key));
            BatchWriter batchWriter = new BatchWriter(batchSize, logger, db)) {
          oldEntryStream.forEach(entry -> batchWriter.add(newColumns.get(key), entry));
        }
      }
    } else {
      logger.accept("No column data to copy from hot store.");
    }
  }

  private <T> Optional<Bytes> getRawVariable(final KvStoreVariable<T> var) {
    return db.getRaw(var);
  }

  @MustBeClosed
  private <K, V> Stream<ColumnEntry<Bytes, Bytes>> streamRawColumn(
      final KvStoreColumn<K, V> kvStoreColumn) {
    return db.streamRaw(kvStoreColumn);
  }

  @Override
  @MustBeClosed
  public Eth1Updater eth1Updater() {
    return new V4HotUpdater(db, schema);
  }

  @Override
  public void close() throws Exception {
    db.close();
  }

  static class V4HotUpdater implements HotUpdater, Eth1Updater {

    private final KvStoreTransaction transaction;
    private final SchemaHot schema;

    KvStoreTransaction getTransaction() {
      return transaction;
    }

    V4HotUpdater(final KvStoreAccessor db, final SchemaHot schema) {
      this.transaction = db.startTransaction();
      this.schema = schema;
    }

    @Override
    public void setGenesisTime(final UInt64 genesisTime) {
      transaction.put(schema.getVariableGenesisTime(), genesisTime);
    }

    @Override
    public void setAnchor(final Checkpoint anchor) {
      transaction.put(schema.getVariableAnchorCheckpoint(), anchor);
    }

    @Override
    public void setJustifiedCheckpoint(final Checkpoint checkpoint) {
      transaction.put(schema.getVariableJustifiedCheckpoint(), checkpoint);
    }

    @Override
    public void setBestJustifiedCheckpoint(final Checkpoint checkpoint) {
      transaction.put(schema.getVariableBestJustifiedCheckpoint(), checkpoint);
    }

    @Override
    public void setFinalizedCheckpoint(final Checkpoint checkpoint) {
      transaction.put(schema.getVariableFinalizedCheckpoint(), checkpoint);
    }

    @Override
    public void setWeakSubjectivityCheckpoint(Checkpoint checkpoint) {
      transaction.put(schema.getVariableWeakSubjectivityCheckpoint(), checkpoint);
    }

    @Override
    public void clearWeakSubjectivityCheckpoint() {
      transaction.delete(schema.getVariableWeakSubjectivityCheckpoint());
    }

    @Override
    public void setLatestFinalizedState(final BeaconState state) {
      transaction.put(schema.getVariableLatestFinalizedState(), state);
    }

    @Override
    public void addHotBlock(final BlockAndCheckpointEpochs block) {
      final Bytes32 blockRoot = block.getRoot();
      transaction.put(schema.getColumnHotBlocksByRoot(), blockRoot, block.getBlock());
      addHotBlockCheckpointEpochs(blockRoot, block.getCheckpointEpochs());
    }

    @Override
    public void addHotBlockCheckpointEpochs(
        final Bytes32 blockRoot, final CheckpointEpochs checkpointEpochs) {
      transaction.put(
          schema.getColumnHotBlockCheckpointEpochsByRoot(), blockRoot, checkpointEpochs);
    }

    @Override
    public void addHotState(final Bytes32 blockRoot, final BeaconState state) {
      transaction.put(schema.getColumnHotStatesByRoot(), blockRoot, state);
    }

    @Override
    public void addHotStateRoots(
        final Map<Bytes32, SlotAndBlockRoot> stateRootToSlotAndBlockRootMap) {
      stateRootToSlotAndBlockRootMap.forEach(
          (stateRoot, slotAndBlockRoot) ->
              transaction.put(
                  schema.getColumnStateRootToSlotAndBlockRoot(), stateRoot, slotAndBlockRoot));
    }

    @Override
    public void pruneHotStateRoots(final List<Bytes32> stateRoots) {
      stateRoots.forEach(
          (root) -> transaction.delete(schema.getColumnStateRootToSlotAndBlockRoot(), root));
    }

    @Override
    public void addVotes(final Map<UInt64, VoteTracker> votes) {
      votes.forEach(
          (validatorIndex, vote) -> transaction.put(schema.getColumnVotes(), validatorIndex, vote));
    }

    @Override
    public void deleteHotBlock(final Bytes32 blockRoot) {
      transaction.delete(schema.getColumnHotBlocksByRoot(), blockRoot);
      transaction.delete(schema.getColumnHotBlockCheckpointEpochsByRoot(), blockRoot);
      deleteHotState(blockRoot);
    }

    @Override
    public void deleteHotState(final Bytes32 blockRoot) {
      transaction.delete(schema.getColumnHotStatesByRoot(), blockRoot);
    }

    @Override
    public void addMinGenesisTimeBlock(final MinGenesisTimeBlockEvent event) {
      transaction.put(schema.getVariableMinGenesisTimeBlock(), event);
    }

    @Override
    public void addDepositsFromBlockEvent(final DepositsFromBlockEvent event) {
      transaction.put(schema.getColumnDepositsFromBlockEvents(), event.getBlockNumber(), event);
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
