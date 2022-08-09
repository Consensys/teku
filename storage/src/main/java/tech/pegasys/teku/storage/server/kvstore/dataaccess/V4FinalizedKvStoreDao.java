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
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.storage.server.kvstore.ColumnEntry;
import tech.pegasys.teku.storage.server.kvstore.KvStoreAccessor;
import tech.pegasys.teku.storage.server.kvstore.KvStoreAccessor.KvStoreTransaction;
import tech.pegasys.teku.storage.server.kvstore.dataaccess.KvStoreCombinedDaoBlinded.FinalizedUpdaterBlinded;
import tech.pegasys.teku.storage.server.kvstore.dataaccess.KvStoreCombinedDaoUnblinded.FinalizedUpdaterUnblinded;
import tech.pegasys.teku.storage.server.kvstore.schema.KvStoreColumn;
import tech.pegasys.teku.storage.server.kvstore.schema.KvStoreVariable;
import tech.pegasys.teku.storage.server.kvstore.schema.SchemaFinalizedSnapshotStateAdapter;

public class V4FinalizedKvStoreDao {
  private final KvStoreAccessor db;
  private final SchemaFinalizedSnapshotStateAdapter schema;
  private final V4FinalizedStateStorageLogic<SchemaFinalizedSnapshotStateAdapter> stateStorageLogic;

  public V4FinalizedKvStoreDao(
      final KvStoreAccessor db,
      final SchemaFinalizedSnapshotStateAdapter schema,
      final V4FinalizedStateStorageLogic<SchemaFinalizedSnapshotStateAdapter> stateStorageLogic) {
    this.db = db;
    this.schema = schema;
    this.stateStorageLogic = stateStorageLogic;
  }

  public void close() throws Exception {
    db.close();
  }

  public Optional<SignedBeaconBlock> getFinalizedBlockAtSlot(final UInt64 slot) {
    return db.get(schema.getColumnFinalizedBlocksBySlot(), slot);
  }

  public Optional<UInt64> getEarliestFinalizedBlockSlot() {
    return db.getFirstEntry(schema.getColumnFinalizedBlocksBySlot()).map(ColumnEntry::getKey);
  }

  public Optional<SignedBeaconBlock> getEarliestFinalizedBlock() {
    return db.getFirstEntry(schema.getColumnFinalizedBlocksBySlot()).map(ColumnEntry::getValue);
  }

  public Optional<SignedBeaconBlock> getLatestFinalizedBlockAtSlot(final UInt64 slot) {
    return db.getFloorEntry(schema.getColumnFinalizedBlocksBySlot(), slot)
        .map(ColumnEntry::getValue);
  }

  public List<SignedBeaconBlock> getNonCanonicalBlocksAtSlot(final UInt64 slot) {
    Optional<Set<Bytes32>> maybeRoots = db.get(schema.getColumnNonCanonicalRootsBySlot(), slot);
    return maybeRoots.stream()
        .flatMap(Collection::stream)
        .flatMap(root -> db.get(schema.getColumnNonCanonicalBlocksByRoot(), root).stream())
        .collect(Collectors.toList());
  }

  public List<SignedBeaconBlock> getBlindedNonCanonicalBlocksAtSlot(final UInt64 slot) {
    final Optional<Set<Bytes32>> maybeRoots =
        db.get(schema.getColumnNonCanonicalRootsBySlot(), slot);
    return maybeRoots.stream()
        .flatMap(Collection::stream)
        .flatMap(root -> db.get(schema.getColumnBlindedBlocksByRoot(), root).stream())
        .collect(Collectors.toList());
  }

  @MustBeClosed
  public Stream<Map.Entry<Bytes, Bytes>> streamUnblindedFinalizedBlocksRaw() {
    return db.streamRaw(schema.getColumnFinalizedBlocksBySlot()).map(entry -> entry);
  }

  public Optional<BeaconState> getLatestAvailableFinalizedState(final UInt64 maxSlot) {
    return stateStorageLogic.getLatestAvailableFinalizedState(db, schema, maxSlot);
  }

