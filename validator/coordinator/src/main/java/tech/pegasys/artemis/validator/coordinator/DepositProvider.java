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

package tech.pegasys.artemis.validator.coordinator;

import static java.lang.StrictMath.toIntExact;
import static tech.pegasys.artemis.util.alogger.ALogger.STDOUT;
import static tech.pegasys.artemis.util.config.Constants.DEPOSIT_CONTRACT_TREE_DEPTH;
import static tech.pegasys.artemis.util.config.Constants.MAX_DEPOSITS;

import com.google.common.primitives.UnsignedLong;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.artemis.datastructures.operations.Deposit;
import tech.pegasys.artemis.datastructures.operations.DepositWithIndex;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.util.DepositUtil;
import tech.pegasys.artemis.datastructures.util.MerkleTree;
import tech.pegasys.artemis.datastructures.util.OptimizedMerkleTree;
import tech.pegasys.artemis.pow.api.Eth1EventsChannel;
import tech.pegasys.artemis.pow.event.DepositsFromBlockEvent;
import tech.pegasys.artemis.pow.event.MinGenesisTimeBlockEvent;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.storage.api.FinalizedCheckpointEventChannel;
import tech.pegasys.artemis.storage.events.FinalizedCheckpointEvent;
import tech.pegasys.artemis.util.SSZTypes.SSZList;

public class DepositProvider implements Eth1EventsChannel, FinalizedCheckpointEventChannel {

  private static final Logger LOG = LogManager.getLogger();

  private final ChainStorageClient chainStorageClient;
  private final MerkleTree depositMerkleTree = new OptimizedMerkleTree(DEPOSIT_CONTRACT_TREE_DEPTH);

  private NavigableMap<UnsignedLong, DepositWithIndex> depositNavigableMap = new TreeMap<>();

  public DepositProvider(ChainStorageClient chainStorageClient) {
    this.chainStorageClient = chainStorageClient;
  }

  @Override
  public void onDepositsFromBlock(DepositsFromBlockEvent event) {
    event.getDeposits().stream()
        .map(DepositUtil::convertDepositEventToOperationDeposit)
        .forEach(
            deposit -> {
              synchronized (DepositProvider.this) {
                if (!chainStorageClient.isPreGenesis()) {
                  LOG.debug("About to process deposit: {}", deposit.getIndex());
                }

                depositNavigableMap.put(deposit.getIndex(), deposit);
                depositMerkleTree.add(deposit.getData().hash_tree_root());
              }
            });
  }

  @Override
  public synchronized void onFinalizedCheckpoint(FinalizedCheckpointEvent event) {
    BeaconState finalizedState =
        chainStorageClient
            .getBlockState(event.getCheckpoint().getRoot())
            .orElseThrow(
                () -> new IllegalArgumentException("Finalized Checkpoint state can not be found."));

    depositNavigableMap.headMap(finalizedState.getEth1_deposit_index()).clear();
  }

  // It's sad that we have to do this. Is there any other way?
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

    return new SSZList<>(
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
