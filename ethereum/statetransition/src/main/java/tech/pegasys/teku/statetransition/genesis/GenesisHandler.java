/*
 * Copyright Consensys Software Inc., 2026
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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ethereum.pow.api.DepositsFromBlockEvent;
import tech.pegasys.teku.ethereum.pow.api.Eth1EventsChannel;
import tech.pegasys.teku.ethereum.pow.api.InvalidDepositEventsException;
import tech.pegasys.teku.ethereum.pow.api.MinGenesisTimeBlockEvent;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.operations.Deposit;
import tech.pegasys.teku.spec.datastructures.operations.DepositWithIndex;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.util.DepositUtil;
import tech.pegasys.teku.spec.genesis.GenesisGenerator;
import tech.pegasys.teku.spec.logic.common.util.BeaconStateUtil;
import tech.pegasys.teku.storage.client.RecentChainData;

public class GenesisHandler implements Eth1EventsChannel {
  private static final Logger LOG = LogManager.getLogger();
  private final RecentChainData recentChainData;
  private final TimeProvider timeProvider;
  private final GenesisGenerator genesisGenerator;
  private final Spec spec;
  private final DepositUtil depositUtil;

  public GenesisHandler(
      final RecentChainData recentChainData, final TimeProvider timeProvider, final Spec spec) {
    this.recentChainData = recentChainData;
    this.timeProvider = timeProvider;
    this.spec = spec;
    this.genesisGenerator = spec.createGenesisGenerator();
    this.depositUtil = new DepositUtil(spec);
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
            .map(depositUtil::convertDepositEventToOperationDeposit)
            .toList();

    processNewData(event.getBlockHash(), event.getBlockTimestamp(), deposits);
  }

  @Override
  public void onMinGenesisTimeBlock(final MinGenesisTimeBlockEvent event) {
    if (!recentChainData.isPreGenesis()) {
      return;
    }
    STATUS_LOG.minGenesisTimeReached();
    processNewData(event.getBlockHash(), event.getTimestamp(), List.of());
  }

  private void processNewData(
      final Bytes32 blockHash,
      final UInt64 timestamp,
      final List<DepositWithIndex> depositsWithIndex) {
    validateDeposits(depositsWithIndex);
    final int previousValidatorRequirementPercent =
        roundPercent(genesisGenerator.getActiveValidatorCount());
    final List<Deposit> deposits =
        depositsWithIndex.stream().map(DepositWithIndex::deposit).toList();
    genesisGenerator.updateCandidateState(blockHash, timestamp, deposits);

    final int newActiveValidatorCount = genesisGenerator.getActiveValidatorCount();
    final BeaconStateUtil beaconStateUtil = spec.atSlot(UInt64.ZERO).getBeaconStateUtil();
    if (beaconStateUtil.isValidGenesisState(
        genesisGenerator.getGenesisTime(), newActiveValidatorCount)) {
      eth2Genesis(genesisGenerator.getGenesisState());
    } else if (roundPercent(newActiveValidatorCount) > previousValidatorRequirementPercent) {
      STATUS_LOG.genesisValidatorsActivated(
          newActiveValidatorCount, spec.getGenesisSpecConfig().getMinGenesisActiveValidatorCount());
    }
  }

  private void validateDeposits(final List<DepositWithIndex> deposits) {
    if (deposits.isEmpty()) {
      return;
    }

    final UInt64 expectedIndex = UInt64.valueOf(genesisGenerator.getDepositCount());
    final DepositWithIndex firstDeposit = deposits.get(0);
    if (!firstDeposit.index().equals(expectedIndex)) {
      throw InvalidDepositEventsException.expectedDepositAtIndex(
          expectedIndex, firstDeposit.index());
    }
  }

  private int roundPercent(final int activeValidatorCount) {
    return activeValidatorCount
        * 100
        / spec.getGenesisSpecConfig().getMinGenesisActiveValidatorCount();
  }

  private void eth2Genesis(final BeaconState genesisState) {
    recentChainData.initializeFromGenesis(genesisState, timeProvider.getTimeInSeconds());
    final Bytes32 genesisBlockRoot = recentChainData.getBestBlockRoot().orElseThrow();
    EVENT_LOG.genesisEvent(
        genesisState.hashTreeRoot(), genesisBlockRoot, genesisState.getGenesisTime());
  }
}
