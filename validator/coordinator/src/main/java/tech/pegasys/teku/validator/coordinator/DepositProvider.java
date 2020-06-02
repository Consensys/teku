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

package tech.pegasys.teku.validator.coordinator;

import com.google.common.primitives.UnsignedLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.datastructures.operations.Deposit;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.datastructures.util.DepositUtil;
import tech.pegasys.teku.pow.api.Eth1EventsChannel;
import tech.pegasys.teku.pow.event.DepositsFromBlockEvent;
import tech.pegasys.teku.pow.event.MinGenesisTimeBlockEvent;
import tech.pegasys.teku.ssz.SSZTypes.SSZList;
import tech.pegasys.teku.core.DepositMerkleTree;
import tech.pegasys.teku.storage.api.FinalizedCheckpointChannel;
import tech.pegasys.teku.storage.client.RecentChainData;

public class DepositProvider implements Eth1EventsChannel, FinalizedCheckpointChannel {

  private static final Logger LOG = LogManager.getLogger();

  private final RecentChainData recentChainData;
  private final Eth1DataCache eth1DataCache;
  private final DepositMerkleTree depositMerkleTree = new DepositMerkleTree();

  public DepositProvider(RecentChainData recentChainData, final Eth1DataCache eth1DataCache) {
    this.recentChainData = recentChainData;
    this.eth1DataCache = eth1DataCache;
  }

  @Override
  public void onDepositsFromBlock(DepositsFromBlockEvent event) {
    event.getDeposits().stream()
        .map(DepositUtil::convertDepositEventToOperationDeposit)
        .forEach(
            deposit -> {
              synchronized (DepositProvider.this) {
                if (!recentChainData.isPreGenesis()) {
                  LOG.debug("About to process deposit: {}", deposit.getIndex());
                }

                depositMerkleTree.addDeposit(deposit);
              }
            });
    eth1DataCache.onBlockWithDeposit(
        event.getBlockTimestamp(),
        new Eth1Data(
            depositMerkleTree.getRoot(),
            UnsignedLong.valueOf(depositMerkleTree.getNumberOfLeaves()),
            event.getBlockHash()));
  }

  @Override
  public synchronized void onNewFinalizedCheckpoint(final Checkpoint checkpoint) {
    BeaconState finalizedState =
        recentChainData
            .getBlockState(checkpoint.getRoot())
            .orElseThrow(
                () -> new IllegalArgumentException("Finalized Checkpoint state can not be found."));

    depositMerkleTree.prune(finalizedState.getEth1_deposit_index());
  }

  @Override
  public void onEth1Block(final Bytes32 blockHash, final UnsignedLong blockTimestamp) {
    eth1DataCache.onEth1Block(blockHash, blockTimestamp);
  }

  @Override
  public void onMinGenesisTimeBlock(MinGenesisTimeBlockEvent event) {}

  public SSZList<Deposit> getDeposits(BeaconState state) {
    return depositMerkleTree.getDeposits(state);
  }

  public int getDepositMapSize() {
    return depositMerkleTree.getDepositMapSize();
  }

}