  public long countNonCanonicalSlots() {
    return db.size(schema.getColumnNonCanonicalRootsBySlot());
  }

  public long countBlindedBlocks() {
    return db.size(schema.getColumnBlindedBlocksByRoot());
  }

  @MustBeClosed
  public Stream<SignedBeaconBlock> streamFinalizedBlocks(
      final UInt64 startSlot, final UInt64 endSlot) {
    return db.stream(schema.getColumnFinalizedBlocksBySlot(), startSlot, endSlot)
        .map(ColumnEntry::getValue);
  }

  public Optional<SignedBeaconBlock> getBlindedBlock(final Bytes32 root) {
    return db.get(schema.getColumnBlindedBlocksByRoot(), root);
  }

  public Optional<Bytes> getExecutionPayload(final Bytes32 root) {
    return db.get(schema.getColumnExecutionPayloadByBlockRoot(), root);
  }

  public Optional<UInt64> getEarliestBlindedBlockSlot() {
    return db.getFirstEntry(schema.getColumnFinalizedBlockRootBySlot()).map(ColumnEntry::getKey);
  }

  public Optional<SignedBeaconBlock> getEarliestBlindedBlock() {
    final Optional<Bytes32> maybeRoot =
        db.getFirstEntry(schema.getColumnFinalizedBlockRootBySlot()).map(ColumnEntry::getValue);
    return maybeRoot.flatMap(root -> db.get(schema.getColumnBlindedBlocksByRoot(), root));
  }

  public Optional<SignedBeaconBlock> getLatestBlindedBlockAtSlot(final UInt64 slot) {
    final Optional<Bytes32> maybeRoot =
        db.getFloorEntry(schema.getColumnFinalizedBlockRootBySlot(), slot)
            .map(ColumnEntry::getValue);
    return maybeRoot.flatMap(root -> db.get(schema.getColumnBlindedBlocksByRoot(), root));
  }

  public Optional<UInt64> getSlotForFinalizedBlockRoot(final Bytes32 blockRoot) {
    return db.get(schema.getColumnSlotsByFinalizedRoot(), blockRoot);
  }

  public Optional<UInt64> getSlotForFinalizedStateRoot(final Bytes32 stateRoot) {
    return db.get(schema.getColumnSlotsByFinalizedStateRoot(), stateRoot);
  }

  public Optional<SlotAndBlockRoot> getSlotAndBlockRootForFinalizedStateRoot(
      final Bytes32 stateRoot) {
    Optional<UInt64> maybeSlot = db.get(schema.getColumnSlotsByFinalizedStateRoot(), stateRoot);
    return maybeSlot.flatMap(
        slot ->
            getFinalizedBlockAtSlot(slot)
                .map(block -> new SlotAndBlockRoot(slot, block.getRoot())));
  }

  public Optional<UInt64> getOptimisticTransitionBlockSlot() {
    return db.get(schema.getOptimisticTransitionBlockSlot());
  }

  public Optional<? extends SignedBeaconBlock> getNonCanonicalBlock(final Bytes32 root) {
    return db.get(schema.getColumnNonCanonicalBlocksByRoot(), root);
  }

  public <T> Optional<Bytes> getRawVariable(final KvStoreVariable<T> var) {
    return db.getRaw(var);
  }

  @MustBeClosed
  public <K, V> Stream<ColumnEntry<Bytes, Bytes>> streamRawColumn(
      final KvStoreColumn<K, V> kvStoreColumn) {
    return db.streamRaw(kvStoreColumn);
  }

  public Optional<SignedBeaconBlock> getFinalizedBlock(final Bytes32 root) {
    return db.get(schema.getColumnSlotsByFinalizedRoot(), root)
        .flatMap(this::getFinalizedBlockAtSlot);
  }

  @MustBeClosed
  public V4FinalizedUpdater finalizedUpdater() {
    return new V4FinalizedKvStoreDao.V4FinalizedUpdater(db, schema, stateStorageLogic.updater());
  }

  public Map<String, KvStoreColumn<?, ?>> getColumnMap() {
    return schema.getColumnMap();
  }

