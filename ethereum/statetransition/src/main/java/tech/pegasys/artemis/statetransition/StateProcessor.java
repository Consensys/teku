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
import java.util.stream.IntStream;
import org.apache.logging.log4j.Level;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.data.BlockProcessingRecord;
import tech.pegasys.artemis.datastructures.Constants;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.operations.DepositWithIndex;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.BeaconStateWithCache;
import tech.pegasys.artemis.datastructures.util.DepositUtil;
import tech.pegasys.artemis.metrics.EpochMetrics;
import tech.pegasys.artemis.statetransition.events.GenesisEvent;
import tech.pegasys.artemis.statetransition.util.EpochProcessingException;
import tech.pegasys.artemis.statetransition.util.SlotProcessingException;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.storage.Store;
import tech.pegasys.artemis.util.alogger.ALogger;
import tech.pegasys.artemis.util.config.ArtemisConfiguration;
import tech.pegasys.pantheon.metrics.MetricsSystem;

/** Class to manage the state tree and initiate state transitions */
public class StateProcessor {
  private final EventBus eventBus;
  private final StateTransition stateTransition;
  private ChainStorageClient chainStorageClient;
  private ArtemisConfiguration config;
  private static final ALogger STDOUT = new ALogger("stdout");
  private List<DepositWithIndex> deposits;
  private BeaconStateWithCache initialState;

  public StateProcessor(
      EventBus eventBus,
      ChainStorageClient chainStorageClient,
      MetricsSystem metricsSystem,
      ArtemisConfiguration config) {
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

    this.eventBus = eventBus;
    this.config = config;
    this.stateTransition = new StateTransition(true, new EpochMetrics(metricsSystem));
    this.chainStorageClient = chainStorageClient;
    this.eventBus.register(this);
  }

  public void eth2Genesis(GenesisEvent genesisEvent) {
    STDOUT.log(Level.INFO, "******* Eth2Genesis Event******* : ");
    this.initialState = genesisEvent.getBeaconState();
    Store store = chainStorageClient.getStore();
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
          eth2Genesis(new GenesisEvent(candidate_state));
        }

      } else {
        BeaconStateWithCache candidate_state =
            initialize_beacon_state_from_eth1(
                Bytes32.fromHexString(event.getResponse().log.getBlockHash()),
                eth1_timestamp,
                deposits);
        if (is_valid_genesis_state(candidate_state)) {
          eth2Genesis(new GenesisEvent(candidate_state));
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
    // Store.Transaction transaction = chainStorageClient.getStore().startTransaction();
    //  BeaconState preState =
    //     BeaconStateWithCache.fromBeaconState(transaction.getBlockState(block.getParent_root()));

    try {
      Store.Transaction transaction = chainStorageClient.getStore().startTransaction();
      final BlockProcessingRecord record = on_block(transaction, block, stateTransition);
      transaction.commit();
      // Add attestations that were processed in the block to processed attestations storage
      block
          .getBody()
          .getAttestations()
          .forEach(
              attestation -> {
                this.chainStorageClient.addProcessedAttestation(attestation);
                STDOUT.log(Level.DEBUG, attestation.toString());
                STDOUT.log(
                    Level.DEBUG,
                    Long.toString(
                        IntStream.range(0, attestation.getAggregation_bits().getCurrentSize())
                            .filter(i -> attestation.getAggregation_bits().getBit(i) == 1)
                            .count()));
              });
      this.eventBus.post(record);
    } catch (StateTransitionException e) {
      //  this.eventBus.post(new BlockProcessingRecord(preState, block, new BeaconState()));
      STDOUT.log(Level.WARN, "Exception in onBlock: " + e.toString());
    }
  }

  @Subscribe
  private void onAttestation(Attestation attestation) {
    try {
      final Store.Transaction transaction = chainStorageClient.getStore().startTransaction();
      on_attestation(transaction, attestation, stateTransition);
      transaction.commit();
      chainStorageClient.addUnprocessedAttestation(attestation);
    } catch (SlotProcessingException | EpochProcessingException e) {
      STDOUT.log(Level.WARN, "Exception in onAttestation: " + e.toString());
    }
  }

  private void setSimulationGenesisTime(BeaconState state) {
    if (config.getInteropActive()) {
      state.setGenesis_time(UnsignedLong.valueOf(config.getGenesisTime()));
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
