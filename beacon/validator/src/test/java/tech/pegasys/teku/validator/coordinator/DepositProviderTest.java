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

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.ethereum.pow.api.DepositsFromBlockEvent;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigLoader;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.datastructures.operations.Deposit;
import tech.pegasys.teku.spec.datastructures.operations.DepositData;
import tech.pegasys.teku.spec.datastructures.operations.DepositWithIndex;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.util.DepositUtil;
import tech.pegasys.teku.spec.datastructures.util.MerkleTree;
import tech.pegasys.teku.spec.datastructures.util.OptimizedMerkleTree;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.client.RecentChainData;

public class DepositProviderTest {

  private Spec spec;
  private DataStructureUtil dataStructureUtil;
  private final RecentChainData recentChainData = mock(RecentChainData.class);
  private final BeaconState state = mock(BeaconState.class);
  private final Eth1DataCache eth1DataCache = mock(Eth1DataCache.class);
  private List<tech.pegasys.teku.ethereum.pow.api.Deposit> allSeenDepositsList;
  private DepositProvider depositProvider;
  private Eth1Data randomEth1Data;

  private MerkleTree depositMerkleTree;

  void setup(final int maxDeposits) {
    when(state.getSlot()).thenReturn(UInt64.valueOf(1234));

    SpecConfig specConfig = SpecConfigLoader.loadConfig("minimal", b -> b.maxDeposits(maxDeposits));
    spec = TestSpecFactory.createPhase0(specConfig);
    dataStructureUtil = new DataStructureUtil(spec);
    depositProvider =
        new DepositProvider(new StubMetricsSystem(), recentChainData, eth1DataCache, spec);
    depositMerkleTree =
        new OptimizedMerkleTree(spec.getGenesisSpecConfig().getDepositContractTreeDepth());
    mockStateEth1DataVotes();
    createDepositEvents(40);
    randomEth1Data = dataStructureUtil.randomEth1Data();
  }

  @Test
  void stateEth1DepositIndexIsEqualToEth1DataDepositCount_NoDepositReturned() {
    setup(5);
    mockStateEth1DepositIndex(2);
    mockEth1DataDepositCount(2);
    mockDepositsFromEth1Block(0, 10);
    SszList<Deposit> deposits = depositProvider.getDeposits(state, randomEth1Data);
    assertThat(deposits).isEmpty();
  }

  @Test
  void numberOfDepositsThatCanBeIncludedLessThanMaxDeposits() {
    setup(16);
    mockStateEth1DepositIndex(5);
    mockEth1DataDepositCount(20);

    mockDepositsFromEth1Block(0, 10);
    mockDepositsFromEth1Block(10, 20);

    SszList<Deposit> deposits = depositProvider.getDeposits(state, randomEth1Data);
    assertThat(deposits).hasSize(15);
    checkThatDepositProofIsValid(deposits);
  }

  @Test
  void numberOfDepositsGetsAdjustedAccordingToOurEth1DataVote() {
    setup(30);
    mockStateEth1DepositIndex(5);
    mockEth1DataDepositCount(20);

    mockDepositsFromEth1Block(0, 10);
    mockDepositsFromEth1Block(10, 30);

    int enoughVoteCount =
        spec.getGenesisSpecConfig().getEpochsPerEth1VotingPeriod()
            * spec.slotsPerEpoch(SpecConfig.GENESIS_EPOCH);
    UInt64 newDepositCount = UInt64.valueOf(30);
    Eth1Data newEth1Data = new Eth1Data(Bytes32.ZERO, newDepositCount, Bytes32.ZERO);
    SszList<Eth1Data> et1hDataVotes =
        Stream.generate(() -> newEth1Data)
            .limit(enoughVoteCount)
            .collect(SszListSchema.create(Eth1Data.SSZ_SCHEMA, 50).collector());
    when(state.getEth1_data_votes()).thenReturn(et1hDataVotes);

    SszList<Deposit> deposits = depositProvider.getDeposits(state, newEth1Data);
    assertThat(deposits).hasSize(25);
    checkThatDepositProofIsValid(deposits);
  }

  @Test
  void numberOfDepositsThatCanBeIncludedMoreThanMaxDeposits() {
    setup(10);
    mockStateEth1DepositIndex(5);
    mockEth1DataDepositCount(20);

    mockDepositsFromEth1Block(0, 10);
    mockDepositsFromEth1Block(10, 20);

    SszList<Deposit> deposits = depositProvider.getDeposits(state, randomEth1Data);
    assertThat(deposits).hasSize(10);
    checkThatDepositProofIsValid(deposits);
  }

  @Test
  void depositsWithFinalizedIndicesGetPrunedFromMap() {
    setup(16);
    Bytes32 finalizedBlockRoot = Bytes32.fromHexString("0x01");
    mockStateEth1DepositIndex(10);
    mockDepositsFromEth1Block(0, 20);
    when(recentChainData.retrieveBlockState(eq(finalizedBlockRoot)))
        .thenReturn(SafeFuture.completedFuture(Optional.ofNullable(state)));

    assertThat(depositProvider.getDepositMapSize()).isEqualTo(20);

    depositProvider.onNewFinalizedCheckpoint(new Checkpoint(UInt64.ONE, finalizedBlockRoot));

    assertThat(depositProvider.getDepositMapSize()).isEqualTo(10);
  }

  @Test
  void shouldDelegateOnEth1BlockToEth1DataCache() {
    setup(16);
    final Bytes32 blockHash = dataStructureUtil.randomBytes32();
    final UInt64 blockTimestamp = dataStructureUtil.randomUInt64();
    depositProvider.onEth1Block(blockHash, blockTimestamp);
    verify(eth1DataCache).onEth1Block(blockHash, blockTimestamp);
  }

