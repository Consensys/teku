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

package tech.pegasys.artemis.storage.rocksdb;

import static tech.pegasys.artemis.storage.rocksdb.RocksDbColumn.CHECKPOINT_STATES;
import static tech.pegasys.artemis.storage.rocksdb.RocksDbColumn.DEFAULT;
import static tech.pegasys.artemis.storage.rocksdb.RocksDbColumn.FINALIZED_BLOCKS_BY_ROOT;
import static tech.pegasys.artemis.storage.rocksdb.RocksDbColumn.FINALIZED_ROOTS_BY_SLOT;
import static tech.pegasys.artemis.storage.rocksdb.RocksDbColumn.FINALIZED_STATES_BY_ROOT;
import static tech.pegasys.artemis.storage.rocksdb.RocksDbColumn.HOT_BLOCKS_BY_ROOT;
import static tech.pegasys.artemis.storage.rocksdb.RocksDbColumn.HOT_STATES_BY_ROOT;
import static tech.pegasys.artemis.storage.rocksdb.RocksDbColumn.LATEST_MESSAGES;
import static tech.pegasys.artemis.storage.rocksdb.RocksDbDefaultColumnKey.BEST_JUSTIFIED_CHECKPOINT_KEY;
import static tech.pegasys.artemis.storage.rocksdb.RocksDbDefaultColumnKey.FINALIZED_CHECKPOINT_KEY;
import static tech.pegasys.artemis.storage.rocksdb.RocksDbDefaultColumnKey.GENESIS_TIME_KEY;
import static tech.pegasys.artemis.storage.rocksdb.RocksDbDefaultColumnKey.JUSTIFIED_CHECKPOINT_KEY;

import com.google.common.collect.ImmutableMap;
import com.google.common.primitives.Longs;
import com.google.common.primitives.UnsignedLong;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.rocksdb.BlockBasedTableConfig;
import org.rocksdb.ColumnFamilyDescriptor;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.ColumnFamilyOptions;
import org.rocksdb.DBOptions;
import org.rocksdb.Env;
import org.rocksdb.LRUCache;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.rocksdb.Statistics;
import org.rocksdb.Transaction;
import org.rocksdb.Transaction.TransactionState;
import org.rocksdb.TransactionDB;
import org.rocksdb.TransactionDBOptions;
import org.rocksdb.WriteOptions;
import tech.pegasys.artemis.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.BeaconStateImpl;
import tech.pegasys.artemis.datastructures.state.Checkpoint;
import tech.pegasys.artemis.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.artemis.storage.Database;
import tech.pegasys.artemis.storage.DatabaseStorageException;
import tech.pegasys.artemis.storage.StateStorageMode;
import tech.pegasys.artemis.storage.Store;
import tech.pegasys.artemis.storage.events.diskupdates.StorageUpdate;
import tech.pegasys.artemis.storage.events.diskupdates.StorageUpdateResult;
import tech.pegasys.artemis.util.sos.SimpleOffsetSerializable;

public class RocksDbDatabase implements Database {

  private static final Logger LOG = LogManager.getLogger();

  static {
    RocksDbUtil.loadNativeLibrary();
  }

  private final DBOptions options;
  private final TransactionDBOptions txOptions;
  private final AtomicBoolean closed = new AtomicBoolean(false);
  private final TransactionDB db;
  private final ImmutableMap<RocksDbColumn, ColumnFamilyHandle> columnHandlesByName;
  private final StateStorageMode stateStorageMode;

  // In memory only
  private final ConcurrentNavigableMap<UnsignedLong, Set<Bytes32>> hotRootsBySlotCache =
      new ConcurrentSkipListMap<>();

  public static Database createOnDisk(
      final RocksDbConfiguration configuration, final StateStorageMode stateStorageMode) {
    return new RocksDbDatabase(configuration, stateStorageMode);
  }

