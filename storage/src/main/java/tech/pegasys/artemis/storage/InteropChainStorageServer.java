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
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import org.apache.logging.log4j.Level;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.Constants;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.BeaconStateWithCache;
import tech.pegasys.artemis.datastructures.state.Checkpoint;
import tech.pegasys.artemis.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.artemis.datastructures.util.StartupUtil;
import tech.pegasys.artemis.storage.events.DBStoreValidEvent;
import tech.pegasys.artemis.storage.events.NodeStartEvent;
import tech.pegasys.artemis.util.alogger.ALogger;
import tech.pegasys.artemis.util.alogger.ALogger.Color;
import tech.pegasys.artemis.util.config.ArtemisConfiguration;

public class InteropChainStorageServer implements ChainStorage {
  private static final ALogger STDOUT = new ALogger("stdout");

  private final EventBus eventBus;
  private final ArtemisConfiguration config;

  public InteropChainStorageServer(EventBus eventBus, ArtemisConfiguration config) {
    this.eventBus = eventBus;
    this.config = config;
    eventBus.register(this);
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
}
