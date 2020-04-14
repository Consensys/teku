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

package tech.pegasys.artemis.storage.server.rocksdb;

import com.google.common.collect.Streams;
import com.google.common.primitives.UnsignedLong;
import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.Checkpoint;
import tech.pegasys.artemis.storage.Store;
import tech.pegasys.artemis.storage.events.StorageUpdate;
import tech.pegasys.artemis.storage.events.StorageUpdateResult;
import tech.pegasys.artemis.storage.server.Database;
import tech.pegasys.artemis.storage.server.StateStorageMode;
import tech.pegasys.artemis.storage.server.rocksdb.core.ColumnEntry;
import tech.pegasys.artemis.storage.server.rocksdb.core.RocksDbInstance;
import tech.pegasys.artemis.storage.server.rocksdb.core.RocksDbInstance.Transaction;
import tech.pegasys.artemis.storage.server.rocksdb.core.RocksDbInstanceFactory;
import tech.pegasys.artemis.storage.server.rocksdb.schema.V2Schema;

public class RocksDbDatabase implements Database {

  private static final Logger LOG = LogManager.getLogger();
  private final StateStorageMode stateStorageMode;

  // Persistent data
  private final RocksDbInstance db;
  // In-memory data
  private final ConcurrentNavigableMap<UnsignedLong, Set<Bytes32>> hotRootsBySlotCache =
      new ConcurrentSkipListMap<>();

  public static Database createOnDisk(
      final RocksDbConfiguration configuration, final StateStorageMode stateStorageMode) {
    return new RocksDbDatabase(configuration, stateStorageMode);
  }

  private RocksDbDatabase(
      final RocksDbConfiguration configuration, final StateStorageMode stateStorageMode) {
    this.stateStorageMode = stateStorageMode;
    this.db = RocksDbInstanceFactory.create(configuration, V2Schema.class);
  }

  @Override
  public void storeGenesis(final Store store) {
    try (final RocksDbInstance.Transaction transaction = db.startTransaction()) {
      transaction.put(V2Schema.GENESIS_TIME, store.getGenesisTime());
      transaction.put(V2Schema.JUSTIFIED_CHECKPOINT, store.getJustifiedCheckpoint());
      transaction.put(V2Schema.BEST_JUSTIFIED_CHECKPOINT, store.getBestJustifiedCheckpoint());
      transaction.put(V2Schema.FINALIZED_CHECKPOINT, store.getFinalizedCheckpoint());

      // We should only have a single checkpoint state at genesis
      final BeaconState genesisState =
          store.getBlockState(store.getFinalizedCheckpoint().getRoot());
      transaction.put(V2Schema.CHECKPOINT_STATES, store.getFinalizedCheckpoint(), genesisState);

      for (Bytes32 root : store.getBlockRoots()) {
        // Since we're storing genesis, we should only have 1 root here corresponding to genesis
        final SignedBeaconBlock block = store.getSignedBlock(root);
        final BeaconState state = store.getBlockState(root);

        // We need to store the genesis block in both hot and cold storage so that on restart
        // we're guaranteed to have at least one block / state to load into RecentChainData.
        // Save to hot storage
        addHotBlock(transaction, root, block);
        transaction.put(V2Schema.HOT_STATES_BY_ROOT, root, state);
        // Save to cold storage
        transaction.put(V2Schema.FINALIZED_ROOTS_BY_SLOT, block.getSlot(), root);
        transaction.put(V2Schema.FINALIZED_BLOCKS_BY_ROOT, root, block);
        transaction.put(V2Schema.FINALIZED_STATES_BY_ROOT, root, state);
      }

      transaction.commit();
    }
  }

  @Override
  public StorageUpdateResult update(final StorageUpdate event) {
    if (event.isEmpty()) {
      return StorageUpdateResult.successfulWithNothingPruned();
    }
    return doUpdate(event);
  }

  @Override
  public Optional<Store> createMemoryStore() {
    Optional<UnsignedLong> maybeGenesisTime = db.get(V2Schema.GENESIS_TIME);
    if (maybeGenesisTime.isEmpty()) {
      // If genesis time hasn't been set, genesis hasn't happened and we have no data
      return Optional.empty();
    }
    final UnsignedLong genesisTime = maybeGenesisTime.get();
    final Checkpoint justifiedCheckpoint = db.getOrThrow(V2Schema.JUSTIFIED_CHECKPOINT);
    final Checkpoint finalizedCheckpoint = db.getOrThrow(V2Schema.FINALIZED_CHECKPOINT);
    final Checkpoint bestJustifiedCheckpoint = db.getOrThrow(V2Schema.BEST_JUSTIFIED_CHECKPOINT);

    final Map<Bytes32, SignedBeaconBlock> hotBlocksByRoot = db.getAll(V2Schema.HOT_BLOCKS_BY_ROOT);
    final Map<Bytes32, BeaconState> hotStatesByRoot = db.getAll(V2Schema.HOT_STATES_BY_ROOT);
    final Map<Checkpoint, BeaconState> checkpointStates = db.getAll(V2Schema.CHECKPOINT_STATES);

    return Optional.of(
        new Store(
            UnsignedLong.valueOf(Instant.now().getEpochSecond()),
            genesisTime,
            justifiedCheckpoint,
            finalizedCheckpoint,
            bestJustifiedCheckpoint,
            hotBlocksByRoot,
            hotStatesByRoot,
            checkpointStates));
  }