  private RocksDbDatabase(
      final RocksDbConfiguration configuration, final StateStorageMode stateStorageMode) {
    this.stateStorageMode = stateStorageMode;
    final Statistics stats = new Statistics();

    options =
        new DBOptions()
            .setCreateIfMissing(true)
            .setMaxOpenFiles(configuration.getMaxOpenFiles())
            .setMaxBackgroundCompactions(configuration.getMaxBackgroundCompactions())
            .setStatistics(stats)
            .setCreateMissingColumnFamilies(true)
            .setEnv(
                Env.getDefault().setBackgroundThreads(configuration.getBackgroundThreadCount()));

    final ColumnFamilyOptions columnFamilyOptions =
        new ColumnFamilyOptions().setTableFormatConfig(createBlockBasedTableConfig(configuration));
    List<ColumnFamilyDescriptor> columnDescriptors =
        EnumSet.allOf(RocksDbColumn.class).stream()
            .map(col -> new ColumnFamilyDescriptor(col.getId(), columnFamilyOptions))
            .collect(Collectors.toList());

    final List<ColumnFamilyHandle> columnHandles = new ArrayList<>(columnDescriptors.size());

    Map<Bytes, RocksDbColumn> columnsById =
        EnumSet.allOf(RocksDbColumn.class).stream()
            .collect(Collectors.toMap(col -> Bytes.wrap(col.getId()), Function.identity()));
    txOptions = new TransactionDBOptions();
    try {
      db =
          TransactionDB.open(
              options,
              txOptions,
              configuration.getDatabaseDir().toString(),
              columnDescriptors,
              columnHandles);

      final ImmutableMap.Builder<RocksDbColumn, ColumnFamilyHandle> builder =
          ImmutableMap.builder();
      for (ColumnFamilyHandle columnHandle : columnHandles) {
        final RocksDbColumn rocksDbColumn = columnsById.get(Bytes.wrap(columnHandle.getName()));
        builder.put(rocksDbColumn, columnHandle);
      }
      columnHandlesByName = builder.build();

    } catch (RocksDBException e) {
      throw new DatabaseStorageException(
          "Failed to open database at path: " + configuration.getDatabaseDir(), e);
    }
  }

  private BlockBasedTableConfig createBlockBasedTableConfig(final RocksDbConfiguration config) {
    final LRUCache cache = new LRUCache(config.getCacheCapacity());
    return new BlockBasedTableConfig().setBlockCache(cache);
  }

  @Override
  public void storeGenesis(final Store store) {
    throwIfClosed();
    final WriteOptions options = new WriteOptions();
    try (Transaction transaction = db.beginTransaction(options)) {
      try {
        ColumnFamilyHandle defaultColumn = columnHandlesByName.get(DEFAULT);
        transaction.put(
            defaultColumn,
            GENESIS_TIME_KEY.getId(),
            Longs.toByteArray(store.getGenesisTime().longValue()));
        transaction.put(
            defaultColumn,
            RocksDbDefaultColumnKey.JUSTIFIED_CHECKPOINT_KEY.getId(),
            serialize(store.getJustifiedCheckpoint()));
        transaction.put(
            defaultColumn,
            RocksDbDefaultColumnKey.BEST_JUSTIFIED_CHECKPOINT_KEY.getId(),
            serialize(store.getBestJustifiedCheckpoint()));
        transaction.put(
            defaultColumn,
            FINALIZED_CHECKPOINT_KEY.getId(),
            serialize(store.getFinalizedCheckpoint()));
        store
            .getBlockRoots()
            .forEach(
                root -> {
                  final SignedBeaconBlock block = store.getSignedBlock(root);
                  final BeaconState state = store.getBlockState(root);
                  try {
                    addHotBlock(transaction, root, block);
                    byte[] rootArray = root.toArrayUnsafe();
                    transaction.put(
                        columnHandlesByName.get(HOT_STATES_BY_ROOT), rootArray, serialize(state));
                    transaction.put(
                        columnHandlesByName.get(FINALIZED_ROOTS_BY_SLOT),
                        Longs.toByteArray(block.getSlot().longValue()),
                        rootArray);
                    transaction.put(
                        columnHandlesByName.get(FINALIZED_BLOCKS_BY_ROOT),
                        rootArray,
                        serialize(block));
                    putFinalizedState(transaction, root, state);
                  } catch (RocksDBException e) {
                    rollback(transaction);
                    throw new DatabaseStorageException("Error Storing Genesis", e);
                  }
                });
        transaction.put(
            columnHandlesByName.get(CHECKPOINT_STATES),
            serialize(store.getJustifiedCheckpoint()),
            serialize(store.getBlockState(store.getJustifiedCheckpoint().getRoot())));
        transaction.put(
            columnHandlesByName.get(CHECKPOINT_STATES),
            serialize(store.getBestJustifiedCheckpoint()),
            serialize(store.getBlockState(store.getBestJustifiedCheckpoint().getRoot())));
        transaction.commit();
      } catch (RocksDBException e) {
        rollback(transaction);
        throw new DatabaseStorageException("Error Storing Genesis", e);
      }
    }
  }

