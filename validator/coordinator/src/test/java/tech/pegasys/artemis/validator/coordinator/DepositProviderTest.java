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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.is_valid_merkle_branch;

import com.google.common.primitives.UnsignedLong;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.blocks.Eth1Data;
import tech.pegasys.artemis.datastructures.operations.Deposit;
import tech.pegasys.artemis.datastructures.operations.DepositData;
import tech.pegasys.artemis.datastructures.operations.DepositWithIndex;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.Checkpoint;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.datastructures.util.DepositUtil;
import tech.pegasys.artemis.datastructures.util.MerkleTree;
import tech.pegasys.artemis.datastructures.util.OptimizedMerkleTree;
import tech.pegasys.artemis.pow.event.DepositsFromBlockEvent;
import tech.pegasys.artemis.storage.RecentChainData;
import tech.pegasys.artemis.storage.events.FinalizedCheckpointEvent;
import tech.pegasys.artemis.util.SSZTypes.SSZList;
import tech.pegasys.artemis.util.config.Constants;

public class DepositProviderTest {

  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private List<tech.pegasys.artemis.pow.event.Deposit> allSeenDepositsList;
  private DepositProvider depositProvider;
  private RecentChainData recentChainData;
  private BeaconState beaconState;
  private MerkleTree depositMerkleTree;

  @BeforeEach
  void setUp() {
    recentChainData = mock(RecentChainData.class);
    beaconState = mock(BeaconState.class);

    depositMerkleTree = new OptimizedMerkleTree(Constants.DEPOSIT_CONTRACT_TREE_DEPTH);
    depositProvider = new DepositProvider(recentChainData);

    createDepositEvents(40);
  }

  @Test
  void stateEth1DepositIndexIsEqualToEth1DataDepositCount_NoDepositReturned() {
    Constants.MAX_DEPOSITS = 5;
    mockStateEth1DepositIndex(2);
    mockEth1DataDepositCount(2);
    mockDepositsFromEth1Block(0, 10);
    SSZList<Deposit> deposits = depositProvider.getDeposits(beaconState);
    assertThat(deposits).isEmpty();
  }

  @Test
  void numberOfDepositsThatCanBeIncludedLessThanMaxDeposits() {
    mockStateEth1DepositIndex(5);
    mockEth1DataDepositCount(20);

    Constants.MAX_DEPOSITS = 16;

    mockDepositsFromEth1Block(0, 10);
    mockDepositsFromEth1Block(10, 20);

    SSZList<Deposit> deposits = depositProvider.getDeposits(beaconState);
    assertThat(deposits).hasSize(15);
    checkThatDepositProofIsValid(deposits);
  }

  @Test
  void numberOfDepositsThatCanBeIncludedMoreThanMaxDeposits() {
    mockStateEth1DepositIndex(5);
    mockEth1DataDepositCount(20);

    Constants.MAX_DEPOSITS = 10;

    mockDepositsFromEth1Block(0, 10);
    mockDepositsFromEth1Block(10, 20);

    SSZList<Deposit> deposits = depositProvider.getDeposits(beaconState);
    assertThat(deposits).hasSize(10);
    checkThatDepositProofIsValid(deposits);
  }

  @Test
  void depositsWithFinalizedIndicesGetPrunedFromMap() {
    Bytes32 finalizedBlockRoot = Bytes32.fromHexString("0x01");
    mockStateEth1DepositIndex(10);
    mockDepositsFromEth1Block(0, 20);
    when(recentChainData.getBlockState(eq(finalizedBlockRoot)))
        .thenReturn(Optional.ofNullable(beaconState));

    assertThat(depositProvider.getDepositMapSize()).isEqualTo(20);

    depositProvider.onFinalizedCheckpoint(
        new FinalizedCheckpointEvent(new Checkpoint(UnsignedLong.ONE, finalizedBlockRoot)));

    assertThat(depositProvider.getDepositMapSize()).isEqualTo(10);
  }

  private void checkThatDepositProofIsValid(SSZList<Deposit> deposits) {
    deposits.forEach(
        deposit ->
            assertThat(
                    is_valid_merkle_branch(
                        deposit.getData().hash_tree_root(),
                        deposit.getProof(),
                        Constants.DEPOSIT_CONTRACT_TREE_DEPTH + 1,
                        ((DepositWithIndex) deposit).getIndex().intValue(),
                        depositMerkleTree.getRoot()))
                .isTrue());
  }

  private void createDepositEvents(int n) {
    allSeenDepositsList =
        IntStream.range(0, n)
            .mapToObj(i -> dataStructureUtil.randomDepositEvent(UnsignedLong.valueOf(i)))
            .collect(Collectors.toList());
  }

  private void mockDepositsFromEth1Block(int startIndex, int n) {
    DepositsFromBlockEvent depositsFromBlockEvent = mock(DepositsFromBlockEvent.class);
    when(depositsFromBlockEvent.getDeposits())
        .thenReturn(allSeenDepositsList.subList(startIndex, startIndex + n));
    depositProvider.onDepositsFromBlock(depositsFromBlockEvent);
  }

  private void mockEth1DataDepositCount(int n) {
    allSeenDepositsList.subList(0, n).stream()
        .map(DepositUtil::convertDepositEventToOperationDeposit)
        .map(Deposit::getData)
        .map(DepositData::hash_tree_root)
        .forEachOrdered(depositMerkleTree::add);

    Eth1Data eth1Data = mock(Eth1Data.class);
    when(beaconState.getEth1_data()).thenReturn(eth1Data);
    when(eth1Data.getDeposit_count()).thenReturn(UnsignedLong.valueOf(n));
  }

  private void mockStateEth1DepositIndex(int n) {
    when(beaconState.getEth1_deposit_index()).thenReturn(UnsignedLong.valueOf(n));
  }
}
