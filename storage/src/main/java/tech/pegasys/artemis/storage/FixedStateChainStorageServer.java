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

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.common.primitives.UnsignedLong;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.logging.log4j.Level;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.BeaconStateWithCache;
import tech.pegasys.artemis.datastructures.state.Checkpoint;
import tech.pegasys.artemis.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.artemis.storage.events.DBStoreValidEvent;
import tech.pegasys.artemis.storage.events.NodeStartEvent;
import tech.pegasys.artemis.util.alogger.ALogger;
import tech.pegasys.artemis.util.alogger.ALogger.Color;
import tech.pegasys.artemis.util.config.ArtemisConfiguration;

public class FixedStateChainStorageServer implements ChainStorage {
  private static final ALogger STDOUT = new ALogger("stdout");

  private final EventBus eventBus;
  private final ArtemisConfiguration config;

  public FixedStateChainStorageServer(EventBus eventBus, ArtemisConfiguration config) {
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
        final DBStoreValidEvent dbStoreValidEvent =
            loadInitialState(Bytes.wrap(Files.readAllBytes(new File(interopStartState).toPath())));
        this.eventBus.post(dbStoreValidEvent);
      } catch (final IOException e) {
        throw new IllegalStateException("Failed to load initial state", e);
      }
    }
  }

  DBStoreValidEvent loadInitialState(final Bytes beaconStateData) {
    final BeaconStateWithCache initialBeaconState = loadBeaconState(beaconStateData);

    final Store initialStore =
        createInitialStore(
            initialBeaconState, UnsignedLong.valueOf(config.getInteropGenesisTime()));
    return new DBStoreValidEvent(initialStore, initialBeaconState.getSlot());
  }

  private static Store createInitialStore(
      final BeaconStateWithCache initialState, final UnsignedLong genesisTime) {
    return get_genesis_store(initialState);
  }

  public static Store get_genesis_store(BeaconStateWithCache genesis_state) {
    BeaconBlock genesis_block = new BeaconBlock(genesis_state.hash_tree_root());
    Bytes32 root = genesis_block.signing_root("signature");
    Checkpoint justified_checkpoint = new Checkpoint(UnsignedLong.valueOf(GENESIS_EPOCH), root);
    Checkpoint finalized_checkpoint = new Checkpoint(UnsignedLong.valueOf(GENESIS_EPOCH), root);
    ConcurrentHashMap<Bytes32, BeaconBlock> blocks = new ConcurrentHashMap<>();
    ConcurrentHashMap<Bytes32, BeaconState> block_states = new ConcurrentHashMap<>();
    ConcurrentHashMap<Checkpoint, BeaconState> checkpoint_states = new ConcurrentHashMap<>();
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
    final BeaconState initialState;
    initialState = SimpleOffsetSerializer.deserialize(data, BeaconState.class);
    return new BeaconStateWithCache(
        initialState.getGenesis_time(),
        initialState.getSlot(),
        initialState.getFork(),
        initialState.getLatest_block_header(),
        initialState.getBlock_roots(),
        initialState.getState_roots(),
        initialState.getHistorical_roots(),
        initialState.getEth1_data(),
        initialState.getEth1_data_votes(),
        initialState.getEth1_deposit_index(),
        initialState.getValidators(),
        initialState.getBalances(),
        initialState.getStart_shard(),
        initialState.getRandao_mixes(),
        initialState.getActive_index_roots(),
        initialState.getCompact_committees_roots(),
        initialState.getSlashings(),
        initialState.getPrevious_epoch_attestations(),
        initialState.getCurrent_epoch_attestations(),
        initialState.getPrevious_crosslinks(),
        initialState.getCurrent_crosslinks(),
        initialState.getJustification_bits(),
        initialState.getPrevious_justified_checkpoint(),
        initialState.getCurrent_justified_checkpoint(),
        initialState.getFinalized_checkpoint());
  }
}