  public Map<String, KvStoreVariable<?>> getVariableMap() {
    return schema.getVariableMap();
  }

  public Optional<Bytes32> getFinalizedBlockRootAtSlot(final UInt64 slot) {
    return db.get(schema.getColumnFinalizedBlockRootBySlot(), slot);
  }

  @MustBeClosed
  public Stream<Bytes32> streamFinalizedBlockRoots(final UInt64 startSlot, final UInt64 endSlot) {
    return db.stream(schema.getColumnFinalizedBlockRootBySlot(), startSlot, endSlot)
        .map(ColumnEntry::getValue);
  }

  public Set<Bytes32> getNonCanonicalBlockRootsAtSlot(final UInt64 slot) {
    return db.get(schema.getColumnNonCanonicalRootsBySlot(), slot).orElseGet(HashSet::new);
  }

  public <V, K> Optional<Bytes> getRaw(final KvStoreColumn<K, V> kvStoreColumn, final K key) {
    return db.getRaw(kvStoreColumn, key);
  }

  public Map<String, Long> getColumnCounts() {
    final Map<String, Long> columnCounts = new HashMap<>();
    schema.getColumnMap().forEach((k, v) -> columnCounts.put(k, db.size(v)));
    return columnCounts;
  }

  @MustBeClosed
  public Stream<Map.Entry<Bytes32, SignedBeaconBlock>> streamUnblindedNonCanonicalBlocks() {
    return db.stream(schema.getColumnNonCanonicalBlocksByRoot()).map(entry -> entry);
  }

  @MustBeClosed
  public Stream<Map.Entry<Bytes32, UInt64>> streamUnblindedFinalizedBlockRoots() {
    return db.stream(schema.getColumnSlotsByFinalizedRoot()).map(entry -> entry);
  }

  @MustBeClosed
  public Stream<UInt64> streamFinalizedStateSlots(final UInt64 startSlot, final UInt64 endSlot) {
    return db.stream(schema.getColumnFinalizedStatesBySlot(), startSlot, endSlot)
        .map(ColumnEntry::getKey);
  }

  @MustBeClosed
  public Stream<SignedBeaconBlock> streamBlindedBlocks() {
    return db.stream(schema.getColumnBlindedBlocksByRoot()).map(ColumnEntry::getValue);
  }

  public Optional<Bytes> getBlindedBlockAsSsz(final Bytes32 blockRoot) {
    return db.getRaw(schema.getColumnBlindedBlocksByRoot(), blockRoot);
  }

  static class V4FinalizedUpdater implements FinalizedUpdaterBlinded, FinalizedUpdaterUnblinded {
    private final KvStoreTransaction transaction;
    private final KvStoreAccessor db;
    private final SchemaFinalizedSnapshotStateAdapter schema;
    private final V4FinalizedStateStorageLogic.FinalizedStateUpdater<
            SchemaFinalizedSnapshotStateAdapter>
        stateStorageUpdater;

    V4FinalizedUpdater(
        final KvStoreAccessor db,
        final SchemaFinalizedSnapshotStateAdapter schema,
        final V4FinalizedStateStorageLogic.FinalizedStateUpdater<
                SchemaFinalizedSnapshotStateAdapter>
            stateStorageUpdater) {
      this.transaction = db.startTransaction();
      this.db = db;
      this.schema = schema;
      this.stateStorageUpdater = stateStorageUpdater;
    }

    @Override
    public void addFinalizedBlock(final SignedBeaconBlock block) {
      transaction.put(schema.getColumnSlotsByFinalizedRoot(), block.getRoot(), block.getSlot());
      transaction.put(schema.getColumnFinalizedBlocksBySlot(), block.getSlot(), block);
    }

    @Override
    public void addFinalizedBlockRootBySlot(final UInt64 slot, final Bytes32 root) {
      transaction.put(schema.getColumnFinalizedBlockRootBySlot(), slot, root);
    }

    @Override
    public void addBlindedFinalizedBlock(
        final SignedBeaconBlock block, final Bytes32 root, final Spec spec) {
      addBlindedBlock(block, root, spec);
      addFinalizedBlockRootBySlot(block.getSlot(), root);
    }