  @Override
  public Optional<Bytes32> getFinalizedRootAtSlot(final UnsignedLong slot) {
    return db.get(V2Schema.FINALIZED_ROOTS_BY_SLOT, slot);
  }

  @Override
  public Optional<Bytes32> getLatestFinalizedRootAtSlot(final UnsignedLong slot) {
    return db.getFloorEntry(V2Schema.FINALIZED_ROOTS_BY_SLOT, slot).map(ColumnEntry::getValue);
  }

  @Override
  public Optional<SignedBeaconBlock> getSignedBlock(final Bytes32 root) {
    return db.get(V2Schema.HOT_BLOCKS_BY_ROOT, root)
        .or(() -> db.get(V2Schema.FINALIZED_BLOCKS_BY_ROOT, root));
  }

  @Override
  public Optional<BeaconState> getState(final Bytes32 root) {
    return db.get(V2Schema.HOT_STATES_BY_ROOT, root)
        .or(() -> db.get(V2Schema.FINALIZED_STATES_BY_ROOT, root));
  }

  @Override
  public void close() throws Exception {
    db.close();
  }

  private Checkpoint getFinalizedCheckpoint() {
    return db.getOrThrow(V2Schema.FINALIZED_CHECKPOINT);
  }

  private StorageUpdateResult doUpdate(final StorageUpdate update) {
    try (final RocksDbInstance.Transaction transaction = db.startTransaction()) {
      final Checkpoint previousFinalizedCheckpoint = getFinalizedCheckpoint();
      final Checkpoint newFinalizedCheckpoint =
          update.getFinalizedCheckpoint().orElse(previousFinalizedCheckpoint);

      update.getGenesisTime().ifPresent(val -> transaction.put(V2Schema.GENESIS_TIME, val));
      update
          .getFinalizedCheckpoint()
          .ifPresent(val -> transaction.put(V2Schema.FINALIZED_CHECKPOINT, val));
      update
          .getJustifiedCheckpoint()
          .ifPresent(val -> transaction.put(V2Schema.JUSTIFIED_CHECKPOINT, val));
      update
          .getBestJustifiedCheckpoint()
          .ifPresent(val -> transaction.put(V2Schema.BEST_JUSTIFIED_CHECKPOINT, val));

      transaction.put(V2Schema.CHECKPOINT_STATES, update.getCheckpointStates());

      update.getBlocks().forEach((root, block) -> addHotBlock(transaction, root, block));
      transaction.put(V2Schema.HOT_STATES_BY_ROOT, update.getBlockStates());

      final StorageUpdateResult result;
      if (previousFinalizedCheckpoint == null
          || !previousFinalizedCheckpoint.equals(newFinalizedCheckpoint)) {
        recordFinalizedBlocks(transaction, update, newFinalizedCheckpoint);
        final Set<Checkpoint> prunedCheckpoints =
            pruneCheckpointStates(transaction, update, newFinalizedCheckpoint);
        final Set<Bytes32> prunedBlockRoots =
            pruneHotBlocks(transaction, update, newFinalizedCheckpoint);
        result = StorageUpdateResult.successful(prunedBlockRoots, prunedCheckpoints);
      } else {
        result = StorageUpdateResult.successfulWithNothingPruned();
      }
      transaction.commit();
      return result;
    }
  }

  private void putFinalizedState(
      RocksDbInstance.Transaction transaction, final Bytes32 blockRoot, final BeaconState state) {
    switch (stateStorageMode) {
      case ARCHIVE:
        transaction.put(V2Schema.FINALIZED_STATES_BY_ROOT, blockRoot, state);
        break;
      case PRUNE:
        // Don't persist finalized state
        break;
      default:
        throw new UnsupportedOperationException("Unhandled storage mode: " + stateStorageMode);
    }
  }

  private void addHotBlock(
      RocksDbInstance.Transaction transaction, final Bytes32 root, final SignedBeaconBlock block) {
    transaction.put(V2Schema.HOT_BLOCKS_BY_ROOT, root, block);
    hotRootsBySlotCache
        .computeIfAbsent(
            block.getSlot(), key -> Collections.newSetFromMap(new ConcurrentHashMap<>()))
        .add(root);
  }

