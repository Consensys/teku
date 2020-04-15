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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.core.StateTransition;
import tech.pegasys.artemis.core.StateTransitionException;
import tech.pegasys.artemis.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.Checkpoint;
import tech.pegasys.artemis.storage.server.DatabaseStorageException;
import tech.pegasys.artemis.storage.server.rocksdb.core.ColumnEntry;
import tech.pegasys.artemis.storage.server.rocksdb.core.RocksDbInstance;
import tech.pegasys.artemis.storage.server.rocksdb.core.RocksDbInstance.Transaction;
import tech.pegasys.artemis.storage.server.rocksdb.schema.V3Schema;

public class V3RocksDbDao implements RocksDbDao {
  private static final Logger LOG = LogManager.getLogger();

  // Persistent data
  private final RocksDbInstance db;
  // In-memory data
  private final NavigableMap<UnsignedLong, Set<Bytes32>> hotRootsBySlotCache =
      new ConcurrentSkipListMap<>();
  private final Map<Bytes32, BeaconState> hotStates = new ConcurrentHashMap<>();

  public V3RocksDbDao(final RocksDbInstance db) {
    this.db = db;
    initialize();
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
  public Optional<UnsignedLong> getHighestFinalizedSlot() {
    return db.getLastEntry(V3Schema.FINALIZED_ROOTS_BY_SLOT).map(ColumnEntry::getKey);
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
  public Optional<BeaconState> getHotState(final Bytes32 root) {
    return Optional.ofNullable(hotStates.get(root));
  }

  @Override
  public Optional<BeaconState> getFinalizedState(final Bytes32 root) {
    return db.get(V3Schema.FINALIZED_STATES_BY_ROOT, root);
  }

  @Override
  public Map<Bytes32, SignedBeaconBlock> getHotBlocks() {
    return db.getAll(V3Schema.HOT_BLOCKS_BY_ROOT);
  }

  @Override
  public Map<Bytes32, BeaconState> getHotStates() {
    return new HashMap<>(hotStates);
  }

  @Override
  public Map<Checkpoint, BeaconState> getCheckpointStates() {
    return db.getAll(V3Schema.CHECKPOINT_STATES);
  }

  @Override
  public Stream<ColumnEntry<Checkpoint, BeaconState>> streamCheckpointStates() {
    return db.stream(V3Schema.CHECKPOINT_STATES);
  }

  private Optional<BeaconState> getLatestFinalizedState() {
    return db.get(V3Schema.LATEST_FINALIZED_STATE);
  }

  public Optional<Bytes32> getLatestFinalizedRoot() {
    return db.getLastEntry(V3Schema.FINALIZED_ROOTS_BY_SLOT).map(ColumnEntry::getValue);
  }

  @Override
  public Updater updater() {
    return new V3Updater(db, hotRootsBySlotCache, hotStates);
  }

  @Override
  public void close() throws Exception {
    hotRootsBySlotCache.clear();
    db.close();
  }

  private void initialize() {
    final Optional<BeaconState> finalizedState = getLatestFinalizedState();
    final Optional<Bytes32> finalizedRoot = getLatestFinalizedRoot();
    final Map<Bytes32, SignedBeaconBlock> hotBlocksByRoot = getHotBlocks();

    if (finalizedRoot.isEmpty() && finalizedState.isEmpty() && hotBlocksByRoot.isEmpty()) {
      // Database is empty, nothing to initialize
      LOG.trace("Database appears to be empty.  Skip initialization of hot states.");
      return;
    } else if (finalizedRoot.isEmpty() || finalizedState.isEmpty()) {
      throw new DatabaseStorageException("Missing latest finalized block information");
    }

    initializeHotStates(finalizedRoot.get(), finalizedState.get(), hotBlocksByRoot);
  }

  private void initializeHotStates(
      final Bytes32 finalizedRoot,
      final BeaconState finalizedState,
      final Map<Bytes32, SignedBeaconBlock> hotBlocksByRoot) {
    // Initialize hot states with latest finalized state
    hotStates.put(finalizedRoot, finalizedState);

    // Index blocks by parent root
    final Map<Bytes32, List<SignedBeaconBlock>> blocksByParent = new HashMap<>();
    for (Entry<Bytes32, SignedBeaconBlock> hotBlockEntry : hotBlocksByRoot.entrySet()) {
      final SignedBeaconBlock currentBlock = hotBlockEntry.getValue();
      final List<SignedBeaconBlock> blockList =
          blocksByParent.computeIfAbsent(currentBlock.getParent_root(), (key) -> new ArrayList<>());
      blockList.add(currentBlock);
    }

    // Generate remaining hot states
    final Deque<Bytes32> parentRoots = new ArrayDeque<>();
    parentRoots.push(finalizedRoot);
    while (!parentRoots.isEmpty()) {
      final Bytes32 parentRoot = parentRoots.pop();
      final BeaconState parentState = hotStates.get(parentRoot);
      final List<SignedBeaconBlock> blocks =
          blocksByParent.computeIfAbsent(parentRoot, (key) -> Collections.emptyList());
      for (SignedBeaconBlock block : blocks) {
        final Bytes32 blockRoot = block.getMessage().hash_tree_root();
        processBlock(parentState, block)
            .ifPresent(
                state -> {
                  hotStates.put(blockRoot, state);
                  parentRoots.push(blockRoot);
                });
      }
    }

    if (hotStates.size() != hotBlocksByRoot.size()) {
      LOG.trace(
          "Only {} hot states produced for {} hot blocks.  Some hot blocks must be incompatible with the latest finalized block.",
          hotStates.size(),
          hotBlocksByRoot.size());
    }
  }

  private final Optional<BeaconState> processBlock(
      final BeaconState preState, final SignedBeaconBlock block) {
    StateTransition stateTransition = new StateTransition();
    try {
      final BeaconState postState = stateTransition.initiate(preState, block);
      return Optional.of(postState);
    } catch (StateTransitionException e) {
      LOG.error(
          "Unable to produce state for block at slot {} ({})",
          block.getSlot(),
          block.getMessage().hash_tree_root());
      return Optional.empty();
    }
  }

  private static class V3Updater implements Updater {

    private final Transaction transaction;
    private final NavigableMap<UnsignedLong, Set<Bytes32>> hotRootsBySlotCache;
    private final Map<Bytes32, BeaconState> hotStates;

    // Hot root by slot updates
    private final NavigableMap<UnsignedLong, Set<Bytes32>> hotRootsBySlotAdditions =
        new ConcurrentSkipListMap<>();
    private final Set<UnsignedLong> prunedSlots = new HashSet<>();

    // Hot state updates
    private final Map<Bytes32, BeaconState> newHotStates = new HashMap<>();
    private final Set<Bytes32> deletedStates = new HashSet<>();

    V3Updater(
        final RocksDbInstance db,
        final NavigableMap<UnsignedLong, Set<Bytes32>> hotRootsBySlotCache,
        final Map<Bytes32, BeaconState> hotStates) {
      this.transaction = db.startTransaction();
      this.hotRootsBySlotCache = hotRootsBySlotCache;
      this.hotStates = hotStates;
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
    public void setLatestFinalizedState(final BeaconState state) {
      transaction.put(V3Schema.LATEST_FINALIZED_STATE, state);
    }

    @Override
    public void setFinalizedCheckpoint(final Checkpoint checkpoint) {
      transaction.put(V3Schema.FINALIZED_CHECKPOINT, checkpoint);
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
      final Bytes32 blockRoot = block.getMessage().hash_tree_root();
      transaction.put(V3Schema.HOT_BLOCKS_BY_ROOT, blockRoot, block);
      hotRootsBySlotAdditions
          .computeIfAbsent(block.getSlot(), key -> new HashSet<>())
          .add(blockRoot);
    }

    @Override
    public void addFinalizedBlock(final SignedBeaconBlock block) {
      final Bytes32 root = block.getMessage().hash_tree_root();
      transaction.put(V3Schema.FINALIZED_ROOTS_BY_SLOT, block.getSlot(), root);
      transaction.put(V3Schema.FINALIZED_BLOCKS_BY_ROOT, root, block);
    }

    @Override
    public void addHotState(final Bytes32 blockRoot, final BeaconState state) {
      newHotStates.put(blockRoot, state);
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
    public void addHotStates(final Map<Bytes32, BeaconState> states) {
      states.forEach(this::addHotState);
    }

    @Override
    public void deleteCheckpointState(final Checkpoint checkpoint) {
      transaction.delete(V3Schema.CHECKPOINT_STATES, checkpoint);
    }

    @Override
    public Set<Bytes32> pruneHotBlocksAtSlotsOlderThan(final UnsignedLong slot) {
      final Map<UnsignedLong, Set<Bytes32>> toRemove = hotRootsBySlotAdditions.headMap(slot);
      toRemove.putAll(hotRootsBySlotCache.headMap(slot));

      final Set<Bytes32> prunedRoots =
          toRemove.values().stream().flatMap(Set::stream).collect(Collectors.toSet());
      for (Set<Bytes32> hotRoots : toRemove.values()) {
        for (Bytes32 root : hotRoots) {
          newHotStates.remove(root);
          deletedStates.add(root);
          transaction.delete(V3Schema.HOT_BLOCKS_BY_ROOT, root);
        }
      }
      hotRootsBySlotAdditions.keySet().removeAll(toRemove.keySet());
      prunedSlots.addAll(toRemove.keySet());

      return prunedRoots;
    }

    @Override
    public void commit() {
      // Commit slot updates
      hotRootsBySlotCache.keySet().removeAll(prunedSlots);
      hotRootsBySlotAdditions.forEach(
          (slot, roots) -> {
            final Set<Bytes32> currentRoots =
                hotRootsBySlotCache.computeIfAbsent(
                    slot, __ -> Collections.newSetFromMap(new ConcurrentHashMap<>()));
            currentRoots.addAll(roots);
          });
      // Commit hot state updates
      deletedStates.forEach(hotStates::remove);
      hotStates.putAll(newHotStates);
      // Commit db updates
      transaction.commit();
      close();
    }

    @Override
    public void cancel() {
      // Clear slot updates
      prunedSlots.clear();
      hotRootsBySlotAdditions.clear();
      // Clear hot state updates
      deletedStates.clear();
      newHotStates.clear();
      // Clear db updates
      transaction.rollback();
      close();
    }

    @Override
    public void close() {
      transaction.close();
    }
  }
}