  private void rollback(final Transaction transaction) {
    try {
      if (transaction.getState() != TransactionState.ROLLEDBACK) {
        transaction.rollback();
      }
    } catch (RocksDBException ex) {
      throw new DatabaseStorageException("Error Rolling Back Tx Storing Genesis", ex);
    }
  }

  private void putFinalizedState(
      Transaction transaction, final Bytes32 blockRoot, final BeaconState state)
      throws RocksDBException {
    switch (stateStorageMode) {
      case ARCHIVE:
        transaction.put(
            columnHandlesByName.get(FINALIZED_BLOCKS_BY_ROOT),
            blockRoot.toArrayUnsafe(),
            serialize(state));
        break;
      case PRUNE:
        // Don't persist finalized state
        break;
    }
  }

  private void addHotBlock(
      Transaction transaction, final Bytes32 root, final SignedBeaconBlock block) {
    try {
      transaction.put(
          columnHandlesByName.get(HOT_BLOCKS_BY_ROOT), root.toArrayUnsafe(), serialize(block));
      addToHotRootsBySlotCache(root, block);
    } catch (RocksDBException e) {
      throw new DatabaseStorageException("Error storing a hotBlock", e);
    }
  }

  private void addToHotRootsBySlotCache(final Bytes32 root, final SignedBeaconBlock block) {
    hotRootsBySlotCache
        .computeIfAbsent(
            block.getSlot(), key -> Collections.newSetFromMap(new ConcurrentHashMap<>()))
        .add(root);
  }

  @Override
  public StorageUpdateResult update(final StorageUpdate event) {
    if (event.isEmpty()) {
      return StorageUpdateResult.successfulWithNothingPruned();
    }
    return doUpdate(event);
  }

