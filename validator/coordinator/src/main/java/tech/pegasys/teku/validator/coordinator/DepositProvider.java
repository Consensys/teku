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

import static java.lang.StrictMath.toIntExact;
import static tech.pegasys.teku.util.config.Constants.DEPOSIT_CONTRACT_TREE_DEPTH;
import static tech.pegasys.teku.util.config.Constants.MAX_DEPOSITS;

import com.google.common.primitives.UnsignedLong;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.datastructures.blocks.Eth1BlockData;
import tech.pegasys.teku.datastructures.operations.Deposit;
import tech.pegasys.teku.datastructures.operations.DepositWithIndex;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.datastructures.util.DepositUtil;
import tech.pegasys.teku.datastructures.util.MerkleTree;
import tech.pegasys.teku.datastructures.util.OptimizedMerkleTree;
import tech.pegasys.teku.pow.api.Eth1EventsChannel;
import tech.pegasys.teku.pow.event.DepositsFromBlockEvent;
import tech.pegasys.teku.pow.event.MinGenesisTimeBlockEvent;
import tech.pegasys.teku.ssz.SSZTypes.SSZList;
import tech.pegasys.teku.storage.api.Eth1DepositChannel;
import tech.pegasys.teku.storage.api.FinalizedCheckpointChannel;
import tech.pegasys.teku.storage.client.RecentChainData;

public class DepositProvider implements Eth1EventsChannel, FinalizedCheckpointChannel {

  private static final Logger LOG = LogManager.getLogger();

  private final RecentChainData recentChainData;
  private final Eth1DepositChannel eth1DepositChannel;
  private final MerkleTree depositMerkleTree = new OptimizedMerkleTree(DEPOSIT_CONTRACT_TREE_DEPTH);

  private NavigableMap<UnsignedLong, DepositWithIndex> depositNavigableMap = new TreeMap<>();

  public DepositProvider(
      final RecentChainData recentChainData, final Eth1DepositChannel eth1DepositChannel) {
    this.recentChainData = recentChainData;
    this.eth1DepositChannel = eth1DepositChannel;
  }

  @Override
  public void onDepositsFromBlock(DepositsFromBlockEvent event) {
    event.getDeposits().stream()
        .map(DepositUtil::convertDepositEventToOperationDeposit)
        .forEach(
            deposit -> {
              if (!recentChainData.isPreGenesis()) {
                LOG.debug("About to process deposit: {}", deposit.getIndex());
              }
              eth1DepositChannel.addEth1Deposit(deposit);
              synchronized (DepositProvider.this) {
                depositNavigableMap.put(deposit.getIndex(), deposit);
                depositMerkleTree.add(deposit.getData().hash_tree_root());
              }
            });
    eth1DepositChannel.addEth1BlockData(
        event.getBlockTimestamp(),
        new Eth1BlockData(UnsignedLong.valueOf(event.getDeposits().size()), lastDepositIndex, event.getBlockHash()));
  }

  @Override
  public synchronized void onNewFinalizedCheckpoint(final Checkpoint checkpoint) {
    BeaconState finalizedState =
        recentChainData
            .getBlockState(checkpoint.getRoot())
            .orElseThrow(
                () -> new IllegalArgumentException("Finalized Checkpoint state can not be found."));

    depositNavigableMap.headMap(finalizedState.getEth1_deposit_index()).clear();
  }

  @Override
  public void onMinGenesisTimeBlock(MinGenesisTimeBlockEvent event) {}

  public SSZList<Deposit> getDeposits(BeaconState state) {
    UnsignedLong eth1DepositCount = state.getEth1_data().getDeposit_count();

    UnsignedLong fromDepositIndex = state.getEth1_deposit_index();
    UnsignedLong latestDepositIndexWithMaxBlock =
        fromDepositIndex.plus(UnsignedLong.valueOf(MAX_DEPOSITS));

    UnsignedLong toDepositIndex =
        latestDepositIndexWithMaxBlock.compareTo(eth1DepositCount) > 0
            ? eth1DepositCount
            : latestDepositIndexWithMaxBlock;

    return SSZList.createMutable(
        getDepositsWithProof(fromDepositIndex, toDepositIndex, eth1DepositCount),
        MAX_DEPOSITS,
        Deposit.class);
  }

  public int getDepositMapSize() {
    return depositNavigableMap.size();
  }

  // TODO: switch the MerkleTree to use UnsignedLongs instead of using toIntExact() here,
  //  it will result in an overflow at some point
  /**
   * @param fromDepositIndex inclusive
   * @param toDepositIndex exclusive
   * @param eth1DepositCount number of deposits in the merkle tree according to Eth1Data in state
   * @return
   */
  private synchronized List<Deposit> getDepositsWithProof(
      UnsignedLong fromDepositIndex, UnsignedLong toDepositIndex, UnsignedLong eth1DepositCount) {
    return depositNavigableMap.subMap(fromDepositIndex, toDepositIndex).values().stream()
        .map(
            deposit ->
                new DepositWithIndex(
                    depositMerkleTree.getProofWithViewBoundary(
                        toIntExact(deposit.getIndex().longValue()),
                        toIntExact(eth1DepositCount.longValue())),
                    deposit.getData(),
                    deposit.getIndex()))
        .collect(Collectors.toList());
  }
}
