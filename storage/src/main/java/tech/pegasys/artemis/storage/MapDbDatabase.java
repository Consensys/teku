/*
 * Copyright 2019 ConsenSys AG.
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

package tech.pegasys.artemis.storage;

import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;
import static tech.pegasys.artemis.util.alogger.ALogger.STDOUT;

import com.google.common.primitives.UnsignedLong;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.mapdb.Atomic;
import org.mapdb.Atomic.Var;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.DBMaker.Maker;
import tech.pegasys.artemis.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.Checkpoint;
import tech.pegasys.artemis.storage.events.StoreDiskUpdateEvent;
import tech.pegasys.artemis.storage.utils.Bytes32Serializer;
import tech.pegasys.artemis.storage.utils.MapDBSerializer;
import tech.pegasys.artemis.storage.utils.UnsignedLongSerializer;

public class MapDbDatabase implements Database {
  private static final Logger LOG = LogManager.getLogger();
  private final DB db;
  private final Var<UnsignedLong> time;
  private final Var<UnsignedLong> genesisTime;
  private final Atomic.Var<Checkpoint> justifiedCheckpoint;
  private final Atomic.Var<Checkpoint> bestJustifiedCheckpoint;
  private final Atomic.Var<Checkpoint> finalizedCheckpoint;

  private final ConcurrentNavigableMap<UnsignedLong, Bytes32> finalizedRootsBySlot;
  private final ConcurrentMap<Bytes32, SignedBeaconBlock> finalizedBlocksByRoot;
  private final ConcurrentMap<Bytes32, BeaconState> finalizedStatesByRoot;
  private final ConcurrentMap<Bytes32, SignedBeaconBlock> hotBlocksByRoot;
  private final ConcurrentMap<Bytes32, BeaconState> hotStatesByRoot;

  private final ConcurrentMap<Checkpoint, BeaconState> checkpointStates;
  private final ConcurrentMap<UnsignedLong, Checkpoint> latestMessages;

  // In memory only
  private final ConcurrentNavigableMap<UnsignedLong, Set<Bytes32>> hotRootsBySlotCache =
      new ConcurrentSkipListMap<>();

  public static Database createOnDisk(final File directory, final boolean startFromDisk) {
    final File databaseFile = new File(directory, "artemis.db");
    try {
      if (!startFromDisk) {
        Files.deleteIfExists(databaseFile.toPath());
      }
    } catch (IOException e) {
      STDOUT.log(Level.WARN, "Failed to clear old database");
    }
    return new MapDbDatabase(DBMaker.fileDB(databaseFile));
  }

  public static Database createInMemory() {
    return new MapDbDatabase(DBMaker.memoryDB());
  }

  private MapDbDatabase(final Maker dbMaker) {
    db = dbMaker.transactionEnable().make();
    time = db.atomicVar("time", new UnsignedLongSerializer()).createOrOpen();
    genesisTime = db.atomicVar("genesisTime", new UnsignedLongSerializer()).createOrOpen();
    justifiedCheckpoint =
        db.atomicVar("justifiedCheckpoint", new MapDBSerializer<>(Checkpoint.class)).createOrOpen();
    bestJustifiedCheckpoint =
        db.atomicVar("bestJustifiedCheckpoint", new MapDBSerializer<>(Checkpoint.class))
            .createOrOpen();
    finalizedCheckpoint =
        db.atomicVar("finalizedCheckpoint", new MapDBSerializer<>(Checkpoint.class)).createOrOpen();

    finalizedRootsBySlot =
        db.treeMap("finalizedRootsBySlot", new UnsignedLongSerializer(), new Bytes32Serializer())
            .createOrOpen();
    finalizedBlocksByRoot =
        db.hashMap(
                "finalizedBlocksByRoot",
                new Bytes32Serializer(),
                new MapDBSerializer<>(SignedBeaconBlock.class))
            .createOrOpen();
    finalizedStatesByRoot =
        db.hashMap(
                "finalizedStatsByRoot",
                new Bytes32Serializer(),
                new MapDBSerializer<>(BeaconState.class))
            .createOrOpen();

    hotBlocksByRoot =
        db.hashMap(
                "hotBlocksByRoot",
                new Bytes32Serializer(),
                new MapDBSerializer<>(SignedBeaconBlock.class))
            .createOrOpen();
    hotStatesByRoot =
        db.hashMap(
                "hotStatesByRoot",
                new Bytes32Serializer(),
                new MapDBSerializer<>(BeaconState.class))
            .createOrOpen();

    checkpointStates =
        db.hashMap(
                "checkpointStates",
                new MapDBSerializer<>(Checkpoint.class),
                new MapDBSerializer<>(BeaconState.class))
            .createOrOpen();

    latestMessages =
        db.hashMap(
                "latestMessages",
                new UnsignedLongSerializer(),
                new MapDBSerializer<>(Checkpoint.class))
            .createOrOpen();

    // Recreate hotRootsBySlotCache
    hotBlocksByRoot.forEach(this::addToHotRootsBySlotCache);
  }

  @Override
  public synchronized void storeGenesis(final Store store) {
    try {
      time.set(store.getTime());
      genesisTime.set(store.getGenesisTime());
      justifiedCheckpoint.set(store.getJustifiedCheckpoint());
      finalizedCheckpoint.set(store.getFinalizedCheckpoint());
      bestJustifiedCheckpoint.set(store.getBestJustifiedCheckpoint());
      store
          .getBlockRoots()
          .forEach(
              root -> {
                final SignedBeaconBlock block = store.getSignedBlock(root);
                final BeaconState state = store.getBlockState(root);
                addHotBlock(root, block);
                hotStatesByRoot.put(root, state);
                finalizedRootsBySlot.put(block.getSlot(), root);
                finalizedBlocksByRoot.put(root, block);
                finalizedStatesByRoot.put(root, state);
              });
      checkpointStates.put(
          store.getJustifiedCheckpoint(),
          store.getBlockState(store.getJustifiedCheckpoint().getRoot()));
      checkpointStates.put(
          store.getBestJustifiedCheckpoint(),
          store.getBlockState(store.getBestJustifiedCheckpoint().getRoot()));
      db.commit();
    } catch (final RuntimeException | Error e) {
      db.rollback();
      throw e;
    }
  }

  @Override
  public synchronized void insert(final StoreDiskUpdateEvent event) {
    try {
      final Checkpoint previousFinalizedCheckpoint = finalizedCheckpoint.get();
      final Checkpoint newFinalizedCheckpoint =
          event.getFinalizedCheckpoint().orElse(previousFinalizedCheckpoint);
      event.getTime().ifPresent(time::set);
      event.getGenesisTime().ifPresent(genesisTime::set);
      event.getFinalizedCheckpoint().ifPresent(finalizedCheckpoint::set);
      event.getJustifiedCheckpoint().ifPresent(justifiedCheckpoint::set);
      event.getBestJustifiedCheckpoint().ifPresent(bestJustifiedCheckpoint::set);
      checkpointStates.putAll(event.getCheckpointStates());
      latestMessages.putAll(event.getLatestMessages());

      event.getBlocks().forEach(this::addHotBlock);
      hotStatesByRoot.putAll(event.getBlockStates());

      if (previousFinalizedCheckpoint == null
          || !previousFinalizedCheckpoint.equals(newFinalizedCheckpoint)) {
        recordFinalizedBlocks(newFinalizedCheckpoint);
        pruneCheckpointStates(newFinalizedCheckpoint);
        pruneHotBlocks(newFinalizedCheckpoint);
      }
      db.commit();
    } catch (final RuntimeException | Error e) {
      db.rollback();
      throw e;
    }
  }

  private void addHotBlock(final Bytes32 root, final SignedBeaconBlock block) {
    hotBlocksByRoot.put(root, block);
    addToHotRootsBySlotCache(root, block);
  }

  private void recordFinalizedBlocks(final Checkpoint newFinalizedCheckpoint) {
    LOG.debug(
        "Record finalized blocks for epoch {} starting at block {}",
        newFinalizedCheckpoint.getEpoch(),
        newFinalizedCheckpoint.getRoot());
    final UnsignedLong highestFinalizedSlot =
        finalizedRootsBySlot.isEmpty() ? UnsignedLong.ZERO : finalizedRootsBySlot.lastKey();
    Bytes32 newlyFinalizedBlockRoot = newFinalizedCheckpoint.getRoot();
    SignedBeaconBlock newlyFinalizedBlock = hotBlocksByRoot.get(newlyFinalizedBlockRoot);
    while (newlyFinalizedBlock != null
        && newlyFinalizedBlock.getSlot().compareTo(highestFinalizedSlot) > 0) {
      LOG.debug(
          "Recording finalized block {} at slot {}",
          newlyFinalizedBlock.getSlot(),
          newlyFinalizedBlockRoot);
      finalizedRootsBySlot.put(newlyFinalizedBlock.getSlot(), newlyFinalizedBlockRoot);
      finalizedBlocksByRoot.put(newlyFinalizedBlockRoot, newlyFinalizedBlock);
      final Optional<BeaconState> finalizedState = getState(newlyFinalizedBlockRoot);
      if (finalizedState.isPresent()) {
        finalizedStatesByRoot.put(newlyFinalizedBlockRoot, finalizedState.get());
      } else {
        LOG.error(
            "Missing finalized state {} for epoch {}",
            newlyFinalizedBlockRoot,
            newFinalizedCheckpoint.getEpoch());
      }
      newlyFinalizedBlockRoot = newlyFinalizedBlock.getMessage().getParent_root();
      newlyFinalizedBlock = hotBlocksByRoot.get(newlyFinalizedBlockRoot);
    }

    if (newlyFinalizedBlock == null) {
      LOG.error(
          "Missing finalized block {} for epoch {}",
          newlyFinalizedBlockRoot,
          newFinalizedCheckpoint.getEpoch());
    }
  }

  private void pruneCheckpointStates(final Checkpoint newFinalizedCheckpoint) {
    checkpointStates
        .keySet()
        .removeIf(
            checkpoint -> checkpoint.getEpoch().compareTo(newFinalizedCheckpoint.getEpoch()) < 0);
  }

  private void pruneHotBlocks(final Checkpoint newFinalizedCheckpoint) {
    // TODO: Can we prune blocks from in the finalized epoch as well?
    final UnsignedLong startOfFinalizedEpoch =
        compute_start_slot_at_epoch(newFinalizedCheckpoint.getEpoch());
    final ConcurrentNavigableMap<UnsignedLong, Set<Bytes32>> toRemove =
        hotRootsBySlotCache.headMap(startOfFinalizedEpoch);
    LOG.trace("Pruning slots {} from non-finalized pool", toRemove::keySet);
    toRemove
        .values()
        .forEach(
            roots -> {
              hotBlocksByRoot.keySet().removeAll(roots);
              hotStatesByRoot.keySet().removeAll(roots);
            });
    hotRootsBySlotCache.keySet().removeAll(toRemove.keySet());
  }

  private void addToHotRootsBySlotCache(final Bytes32 root, final SignedBeaconBlock block) {
    hotRootsBySlotCache
        .computeIfAbsent(
            block.getSlot(), key -> Collections.newSetFromMap(new ConcurrentHashMap<>()))
        .add(root);
  }

  @Override
  public Store createMemoryStore() {
    return new Store(
        time.get(),
        genesisTime.get(),
        justifiedCheckpoint.get(),
        finalizedCheckpoint.get(),
        bestJustifiedCheckpoint.get(),
        hotBlocksByRoot,
        hotStatesByRoot,
        checkpointStates,
        latestMessages);
  }

  @Override
  public Optional<Bytes32> getFinalizedRootAtSlot(final UnsignedLong slot) {
    return Optional.ofNullable(finalizedRootsBySlot.get(slot));
  }

  @Override
  public Optional<Bytes32> getLatestFinalizedRootAtSlot(final UnsignedLong slot) {
    return Optional.ofNullable(finalizedRootsBySlot.headMap(slot, true).lastEntry())
        .map(Entry::getValue);
  }

  @Override
  public Optional<SignedBeaconBlock> getSignedBlock(final Bytes32 root) {
    final SignedBeaconBlock block = hotBlocksByRoot.get(root);
    return block != null
        ? Optional.of(block)
        : Optional.ofNullable(finalizedBlocksByRoot.get(root));
  }

  @Override
  public Optional<BeaconState> getState(final Bytes32 root) {
    return Optional.ofNullable(hotStatesByRoot.get(root));
  }

  @Override
  public void close() {
    db.close();
  }
}