  private StorageUpdateResult doUpdate(final StorageUpdate event) {
    try (Transaction transaction = db.beginTransaction(new WriteOptions())) {
      try {
        ColumnFamilyHandle defaultColumn = columnHandlesByName.get(DEFAULT);
        final Checkpoint previousFinalizedCheckpoint =
            deserialize(db.get(defaultColumn, FINALIZED_CHECKPOINT_KEY.getId()), Checkpoint.class);

        final Checkpoint newFinalizedCheckpoint =
            event.getFinalizedCheckpoint().orElse(previousFinalizedCheckpoint);
        event
            .getGenesisTime()
            .ifPresent(
                time ->
                    storeDefaultValue(
                        transaction, GENESIS_TIME_KEY, Longs.toByteArray(time.longValue())));
        event
            .getFinalizedCheckpoint()
            .ifPresent(
                (finalizedCheckpoint) ->
                    storeDefaultValue(
                        transaction, FINALIZED_CHECKPOINT_KEY, serialize(finalizedCheckpoint)));
        event
            .getJustifiedCheckpoint()
            .ifPresent(
                (justifiedCheckpoint) ->
                    storeDefaultValue(
                        transaction, JUSTIFIED_CHECKPOINT_KEY, serialize(justifiedCheckpoint)));
        event
            .getBestJustifiedCheckpoint()
            .ifPresent(
                (justifiedCheckpoint) ->
                    storeDefaultValue(
                        transaction,
                        BEST_JUSTIFIED_CHECKPOINT_KEY,
                        serialize(justifiedCheckpoint)));
        event
            .getCheckpointStates()
            .forEach(
                (checkpoint, beaconState) ->
                    store(
                        transaction,
                        CHECKPOINT_STATES,
                        serialize(checkpoint),
                        serialize(beaconState)));
        event
            .getLatestMessages()
            .forEach(
                (stateId, checkpoint) ->
                    store(
                        transaction,
                        LATEST_MESSAGES,
                        Longs.toByteArray(stateId.longValue()),
                        serialize(checkpoint)));

        event.getBlocks().forEach((root, block) -> addHotBlock(transaction, root, block));
        event
            .getBlockStates()
            .forEach(
                (blockNo, beaconState) ->
                    store(
                        transaction,
                        HOT_STATES_BY_ROOT,
                        blockNo.toArrayUnsafe(),
                        serialize(beaconState)));

        final StorageUpdateResult result;
        if (previousFinalizedCheckpoint == null
            || !previousFinalizedCheckpoint.equals(newFinalizedCheckpoint)) {
          recordFinalizedBlocks(newFinalizedCheckpoint, transaction);
          final Set<Checkpoint> prunedCheckpoints =
              pruneCheckpointStates(newFinalizedCheckpoint, transaction);
          final Set<Bytes32> prunedBlockRoots = pruneHotBlocks(newFinalizedCheckpoint, transaction);
          result = StorageUpdateResult.successful(prunedBlockRoots, prunedCheckpoints);
        } else {
          result = StorageUpdateResult.successfulWithNothingPruned();
        }
        transaction.commit();
        return result;
      } catch (final RuntimeException | Error | RocksDBException e) {
        try {
          transaction.rollback();
        } catch (RocksDBException ex) {
          LOG.error("exception rolling back transaction", ex);
        }
        return StorageUpdateResult.failed(new RuntimeException(e));
      }
    }
  }

  private Set<Checkpoint> pruneCheckpointStates(
      final Checkpoint newFinalizedCheckpoint, final Transaction transaction) {
    ColumnFamilyHandle checkpointStates = columnHandlesByName.get(CHECKPOINT_STATES);
    Set<Checkpoint> prunedCheckpoints = new HashSet<>();
    try (RocksIterator rocksIterator = db.newIterator(checkpointStates)) {
      for (rocksIterator.seekToFirst(); rocksIterator.isValid(); rocksIterator.next()) {
        final byte[] key = rocksIterator.key();
        Checkpoint checkpoint = deserialize(key, Checkpoint.class);
        if (checkpoint.getEpoch().compareTo(newFinalizedCheckpoint.getEpoch()) < 0) {
          transaction.delete(checkpointStates, key);
          prunedCheckpoints.add(checkpoint);
        }
        rocksIterator.next();
      }
    } catch (RocksDBException e) {
      throw new DatabaseStorageException("error pruning checkpoints", e);
    }
    return prunedCheckpoints;
  }

