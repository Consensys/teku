/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.artemis.statetransition.genesis;

import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.initialize_beacon_state_from_eth1;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.is_valid_genesis_state;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.is_valid_genesis_stateSim;
import static tech.pegasys.artemis.util.alogger.ALogger.STDOUT;
import static tech.pegasys.artemis.util.config.Constants.MIN_GENESIS_ACTIVE_VALIDATOR_COUNT;
import static tech.pegasys.artemis.util.config.Constants.MIN_GENESIS_TIME;

import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.apache.logging.log4j.Level;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.operations.DepositWithIndex;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.BeaconStateWithCache;
import tech.pegasys.artemis.datastructures.util.DepositUtil;
import tech.pegasys.artemis.pow.api.DepositEventChannel;
import tech.pegasys.artemis.pow.event.DepositsFromBlockEvent;
import tech.pegasys.artemis.statetransition.events.GenesisEvent;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.util.config.ArtemisConfiguration;
import tech.pegasys.artemis.util.config.Constants;

public class PreGenesisDepositHandler implements DepositEventChannel {

  private final ArtemisConfiguration config;
  private final ChainStorageClient chainStorageClient;
  private final List<DepositWithIndex> deposits = new ArrayList<>();

  public PreGenesisDepositHandler(
      final ArtemisConfiguration config, final ChainStorageClient chainStorageClient) {
    this.config = config;
    this.chainStorageClient = chainStorageClient;
  }

  @Override
  public void notifyDepositsFromBlock(final DepositsFromBlockEvent event) {
    if (!chainStorageClient.isPreGenesis()) {
      return;
    }
    event.getDeposits().stream()
        .map(DepositUtil::convertDepositEventToOperationDeposit)
        .forEach(deposits::add);

    final Bytes32 eth1BlockHash = event.getBlockHash();
    final UnsignedLong eth1_timestamp = event.getBlockTimestamp();

    // Approximation to save CPU cycles of creating new BeaconState on every Deposit captured
    if (isGenesisReasonable(
        eth1_timestamp, deposits, config.getDepositMode().equals(Constants.DEPOSIT_SIM))) {
      if (config.getDepositMode().equals(Constants.DEPOSIT_SIM)) {
        BeaconStateWithCache candidate_state =
            initialize_beacon_state_from_eth1(eth1BlockHash, eth1_timestamp, deposits);
        if (is_valid_genesis_stateSim(candidate_state)) {
          setSimulationGenesisTime(candidate_state);
          eth2Genesis(new GenesisEvent(candidate_state));
        }

      } else {
        BeaconStateWithCache candidate_state =
            initialize_beacon_state_from_eth1(eth1BlockHash, eth1_timestamp, deposits);
        if (is_valid_genesis_state(candidate_state)) {
          eth2Genesis(new GenesisEvent(candidate_state));
        }
      }
    }
  }

  private void eth2Genesis(GenesisEvent genesisEvent) {
    STDOUT.log(Level.INFO, "******* Eth2Genesis Event******* : ");
    final BeaconStateWithCache initialState = genesisEvent.getBeaconState();
    chainStorageClient.initializeFromGenesis(initialState);
    Bytes32 genesisBlockRoot = chainStorageClient.getBestBlockRoot();
    STDOUT.log(Level.INFO, "Initial state root is " + initialState.hash_tree_root().toHexString());
    STDOUT.log(Level.INFO, "Genesis block root is " + genesisBlockRoot.toHexString());
  }

  public boolean isGenesisReasonable(
      UnsignedLong eth1_timestamp, List<DepositWithIndex> deposits, boolean isSimulation) {
    final boolean sufficientValidators = deposits.size() >= MIN_GENESIS_ACTIVE_VALIDATOR_COUNT;
    if (isSimulation) return sufficientValidators;
    final boolean afterMinGenesisTime = eth1_timestamp.compareTo(MIN_GENESIS_TIME) >= 0;
    return afterMinGenesisTime && sufficientValidators;
  }

  private void setSimulationGenesisTime(BeaconState state) {
    Date date = new Date();
    state.setGenesis_time(
        UnsignedLong.valueOf((date.getTime() / 1000)).plus(Constants.GENESIS_START_DELAY));
  }
}
