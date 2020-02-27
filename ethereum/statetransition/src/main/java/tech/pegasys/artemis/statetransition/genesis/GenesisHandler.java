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

import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.isThereEnoughNumberOfValidators;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.is_valid_genesis_state;

import com.google.common.primitives.UnsignedLong;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.operations.DepositWithIndex;
import tech.pegasys.artemis.datastructures.state.BeaconStateWithCache;
import tech.pegasys.artemis.datastructures.util.DepositUtil;
import tech.pegasys.artemis.datastructures.util.GenesisGenerator;
import tech.pegasys.artemis.pow.api.DepositEventChannel;
import tech.pegasys.artemis.pow.api.MinGenesisTimeBlockEventChannel;
import tech.pegasys.artemis.pow.event.DepositsFromBlockEvent;
import tech.pegasys.artemis.pow.event.MinGenesisTimeBlockEvent;
import tech.pegasys.artemis.statetransition.events.GenesisEvent;
import tech.pegasys.artemis.storage.ChainStorageClient;

public class GenesisHandler implements DepositEventChannel, MinGenesisTimeBlockEventChannel {

  private static final Logger LOG = LogManager.getLogger();

  private final ChainStorageClient chainStorageClient;
  private final GenesisGenerator genesisGenerator = new GenesisGenerator();

  private Queue<DepositsFromBlockEvent> bufferedDepositsFromBlockEvents = new LinkedList<>();
  private volatile boolean genesisAlreadyTriggered = false;

  public GenesisHandler(final ChainStorageClient chainStorageClient) {
    this.chainStorageClient = chainStorageClient;
  }

  @Override
  public synchronized void onDepositsFromBlock(final DepositsFromBlockEvent event) {
    if (!chainStorageClient.isPreGenesis() || genesisAlreadyTriggered) {
      return;
    }

    if (isThereEnoughNumberOfValidators(genesisGenerator.getCandidateState())) {
      bufferedDepositsFromBlockEvents.add(event);
    } else {
      addDepositsToState(event);
      if (is_valid_genesis_state(genesisGenerator.getCandidateState())) {
        triggerGenesis();
      }
    }
  }

  private void addDepositsToState(DepositsFromBlockEvent event) {
    final Bytes32 eth1BlockHash = event.getBlockHash();
    final UnsignedLong eth1Timestamp = event.getBlockTimestamp();
    final List<DepositWithIndex> deposits =
        event.getDeposits().stream()
            .map(DepositUtil::convertDepositEventToOperationDeposit)
            .collect(Collectors.toList());
    genesisGenerator.addDepositsFromBlock(eth1BlockHash, eth1Timestamp, deposits);
  }

  @Override
  public synchronized void onMinGenesisTimeBlock(MinGenesisTimeBlockEvent event) {
    if (genesisAlreadyTriggered) {
      return;
    }

    if (isThereEnoughNumberOfValidators(genesisGenerator.getCandidateState())) {
      processEventsUpToMinGenesisTimeBlock(event);
      triggerGenesis();
    }
  }

  private void processEventsUpToMinGenesisTimeBlock(MinGenesisTimeBlockEvent event) {
    UnsignedLong genesisBlockNumber = event.getBlockNumber();
    while (bufferedDepositsFromBlockEvents.peek() != null) {
      DepositsFromBlockEvent depositsFromBlockEvent = bufferedDepositsFromBlockEvents.remove();
      if (depositsFromBlockEvent.getBlockNumber().compareTo(genesisBlockNumber) > 0) {
        break;
      } else {
        addDepositsToState(depositsFromBlockEvent);
      }
    }
    genesisGenerator.setBlockInformation(event.getBlockHash(), genesisBlockNumber);
    triggerGenesis();
  }

  private void triggerGenesis() {
    BeaconStateWithCache genesisState = genesisGenerator.getGenesisState();
    eth2Genesis(new GenesisEvent(genesisState));
  }

  private void eth2Genesis(GenesisEvent genesisEvent) {
    LOG.info("******* Eth2Genesis Event******* : ");
    final BeaconStateWithCache initialState = genesisEvent.getBeaconState();
    chainStorageClient.initializeFromGenesis(initialState);
    Bytes32 genesisBlockRoot = chainStorageClient.getBestBlockRoot();
    LOG.info("Initial state root is " + initialState.hash_tree_root().toHexString());
    LOG.info("Genesis block root is " + genesisBlockRoot.toHexString());
  }
}
