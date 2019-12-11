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

import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.initialize_beacon_state_from_eth1;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.is_valid_genesis_state;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.is_valid_genesis_stateSim;
import static tech.pegasys.artemis.statetransition.util.ForkChoiceUtil.get_head;
import static tech.pegasys.artemis.statetransition.util.ForkChoiceUtil.on_attestation;
import static tech.pegasys.artemis.statetransition.util.ForkChoiceUtil.on_block;
import static tech.pegasys.artemis.util.alogger.ALogger.STDOUT;
import static tech.pegasys.artemis.util.config.Constants.MIN_GENESIS_ACTIVE_VALIDATOR_COUNT;
import static tech.pegasys.artemis.util.config.Constants.MIN_GENESIS_TIME;

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
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.artemis.data.BlockProcessingRecord;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.operations.AggregateAndProof;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.operations.DepositWithIndex;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.BeaconStateWithCache;
import tech.pegasys.artemis.datastructures.util.DepositUtil;
import tech.pegasys.artemis.metrics.EpochMetrics;
import tech.pegasys.artemis.statetransition.events.GenesisEvent;
import tech.pegasys.artemis.statetransition.events.ProcessedAggregateEvent;
import tech.pegasys.artemis.statetransition.events.ProcessedAttestationEvent;
import tech.pegasys.artemis.statetransition.events.ProcessedBlockEvent;
import tech.pegasys.artemis.statetransition.util.EpochProcessingException;
import tech.pegasys.artemis.statetransition.util.SlotProcessingException;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.storage.HistoricalChainData;
import tech.pegasys.artemis.storage.Store;
import tech.pegasys.artemis.storage.events.StoreDiskUpdateEvent;
import tech.pegasys.artemis.util.config.ArtemisConfiguration;
import tech.pegasys.artemis.util.config.Constants;

/** Class to manage the state tree and initiate state transitions */
public class StateProcessor {
  private final EventBus eventBus;
  private final StateTransition stateTransition;
  private ChainStorageClient chainStorageClient;
  private ArtemisConfiguration config;
  private List<DepositWithIndex> deposits;

  private boolean genesisReady = false;

  public StateProcessor(
      EventBus eventBus,
      ChainStorageClient chainStorageClient,
      MetricsSystem metricsSystem,
      ArtemisConfiguration config) {
    this.eventBus = eventBus;
    this.config = config;
    this.stateTransition = new StateTransition(true, new EpochMetrics(metricsSystem));
    this.chainStorageClient = chainStorageClient;
    this.eventBus.register(this);
  }

  public void eth2Genesis(GenesisEvent genesisEvent) {
    this.genesisReady = true;
    STDOUT.log(Level.INFO, "******* Eth2Genesis Event******* : ");
    final BeaconStateWithCache initialState = genesisEvent.getBeaconState();
    chainStorageClient.initialize(initialState);
    Bytes32 genesisBlockRoot = chainStorageClient.getBestBlockRoot();
    STDOUT.log(Level.INFO, "Initial state root is " + initialState.hash_tree_root().toHexString());
    STDOUT.log(Level.INFO, "Genesis block root is " + genesisBlockRoot.toHexString());
  }

  public Bytes32 processHead(UnsignedLong nodeSlot) {
    Store store = chainStorageClient.getStore();
    Bytes32 headBlockRoot = get_head(store);
    BeaconBlock headBlock = store.getBlock(headBlockRoot);
    BeaconState headState = store.getBlockState(headBlockRoot);
    chainStorageClient.updateBestBlock(headBlockRoot, headBlock.getSlot());

    try {
      stateTransition.process_slots(
          BeaconStateWithCache.fromBeaconState(headState), nodeSlot, false);
    } catch (SlotProcessingException | EpochProcessingException e) {
      STDOUT.log(Level.WARN, "Error when processing empty slots");
      STDOUT.log(Level.WARN, e.toString());
    }
    return headBlockRoot;
  }

