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

import static tech.pegasys.artemis.datastructures.Constants.GENESIS_EPOCH;
import static tech.pegasys.artemis.datastructures.Constants.SECONDS_PER_SLOT;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.common.primitives.UnsignedLong;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
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
import tech.pegasys.artemis.datastructures.Constants;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.BeaconStateWithCache;
import tech.pegasys.artemis.datastructures.state.Checkpoint;
import tech.pegasys.artemis.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.artemis.datastructures.util.StartupUtil;
import tech.pegasys.artemis.storage.events.DBStoreValidEvent;
import tech.pegasys.artemis.storage.events.NodeStartEvent;
import tech.pegasys.artemis.storage.events.StoreDiskUpdateEvent;
import tech.pegasys.artemis.storage.utils.Bytes32Serializer;
import tech.pegasys.artemis.storage.utils.UnsignedLongSerializer;
import tech.pegasys.artemis.util.alogger.ALogger;
import tech.pegasys.artemis.util.alogger.ALogger.Color;
import tech.pegasys.artemis.util.config.ArtemisConfiguration;
import tech.pegasys.artemis.util.sos.SimpleOffsetSerializable;

public class InteropChainStorageServer {
  private static final ALogger STDOUT = new ALogger("stdout");

  private final EventBus eventBus;
  private final ArtemisConfiguration config;

  // Locks
  private final ReadWriteLock lock = new ReentrantReadWriteLock();

  // Database instance
  private static DB db = DBMaker.fileDB("artemis.db").transactionEnable().make();

  // Store object
  private Atomic.Var<UnsignedLong> time;
  private Atomic.Var<Checkpoint> justifiedCheckpoint;
  private Atomic.Var<Checkpoint> finalizedCheckpoint;
  private ConcurrentMap<Bytes32, BeaconBlock> blocks;
  private ConcurrentMap<Bytes32, BeaconState> block_states;
  private ConcurrentMap<Checkpoint, BeaconState> checkpoint_states;
  private ConcurrentMap<UnsignedLong, LatestMessage> latest_messages;

  public InteropChainStorageServer(EventBus eventBus, ArtemisConfiguration config) {
    this.eventBus = eventBus;
    this.config = config;
    eventBus.register(this);

    // Store initialization
    time = db.atomicVar("time", new UnsignedLongSerializer()).createOrOpen();
    justifiedCheckpoint =
            db.atomicVar("justified_checkpoint", new MapDBSerializer<Checkpoint>(Checkpoint.class)).createOrOpen();
    finalizedCheckpoint =
            db.atomicVar("finalized_checkpoint", new MapDBSerializer<Checkpoint>(Checkpoint.class)).createOrOpen();
    blocks =
            db.hashMap("blocks_map", new Bytes32Serializer(), new MapDBSerializer<BeaconBlock>(BeaconBlock.class))
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

  @Subscribe
  public void onStoreDiskUpdate(StoreDiskUpdateEvent storeDiskUpdateEvent) {
    Store.Transaction transaction = storeDiskUpdateEvent.getTransaction();

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
    } finally {
      writeLock.unlock();
    }
  }

  @Subscribe
  public void onNodeStart(NodeStartEvent nodeStartEvent) {
    final String interopStartState = config.getInteropStartState();
    if (config.getInteropActive() && interopStartState != null) {
      try {
        STDOUT.log(Level.INFO, "Loading initial state from " + interopStartState, Color.GREEN);
        final DBStoreValidEvent event =
            loadInitialState(Bytes.wrap(Files.readAllBytes(new File(interopStartState).toPath())));
        this.eventBus.post(event);
      } catch (final IOException e) {
        throw new IllegalStateException("Failed to load initial state", e);
      }
    } else if (this.config.getDepositMode().equals(Constants.DEPOSIT_TEST)) {
      BeaconStateWithCache initialState = StartupUtil.createInitialBeaconState(this.config);
      this.eventBus.post(createDBStoreValidEvent(initialState));
    }
  }

  DBStoreValidEvent loadInitialState(final Bytes beaconStateData) {
    return createDBStoreValidEvent(loadBeaconState(beaconStateData));
  }

  private DBStoreValidEvent createDBStoreValidEvent(final BeaconStateWithCache initialBeaconState) {
    final Store initialStore = get_genesis_store(initialBeaconState);
    UnsignedLong genesisTime = initialBeaconState.getGenesis_time();
    UnsignedLong currentTime = UnsignedLong.valueOf(System.currentTimeMillis() / 1000);
    UnsignedLong currentSlot = UnsignedLong.ZERO;
    if (currentTime.compareTo(genesisTime) > 0) {
      UnsignedLong deltaTime = currentTime.minus(genesisTime);
      currentSlot = deltaTime.dividedBy(UnsignedLong.valueOf(SECONDS_PER_SLOT));
    } else {
      try {
        UnsignedLong sleepTime = genesisTime.minus(currentTime);
        // sleep until genesis
        STDOUT.log(Level.INFO, "Sleep for " + sleepTime + " seconds until genesis.", Color.GREEN);
        Thread.sleep(sleepTime.longValue());
      } catch (InterruptedException e) {
        e.printStackTrace();
        throw new IllegalArgumentException("Error in loadInitialState()");
      }
    }
    return new DBStoreValidEvent(initialStore, currentSlot, genesisTime, initialBeaconState);
  }

  public static Store get_genesis_store(BeaconStateWithCache genesis_state) {
    BeaconBlock genesis_block = new BeaconBlock(genesis_state.hash_tree_root());
    Bytes32 root = genesis_block.signing_root("signature");
    Checkpoint justified_checkpoint = new Checkpoint(UnsignedLong.valueOf(GENESIS_EPOCH), root);
    Checkpoint finalized_checkpoint = new Checkpoint(UnsignedLong.valueOf(GENESIS_EPOCH), root);
    Map<Bytes32, BeaconBlock> blocks = new HashMap<>();
    Map<Bytes32, BeaconState> block_states = new HashMap<>();
    Map<Checkpoint, BeaconState> checkpoint_states = new HashMap<>();
    blocks.put(root, genesis_block);
    block_states.put(root, new BeaconStateWithCache(genesis_state));
    checkpoint_states.put(justified_checkpoint, new BeaconStateWithCache(genesis_state));
    return new Store(
        genesis_state.getGenesis_time(),
        justified_checkpoint,
        finalized_checkpoint,
        blocks,
        block_states,
        checkpoint_states);
  }

  private static BeaconStateWithCache loadBeaconState(final Bytes data) {
    return BeaconStateWithCache.fromBeaconState(
        SimpleOffsetSerializer.deserialize(data, BeaconState.class));
  }

  private class MapDBSerializer<T extends  SimpleOffsetSerializable> implements Serializer<T>, Serializable {

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
