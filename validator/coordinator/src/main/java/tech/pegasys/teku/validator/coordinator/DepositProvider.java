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

import static tech.pegasys.teku.core.BlockProcessorUtil.getVoteCount;
import static tech.pegasys.teku.core.BlockProcessorUtil.isEnoughVotesToUpdateEth1Data;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.util.config.Constants.DEPOSIT_CONTRACT_TREE_DEPTH;
import static tech.pegasys.teku.util.config.Constants.MAX_DEPOSITS;

import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import tech.pegasys.teku.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.datastructures.operations.Deposit;
import tech.pegasys.teku.datastructures.operations.DepositWithIndex;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.datastructures.util.DepositUtil;
import tech.pegasys.teku.datastructures.util.MerkleTree;
import tech.pegasys.teku.datastructures.util.OptimizedMerkleTree;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
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

  private final NavigableMap<UInt64, DepositWithIndex> depositNavigableMap = new TreeMap<>();
  private final Counter depositCounter;

  public DepositProvider(
      MetricsSystem metricsSystem,
      RecentChainData recentChainData,
      final Eth1DataCache eth1DataCache) {
    this.recentChainData = recentChainData;
    this.eth1DataCache = eth1DataCache;
    depositCounter =
        metricsSystem.createCounter(
            TekuMetricCategory.BEACON,
            "eth1_deposit_total",
            "Total number of received ETH1 deposits");
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
    depositCounter.inc(event.getDeposits().size());
    eth1DataCache.onBlockWithDeposit(
        event.getBlockTimestamp(),
        new Eth1Data(
            depositMerkleTree.getRoot(),
            UInt64.valueOf(depositMerkleTree.getNumberOfLeaves()),
            event.getBlockHash()));
  }

  @Override
  public void onNewFinalizedCheckpoint(final Checkpoint checkpoint) {
    recentChainData
        .retrieveBlockState(checkpoint.getRoot())
        .thenAccept(
            finalizedState -> {
              if (finalizedState.isEmpty()) {
                LOG.error("Finalized checkpoint state not found.");
                return;
              }
              final UInt64 depositIndex = finalizedState.get().getEth1_deposit_index();
              pruneDeposits(depositIndex);
            })
        .reportExceptions();
  }

  private synchronized void pruneDeposits(final UInt64 fromIndex) {
    depositNavigableMap.headMap(fromIndex, false).clear();
  }

  @Override
  public void onEth1Block(final Bytes32 blockHash, final UInt64 blockTimestamp) {
    eth1DataCache.onEth1Block(blockHash, blockTimestamp);
  }

  @Override
  public void onMinGenesisTimeBlock(MinGenesisTimeBlockEvent event) {}

  public synchronized SSZList<Deposit> getDeposits(BeaconState state, Eth1Data eth1Data) {
    UInt64 eth1DepositCount;
    if (isEnoughVotesToUpdateEth1Data(getVoteCount(state, eth1Data) + 1)) {
      eth1DepositCount = eth1Data.getDeposit_count();
    } else {
      eth1DepositCount = state.getEth1_data().getDeposit_count();
    }

    UInt64 eth1DepositIndex = state.getEth1_deposit_index();

    // We need to have all the deposits that can be included in the state available to ensure
    // the generated proofs are valid
    checkRequiredDepositsAvailable(eth1DepositCount, eth1DepositIndex);

    UInt64 latestDepositIndexWithMaxBlock = eth1DepositIndex.plus(MAX_DEPOSITS);

    UInt64 toDepositIndex =
        latestDepositIndexWithMaxBlock.isGreaterThan(eth1DepositCount)
            ? eth1DepositCount
            : latestDepositIndexWithMaxBlock;

    return SSZList.createMutable(
        getDepositsWithProof(eth1DepositIndex, toDepositIndex, eth1DepositCount),
        MAX_DEPOSITS,
        Deposit.class);
  }

  private void checkRequiredDepositsAvailable(
      final UInt64 eth1DepositCount, final UInt64 eth1DepositIndex) {
    // Note that eth1_deposit_index in the state is actually actually the number of deposits
    // included, so always one bigger than the index of the last included deposit,
    // hence lastKey().plus(ONE).
    final UInt64 maxPossibleResultingDepositIndex =
        depositNavigableMap.isEmpty() ? eth1DepositIndex : depositNavigableMap.lastKey().plus(ONE);
    if (maxPossibleResultingDepositIndex.compareTo(eth1DepositCount) < 0) {
      throw MissingDepositsException.missingRange(
          maxPossibleResultingDepositIndex.plus(UInt64.ONE), eth1DepositCount);
    }
  }

  public synchronized int getDepositMapSize() {
    return depositNavigableMap.size();
  }

  /**
   * @param fromDepositIndex inclusive
   * @param toDepositIndex exclusive
   * @param eth1DepositCount number of deposits in the merkle tree according to Eth1Data in state
   * @return
   */
  private List<Deposit> getDepositsWithProof(
      UInt64 fromDepositIndex, UInt64 toDepositIndex, UInt64 eth1DepositCount) {
    final AtomicReference<UInt64> expectedDepositIndex = new AtomicReference<>(fromDepositIndex);
    return depositNavigableMap.subMap(fromDepositIndex, true, toDepositIndex, false).values()
        .stream()
        .map(
            deposit -> {
              if (!deposit.getIndex().equals(expectedDepositIndex.get())) {
                throw MissingDepositsException.missingRange(
                    expectedDepositIndex.get(), deposit.getIndex());
              }
              expectedDepositIndex.set(deposit.getIndex().plus(ONE));
              return new DepositWithIndex(
                  depositMerkleTree.getProofWithViewBoundary(
                      deposit.getIndex().intValue(), eth1DepositCount.intValue()),
                  deposit.getData(),
                  deposit.getIndex());
            })
        .collect(Collectors.toList());
  }
}