  private Set<Checkpoint> pruneCheckpointStates(
      final Transaction transaction,
      final StorageUpdate update,
      final Checkpoint newFinalizedCheckpoint) {
    final Set<Checkpoint> prunedCheckpoints = new HashSet<>();
    try (final Stream<ColumnEntry<Checkpoint, BeaconState>> stream =
        db.stream(V2Schema.CHECKPOINT_STATES)) {
      Streams.concat(stream, update.getCheckpointStates().entrySet().stream())
          .filter(e -> e.getKey().getEpoch().compareTo(newFinalizedCheckpoint.getEpoch()) < 0)
          .forEach(
              entry -> {
                transaction.delete(V2Schema.CHECKPOINT_STATES, entry.getKey());
                prunedCheckpoints.add(entry.getKey());
              });
    }
    return prunedCheckpoints;
  }

  private Set<Bytes32> pruneHotBlocks(
      final Transaction transaction,
      final StorageUpdate update,
      final Checkpoint newFinalizedCheckpoint) {
    Optional<SignedBeaconBlock> newlyFinalizedBlock =
        getHotBlock(update, newFinalizedCheckpoint.getRoot());
    if (newlyFinalizedBlock.isEmpty()) {
      LOG.error(
          "Missing finalized block {} for epoch {}",
          newFinalizedCheckpoint.getRoot(),
          newFinalizedCheckpoint.getEpoch());
      return Collections.emptySet();
    }
    final UnsignedLong finalizedSlot = newlyFinalizedBlock.get().getSlot();
    final ConcurrentNavigableMap<UnsignedLong, Set<Bytes32>> toRemove =
        hotRootsBySlotCache.headMap(finalizedSlot);
    LOG.trace("Pruning slots {} from non-finalized pool", toRemove::keySet);
    final Set<Bytes32> prunedRoots =
        toRemove.values().stream().flatMap(Set::stream).collect(Collectors.toSet());
    for (Set<Bytes32> hotRoots : toRemove.values()) {
      for (Bytes32 root : hotRoots) {
        transaction.delete(V2Schema.HOT_STATES_BY_ROOT, root);
        transaction.delete(V2Schema.HOT_BLOCKS_BY_ROOT, root);
      }
    }
    hotRootsBySlotCache.keySet().removeAll(toRemove.keySet());
    return prunedRoots;
  }

  private void recordFinalizedBlocks(
      Transaction transaction,
      final StorageUpdate update,
      final Checkpoint newFinalizedCheckpoint) {
    LOG.debug(
        "Record finalized blocks for epoch {} starting at block {}",
        newFinalizedCheckpoint.getEpoch(),
        newFinalizedCheckpoint.getRoot());

    final UnsignedLong highestFinalizedSlot =
        db.getLastEntry(V2Schema.FINALIZED_ROOTS_BY_SLOT)
            .map(ColumnEntry::getKey)
            .orElse(UnsignedLong.ZERO);

    Bytes32 newlyFinalizedBlockRoot = newFinalizedCheckpoint.getRoot();
    Optional<SignedBeaconBlock> newlyFinalizedBlock = getHotBlock(update, newlyFinalizedBlockRoot);
    while (newlyFinalizedBlock.isPresent()
        && newlyFinalizedBlock.get().getSlot().compareTo(highestFinalizedSlot) > 0) {
      LOG.debug(
          "Recording finalized block {} at slot {}",
          newlyFinalizedBlockRoot,
          newlyFinalizedBlock.get().getSlot());
      transaction.put(
          V2Schema.FINALIZED_ROOTS_BY_SLOT,
          newlyFinalizedBlock.get().getSlot(),
          newlyFinalizedBlockRoot);
      transaction.put(
          V2Schema.FINALIZED_BLOCKS_BY_ROOT, newlyFinalizedBlockRoot, newlyFinalizedBlock.get());
      final Optional<BeaconState> finalizedState = getHotState(update, newlyFinalizedBlockRoot);
      if (finalizedState.isPresent()) {
        putFinalizedState(transaction, newlyFinalizedBlockRoot, finalizedState.get());
      } else {
        LOG.error(
            "Missing finalized state {} for epoch {}",
            newlyFinalizedBlockRoot,
            newFinalizedCheckpoint.getEpoch());
      }

      // Update for next round of iteration
      newlyFinalizedBlockRoot = newlyFinalizedBlock.get().getMessage().getParent_root();
      newlyFinalizedBlock = getHotBlock(update, newlyFinalizedBlockRoot);
    }

    if (newlyFinalizedBlock.isEmpty()) {
      LOG.error(
          "Missing finalized block {} for epoch {}",
          newlyFinalizedBlockRoot,
          newFinalizedCheckpoint.getEpoch());
    }
  }

  private Optional<SignedBeaconBlock> getHotBlock(final StorageUpdate update, final Bytes32 root) {
    return Optional.ofNullable(update.getBlocks().get(root))
        .or(() -> db.get(V2Schema.HOT_BLOCKS_BY_ROOT, root));
  }

  private Optional<BeaconState> getHotState(final StorageUpdate update, final Bytes32 root) {
    return Optional.ofNullable(update.getBlockStates().get(root))
        .or(() -> db.get(V2Schema.HOT_STATES_BY_ROOT, root));
  }
}
