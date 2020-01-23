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

import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.initialize_beacon_state_from_eth1;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.is_valid_genesis_state;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.is_valid_genesis_stateSim;
import static tech.pegasys.artemis.statetransition.util.ForkChoiceUtil.get_head;
import static tech.pegasys.artemis.util.alogger.ALogger.STDOUT;
import static tech.pegasys.artemis.util.config.Constants.MIN_GENESIS_ACTIVE_VALIDATOR_COUNT;
import static tech.pegasys.artemis.util.config.Constants.MIN_GENESIS_TIME;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.common.primitives.UnsignedLong;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.NavigableSet;
import java.util.TreeSet;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.operations.DepositWithIndex;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.BeaconStateWithCache;
import tech.pegasys.artemis.datastructures.util.DepositUtil;
import tech.pegasys.artemis.statetransition.blockimport.BlockImportResult;
import tech.pegasys.artemis.statetransition.blockimport.BlockImporter;
import tech.pegasys.artemis.statetransition.events.BlockProposedEvent;
import tech.pegasys.artemis.statetransition.events.GenesisEvent;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.storage.Store;
import tech.pegasys.artemis.util.config.ArtemisConfiguration;
import tech.pegasys.artemis.util.config.Constants;

/** Class to manage the state tree and initiate state transitions */
public class StateProcessor {
  private static final Logger LOG = LogManager.getLogger();

  private final BlockImporter blockImporter;
  private final ChainStorageClient chainStorageClient;
  private final ArtemisConfiguration config;
  private final List<DepositWithIndex> deposits = new ArrayList<>();
  private final NavigableSet<DepositWithIndex> pendingDeposits = new TreeSet<>();

  private boolean genesisReady = false;

  public StateProcessor(
      EventBus eventBus, ChainStorageClient chainStorageClient, ArtemisConfiguration config) {
    this.config = config;
    this.chainStorageClient = chainStorageClient;
    this.blockImporter = new BlockImporter(chainStorageClient, eventBus);
    eventBus.register(this);
  }

  public void eth2Genesis(GenesisEvent genesisEvent) {
    this.genesisReady = true;
    STDOUT.log(Level.INFO, "******* Eth2Genesis Event******* : ");
    final BeaconStateWithCache initialState = genesisEvent.getBeaconState();
    chainStorageClient.initializeFromGenesis(initialState);
    Bytes32 genesisBlockRoot = chainStorageClient.getBestBlockRoot();
    STDOUT.log(Level.INFO, "Initial state root is " + initialState.hash_tree_root().toHexString());
    STDOUT.log(Level.INFO, "Genesis block root is " + genesisBlockRoot.toHexString());
  }

  public Bytes32 processHead() {
    Store store = chainStorageClient.getStore();
    Bytes32 headBlockRoot = get_head(store);
    BeaconBlock headBlock = store.getBlock(headBlockRoot);
    chainStorageClient.updateBestBlock(headBlockRoot, headBlock.getSlot());
    return headBlockRoot;
  }

  @Subscribe
  public void onDeposit(tech.pegasys.artemis.pow.event.Deposit event) {
    STDOUT.log(Level.DEBUG, "New deposit received");
    pendingDeposits.add(DepositUtil.convertDepositEventToOperationDeposit(event));

    processPendingDeposits();
  }

  private void processPendingDeposits() {
    UnsignedLong expectedDepositIndex =
        deposits.isEmpty()
            ? UnsignedLong.ZERO
            : deposits.get(deposits.size() - 1).getIndex().plus(UnsignedLong.ONE);

    for (Iterator<DepositWithIndex> i = pendingDeposits.iterator(); i.hasNext(); ) {
      final DepositWithIndex depositToProcess = i.next();
      if (!depositToProcess.getIndex().equals(expectedDepositIndex)) {
        return;
      }
      processDeposit(depositToProcess);
      i.remove();
      expectedDepositIndex = expectedDepositIndex.plus(UnsignedLong.ONE);
    }
  }

  private void processDeposit(final DepositWithIndex depositWithIndex) {
    deposits.add(depositWithIndex);
    // Eth1 hash has to be from the block containing the last required deposit but we may be
    // receiving deposits out of order.
    Bytes32 genesisEth1BlockHash = Bytes32.fromHexString(depositWithIndex.getLog().getBlockHash());

    UnsignedLong eth1_timestamp = null;
    try {
      eth1_timestamp =
          DepositUtil.getEpochBlockTimeByDepositBlockHash(
              genesisEth1BlockHash, config.getNodeUrl());
    } catch (IOException e) {
      STDOUT.log(Level.FATAL, e.toString());
    }

    // Approximation to save CPU cycles of creating new BeaconState on every Deposit captured
    if (isGenesisReasonable(
        eth1_timestamp, deposits, config.getDepositMode().equals(Constants.DEPOSIT_SIM))) {
      if (config.getDepositMode().equals(Constants.DEPOSIT_SIM)) {
        BeaconStateWithCache candidate_state =
            initialize_beacon_state_from_eth1(
                genesisEth1BlockHash, eth1_timestamp, new ArrayList<>(deposits));
        if (is_valid_genesis_stateSim(candidate_state)) {
          setSimulationGenesisTime(candidate_state);
          eth2Genesis(new GenesisEvent(candidate_state));
        }

      } else {
        BeaconStateWithCache candidate_state =
            initialize_beacon_state_from_eth1(
                genesisEth1BlockHash, eth1_timestamp, new ArrayList<>(deposits));
        if (is_valid_genesis_state(candidate_state)) {
          eth2Genesis(new GenesisEvent(candidate_state));
        }
      }
    }
  }

  public boolean isGenesisReasonable(
      UnsignedLong eth1_timestamp, Collection<DepositWithIndex> deposits, boolean isSimulation) {
    final boolean sufficientValidators = deposits.size() >= MIN_GENESIS_ACTIVE_VALIDATOR_COUNT;
    if (isSimulation) return sufficientValidators;
    final boolean afterMinGenesisTime = eth1_timestamp.compareTo(MIN_GENESIS_TIME) >= 0;
    return afterMinGenesisTime && sufficientValidators;
  }

  @Subscribe
  @SuppressWarnings("unused")
  private void onBlockProposed(final BlockProposedEvent blockProposedEvent) {
    LOG.trace("Preparing to import proposed block: {}", blockProposedEvent.getBlock());
    final BlockImportResult result = blockImporter.importBlock(blockProposedEvent.getBlock());
    if (result.isSuccessful()) {
      LOG.trace("Successfully imported proposed block: {}", blockProposedEvent.getBlock());
    } else {
      LOG.error(
          "Failed to import proposed block for reason + "
              + result.getFailureReason()
              + ": "
              + blockProposedEvent,
          result.getFailureCause().orElse(null));
    }
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
