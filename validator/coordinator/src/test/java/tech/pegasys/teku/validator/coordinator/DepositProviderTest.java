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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.is_valid_merkle_branch;

import com.google.common.primitives.UnsignedLong;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.datastructures.operations.Deposit;
import tech.pegasys.teku.datastructures.operations.DepositData;
import tech.pegasys.teku.datastructures.operations.DepositWithIndex;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.datastructures.util.DepositUtil;
import tech.pegasys.teku.datastructures.util.MerkleTree;
import tech.pegasys.teku.datastructures.util.OptimizedMerkleTree;
import tech.pegasys.teku.pow.event.DepositsFromBlockEvent;
import tech.pegasys.teku.ssz.SSZTypes.SSZList;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.util.config.Constants;

public class DepositProviderTest {

  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private final RecentChainData recentChainData = mock(RecentChainData.class);
  private final BeaconState beaconState = mock(BeaconState.class);
  private final Eth1DataCache eth1DataCache = mock(Eth1DataCache.class);
  private final MerkleTree depositMerkleTree =
      new OptimizedMerkleTree(Constants.DEPOSIT_CONTRACT_TREE_DEPTH);
  private List<tech.pegasys.teku.pow.event.Deposit> allSeenDepositsList;
  private final DepositProvider depositProvider =
      new DepositProvider(recentChainData, eth1DataCache);

  @BeforeEach
  void setUp() {
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

    depositProvider.onNewFinalizedCheckpoint(new Checkpoint(UnsignedLong.ONE, finalizedBlockRoot));

    assertThat(depositProvider.getDepositMapSize()).isEqualTo(10);
  }

  @Test
  void shouldDelegateOnEth1BlockToEth1DataCache() {
    final Bytes32 blockHash = dataStructureUtil.randomBytes32();
    final UnsignedLong blockTimestamp = dataStructureUtil.randomUnsignedLong();
    depositProvider.onEth1Block(blockHash, blockTimestamp);
    verify(eth1DataCache).onEth1Block(blockHash, blockTimestamp);
  }

  @Test
  void shouldNotifyEth1DataCacheOfDepositBlocks() {
    final tech.pegasys.teku.pow.event.Deposit deposit =
        dataStructureUtil.randomDepositEvent(UnsignedLong.ZERO);
    final DepositsFromBlockEvent event =
        new DepositsFromBlockEvent(
            dataStructureUtil.randomUnsignedLong(),
            dataStructureUtil.randomBytes32(),
            dataStructureUtil.randomUnsignedLong(),
            List.of(deposit));
    depositProvider.onDepositsFromBlock(event);

    depositMerkleTree.add(
        DepositUtil.convertDepositEventToOperationDeposit(deposit).getData().hash_tree_root());
    verify(eth1DataCache)
        .onBlockWithDeposit(
            event.getBlockTimestamp(),
            new Eth1Data(depositMerkleTree.getRoot(), UnsignedLong.ONE, event.getBlockHash()));
  }

  @Test
  void shouldNotThrowMissingDepositsExceptionWhenAllKnownDepositsHaveBeenIncluded() {
    mockStateEth1DepositIndex(5);
    mockEth1DataDepositCount(5);
    mockDepositsFromEth1Block(0, 5);
    assertThat(depositProvider.getDeposits(beaconState)).isEmpty();
  }

  @Test
  void shouldThrowMissingDepositsExceptionWhenRequiredDepositsAreNotAvailable() {
    mockStateEth1DepositIndex(5);
    mockEth1DataDepositCount(10);
    assertThatThrownBy(() -> depositProvider.getDeposits(beaconState))
        .isInstanceOf(MissingDepositsException.class)
        .hasMessageContaining("6 to 10");
  }

  @Test
  void shouldThrowMissingDepositsExceptionWhenAllDepositsRequiredForStateNotAvailable() {
    // To generate a valid proof we need the deposits up to state deposit count
    // So fail even if we could have filled MAX_DEPOSITS
    Constants.MAX_DEPOSITS = 1;
    mockDepositsFromEth1Block(0, 8);
    mockStateEth1DepositIndex(5);
    mockEth1DataDepositCount(10);

    assertThatThrownBy(() -> depositProvider.getDeposits(beaconState))
        .isInstanceOf(MissingDepositsException.class)
        .hasMessageContaining("9 to 10");
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