  @Subscribe
  public void onDeposit(tech.pegasys.artemis.pow.event.Deposit event) {
    STDOUT.log(Level.DEBUG, "New deposit received");
    if (deposits == null) deposits = new ArrayList<>();
    deposits.add(DepositUtil.convertDepositEventToOperationDeposit(event));

    UnsignedLong eth1_timestamp = null;
    try {
      eth1_timestamp =
          DepositUtil.getEpochBlockTimeByDepositBlockNumber(
              event.getResponse().log.getBlockNumber(), config.getNodeUrl());
    } catch (IOException e) {
      STDOUT.log(Level.FATAL, e.toString());
    }

    // Approximation to save CPU cycles of creating new BeaconState on every Deposit captured
    if (isGenesisReasonable(
        eth1_timestamp, deposits, config.getDepositMode().equals(Constants.DEPOSIT_SIM))) {
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

  public boolean isGenesisReasonable(
      UnsignedLong eth1_timestamp, List<DepositWithIndex> deposits, boolean isSimulation) {
    final boolean sufficientValidators = deposits.size() >= MIN_GENESIS_ACTIVE_VALIDATOR_COUNT;
    if (isSimulation) return sufficientValidators;
    final boolean afterMinGenesisTime = eth1_timestamp.compareTo(MIN_GENESIS_TIME) >= 0;
    return afterMinGenesisTime && sufficientValidators;
  }

  @Subscribe
  @SuppressWarnings("unused")
  private void onBlock(BeaconBlock block) {
    try {
      Store.Transaction transaction =
          chainStorageClient.getStore().startTransaction(block.getSlot());
      final BlockProcessingRecord record = on_block(transaction, block, stateTransition);
      transaction.commit();
      eventBus.post(new StoreDiskUpdateEvent(transaction));

      // Add attestations that were processed in the block to processed attestations storage
      List<Long> numberOfAttestersInAttestations = new ArrayList<>();
      block
          .getBody()
          .getAttestations()
          .forEach(
              attestation -> {
                numberOfAttestersInAttestations.add(
                    IntStream.range(0, attestation.getAggregation_bits().getCurrentSize())
                        .filter(i -> attestation.getAggregation_bits().getBit(i) == 1)
                        .count());
              });

      this.eventBus.post(new ProcessedBlockEvent(block.getBody().getAttestations()));

      STDOUT.log(
          Level.DEBUG, "Number of attestations: " + block.getBody().getAttestations().size());
      STDOUT.log(
          Level.DEBUG, "Number of attesters in attestations: " + numberOfAttestersInAttestations);

      this.eventBus.post(record);
    } catch (StateTransitionException e) {
      //  this.eventBus.post(new BlockProcessingRecord(preState, block, new BeaconState()));
      STDOUT.log(Level.WARN, "Exception in onBlock: " + e.toString());
    }
    System.out.println("Requesting");
    new HistoricalChainData(eventBus)
        .getBlockBySlot(block.getSlot().minus(UnsignedLong.ONE))
        .thenAccept(b -> System.out.println(b))
        .exceptionally(
            t -> {
              t.printStackTrace();
              return null;
            });
  }

  private void onAttestation(Attestation attestation) {
    try {
      final Store.Transaction transaction =
          chainStorageClient
              .getStore()
              .startTransaction(
                  compute_start_slot_at_epoch(attestation.getData().getTarget().getEpoch()));
      on_attestation(transaction, attestation, stateTransition);
      transaction.commit();
      eventBus.post(new StoreDiskUpdateEvent(transaction));

    } catch (SlotProcessingException | EpochProcessingException e) {
      STDOUT.log(Level.WARN, "Exception in onAttestation: " + e.toString());
    }
  }

  @Subscribe
  @SuppressWarnings("unused")
  private void onGossipedAttestation(Attestation attestation) {
    onAttestation(attestation);
    this.eventBus.post(new ProcessedAttestationEvent(attestation));
  }

  @Subscribe
  @SuppressWarnings("unused")
  private void onAggregateAndProof(AggregateAndProof aggregateAndProof) {
    Attestation aggregate = aggregateAndProof.getAggregate();
    onAttestation(aggregate);
    this.eventBus.post(new ProcessedAggregateEvent(aggregate));
  }

  private void setSimulationGenesisTime(BeaconState state) {
    Date date = new Date();
    state.setGenesis_time(
        UnsignedLong.valueOf((date.getTime() / 1000)).plus(Constants.GENESIS_START_DELAY));
  }

  public boolean isGenesisReady() {
    return genesisReady;
  }
}
