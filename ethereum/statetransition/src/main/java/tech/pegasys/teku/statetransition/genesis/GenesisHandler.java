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

package tech.pegasys.teku.statetransition.genesis;

import static tech.pegasys.teku.infrastructure.logging.EventLogger.EVENT_LOG;
import static tech.pegasys.teku.infrastructure.logging.StatusLogger.STATUS_LOG;

import java.util.List;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.operations.DepositWithIndex;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.util.BeaconStateUtil;
import tech.pegasys.teku.datastructures.util.DepositUtil;
import tech.pegasys.teku.datastructures.util.GenesisGenerator;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.pow.api.Eth1EventsChannel;
import tech.pegasys.teku.pow.event.DepositsFromBlockEvent;
import tech.pegasys.teku.pow.event.MinGenesisTimeBlockEvent;
import tech.pegasys.teku.pow.exception.InvalidDepositEventsException;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.util.config.Constants;

public class GenesisHandler implements Eth1EventsChannel {
  private static final Logger LOG = LogManager.getLogger();
  private final RecentChainData recentChainData;
  private final GenesisGenerator genesisGenerator = new GenesisGenerator();

  public GenesisHandler(final RecentChainData recentChainData) {
    this.recentChainData = recentChainData;
  }

  @Override
  public void onDepositsFromBlock(final DepositsFromBlockEvent event) {
    if (!recentChainData.isPreGenesis()) {
      return;
    }

    LOG.trace(
        "Processing {} deposits from block {}", event.getDeposits().size(), event.getBlockNumber());
    final List<DepositWithIndex> deposits =
        event.getDeposits().stream()
            .map(DepositUtil::convertDepositEventToOperationDeposit)
            .collect(Collectors.toList());

    processNewData(event.getBlockHash(), event.getBlockTimestamp(), deposits);
  }

  @Override
  public void onMinGenesisTimeBlock(MinGenesisTimeBlockEvent event) {
    if (!recentChainData.isPreGenesis()) {
      return;
    }
    STATUS_LOG.minGenesisTimeReached();
    processNewData(event.getBlockHash(), event.getTimestamp(), List.of());
  }

  private void processNewData(
      Bytes32 blockHash, UInt64 timestamp, List<DepositWithIndex> deposits) {
    validateDeposits(deposits);
    final int previousValidatorRequirementPercent =
        roundPercent(genesisGenerator.getActiveValidatorCount());
    genesisGenerator.updateCandidateState(blockHash, timestamp, deposits);

    final int newActiveValidatorCount = genesisGenerator.getActiveValidatorCount();
    if (BeaconStateUtil.is_valid_genesis_state(
        genesisGenerator.getGenesisTime(), newActiveValidatorCount)) {
      eth2Genesis(genesisGenerator.getGenesisState());
    } else if (roundPercent(newActiveValidatorCount) > previousValidatorRequirementPercent) {
      STATUS_LOG.genesisValidatorsActivated(
          newActiveValidatorCount, Constants.MIN_GENESIS_ACTIVE_VALIDATOR_COUNT);
    }
  }

  private void validateDeposits(final List<DepositWithIndex> deposits) {
    if (deposits.isEmpty()) {
      return;
    }

    final UInt64 expectedIndex = UInt64.valueOf(genesisGenerator.getDepositCount());
    final DepositWithIndex firstDeposit = deposits.get(0);
    if (!firstDeposit.getIndex().equals(expectedIndex)) {
      throw InvalidDepositEventsException.expectedDepositAtIndex(
          expectedIndex, firstDeposit.getIndex());
    }
  }

  private int roundPercent(int activeValidatorCount) {
    return activeValidatorCount * 100 / Constants.MIN_GENESIS_ACTIVE_VALIDATOR_COUNT;
  }

  private void eth2Genesis(BeaconState genesisState) {
    recentChainData.initializeFromGenesis(genesisState);
    Bytes32 genesisBlockRoot = recentChainData.getBestBlockRoot().orElseThrow();
    EVENT_LOG.genesisEvent(
        genesisState.hash_tree_root(), genesisBlockRoot, genesisState.getGenesis_time());
  }
}
