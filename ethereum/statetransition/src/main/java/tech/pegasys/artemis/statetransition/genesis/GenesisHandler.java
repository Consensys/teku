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

import static tech.pegasys.teku.logging.EventLogger.EVENT_LOG;

import com.google.common.primitives.UnsignedLong;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.operations.DepositWithIndex;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.util.BeaconStateUtil;
import tech.pegasys.artemis.datastructures.util.DepositUtil;
import tech.pegasys.artemis.datastructures.util.GenesisGenerator;
import tech.pegasys.artemis.pow.api.Eth1EventsChannel;
import tech.pegasys.artemis.pow.event.DepositsFromBlockEvent;
import tech.pegasys.artemis.pow.event.MinGenesisTimeBlockEvent;
import tech.pegasys.artemis.storage.client.RecentChainData;

public class GenesisHandler implements Eth1EventsChannel {

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

    final List<DepositWithIndex> deposits =
        event.getDeposits().stream()
            .map(DepositUtil::convertDepositEventToOperationDeposit)
            .collect(Collectors.toList());

    processNewData(event.getBlockHash(), event.getBlockTimestamp(), deposits);
  }

  @Override
  public void onMinGenesisTimeBlock(MinGenesisTimeBlockEvent event) {
    processNewData(event.getBlockHash(), event.getTimestamp(), List.of());
  }

  private void processNewData(
      Bytes32 blockHash, UnsignedLong timestamp, List<DepositWithIndex> deposits) {
    genesisGenerator.updateCandidateState(blockHash, timestamp, deposits);

    genesisGenerator
        .getGenesisStateIfValid(BeaconStateUtil::is_valid_genesis_state)
        .ifPresent(candidateState -> eth2Genesis(candidateState.commitChanges()));
  }

  private void eth2Genesis(BeaconState genesisState) {
    recentChainData.initializeFromGenesis(genesisState);
    Bytes32 genesisBlockRoot = recentChainData.getBestBlockRoot().orElseThrow();
    EVENT_LOG.genesisEvent(genesisState.hash_tree_root(), genesisBlockRoot);
  }
}
