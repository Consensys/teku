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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.errorprone.annotations.MustBeClosed;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.collections.LimitedSet;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateSchema;
import tech.pegasys.teku.ssz.schema.SszSchema.BackingNodeSource;
import tech.pegasys.teku.ssz.tree.BranchNode;
import tech.pegasys.teku.ssz.tree.LeafDataNode;
import tech.pegasys.teku.ssz.tree.TreeNode;
import tech.pegasys.teku.ssz.tree.TreeUtil;
import tech.pegasys.teku.storage.server.kvstore.ColumnEntry;
import tech.pegasys.teku.storage.server.kvstore.KvStoreAccessor;
import tech.pegasys.teku.storage.server.kvstore.KvStoreAccessor.KvStoreTransaction;
import tech.pegasys.teku.storage.server.kvstore.schema.KvStoreColumn;
import tech.pegasys.teku.storage.server.kvstore.schema.KvStoreVariable;
import tech.pegasys.teku.storage.server.kvstore.schema.SchemaFinalized;

public class V4FinalizedKvStoreDao implements KvStoreFinalizedDao {

  private final Spec spec;
  private final KvStoreAccessor db;
  private final SchemaFinalized schema;

  private final Set<Bytes32> knownStoredMerkleLeaves = LimitedSet.create(100_000);
  private final Set<Bytes32> knownStoredMerkleBranches = LimitedSet.create(100_000);

  public V4FinalizedKvStoreDao(
      final Spec spec,
      final KvStoreAccessor db,
      final SchemaFinalized schema,
      @SuppressWarnings("unused") final long stateStorageFrequency) {
    this.spec = spec;
    this.db = db;
    this.schema = schema;
  }

  @Override
  public void close() throws Exception {
    db.close();
  }

  @Override
  public Optional<SignedBeaconBlock> getFinalizedBlockAtSlot(final UInt64 slot) {
    return db.get(schema.getColumnFinalizedBlocksBySlot(), slot);
  }

  @Override
  public Optional<UInt64> getEarliestFinalizedBlockSlot() {
    return db.getFirstEntry(schema.getColumnFinalizedBlocksBySlot()).map(ColumnEntry::getKey);
  }

  @Override
  public Optional<SignedBeaconBlock> getEarliestFinalizedBlock() {
    return db.getFirstEntry(schema.getColumnFinalizedBlocksBySlot()).map(ColumnEntry::getValue);
  }

  @Override
  public Optional<SignedBeaconBlock> getLatestFinalizedBlockAtSlot(final UInt64 slot) {
    return db.getFloorEntry(schema.getColumnFinalizedBlocksBySlot(), slot)
        .map(ColumnEntry::getValue);
  }

  @Override
  public Set<SignedBeaconBlock> getNonCanonicalBlocksAtSlot(final UInt64 slot) {
    Optional<Set<Bytes32>> maybeRoots = db.get(schema.getColumnNonCanonicalRootsBySlot(), slot);
    return maybeRoots.stream()
        .flatMap(Collection::stream)
        .flatMap(root -> db.get(schema.getColumnNonCanonicalBlocksByRoot(), root).stream())
        .collect(Collectors.toSet());
  }

  @Override
  public Optional<BeaconState> getLatestAvailableFinalizedState(final UInt64 maxSlot) {
    return db.getFloorEntry(schema.getColumnFinalizedStateRootsBySlot(), maxSlot)
        .map(ColumnEntry::getValue)
        .map(this::recreateState);
  }

  private BeaconState recreateState(final Bytes32 stateRoot) {
    final UInt64 slot = getSlotForFinalizedStateRoot(stateRoot).orElseThrow();
    final BeaconStateSchema<?, ?> stateSchema =
        spec.atSlot(slot).getSchemaDefinitions().getBeaconStateSchema();
    final TreeNode backingTree =
        stateSchema.loadBackingNodes(
            new BackingNodeSource() {
              @Override
              public Pair<Bytes32, Bytes32> getBranchData(final Bytes32 root) {
                return db.get(schema.getColumnFinalizedStateMerkleTrieBranches(), root)
                    .map(
                        data -> {
                          checkArgument(
                              data.size() == Bytes32.SIZE * 2,
                              "Branch data was too short %s",
                              data);
                          return Pair.of(
                              Bytes32.wrap(data.slice(0, Bytes32.SIZE)),
                              Bytes32.wrap(data.slice(Bytes32.SIZE)));
                        })
                    .orElseThrow(
                        () -> new IllegalStateException("Missing branch data for root " + root));
              }

              @Override
              public Bytes getLeafData(final Bytes32 root) {
                return db.get(schema.getColumnFinalizedStateMerkleTrieLeaves(), root)
                    .orElseThrow(
                        () -> new IllegalStateException("Missing leaf data for root " + root));
              }
            },
            stateRoot);
    return stateSchema.createFromBackingNode(backingTree);
  }

  @Override
  @MustBeClosed
  public Stream<SignedBeaconBlock> streamFinalizedBlocks(
      final UInt64 startSlot, final UInt64 endSlot) {
    return db.stream(schema.getColumnFinalizedBlocksBySlot(), startSlot, endSlot)
        .map(ColumnEntry::getValue);
  }

  @Override
  public Optional<UInt64> getSlotForFinalizedBlockRoot(final Bytes32 blockRoot) {
    return db.get(schema.getColumnSlotsByFinalizedRoot(), blockRoot);
  }

  @Override
  public Optional<UInt64> getSlotForFinalizedStateRoot(final Bytes32 stateRoot) {
    return db.get(schema.getColumnSlotsByFinalizedStateRoot(), stateRoot);
  }