  private Set<Bytes32> pruneHotBlocks(
      final Checkpoint newFinalizedCheckpoint, final Transaction transaction)
      throws RocksDBException {
    SignedBeaconBlock newlyFinalizedBlock =
        deserialize(
            db.get(
                columnHandlesByName.get(HOT_BLOCKS_BY_ROOT),
                newFinalizedCheckpoint.getRoot().toArrayUnsafe()),
            SignedBeaconBlock.class);
    if (newlyFinalizedBlock == null) {
      LOG.error(
          "Missing finalized block {} for epoch {}",
          newFinalizedCheckpoint.getRoot(),
          newFinalizedCheckpoint.getEpoch());
      return Collections.emptySet();
    }
    final UnsignedLong finalizedSlot = newlyFinalizedBlock.getSlot();
    final ConcurrentNavigableMap<UnsignedLong, Set<Bytes32>> toRemove =
        hotRootsBySlotCache.headMap(finalizedSlot);
    LOG.trace("Pruning slots {} from non-finalized pool", toRemove::keySet);
    final Set<Bytes32> prunedRoots =
        toRemove.values().stream().flatMap(Set::stream).collect(Collectors.toSet());
    toRemove
        .values()
        .forEach(
            roots ->
                roots.forEach(
                    root -> {
                      try {
                        transaction.delete(
                            columnHandlesByName.get(HOT_STATES_BY_ROOT), root.toArrayUnsafe());
                        transaction.delete(
                            columnHandlesByName.get(HOT_BLOCKS_BY_ROOT), root.toArrayUnsafe());
                      } catch (RocksDBException e) {
                        throw new DatabaseStorageException("Error deleting hot value by root", e);
                      }
                    }));
    hotRootsBySlotCache.keySet().removeAll(toRemove.keySet());
    return prunedRoots;
  }

  private void recordFinalizedBlocks(
      final Checkpoint newFinalizedCheckpoint, Transaction transaction) throws RocksDBException {
    LOG.debug(
        "Record finalized blocks for epoch {} starting at block {}",
        newFinalizedCheckpoint.getEpoch(),
        newFinalizedCheckpoint.getRoot());
    final UnsignedLong highestFinalizedSlot;
    try (RocksIterator rocksIterator =
        db.newIterator(columnHandlesByName.get(FINALIZED_ROOTS_BY_SLOT))) {
      rocksIterator.seekToLast();

      highestFinalizedSlot =
          rocksIterator.isValid()
              ? UnsignedLong.valueOf(Longs.fromByteArray(rocksIterator.key()))
              : UnsignedLong.ZERO;
    }
    Bytes32 newlyFinalizedBlockRoot = newFinalizedCheckpoint.getRoot();
    SignedBeaconBlock newlyFinalizedBlock =
        deserialize(
            db.get(
                columnHandlesByName.get(HOT_BLOCKS_BY_ROOT),
                newlyFinalizedBlockRoot.toArrayUnsafe()),
            SignedBeaconBlock.class);
    while (newlyFinalizedBlock != null
        && newlyFinalizedBlock.getSlot().compareTo(highestFinalizedSlot) > 0) {
      LOG.debug(
          "Recording finalized block {} at slot {}",
          newlyFinalizedBlock.getSlot(),
          newlyFinalizedBlockRoot);
      store(
          transaction,
          FINALIZED_ROOTS_BY_SLOT,
          Longs.toByteArray(newlyFinalizedBlock.getSlot().longValue()),
          newlyFinalizedBlockRoot.toArrayUnsafe());
      store(
          transaction,
          FINALIZED_BLOCKS_BY_ROOT,
          newlyFinalizedBlockRoot.toArrayUnsafe(),
          serialize(newlyFinalizedBlock));
      final Optional<BeaconState> finalizedState = getState(newlyFinalizedBlockRoot);
      if (finalizedState.isPresent()) {
        putFinalizedState(transaction, newlyFinalizedBlockRoot, finalizedState.get());
      } else {
        LOG.error(
            "Missing finalized state {} for epoch {}",
            newlyFinalizedBlockRoot,
            newFinalizedCheckpoint.getEpoch());
      }
      newlyFinalizedBlockRoot = newlyFinalizedBlock.getMessage().getParent_root();

      newlyFinalizedBlock =
          deserialize(
              db.get(
                  columnHandlesByName.get(HOT_BLOCKS_BY_ROOT),
                  newlyFinalizedBlockRoot.toArrayUnsafe()),
              SignedBeaconBlock.class);
    }

    if (newlyFinalizedBlock == null) {
      LOG.error(
          "Missing finalized block {} for epoch {}",
          newlyFinalizedBlockRoot,
          newFinalizedCheckpoint.getEpoch());
    }
  }

