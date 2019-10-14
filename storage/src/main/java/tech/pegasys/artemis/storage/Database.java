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

import com.google.common.primitives.UnsignedLong;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
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

  private static final ALogger STDOUT = new ALogger("stdout");

  // Locks
  private final ReadWriteLock lock = new ReentrantReadWriteLock();

  // Database instance
  private DB db;

  // Store object
  private Atomic.Var<UnsignedLong> time;
  private Atomic.Var<Checkpoint> justifiedCheckpoint;
  private Atomic.Var<Checkpoint> finalizedCheckpoint;
  private ConcurrentMap<Bytes32, BeaconBlock> blocks;
  private ConcurrentMap<Bytes32, BeaconState> block_states;
  private ConcurrentMap<Checkpoint, BeaconState> checkpoint_states;
  private ConcurrentMap<UnsignedLong, LatestMessage> latest_messages;

  Database(String dbFileName, boolean clearOldDatabase) {
    try {
      if (clearOldDatabase) Files.deleteIfExists(Paths.get(dbFileName));
    } catch (IOException e) {
      STDOUT.log(Level.WARN, "Failed to clear old database");
    }

    db = DBMaker.fileDB(dbFileName).transactionEnable().make();

    // Store initialization
    time = db.atomicVar("time", new UnsignedLongSerializer()).createOrOpen();
    justifiedCheckpoint =
        db.atomicVar("justified_checkpoint", new MapDBSerializer<Checkpoint>(Checkpoint.class))
            .createOrOpen();
    finalizedCheckpoint =
        db.atomicVar("finalized_checkpoint", new MapDBSerializer<Checkpoint>(Checkpoint.class))
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
                new MapDBSerializer<LatestMessage>(LatestMessage.class))
            .createOrOpen();
  }

  public void insert(Store.Transaction transaction) {
    final Lock writeLock = lock.writeLock();
    writeLock.lock();
    try {

      time.set(transaction.getTime());
      justifiedCheckpoint.set(transaction.getJustifiedCheckpoint());
      finalizedCheckpoint.set(transaction.getFinalizedCheckpoint());
      blocks.putAll(transaction.getBlocks());
      block_states.putAll(transaction.getBlockStates());
      checkpoint_states.putAll(transaction.getCheckpointStates());
      latest_messages.putAll(transaction.getLatestMessages());

      db.commit();
    } catch (Exception e) {

      db.rollback();

    } finally {
      writeLock.unlock();
    }
  }

  public Atomic.Var<UnsignedLong> getTime() {
    return time;
  }

  public Atomic.Var<Checkpoint> getJustifiedCheckpoint() {
    return justifiedCheckpoint;
  }

  public Atomic.Var<Checkpoint> getFinalizedCheckpoint() {
    return finalizedCheckpoint;
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

  public Optional<LatestMessage> getLatest_message(UnsignedLong validatorIndex) {
    boolean blockContained = latest_messages.containsKey(validatorIndex);
    return blockContained ? Optional.of(latest_messages.get(validatorIndex)) : Optional.empty();
  }

  public void close() {
    db.close();
  }

  @SuppressWarnings("rawtypes")
  private static class MapDBSerializer<T extends SimpleOffsetSerializable>
      implements Serializer<T>, Serializable {

    private Class classInfo;

    public MapDBSerializer(Class classInformation) {
      this.classInfo = classInformation;
    }

    @Override
    public void serialize(DataOutput2 out, SimpleOffsetSerializable object) throws IOException {
      out.writeChars(SimpleOffsetSerializer.serialize(object).toHexString());
    }

    @Override
    public T deserialize(DataInput2 in, int available) throws IOException {
      return SimpleOffsetSerializer.deserialize(Bytes.fromHexString(in.readLine()), classInfo);
    }
  }
}
