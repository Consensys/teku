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

import static tech.pegasys.artemis.datastructures.Constants.MIN_GENESIS_ACTIVE_VALIDATOR_COUNT;
import static tech.pegasys.artemis.datastructures.Constants.MIN_GENESIS_TIME;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.initialize_beacon_state_from_eth1;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.is_valid_genesis_state;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.is_valid_genesis_stateSim;
import static tech.pegasys.artemis.statetransition.util.ForkChoiceUtil.get_genesis_store;
import static tech.pegasys.artemis.statetransition.util.ForkChoiceUtil.get_head;
import static tech.pegasys.artemis.statetransition.util.ForkChoiceUtil.on_attestation;
import static tech.pegasys.artemis.statetransition.util.ForkChoiceUtil.on_block;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.common.primitives.UnsignedLong;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.apache.logging.log4j.Level;
import org.apache.tuweni.bytes.Bytes32;
import org.json.simple.parser.ParseException;
import tech.pegasys.artemis.datastructures.Constants;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.operations.Deposit;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.BeaconStateWithCache;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.datastructures.util.DepositUtil;
import tech.pegasys.artemis.service.serviceutils.ServiceConfig;
import tech.pegasys.artemis.statetransition.events.GenesisEvent;
import tech.pegasys.artemis.statetransition.util.EpochProcessingException;
import tech.pegasys.artemis.statetransition.util.SlotProcessingException;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.storage.Store;
import tech.pegasys.artemis.storage.events.NodeStartEvent;
import tech.pegasys.artemis.util.alogger.ALogger;
import tech.pegasys.artemis.util.config.ArtemisConfiguration;

/** Class to manage the state tree and initiate state transitions */
public class StateProcessor {

  private final EventBus eventBus;
  private ChainStorageClient chainStorageClient;
  private ArtemisConfiguration config;
  private static final ALogger STDOUT = new ALogger("stdout");
  private List<Deposit> deposits;
  private BeaconStateWithCache initialState;

  public StateProcessor(ServiceConfig config, ChainStorageClient chainStorageClient) {
    this.eventBus = config.getEventBus();
    this.config = config.getConfig();
    this.eventBus.register(this);
    this.chainStorageClient = chainStorageClient;

    if (this.config.getDepositMode().equals(Constants.DEPOSIT_TEST)) {
      try {
        this.initialState = DataStructureUtil.createInitialBeaconState2(this.config);
        setSimulationGenesisTime(initialState);
        this.eventBus.post(new NodeStartEvent(initialState));
      } catch (ParseException | IOException e) {
        STDOUT.log(Level.FATAL, "StateProcessor initializing: " + e.toString());
      }
    }
  }

  @Subscribe
  public void onEth2Genesis(GenesisEvent genesisEvent) {
    STDOUT.log(Level.INFO, "******* Eth2Genesis Event detected ******* : ");
    Store store = chainStorageClient.getStore();
    if (store == null) {
      store = get_genesis_store(initialState);
      chainStorageClient.setStore(store);
      chainStorageClient.setGenesisTime(initialState.getGenesis_time());
    }
    Bytes32 genesisBlockRoot = get_head(store);
    STDOUT.log(Level.INFO, "Initial state root is " + initialState.hash_tree_root().toHexString());
    STDOUT.log(Level.INFO, "Genesis block root is " + genesisBlockRoot.toHexString());
  }

  @Subscribe
  public void onDeposit(tech.pegasys.artemis.pow.event.Deposit event) {
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
        BeaconStateWithCache candidate_state =
            initialize_beacon_state_from_eth1(
                Bytes32.fromHexString(event.getResponse().log.getBlockHash()),
                eth1_timestamp,
                deposits);
        if (is_valid_genesis_stateSim(candidate_state)) {
          setSimulationGenesisTime(candidate_state);
          initialState = candidate_state;
          this.eventBus.post(new GenesisEvent());
        }

      } else {
        BeaconStateWithCache candidate_state =
            initialize_beacon_state_from_eth1(
                Bytes32.fromHexString(event.getResponse().log.getBlockHash()),
                eth1_timestamp,
                deposits);
        if (is_valid_genesis_state(candidate_state)) {
          initialState = candidate_state;
          this.eventBus.post(new GenesisEvent());
        }
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
  private void onBlock(BeaconBlock block) {
    try {
      on_block(chainStorageClient.getStore(), block, eventBus);

      // Add attestations that were processed in the block to processed attestations storage
      block
          .getBody()
          .getAttestations()
          .forEach(attestation -> this.chainStorageClient.addProcessedAttestation(attestation));
    } catch (StateTransitionException e) {
      STDOUT.log(Level.WARN, "Exception in onBlock: " + e.toString());
    }
  }

  @Subscribe
  private void onAttestation(Attestation attestation) {
    try {
      on_attestation(chainStorageClient.getStore(), attestation, eventBus);
    } catch (SlotProcessingException | EpochProcessingException e) {
      STDOUT.log(Level.WARN, "Exception in onAttestation: " + e.toString());
    }
  }

  private void setSimulationGenesisTime(BeaconState state) {
    if (Constants.GENESIS_TIME.equals(UnsignedLong.MAX_VALUE)) {
      Date date = new Date();
      state.setGenesis_time(
          UnsignedLong.valueOf((date.getTime() / 1000)).plus(Constants.GENESIS_START_DELAY));
    } else {
      state.setGenesis_time(Constants.GENESIS_TIME);
    }
    chainStorageClient.setGenesisTime(state.getGenesis_time());
  }
}
