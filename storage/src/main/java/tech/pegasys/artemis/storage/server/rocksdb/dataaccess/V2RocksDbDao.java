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

package tech.pegasys.artemis.storage.server.rocksdb.dataaccess;

import com.google.common.primitives.UnsignedLong;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.Checkpoint;
import tech.pegasys.artemis.storage.server.rocksdb.core.ColumnEntry;
import tech.pegasys.artemis.storage.server.rocksdb.core.RocksDbInstance;
import tech.pegasys.artemis.storage.server.rocksdb.core.RocksDbInstance.Transaction;
import tech.pegasys.artemis.storage.server.rocksdb.schema.V2Schema;

public class V2RocksDbDao implements RocksDbDAO {
  // Persistent data
  private final RocksDbInstance db;
  // In-memory data
  private final NavigableMap<UnsignedLong, Set<Bytes32>> hotRootsBySlotCache =
      new ConcurrentSkipListMap<>();

  public V2RocksDbDao(final RocksDbInstance db) {
    this.db = db;
  }

  @Override
  public Optional<UnsignedLong> getGenesisTime() {
    return db.get(V2Schema.GENESIS_TIME);
  }

  @Override
  public Optional<Checkpoint> getJustifiedCheckpoint() {
    return db.get(V2Schema.JUSTIFIED_CHECKPOINT);
  }

  @Override
  public Optional<Checkpoint> getBestJustifiedCheckpoint() {
    return db.get(V2Schema.BEST_JUSTIFIED_CHECKPOINT);
  }

  @Override
  public Optional<Checkpoint> getFinalizedCheckpoint() {
    return db.get(V2Schema.FINALIZED_CHECKPOINT);
  }