  @Override
  public Optional<SlotAndBlockRoot> getSlotAndBlockRootForFinalizedStateRoot(
      final Bytes32 stateRoot) {
    Optional<UInt64> maybeSlot = db.get(schema.getColumnSlotsByFinalizedStateRoot(), stateRoot);
    return maybeSlot.flatMap(
        slot ->
            getFinalizedBlockAtSlot(slot)
                .map(block -> new SlotAndBlockRoot(slot, block.getRoot())));
  }

  @Override
  public Optional<? extends SignedBeaconBlock> getNonCanonicalBlock(final Bytes32 root) {
    return db.get(schema.getColumnNonCanonicalBlocksByRoot(), root);
  }

  @Override
  public void ingest(
      final KvStoreFinalizedDao finalizedDao, final int batchSize, final Consumer<String> logger) {
    checkArgument(batchSize > 1, "Batch size must be greater than 1 element");
    checkArgument(
        finalizedDao instanceof V4FinalizedKvStoreDao,
        "Expected instance of V4FinalizedKvStoreDao");
    final V4FinalizedKvStoreDao dao = (V4FinalizedKvStoreDao) finalizedDao;

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
    }
    final Map<String, KvStoreColumn<?, ?>> newColumns = schema.getColumnMap();
    if (newColumns.size() > 0) {
      final Map<String, KvStoreColumn<?, ?>> oldColumns = dao.schema.getColumnMap();
      for (String key : newColumns.keySet()) {
        logger.accept(String.format("Copy column %s", key));
        try (final Stream<ColumnEntry<Bytes, Bytes>> oldEntryStream =
                dao.streamRawColumn(oldColumns.get(key));
            BatchWriter batchWriter = new BatchWriter(batchSize, logger, db)) {
          oldEntryStream.forEach(entry -> batchWriter.add(newColumns.get(key), entry));
        }
      }
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
  public Optional<SignedBeaconBlock> getFinalizedBlock(final Bytes32 root) {
    return db.get(schema.getColumnSlotsByFinalizedRoot(), root)
        .flatMap(this::getFinalizedBlockAtSlot);
  }

  @Override
  @MustBeClosed
  public FinalizedUpdater finalizedUpdater() {
    return new V4FinalizedKvStoreDao.V4FinalizedUpdater(
        db, schema, knownStoredMerkleLeaves, knownStoredMerkleBranches);
  }

  static class V4FinalizedUpdater implements FinalizedUpdater {
    private final KvStoreTransaction transaction;
    private final KvStoreAccessor db;
    private final SchemaFinalized schema;
    private final Set<Bytes32> previouslyStoredMerkleLeaves;
    private final Set<Bytes32> previouslyStoredMerkleBranches;
    private final Set<Bytes32> newlyStoredMerkleLeaves = new HashSet<>();
    private final Set<Bytes32> newlyStoredMerkleBranches = new HashSet<>();

    V4FinalizedUpdater(
        final KvStoreAccessor db,
        final SchemaFinalized schema,
        final Set<Bytes32> previouslyStoredMerkleLeaves,
        final Set<Bytes32> previouslyStoredMerkleBranches) {
      this.transaction = db.startTransaction();
      this.db = db;
      this.schema = schema;
      this.previouslyStoredMerkleLeaves = previouslyStoredMerkleLeaves;
      this.previouslyStoredMerkleBranches = previouslyStoredMerkleBranches;
    }

    @Override
    public void addFinalizedBlock(final SignedBeaconBlock block) {
      transaction.put(schema.getColumnSlotsByFinalizedRoot(), block.getRoot(), block.getSlot());
      transaction.put(schema.getColumnFinalizedBlocksBySlot(), block.getSlot(), block);
    }

    @Override
    public void addNonCanonicalBlock(final SignedBeaconBlock block) {
      transaction.put(schema.getColumnNonCanonicalBlocksByRoot(), block.getRoot(), block);
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
      transaction.put(
          schema.getColumnFinalizedStateRootsBySlot(), state.getSlot(), state.hashTreeRoot());
      TreeUtil.iterateNonZero(
          state.getBackingNode(),
          n -> {
            final Bytes32 root = n.hashTreeRoot();
            if (n instanceof LeafDataNode) {
              if (previouslyStoredMerkleLeaves.contains(root)
                  || newlyStoredMerkleLeaves.contains(root)) {
                // Already stored
                return;
              }
              newlyStoredMerkleLeaves.add(root);
              transaction.put(
                  schema.getColumnFinalizedStateMerkleTrieLeaves(),
                  root,
                  ((LeafDataNode) n).getData());
            } else if (n instanceof BranchNode) {
              if (previouslyStoredMerkleBranches.contains(root)
                  || newlyStoredMerkleBranches.contains(root)) {
                // Already stored
                // TODO: Find a way to skip all nodes under this one as they're already stored too
                return;
              }
              newlyStoredMerkleBranches.add(root);
              final BranchNode node = (BranchNode) n;
              transaction.put(
                  schema.getColumnFinalizedStateMerkleTrieBranches(),
                  root,
                  Bytes.wrap(node.left().hashTreeRoot(), node.right().hashTreeRoot()));
            } else {
              throw new IllegalArgumentException("Unknown node type: " + n.getClass());
            }
          });
    }

    @Override
    public void addFinalizedStateRoot(final Bytes32 stateRoot, final UInt64 slot) {
      transaction.put(schema.getColumnSlotsByFinalizedStateRoot(), stateRoot, slot);
    }

    @Override
    public void commit() {
      // Commit db updates
      transaction.commit();
      previouslyStoredMerkleLeaves.addAll(newlyStoredMerkleLeaves);
      previouslyStoredMerkleBranches.addAll(newlyStoredMerkleBranches);
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
