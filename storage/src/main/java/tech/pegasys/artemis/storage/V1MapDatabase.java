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

import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;
import static tech.pegasys.artemis.util.alogger.ALogger.STDOUT;

import com.google.common.primitives.UnsignedLong;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.LongStream;
import org.apache.logging.log4j.Level;
import org.apache.tuweni.bytes.Bytes32;
import org.mapdb.Atomic;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.DBMaker.Maker;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.Checkpoint;
import tech.pegasys.artemis.storage.utils.Bytes32Serializer;
import tech.pegasys.artemis.storage.utils.MapDBSerializer;
import tech.pegasys.artemis.storage.utils.UnsignedLongSerializer;
import tech.pegasys.artemis.util.alogger.ALogger;

public class V1MapDatabase implements Database {

  // Locks
  private final ReadWriteLock lock = new ReentrantReadWriteLock();

  // Database instance
  private DB db;

  // Store object
  private final Atomic.Var<UnsignedLong> time;
  private final Atomic.Var<UnsignedLong> genesisTime;
  private final Atomic.Var<Checkpoint> justifiedCheckpoint;
  private final Atomic.Var<Checkpoint> finalizedCheckpoint;
  private final Atomic.Var<Checkpoint> bestJustifiedCheckpoint;
  private final ConcurrentMap<Bytes32, BeaconBlock> blocks;
  private final ConcurrentMap<Bytes32, BeaconState> block_states;
  private final ConcurrentMap<Checkpoint, BeaconState> checkpoint_states;
  private final ConcurrentMap<UnsignedLong, Checkpoint> latest_messages;

  // Slot -> Map references
  private final ConcurrentNavigableMap<UnsignedLong, Bytes32> block_root_references;
  private final ConcurrentMap<UnsignedLong, Checkpoint> checkpoint_references;
  private final Atomic.Var<UnsignedLong> latest_slot;

  static V1MapDatabase createForFile(final String dbFileName, final boolean startFromDisk) {
    try {
      if (!startFromDisk) Files.deleteIfExists(Paths.get(dbFileName));
    } catch (IOException e) {
      STDOUT.log(Level.WARN, "Failed to clear old database");
    }
    return new V1MapDatabase(DBMaker.fileDB(dbFileName));
  }

  static V1MapDatabase createInMemory() {
    return new V1MapDatabase(DBMaker.memoryDB());
  }

  @SuppressWarnings("CheckReturnValue")
  private V1MapDatabase(Maker dbMaker) {
    db = dbMaker.transactionEnable().make();

    // Store initialization
    time = db.atomicVar("time", new UnsignedLongSerializer()).createOrOpen();
    genesisTime = db.atomicVar("genesis_time", new UnsignedLongSerializer()).createOrOpen();
    justifiedCheckpoint =
        db.atomicVar("justified_checkpoint", new MapDBSerializer<>(Checkpoint.class))
            .createOrOpen();
    finalizedCheckpoint =
        db.atomicVar("finalized_checkpoint", new MapDBSerializer<>(Checkpoint.class))
            .createOrOpen();
    bestJustifiedCheckpoint =
        db.atomicVar("best_justified_checkpoint", new MapDBSerializer<>(Checkpoint.class))
            .createOrOpen();
    blocks =
        db.hashMap("blocks_map", new Bytes32Serializer(), new MapDBSerializer<>(BeaconBlock.class))
            .createOrOpen();
    block_states =
        db.hashMap(
                "block_states_map",
                new Bytes32Serializer(),
                new MapDBSerializer<>(BeaconState.class))
            .createOrOpen();
    checkpoint_states =
        db.hashMap(
                "checkpoint_states_map",
                new MapDBSerializer<>(Checkpoint.class),
                new MapDBSerializer<>(BeaconState.class))
            .createOrOpen();
    latest_messages =
        db.hashMap(
                "latest_messages_map",
                new UnsignedLongSerializer(),
                new MapDBSerializer<>(Checkpoint.class))
            .createOrOpen();

    // Optimization variables (used to not load the entire database content in memory when Artemis
    // restarts)
    latest_slot = db.atomicVar("latest_slot", new UnsignedLongSerializer()).createOrOpen();
    try {
      latest_slot.get().equals(null);
    } catch (Exception e) {
      latest_slot.set(UnsignedLong.ZERO);
    }
    block_root_references =
        db.treeMap(
                "block_root_references_map", new UnsignedLongSerializer(), new Bytes32Serializer())
            .createOrOpen();
    checkpoint_references =
        db.hashMap(
                "checkpoint_references_map",
                new UnsignedLongSerializer(),
                new MapDBSerializer<>(Checkpoint.class))
            .createOrOpen();
  }