  @Test
  void shouldNotifyEth1DataCacheOfDepositBlocks() {
    setup(16);
    final tech.pegasys.teku.ethereum.pow.api.Deposit deposit =
        dataStructureUtil.randomDepositEvent(UInt64.ZERO);
    final DepositsFromBlockEvent event =
        DepositsFromBlockEvent.create(
            dataStructureUtil.randomUInt64(),
            dataStructureUtil.randomBytes32(),
            dataStructureUtil.randomUInt64(),
            Stream.of(deposit));
    depositProvider.onDepositsFromBlock(event);

    depositMerkleTree.add(
        DepositUtil.convertDepositEventToOperationDeposit(deposit).getData().hashTreeRoot());
    verify(eth1DataCache)
        .onBlockWithDeposit(
            event.getBlockTimestamp(),
            new Eth1Data(depositMerkleTree.getRoot(), UInt64.ONE, event.getBlockHash()));
  }

  @Test
  void shouldNotThrowMissingDepositsExceptionWhenAllKnownDepositsHaveBeenIncluded() {
    setup(16);
    mockStateEth1DepositIndex(5);
    mockEth1DataDepositCount(5);
    mockDepositsFromEth1Block(0, 5);
    assertThat(depositProvider.getDeposits(state, randomEth1Data)).isEmpty();
  }

  @Test
  void shouldThrowMissingDepositsExceptionWhenRequiredDepositsAreNotAvailable() {
    setup(16);
    mockStateEth1DepositIndex(5);
    mockEth1DataDepositCount(10);
    assertThatThrownBy(() -> depositProvider.getDeposits(state, randomEth1Data))
        .isInstanceOf(MissingDepositsException.class)
        .hasMessageContaining("6 to 10");
  }

  @Test
  void shouldThrowMissingDepositsExceptionWhenAllDepositsRequiredForStateNotAvailable() {
    setup(1);
    // To generate a valid proof we need the deposits up to state deposit count
    // So fail even if we could have filled MAX_DEPOSITS
    mockDepositsFromEth1Block(0, 8);
    mockStateEth1DepositIndex(5);
    mockEth1DataDepositCount(10);

    assertThatThrownBy(() -> depositProvider.getDeposits(state, randomEth1Data))
        .isInstanceOf(MissingDepositsException.class)
        .hasMessageContaining("9 to 10");
  }

  @Test
  void shouldThrowWhenAllDepositsRequiredForStateNotAvailable_skippedDeposit() {
    setup(5);
    mockDepositsFromEth1Block(0, 7);
    // Deposit 7 is missing
    mockDepositsFromEth1Block(8, 10);
    mockStateEth1DepositIndex(5);
    mockEth1DataDepositCount(10);

    assertThatThrownBy(() -> depositProvider.getDeposits(state, randomEth1Data))
        .isInstanceOf(MissingDepositsException.class)
        .hasMessageContaining("7 to 8");
  }

  @Test
  void shouldThrowWhenAllDepositsRequiredForStateNotAvailable_skippedDeposits() {
    setup(5);
    mockDepositsFromEth1Block(0, 7);
    // Deposits 7,8 are missing
    mockDepositsFromEth1Block(9, 10);
    mockStateEth1DepositIndex(5);
    mockEth1DataDepositCount(10);

    assertThatThrownBy(() -> depositProvider.getDeposits(state, randomEth1Data))
        .isInstanceOf(MissingDepositsException.class)
        .hasMessageContaining("7 to 9");
  }

  private void checkThatDepositProofIsValid(SszList<Deposit> deposits) {
    final SpecVersion genesisSpec = spec.getGenesisSpec();
    deposits.forEach(
        deposit ->
            assertThat(
                    genesisSpec
                        .predicates()
                        .isValidMerkleBranch(
                            deposit.getData().hashTreeRoot(),
                            deposit.getProof(),
                            genesisSpec.getConfig().getDepositContractTreeDepth() + 1,
                            ((DepositWithIndex) deposit).getIndex().intValue(),
                            depositMerkleTree.getRoot()))
                .isTrue());
  }

  private void createDepositEvents(int n) {
    allSeenDepositsList =
        IntStream.range(0, n)
            .mapToObj(i -> dataStructureUtil.randomDepositEvent(UInt64.valueOf(i)))
            .collect(Collectors.toList());
  }

  private void mockDepositsFromEth1Block(int startIndex, int n) {
    allSeenDepositsList.subList(startIndex, n).stream()
        .map(DepositUtil::convertDepositEventToOperationDeposit)
        .map(Deposit::getData)
        .map(DepositData::hashTreeRoot)
        .forEachOrdered(depositMerkleTree::add);

    DepositsFromBlockEvent depositsFromBlockEvent = mock(DepositsFromBlockEvent.class);
    when(depositsFromBlockEvent.getDeposits())
        .thenReturn(allSeenDepositsList.subList(startIndex, startIndex + n));
    when(depositsFromBlockEvent.getBlockHash()).thenReturn(Bytes32.ZERO);
    depositProvider.onDepositsFromBlock(depositsFromBlockEvent);
  }

  private void mockEth1DataDepositCount(int n) {
    Eth1Data eth1Data = mock(Eth1Data.class);
    when(state.getEth1_data()).thenReturn(eth1Data);
    when(eth1Data.getDeposit_count()).thenReturn(UInt64.valueOf(n));
  }

  private void mockStateEth1DepositIndex(int n) {
    when(state.getEth1_deposit_index()).thenReturn(UInt64.valueOf(n));
  }

  private void mockStateEth1DataVotes() {
    when(state.getEth1_data_votes()).thenReturn(SszListSchema.create(Eth1Data.SSZ_SCHEMA, 0).of());
  }
}