  @Override
  public Optional<UnsignedLong> getHighestFinalizedSlot() {
    return db.getLastEntry(V2Schema.FINALIZED_ROOTS_BY_SLOT).map(ColumnEntry::getKey);
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
  public Optional<SignedBeaconBlock> getHotBlock(final Bytes32 root) {
    return db.get(V2Schema.HOT_BLOCKS_BY_ROOT, root);
  }

  @Override
  public Optional<SignedBeaconBlock> getFinalizedBlock(final Bytes32 root) {
    return db.get(V2Schema.FINALIZED_BLOCKS_BY_ROOT, root);
  }

  @Override
  public Optional<BeaconState> getHotState(final Bytes32 root) {
    return db.get(V2Schema.HOT_STATES_BY_ROOT, root);
  }

  @Override
  public Optional<BeaconState> getFinalizedState(final Bytes32 root) {
    return db.get(V2Schema.FINALIZED_STATES_BY_ROOT, root);
  }

  @Override
  public Map<Bytes32, SignedBeaconBlock> getHotBlocks() {
    return db.getAll(V2Schema.HOT_BLOCKS_BY_ROOT);
  }

  @Override
  public Map<Bytes32, BeaconState> getHotStates() {
    return db.getAll(V2Schema.HOT_STATES_BY_ROOT);
  }

  @Override
  public Map<Checkpoint, BeaconState> getCheckpointStates() {
    return db.getAll(V2Schema.CHECKPOINT_STATES);
  }

  @Override
  public Map<UnsignedLong, Checkpoint> getLatestMessages() {
    return db.getAll(V2Schema.LATEST_MESSAGES);
  }

  @Override
  public Stream<ColumnEntry<Checkpoint, BeaconState>> streamCheckpointStates() {
    return db.stream(V2Schema.CHECKPOINT_STATES);
  }

  @Override
  public Updater updater() {
    return new V2Updater(db, hotRootsBySlotCache);
  }

  @Override
  public void close() throws Exception {
    hotRootsBySlotCache.clear();
    db.close();
  }

  private static class V2Updater implements RocksDbDAO.Updater {

    private final Transaction transaction;
    private final NavigableMap<UnsignedLong, Set<Bytes32>> hotRootsBySlotCache;

    private final NavigableMap<UnsignedLong, Set<Bytes32>> hotRootsBySlotAdditions =
        new ConcurrentSkipListMap<>();
    private final Set<UnsignedLong> prunedSlots = new HashSet<>();

    V2Updater(
        final RocksDbInstance db,
        final NavigableMap<UnsignedLong, Set<Bytes32>> hotRootsBySlotCache) {
      this.transaction = db.startTransaction();
      this.hotRootsBySlotCache = hotRootsBySlotCache;
    }

    @Override
    public void setGenesisTime(final UnsignedLong genesisTime) {
      transaction.put(V2Schema.GENESIS_TIME, genesisTime);
    }

    @Override
    public void setJustifiedCheckpoint(final Checkpoint checkpoint) {
      transaction.put(V2Schema.JUSTIFIED_CHECKPOINT, checkpoint);
    }

    @Override
    public void setBestJustifiedCheckpoint(final Checkpoint checkpoint) {
      transaction.put(V2Schema.BEST_JUSTIFIED_CHECKPOINT, checkpoint);
    }

    @Override
    public void setLatestFinalizedState(final BeaconState state) {
      // No-op for this implementation
    }

    @Override
    public void addLatestMessage(final UnsignedLong slot, final Checkpoint checkpoint) {
      transaction.put(V2Schema.LATEST_MESSAGES, slot, checkpoint);
    }

    @Override
    public void addLatestMessages(final Map<UnsignedLong, Checkpoint> latestMessages) {
      latestMessages.forEach(this::addLatestMessage);
    }

    @Override
    public void setFinalizedCheckpoint(final Checkpoint checkpoint) {
      transaction.put(V2Schema.FINALIZED_CHECKPOINT, checkpoint);
    }

    @Override
    public void addCheckpointState(final Checkpoint checkpoint, final BeaconState state) {
      transaction.put(V2Schema.CHECKPOINT_STATES, checkpoint, state);
    }

    @Override
    public void addCheckpointStates(final Map<Checkpoint, BeaconState> checkpointStates) {
      checkpointStates.forEach(this::addCheckpointState);
    }

    @Override
    public void addHotBlock(final SignedBeaconBlock block) {
      final Bytes32 blockRoot = block.getMessage().hash_tree_root();
      transaction.put(V2Schema.HOT_BLOCKS_BY_ROOT, blockRoot, block);
      hotRootsBySlotAdditions
          .computeIfAbsent(
              block.getSlot(), key -> Collections.newSetFromMap(new ConcurrentHashMap<>()))
          .add(blockRoot);
    }

    @Override
    public void addFinalizedBlock(final SignedBeaconBlock block) {
      final Bytes32 root = block.getMessage().hash_tree_root();
      transaction.put(V2Schema.FINALIZED_ROOTS_BY_SLOT, block.getSlot(), root);
      transaction.put(V2Schema.FINALIZED_BLOCKS_BY_ROOT, root, block);
    }

    @Override
    public void addHotState(final Bytes32 blockRoot, final BeaconState state) {
      transaction.put(V2Schema.HOT_STATES_BY_ROOT, blockRoot, state);
    }

    @Override
    public void addFinalizedState(final Bytes32 blockRoot, final BeaconState state) {
      transaction.put(V2Schema.FINALIZED_STATES_BY_ROOT, blockRoot, state);
    }

    @Override
    public void addHotBlocks(final Map<Bytes32, SignedBeaconBlock> blocks) {
      blocks.values().forEach(this::addHotBlock);
    }

    @Override
    public void addHotStates(final Map<Bytes32, BeaconState> states) {
      states.forEach(this::addHotState);
    }

    @Override
    public void deleteCheckpointState(final Checkpoint checkpoint) {
      transaction.delete(V2Schema.CHECKPOINT_STATES, checkpoint);
    }

    @Override
    public Set<Bytes32> pruneHotBlocksAtSlotsGreaterThan(final UnsignedLong slot) {
      final Map<UnsignedLong, Set<Bytes32>> toRemove = hotRootsBySlotCache.headMap(slot);
      toRemove.putAll(hotRootsBySlotAdditions.headMap(slot));

      final Set<Bytes32> prunedRoots =
          toRemove.values().stream().flatMap(Set::stream).collect(Collectors.toSet());
      for (Set<Bytes32> hotRoots : toRemove.values()) {
        for (Bytes32 root : hotRoots) {
          transaction.delete(V2Schema.HOT_STATES_BY_ROOT, root);
          transaction.delete(V2Schema.HOT_BLOCKS_BY_ROOT, root);
        }
      }
      hotRootsBySlotAdditions.keySet().removeAll(toRemove.keySet());
      prunedSlots.addAll(toRemove.keySet());

      return prunedRoots;
    }

    @Override
    public void commit() {
      hotRootsBySlotCache.keySet().removeAll(prunedSlots);
      hotRootsBySlotCache.putAll(hotRootsBySlotAdditions);
      transaction.commit();
      close();
    }

    @Override
    public void cancel() {
      prunedSlots.clear();
      hotRootsBySlotAdditions.clear();
      transaction.rollback();
      close();
    }

    @Override
    public void close() {
      transaction.close();
    }
  }
}
