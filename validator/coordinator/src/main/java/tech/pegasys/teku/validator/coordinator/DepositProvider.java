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

import static com.google.common.primitives.UnsignedLong.ONE;
import static java.lang.StrictMath.toIntExact;
import static tech.pegasys.teku.core.BlockProcessorUtil.getVoteCount;
import static tech.pegasys.teku.core.BlockProcessorUtil.isEnoughVotesToUpdateEth1Data;
import static tech.pegasys.teku.util.config.Constants.DEPOSIT_CONTRACT_TREE_DEPTH;
import static tech.pegasys.teku.util.config.Constants.MAX_DEPOSITS;

import com.google.common.primitives.UnsignedLong;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.blocks.Eth1Data;
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
import tech.pegasys.teku.storage.api.FinalizedCheckpointChannel;
import tech.pegasys.teku.storage.client.RecentChainData;

public class DepositProvider implements Eth1EventsChannel, FinalizedCheckpointChannel {

  private static final Logger LOG = LogManager.getLogger();

  private final RecentChainData recentChainData;
  private final Eth1DataCache eth1DataCache;
  private final MerkleTree depositMerkleTree = new OptimizedMerkleTree(DEPOSIT_CONTRACT_TREE_DEPTH);

  private final NavigableMap<UnsignedLong, DepositWithIndex> depositNavigableMap = new TreeMap<>();

  public DepositProvider(RecentChainData recentChainData, final Eth1DataCache eth1DataCache) {
    this.recentChainData = recentChainData;
    this.eth1DataCache = eth1DataCache;
  }

  @Override
  public synchronized void onDepositsFromBlock(DepositsFromBlockEvent event) {
    event.getDeposits().stream()
        .map(DepositUtil::convertDepositEventToOperationDeposit)
        .forEach(
            deposit -> {
              if (!recentChainData.isPreGenesis()) {
                LOG.debug("About to process deposit: {}", deposit.getIndex());
              }

              depositNavigableMap.put(deposit.getIndex(), deposit);
              depositMerkleTree.add(deposit.getData().hash_tree_root());
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

    depositNavigableMap.headMap(finalizedState.getEth1_deposit_index()).clear();
  }

  @Override
  public void onEth1Block(final Bytes32 blockHash, final UnsignedLong blockTimestamp) {
    eth1DataCache.onEth1Block(blockHash, blockTimestamp);
  }

  @Override
  public void onMinGenesisTimeBlock(MinGenesisTimeBlockEvent event) {}

  public synchronized SSZList<Deposit> getDeposits(BeaconState state, Eth1Data eth1Data) {
    UnsignedLong eth1DepositCount;
    if (isEnoughVotesToUpdateEth1Data(getVoteCount(state, eth1Data) + 1)) {
      eth1DepositCount = eth1Data.getDeposit_count();
    } else {
      eth1DepositCount = state.getEth1_data().getDeposit_count();
    }

    UnsignedLong eth1DepositIndex = state.getEth1_deposit_index();

    // We need to have all the deposits that can be included in the state available to ensure
    // the generated proofs are valid
    checkRequiredDepositsAvailable(eth1DepositCount, eth1DepositIndex);

    UnsignedLong latestDepositIndexWithMaxBlock =
        eth1DepositIndex.plus(UnsignedLong.valueOf(MAX_DEPOSITS));

    UnsignedLong toDepositIndex =
        latestDepositIndexWithMaxBlock.compareTo(eth1DepositCount) > 0
            ? eth1DepositCount
            : latestDepositIndexWithMaxBlock;

    return SSZList.createMutable(
        getDepositsWithProof(eth1DepositIndex, toDepositIndex, eth1DepositCount),
        MAX_DEPOSITS,
        Deposit.class);
  }

  private void checkRequiredDepositsAvailable(
      final UnsignedLong eth1DepositCount, final UnsignedLong eth1DepositIndex) {
    // Note that eth1_deposit_index in the state is actually actually the number of deposits
    // included, so always one bigger than the index of the last included deposit,
    // hence lastKey().plus(ONE).
    final UnsignedLong maxPossibleResultingDepositIndex =
        depositNavigableMap.isEmpty() ? eth1DepositIndex : depositNavigableMap.lastKey().plus(ONE);
    if (maxPossibleResultingDepositIndex.compareTo(eth1DepositCount) < 0) {
      throw new MissingDepositsException(maxPossibleResultingDepositIndex, eth1DepositCount);
    }
  }

  public synchronized int getDepositMapSize() {
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
  private List<Deposit> getDepositsWithProof(
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
