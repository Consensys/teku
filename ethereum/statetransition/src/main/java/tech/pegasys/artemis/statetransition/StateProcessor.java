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
import tech.pegasys.artemis.datastructures.Constants;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.operations.DepositWithIndex;
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
  private final StateTransition stateTransition;
  private ChainStorageClient chainStorageClient;
  private ArtemisConfiguration config;
  private static final ALogger STDOUT = new ALogger("stdout");
  private List<DepositWithIndex> deposits;
  private BeaconStateWithCache initialState;

  public StateProcessor(ServiceConfig config, ChainStorageClient chainStorageClient) {
    /*
    List<Long> serializationTimeArray = new ArrayList<>();
    List<Long> deserializationTimeArray = new ArrayList<>();
    for (int i = 0; i < 100; i++) {
      BeaconBlock beaconBlock = DataStructureUtil.randomBeaconBlock(100);
      long startTime = System.currentTimeMillis();
      Bytes serialized = SimpleOffsetSerializer.serialize(beaconBlock);
      serializationTimeArray.add(System.currentTimeMillis() - startTime);
      startTime = System.currentTimeMillis();
      BeaconBlock newBeaconBlock =
              SimpleOffsetSerializer.deserialize(serialized, BeaconBlock.class);
      deserializationTimeArray.add(System.currentTimeMillis() - startTime);
    }

    System.out.println("serialization: " + serializationTimeArray);
    System.out.println("deserialization: " + deserializationTimeArray);
    */

    this.eventBus = config.getEventBus();
    this.config = config.getConfig();
    this.stateTransition = new StateTransition(true, new EpochMetrics(config.getMetricsSystem()));
    this.chainStorageClient = chainStorageClient;
    this.eventBus.register(this);

    if (this.config.getDepositMode().equals(Constants.DEPOSIT_TEST)) {
      initialState = DataStructureUtil.createInitialBeaconState(this.config);
      setSimulationGenesisTime(initialState);
      this.eventBus.post(new NodeStartEvent(initialState));
    }
  }

  @Subscribe
  public void onEth2Genesis(GenesisEvent genesisEvent) {
    STDOUT.log(Level.INFO, "******* Eth2Genesis Event detected ******* : ");
    Store store = chainStorageClient.getStore();
    if (store == null) {
      store = get_genesis_store(initialState);
      chainStorageClient.setStore(store);
      UnsignedLong genesisTime = initialState.getGenesis_time();
      chainStorageClient.setGenesisTime(genesisTime);
    }
    Bytes32 genesisBlockRoot = get_head(store);
    STDOUT.log(Level.INFO, "Initial state root is " + initialState.hash_tree_root().toHexString());
    STDOUT.log(Level.INFO, "Genesis block root is " + genesisBlockRoot.toHexString());
  }

  @Subscribe
  public void onDeposit(tech.pegasys.artemis.pow.event.Deposit event) {
    if (deposits == null) deposits = new ArrayList<>();
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

  public boolean isGenesisReasonable(UnsignedLong eth1_timestamp, List<DepositWithIndex> deposits) {
    if (config.getInteropActive()) {
      return false; // Interop mode never performs genesis in response to deposit events.
    }
    final boolean afterMinGenesisTime = eth1_timestamp.compareTo(MIN_GENESIS_TIME) >= 0;
    final boolean sufficientValidators = deposits.size() >= MIN_GENESIS_ACTIVE_VALIDATOR_COUNT;
    return afterMinGenesisTime && sufficientValidators;
  }

  @Subscribe
  private void onBlock(BeaconBlock block) {
    try {
      Store.Transaction transaction = chainStorageClient.getStore().startTransaction();
      on_block(transaction, block, stateTransition);
      transaction.commit();
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
      final Store.Transaction transaction = chainStorageClient.getStore().startTransaction();
      on_attestation(transaction, attestation, stateTransition);
      transaction.commit();
    } catch (SlotProcessingException | EpochProcessingException e) {
      STDOUT.log(Level.WARN, "Exception in onAttestation: " + e.toString());
    }
  }

  private void setSimulationGenesisTime(BeaconState state) {
    if (config.getInteropActive()) {
      state.setGenesis_time(UnsignedLong.valueOf(config.getInteropGenesisTime()));
    } else if (Constants.GENESIS_TIME.equals(UnsignedLong.MAX_VALUE)) {
      Date date = new Date();
      state.setGenesis_time(
          UnsignedLong.valueOf((date.getTime() / 1000)).plus(Constants.GENESIS_START_DELAY));
    } else {
      state.setGenesis_time(Constants.GENESIS_TIME);
    }
    chainStorageClient.setGenesisTime(state.getGenesis_time());
  }
}
