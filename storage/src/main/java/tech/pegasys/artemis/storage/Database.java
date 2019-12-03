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

import com.google.common.eventbus.EventBus;
import com.google.common.primitives.UnsignedLong;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.LongStream;
import org.apache.logging.log4j.Level;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.mapdb.Atomic;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.DataInput2;
import org.mapdb.DataOutput2;
import org.mapdb.Serializer;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.Checkpoint;
import tech.pegasys.artemis.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.artemis.storage.utils.Bytes32Serializer;
import tech.pegasys.artemis.storage.utils.UnsignedLongSerializer;
import tech.pegasys.artemis.util.alogger.ALogger;
import tech.pegasys.artemis.util.sos.SimpleOffsetSerializable;

public class Database {

  // Locks
  private final ReadWriteLock lock = new ReentrantReadWriteLock();

  // Database instance
  private DB db;

  // Store object
  private Atomic.Var<UnsignedLong> time;
  private Atomic.Var<UnsignedLong> genesisTime;
  private Atomic.Var<Checkpoint> justifiedCheckpoint;
  private Atomic.Var<Checkpoint> finalizedCheckpoint;
  private Atomic.Var<Checkpoint> bestJustifiedCheckpoint;
  private ConcurrentMap<Bytes32, BeaconBlock> blocks;
  private ConcurrentMap<Bytes32, BeaconState> block_states;
  private ConcurrentMap<Checkpoint, BeaconState> checkpoint_states;
  private ConcurrentMap<UnsignedLong, Checkpoint> latest_messages;

  // Slot -> Map references
  private ConcurrentMap<UnsignedLong, Bytes32> block_root_references;
  private ConcurrentMap<UnsignedLong, Checkpoint> checkpoint_references;
  private Atomic.Var<UnsignedLong> latest_slot;

  @SuppressWarnings("CheckReturnValue")
  Database(String dbFileName, EventBus eventBus, boolean startFromDisk) {
    try {
      if (!startFromDisk) Files.deleteIfExists(Paths.get(dbFileName));
    } catch (IOException e) {
      STDOUT.log(Level.WARN, "Failed to clear old database");
    }

    db = DBMaker.fileDB(dbFileName).transactionEnable().make();

    // Store initialization
    time = db.atomicVar("time", new UnsignedLongSerializer()).createOrOpen();
    genesisTime = db.atomicVar("genesis_time", new UnsignedLongSerializer()).createOrOpen();
    justifiedCheckpoint =
        db.atomicVar("justified_checkpoint", new MapDBSerializer<Checkpoint>(Checkpoint.class))
            .createOrOpen();
    finalizedCheckpoint =
        db.atomicVar("finalized_checkpoint", new MapDBSerializer<Checkpoint>(Checkpoint.class))
            .createOrOpen();
    bestJustifiedCheckpoint =
        db.atomicVar("best_justified_checkpoint", new MapDBSerializer<Checkpoint>(Checkpoint.class))
            .createOrOpen();
    blocks =
        db.hashMap(
                "blocks_map",
                new Bytes32Serializer(),
                new MapDBSerializer<BeaconBlock>(BeaconBlock.class))
            .createOrOpen();
    block_states =
        db.hashMap(
                "block_states_map",
                new Bytes32Serializer(),
                new MapDBSerializer<BeaconState>(BeaconState.class))
            .createOrOpen();
    checkpoint_states =
        db.hashMap(
                "checkpoint_states_map",
                new MapDBSerializer<Checkpoint>(Checkpoint.class),
                new MapDBSerializer<BeaconState>(BeaconState.class))
            .createOrOpen();
    latest_messages =
        db.hashMap(
                "latest_messages_map",
                new UnsignedLongSerializer(),
                new MapDBSerializer<Checkpoint>(Checkpoint.class))
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
        db.hashMap(
                "block_root_references_map", new UnsignedLongSerializer(), new Bytes32Serializer())
            .createOrOpen();
    checkpoint_references =
        db.hashMap(
                "checkpoint_references_map",
                new UnsignedLongSerializer(),
                new MapDBSerializer<Checkpoint>(Checkpoint.class))
            .createOrOpen();

    if (startFromDisk) {
      STDOUT.log(
          Level.INFO,
          "Using the database to load Store and thus the previously built Store will be overwritten.",
          ALogger.Color.GREEN);
      Store memoryStore = createMemoryStore();
      eventBus.post(memoryStore);
    }
  }

  public Store createMemoryStore() {
    UnsignedLong time_memory = time.get();
    UnsignedLong genesis_time_memory = genesisTime.get();
    Checkpoint justified_checkpoint_memory = new Checkpoint(justifiedCheckpoint.get());
    Checkpoint finalized_checkpoint_memory = new Checkpoint(finalizedCheckpoint.get());
    Checkpoint best_justified_checkpoint_memory = new Checkpoint(bestJustifiedCheckpoint.get());
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
              Checkpoint checkpoint =
                  checkpoint_references.get(
                      compute_start_slot_at_epoch(
                          compute_epoch_at_slot(UnsignedLong.valueOf(currentSlot))));

              blocks_memory.put(root, blocks.get(root));
              block_states_memory.put(root, block_states.get(root));
              checkpoint_states_memory.put(checkpoint, checkpoint_states.get(checkpoint));
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

  public Optional<BeaconBlock> getBlock(Bytes32 blockRoot) {
    boolean blockContained = blocks.containsKey(blockRoot);
    return blockContained ? Optional.of(blocks.get(blockRoot)) : Optional.empty();
  }

  public Optional<BeaconState> getBlock_state(Bytes32 blockRoot) {
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

  public void close() {
    db.close();
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static class MapDBSerializer<T> implements Serializer<T>, Serializable {

    private Class classInfo;

    public MapDBSerializer(Class classInformation) {
      this.classInfo = classInformation;
    }

    @Override
    public void serialize(DataOutput2 out, Object object) throws IOException {
      out.writeChars(
          SimpleOffsetSerializer.serialize((SimpleOffsetSerializable) object).toHexString());
    }

    @Override
    public T deserialize(DataInput2 in, int available) throws IOException {
      return (T) SimpleOffsetSerializer.deserialize(Bytes.fromHexString(in.readLine()), classInfo);
    }
  }
}