  private void store(
      Transaction transaction, RocksDbColumn column, final byte[] key, final byte[] value) {
    try {
      transaction.put(columnHandlesByName.get(column), key, value);
    } catch (RocksDBException e) {
      throw new DatabaseStorageException("unable to store value", e);
    }
  }

  private void storeDefaultValue(
      Transaction transaction, final RocksDbDefaultColumnKey key, final byte[] byteArray) {
    store(transaction, DEFAULT, key.getId(), byteArray);
  }

  private <T> T getSingletonValue(RocksDbDefaultColumnKey key, Class<T> classInfo)
      throws RocksDBException {
    byte[] bytes = db.get(columnHandlesByName.get(DEFAULT), key.getId());
    if (bytes != null) {
      return deserialize(bytes, classInfo);
    } else {
      return null;
    }
  }

  @Override
  public Optional<Store> createMemoryStore() {
    try {
      byte[] genesisTimeBytes = db.get(columnHandlesByName.get(DEFAULT), GENESIS_TIME_KEY.getId());
      if (genesisTimeBytes == null) {
        // If genesis time hasn't been set, genesis hasn't happened and we have no data
        return Optional.empty();
      }
      final UnsignedLong genesisTime = UnsignedLong.valueOf(Longs.fromByteArray(genesisTimeBytes));
      final Checkpoint justifiedCheckpoint =
          getSingletonValue(JUSTIFIED_CHECKPOINT_KEY, Checkpoint.class);
      final Checkpoint finalizedCheckpoint =
          getSingletonValue(FINALIZED_CHECKPOINT_KEY, Checkpoint.class);
      final Checkpoint bestJustifiedCheckpoint =
          getSingletonValue(BEST_JUSTIFIED_CHECKPOINT_KEY, Checkpoint.class);
      final Map<Bytes32, SignedBeaconBlock> hotBlocksByRoot =
          allValues(HOT_BLOCKS_BY_ROOT, Bytes32.class, SignedBeaconBlock.class);
      final Map<Bytes32, BeaconState> hotStatesByRoot =
          allValues(HOT_STATES_BY_ROOT, Bytes32.class, BeaconState.class);
      final Map<Checkpoint, BeaconState> checkpointStates =
          allValues(CHECKPOINT_STATES, Checkpoint.class, BeaconState.class);
      final Map<UnsignedLong, Checkpoint> latestMessages =
          allUnsignedLongValues(LATEST_MESSAGES, Checkpoint.class);
      return Optional.of(
          new Store(
              UnsignedLong.valueOf(Instant.now().getEpochSecond()),
              genesisTime,
              justifiedCheckpoint,
              finalizedCheckpoint,
              bestJustifiedCheckpoint,
              hotBlocksByRoot,
              hotStatesByRoot,
              checkpointStates,
              latestMessages));
    } catch (RocksDBException e) {
      throw new DatabaseStorageException("Unable to create memory store", e);
    }
  }

  private <K, V> Map<K, V> allValues(
      final RocksDbColumn column, final Class<K> keyClass, final Class<V> valueClass) {
    try (RocksIterator rocksIterator = db.newIterator(columnHandlesByName.get(column))) {
      rocksIterator.seekToFirst();
      Map<K, V> result = new HashMap<>();
      for (rocksIterator.seekToFirst(); rocksIterator.isValid(); rocksIterator.next()) {
        K key = deserialize(rocksIterator.key(), keyClass);
        V value = deserialize(rocksIterator.value(), valueClass);
        result.put(key, value);
        rocksIterator.next();
      }
      return result;
    }
  }

