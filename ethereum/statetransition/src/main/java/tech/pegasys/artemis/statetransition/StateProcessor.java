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

package tech.pegasys.artemis.statetransition;

import static tech.pegasys.artemis.statetransition.StateTransition.process_slots;
import static tech.pegasys.artemis.statetransition.util.ForkChoiceUtil.get_genesis_store;
import static tech.pegasys.artemis.statetransition.util.ForkChoiceUtil.get_head;
import static tech.pegasys.artemis.statetransition.util.ForkChoiceUtil.on_attestation;
import static tech.pegasys.artemis.statetransition.util.ForkChoiceUtil.on_block;
import static tech.pegasys.artemis.statetransition.util.ForkChoiceUtil.on_tick;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.common.primitives.UnsignedLong;
import org.apache.logging.log4j.Level;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.crypto.SECP256K1.PublicKey;
import org.json.simple.parser.ParseException;
import tech.pegasys.artemis.data.RawRecord;
import tech.pegasys.artemis.datastructures.Constants;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.operations.Deposit;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.BeaconStateWithCache;
import tech.pegasys.artemis.datastructures.util.BeaconBlockUtil;
import tech.pegasys.artemis.datastructures.util.BeaconStateUtil;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.datastructures.util.DepositUtil;
import tech.pegasys.artemis.service.serviceutils.ServiceConfig;
import tech.pegasys.artemis.statetransition.util.EpochProcessingException;
import tech.pegasys.artemis.statetransition.util.SlotProcessingException;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.storage.Store;
import tech.pegasys.artemis.util.alogger.ALogger;
import tech.pegasys.artemis.util.config.ArtemisConfiguration;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static tech.pegasys.artemis.datastructures.Constants.MIN_GENESIS_ACTIVE_VALIDATOR_COUNT;
import static tech.pegasys.artemis.datastructures.Constants.MIN_GENESIS_TIME;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.initialize_beacon_state_from_eth1;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.is_valid_genesis_state;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.is_valid_genesis_stateSim;
import static tech.pegasys.artemis.statetransition.StateTransition.process_slots;

/** Class to manage the state tree and initiate state transitions */
public class StateProcessor {

  private final EventBus eventBus;
  private Store store;
  private ChainStorageClient chainStorageClient;
  private ArtemisConfiguration config;
  private static final ALogger STDOUT = new ALogger("stdout");
  private List<Deposit> deposits;

  public StateProcessor(ServiceConfig config, ChainStorageClient chainStorageClient) {
    this.eventBus = config.getEventBus();
    this.config = config.getConfig();
    this.eventBus.register(this);
    this.chainStorageClient = chainStorageClient;
  }

  public void onEth2Genesis(BeaconState initial_state) {
    STDOUT.log(Level.INFO, "******* Eth2Genesis Event detected ******* : ");
    this.nodeSlot = UnsignedLong.valueOf(Constants.GENESIS_SLOT);
    this.nodeTime =
        UnsignedLong.valueOf(Constants.GENESIS_SLOT)
            .times(UnsignedLong.valueOf(Constants.SECONDS_PER_SLOT));
    STDOUT.log(Level.INFO, "Node slot: " + nodeSlot);
    STDOUT.log(Level.INFO, "Node time: " + nodeTime);
    try {
      Bytes32 initial_state_root = initial_state.hash_tree_root();
      BeaconBlock genesis_block = BeaconBlockUtil.get_empty_block();
      genesis_block.setState_root(initial_state_root);
      Bytes32 genesis_block_root = genesis_block.signing_root("signature");
      STDOUT.log(Level.INFO, "Initial state root is " + initial_state_root.toHexString());
      STDOUT.log(Level.INFO, "Genesis block root is " + genesis_block_root.toHexString());
      this.store.addState(initial_state_root, initial_state);
      this.store.addProcessedBlock(genesis_block_root, genesis_block);
      this.headBlock = genesis_block;
      this.justifiedStateRoot = initial_state_root;
      this.currentJustifiedBlockRoot = genesis_block_root;
      this.finalizedStateRoot = initial_state_root;
      this.finalizedBlockRoot = genesis_block_root;
      this.finalizedEpoch = initial_state.getFinalized_epoch();
      this.eventBus.post(
          new GenesisHeadStateEvent((BeaconStateWithCache) initial_state, genesis_block));
    } catch (IllegalStateException e) {
      STDOUT.log(Level.FATAL, e.toString());
    }
  }

  @Subscribe
  public void onDeposit(tech.pegasys.artemis.pow.event.Deposit event) {
    if (config.getDepositMode().equals(Constants.DEPOSIT_TEST)) {
      try {
        BeaconState initial_state = DataStructureUtil.createInitialBeaconState(config);
        onEth2Genesis(initial_state);
        return;
      } catch (ParseException | IOException e) {
        STDOUT.log(Level.FATAL, e.toString());
      }
    }

    if (deposits == null) deposits = new ArrayList<Deposit>();
    deposits.add(DepositUtil.convertEventDepositToOperationDeposit(event));

    UnsignedLong eth1_timestamp = null;
    try {
      eth1_timestamp =
          DepositUtil.getEpochBlockTimeByDepositBlockNumber(
              event.getResponse().log.getBlockNumber(), config.getNodeUrl());
    } catch (IOException e) {
      STDOUT.log(Level.FATAL, e.toString());
    }

    // Approximation to save CPU cycles of creating new BeaconState on every Deposit captured
    if (isGenesisReasonable(eth1_timestamp, deposits)) {
      if (config.getDepositMode().equals(Constants.DEPOSIT_SIM)) {
        BeaconState candidate_state =
            initialize_beacon_state_from_eth1(
                Bytes32.fromHexString(event.getResponse().log.getBlockHash()),
                eth1_timestamp,
                deposits);
        if (is_valid_genesis_stateSim(candidate_state)) onEth2Genesis(candidate_state);
      } else {
        BeaconState candidate_state =
            initialize_beacon_state_from_eth1(
                Bytes32.fromHexString(event.getResponse().log.getBlockHash()),
                eth1_timestamp,
                deposits);
        if (is_valid_genesis_state(candidate_state)) onEth2Genesis(candidate_state);
      }
    }
  }

  public static boolean isGenesisReasonable(UnsignedLong eth1_timestamp, List<Deposit> deposits) {
    return (eth1_timestamp.compareTo(MIN_GENESIS_TIME) >= 0
        && deposits.size() >= MIN_GENESIS_ACTIVE_VALIDATOR_COUNT);
  }

  public static boolean isGenesisReasonableSim(List<Deposit> deposits) {
    return (deposits.size() >= MIN_GENESIS_ACTIVE_VALIDATOR_COUNT);
  }

  @Subscribe
  private void onTick(Date date) {
    on_tick(store, UnsignedLong.valueOf(date.getTime()));
  }

  @Subscribe
  private void onBlock(BeaconBlock block) {
    on_block(store, block);
    // Add attestations that were processed in the block to processed attestations storage
    block
            .getBody()
            .getAttestations()
            .forEach(attestation -> this.chainStorageClient.addProcessedAttestation(attestation));
  }

  @Subscribe
  private void onAttestation(Attestation attestation) {
    on_attestation(store, attestation);
  }
}