    @Override
    public void addBlindedFinalizedBlockRaw(
        final Bytes blockBytes, final Bytes32 root, final UInt64 slot) {
      transaction.putRaw(schema.getColumnBlindedBlocksByRoot(), root, blockBytes);
      addFinalizedBlockRootBySlot(slot, root);
    }

    @Override
    public void addBlindedBlock(
        final SignedBeaconBlock block, final Bytes32 blockRoot, final Spec spec) {
      transaction.put(
          schema.getColumnBlindedBlocksByRoot(),
          blockRoot,
          block.blind(spec.atSlot(block.getSlot()).getSchemaDefinitions()));
      final Optional<ExecutionPayload> maybePayload =
          block.getMessage().getBody().getOptionalExecutionPayload();
      maybePayload.ifPresent(payload -> addExecutionPayload(blockRoot, payload));
    }

    @Override
    public void addExecutionPayload(final Bytes32 blockRoot, final ExecutionPayload payload) {
      transaction.put(
          schema.getColumnExecutionPayloadByBlockRoot(), blockRoot, payload.sszSerialize());
    }

    @Override
    public void deleteBlindedBlock(final Bytes32 root) {
      final Optional<SignedBeaconBlock> maybeBlock =
          db.get(schema.getColumnBlindedBlocksByRoot(), root);
      maybeBlock.ifPresent(
          block -> {
            transaction.delete(schema.getColumnBlindedBlocksByRoot(), root);
            Optional<ExecutionPayloadHeader> maybeHeader =
                block.getMessage().getBody().getOptionalExecutionPayloadHeader();
            maybeHeader.ifPresent(header -> deleteExecutionPayload(header.hashTreeRoot()));
          });
    }

    @Override
    public void deleteExecutionPayload(final Bytes32 blockRoot) {
      transaction.delete(schema.getColumnExecutionPayloadByBlockRoot(), blockRoot);
    }

    @Override
    public void addNonCanonicalBlock(final SignedBeaconBlock block) {
      transaction.put(schema.getColumnNonCanonicalBlocksByRoot(), block.getRoot(), block);
    }

    @Override
    public void deleteUnblindedFinalizedBlock(final UInt64 slot, final Bytes32 blockRoot) {
      transaction.delete(schema.getColumnFinalizedBlocksBySlot(), slot);
      transaction.delete(schema.getColumnSlotsByFinalizedRoot(), blockRoot);
    }

    @Override
    public void deleteUnblindedNonCanonicalBlockOnly(final Bytes32 blockRoot) {
      transaction.delete(schema.getColumnNonCanonicalBlocksByRoot(), blockRoot);
    }

    @Override
    public void addNonCanonicalRootAtSlot(final UInt64 slot, final Set<Bytes32> blockRoots) {
      Optional<Set<Bytes32>> maybeRoots = db.get(schema.getColumnNonCanonicalRootsBySlot(), slot);
      final Set<Bytes32> roots = maybeRoots.orElse(new HashSet<>());
      if (roots.addAll(blockRoots)) {
        transaction.put(schema.getColumnNonCanonicalRootsBySlot(), slot, roots);
      }
    }

    @Override
    public void addFinalizedState(final Bytes32 blockRoot, final BeaconState state) {
      stateStorageUpdater.addFinalizedState(db, transaction, schema, state);
    }

    @Override
    public void addFinalizedStateRoot(final Bytes32 stateRoot, final UInt64 slot) {
      transaction.put(schema.getColumnSlotsByFinalizedStateRoot(), stateRoot, slot);
    }

    @Override
    public void setOptimisticTransitionBlockSlot(final Optional<UInt64> transitionBlockSlot) {
      if (transitionBlockSlot.isPresent()) {
        transaction.put(schema.getOptimisticTransitionBlockSlot(), transitionBlockSlot.get());
      } else {
        transaction.delete(schema.getOptimisticTransitionBlockSlot());
      }
    }

    @Override
    public void commit() {
      // Commit db updates
      transaction.commit();
      stateStorageUpdater.commit();
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