  private <V> Map<UnsignedLong, V> allUnsignedLongValues(
      final RocksDbColumn column, final Class<V> valueClass) {
    try (RocksIterator rocksIterator = db.newIterator(columnHandlesByName.get(column))) {
      Map<UnsignedLong, V> result = new HashMap<>();
      for (rocksIterator.seekToFirst(); rocksIterator.isValid(); rocksIterator.next()) {
        UnsignedLong key = UnsignedLong.valueOf(Longs.fromByteArray(rocksIterator.key()));
        V value = deserialize(rocksIterator.value(), valueClass);
        result.put(key, value);
      }
      return result;
    }
  }

  @Override
  public Optional<Bytes32> getFinalizedRootAtSlot(final UnsignedLong slot) {
    try {
      byte[] bytes =
          db.get(
              columnHandlesByName.get(FINALIZED_ROOTS_BY_SLOT),
              Longs.toByteArray(slot.longValue()));
      return bytes == null ? Optional.empty() : Optional.of(Bytes32.wrap(bytes));
    } catch (RocksDBException e) {
      throw new DatabaseStorageException("unable to getFinalizedRootAtSlot " + slot, e);
    }
  }

  @Override
  public Optional<Bytes32> getLatestFinalizedRootAtSlot(final UnsignedLong slot) {
    try (RocksIterator rocksIterator =
        db.newIterator(columnHandlesByName.get(FINALIZED_ROOTS_BY_SLOT))) {
      rocksIterator.seekForPrev(Longs.toByteArray(slot.longValue()));
      if (rocksIterator.isValid()) {
        rocksIterator.prev();
      }
      return rocksIterator.isValid()
          ? Optional.of(Bytes32.wrap(rocksIterator.value()))
          : Optional.empty();
    }
  }

  @Override
  public Optional<SignedBeaconBlock> getSignedBlock(final Bytes32 root) {
    byte[] bytes;
    try {
      bytes = db.get(columnHandlesByName.get(HOT_BLOCKS_BY_ROOT), root.toArrayUnsafe());

      if (bytes == null) {
        bytes = db.get(columnHandlesByName.get(FINALIZED_BLOCKS_BY_ROOT), root.toArrayUnsafe());
      }
    } catch (RocksDBException e) {
      throw new DatabaseStorageException("error trying to load block from db ");
    }
    return bytes != null
        ? Optional.of(deserialize(bytes, SignedBeaconBlock.class))
        : Optional.empty();
  }

  @Override
  public Optional<BeaconState> getState(final Bytes32 root) {
    try {
      byte[] key = root.toArrayUnsafe();
      byte[] bytes = db.get(columnHandlesByName.get(HOT_BLOCKS_BY_ROOT), key);
      if (bytes == null) {
        bytes = db.get(columnHandlesByName.get(FINALIZED_STATES_BY_ROOT), key);
      }
      return bytes == null
          ? Optional.empty()
          : Optional.of(deserialize(bytes, BeaconStateImpl.class));
    } catch (RocksDBException e) {
      throw new DatabaseStorageException("unable to getState for root" + root, e);
    }
  }

  @Override
  public void close() {
    if (closed.compareAndSet(false, true)) {
      txOptions.close();
      options.close();
      db.close();
    }
  }

  private void throwIfClosed() {
    if (closed.get()) {
      LOG.error("Attempting to use a closed RocksDBKeyValueStorage");
      throw new IllegalStateException("Storage has been closed");
    }
  }

  private byte[] serialize(SimpleOffsetSerializable value) {
    return SimpleOffsetSerializer.serialize(value).toArrayUnsafe();
  }

  public <T> T deserialize(byte[] fromDb, Class<T> classInfo) {
    return SimpleOffsetSerializer.deserialize(Bytes.wrap(fromDb), classInfo);
  }
}
