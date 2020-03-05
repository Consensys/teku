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

import com.google.common.primitives.UnsignedLong;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.logging.log4j.Level;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.operations.DepositWithIndex;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.MutableBeaconState;
import tech.pegasys.artemis.datastructures.util.BeaconStateUtil;
import tech.pegasys.artemis.datastructures.util.DepositUtil;
import tech.pegasys.artemis.datastructures.util.GenesisGenerator;
import tech.pegasys.artemis.pow.api.DepositEventChannel;
import tech.pegasys.artemis.pow.event.DepositsFromBlockEvent;
import tech.pegasys.artemis.statetransition.events.GenesisEvent;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.util.config.ArtemisConfiguration;
import tech.pegasys.artemis.util.config.Constants;
import tech.pegasys.teku.logging.StatusLogger;

public class PreGenesisDepositHandler implements DepositEventChannel {

  private static final StatusLogger STATUS_LOG = StatusLogger.getLogger();

  private final ArtemisConfiguration config;
  private final ChainStorageClient chainStorageClient;
  private final GenesisGenerator genesisGenerator = new GenesisGenerator();

  public PreGenesisDepositHandler(
      final ArtemisConfiguration config, final ChainStorageClient chainStorageClient) {
    this.config = config;
    this.chainStorageClient = chainStorageClient;
  }

  @Override
  public void onDepositsFromBlock(final DepositsFromBlockEvent event) {
    if (!chainStorageClient.isPreGenesis()) {
      return;
    }

    final Bytes32 eth1BlockHash = event.getBlockHash();
    final UnsignedLong eth1Timestamp = event.getBlockTimestamp();
    final List<DepositWithIndex> deposits =
        event.getDeposits().stream()
            .map(DepositUtil::convertDepositEventToOperationDeposit)
            .collect(Collectors.toList());
    genesisGenerator.addDepositsFromBlock(eth1BlockHash, eth1Timestamp, deposits);

    if (config.getDepositMode().equals(Constants.DEPOSIT_SIM)) {
      genesisGenerator
          .getGenesisStateIfValid(BeaconStateUtil::is_valid_genesis_stateSim)
          .ifPresent(
              candidate_state -> {
                setSimulationGenesisTime(candidate_state);
                eth2Genesis(new GenesisEvent(candidate_state.commitChanges()));
              });
    } else {
      genesisGenerator
          .getGenesisStateIfValid(BeaconStateUtil::is_valid_genesis_state)
          .ifPresent(candidate_state -> eth2Genesis(new GenesisEvent(candidate_state)));
    }
  }

  private void eth2Genesis(GenesisEvent genesisEvent) {
    STATUS_LOG.log(Level.INFO, "******* Eth2Genesis Event******* : ");
    final BeaconState initialState = genesisEvent.getBeaconState();
    chainStorageClient.initializeFromGenesis(initialState);
    Bytes32 genesisBlockRoot = chainStorageClient.getBestBlockRoot();
    STATUS_LOG.log(
        Level.INFO, "Initial state root is " + initialState.hash_tree_root().toHexString());
    STATUS_LOG.log(Level.INFO, "Genesis block root is " + genesisBlockRoot.toHexString());
  }

  private void setSimulationGenesisTime(MutableBeaconState state) {
    Date date = new Date();
    state.setGenesis_time(
        UnsignedLong.valueOf((date.getTime() / 1000)).plus(Constants.GENESIS_START_DELAY));
  }
}