  @Override
  public Store createMemoryStore() {
    UnsignedLong time_memory = time.get();
    UnsignedLong genesis_time_memory = genesisTime.get();
    Checkpoint justified_checkpoint_memory = justifiedCheckpoint.get();
    Checkpoint finalized_checkpoint_memory = finalizedCheckpoint.get();
    Checkpoint best_justified_checkpoint_memory = bestJustifiedCheckpoint.get();
    Map<UnsignedLong, Checkpoint> latest_messages_memory = latest_messages;
    Map<Bytes32, BeaconBlock> blocks_memory = new HashMap<>();
    Map<Bytes32, BeaconState> block_states_memory = new HashMap<>();
    Map<Checkpoint, BeaconState> checkpoint_states_memory = new HashMap<>();

    UnsignedLong slot = latest_slot.get();

    LongStream.range(
            compute_start_slot_at_epoch(finalized_checkpoint_memory.getEpoch()).longValue(),
            slot.longValue() + 1)
        .forEach(
            currentSlot -> {
              Bytes32 root = block_root_references.get(UnsignedLong.valueOf(currentSlot));
              final UnsignedLong checkpointSlot =
                  compute_start_slot_at_epoch(
                      compute_epoch_at_slot(UnsignedLong.valueOf(currentSlot)));

              if (checkpoint_references.containsKey(checkpointSlot)) {
                Checkpoint checkpoint = checkpoint_references.get(checkpointSlot);
                checkpoint_states_memory.put(checkpoint, checkpoint_states.get(checkpoint));
              }

              blocks_memory.put(root, blocks.get(root));
              block_states_memory.put(root, block_states.get(root));
            });

    return new Store(
        time_memory,
        genesis_time_memory,
        justified_checkpoint_memory,
        finalized_checkpoint_memory,
        best_justified_checkpoint_memory,
        blocks_memory,
        block_states_memory,
        checkpoint_states_memory,
        latest_messages_memory);
  }

  @Override
  public void storeGenesis(final Store store) {}

  @Override
  public void insert(Store.Transaction transaction) {
    final Lock writeLock = lock.writeLock();
    writeLock.lock();
    try {
      transaction
          .getSlot()
          .ifPresent(
              transaction_slot -> {
                if (latest_slot.get().compareTo(transaction_slot) < 0) {
                  latest_slot.set(transaction_slot);
                }
              });

      time.set(transaction.getTime());
      genesisTime.set(transaction.getGenesisTime());
      justifiedCheckpoint.set(transaction.getJustifiedCheckpoint());
      finalizedCheckpoint.set(transaction.getFinalizedCheckpoint());
      bestJustifiedCheckpoint.set(transaction.getBestJustifiedCheckpoint());
      blocks.putAll(transaction.getBlocks());
      block_states.putAll(transaction.getBlockStates());
      checkpoint_states.putAll(transaction.getCheckpointStates());
      latest_messages.putAll(transaction.getLatestMessages());

      Map<UnsignedLong, Bytes32> new_block_root_references = new HashMap<>();
      Map<UnsignedLong, Checkpoint> new_checkpoint_references = new HashMap<>();

      transaction
          .getBlocks()
          .forEach((root, block) -> new_block_root_references.put(block.getSlot(), root));

      transaction
          .getCheckpointStates()
          .keySet()
          .forEach(
              checkpoint ->
                  new_checkpoint_references.put(
                      compute_start_slot_at_epoch(checkpoint.getEpoch()), checkpoint));

      block_root_references.putAll(new_block_root_references);
      checkpoint_references.putAll(new_checkpoint_references);

      db.commit();
    } catch (Exception e) {

      db.rollback();
      STDOUT.log(
          Level.WARN, "Unable to insert new data into DB " + e.toString(), ALogger.Color.RED);

    } finally {
      writeLock.unlock();
    }
  }

  public Atomic.Var<UnsignedLong> getTime() {
    return time;
  }

  public Atomic.Var<UnsignedLong> getGenesisTime() {
    return genesisTime;
  }

  public Atomic.Var<Checkpoint> getJustifiedCheckpoint() {
    return justifiedCheckpoint;
  }

  public Atomic.Var<Checkpoint> getFinalizedCheckpoint() {
    return finalizedCheckpoint;
  }

  public Atomic.Var<Checkpoint> getBestJustifiedCheckpoint() {
    return bestJustifiedCheckpoint;
  }

  @Override
  public Optional<BeaconBlock> getBlock(Bytes32 blockRoot) {
    boolean blockContained = blocks.containsKey(blockRoot);
    return blockContained ? Optional.of(blocks.get(blockRoot)) : Optional.empty();
  }

  @Override
  public Optional<BeaconState> getState(Bytes32 blockRoot) {
    boolean blockContained = block_states.containsKey(blockRoot);
    return blockContained ? Optional.of(block_states.get(blockRoot)) : Optional.empty();
  }

  public Optional<BeaconState> getCheckpoint_state(Checkpoint checkpoint) {
    boolean blockContained = checkpoint_states.containsKey(checkpoint);
    return blockContained ? Optional.of(checkpoint_states.get(checkpoint)) : Optional.empty();
  }

  public Optional<Checkpoint> getLatest_message(UnsignedLong validatorIndex) {
    boolean blockContained = latest_messages.containsKey(validatorIndex);
    return blockContained ? Optional.of(latest_messages.get(validatorIndex)) : Optional.empty();
  }

  @Override
  public Optional<Bytes32> getFinalizedRootAtSlot(final UnsignedLong slot) {
    final Checkpoint finalizedCheckpoint = this.finalizedCheckpoint.get();
    if (finalizedCheckpoint == null || slotIsNotFinalized(slot, finalizedCheckpoint)) {
      return Optional.empty();
    }
    return Optional.ofNullable(block_root_references.headMap(slot, true).lastEntry())
        .map(Entry::getValue);
  }

  private boolean slotIsNotFinalized(
      final UnsignedLong slot, final Checkpoint finalizedCheckpoint) {
    final UnsignedLong firstSlotAfterFinalizedEpoch =
        compute_start_slot_at_epoch(finalizedCheckpoint.getEpoch().plus(UnsignedLong.ONE));
    return firstSlotAfterFinalizedEpoch.compareTo(slot) <= 0;
  }

  @Override
  public void close() {
    db.close();
  }
}
