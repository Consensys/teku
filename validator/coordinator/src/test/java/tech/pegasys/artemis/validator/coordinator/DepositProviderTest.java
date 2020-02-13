package tech.pegasys.artemis.validator.coordinator;


import com.google.common.primitives.UnsignedLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.blocks.Eth1Data;
import tech.pegasys.artemis.datastructures.operations.Deposit;
import tech.pegasys.artemis.datastructures.operations.DepositData;
import tech.pegasys.artemis.datastructures.operations.DepositWithIndex;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.datastructures.util.DepositUtil;
import tech.pegasys.artemis.datastructures.util.MerkleTree;
import tech.pegasys.artemis.datastructures.util.OptimizedMerkleTree;
import tech.pegasys.artemis.pow.event.DepositsFromBlockEvent;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.util.SSZTypes.SSZList;
import tech.pegasys.artemis.util.config.Constants;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.is_valid_merkle_branch;

public class DepositProviderTest {

  private int seed = 0;
  private int depositCount;

  private List<tech.pegasys.artemis.pow.event.Deposit> allSeenDepositsList;
  private DepositProvider depositProvider;
  private ChainStorageClient chainStorageClient;
  private BeaconState beaconState;
  private MerkleTree depositMerkleTree;

  @BeforeEach
  void setUp() {
    depositCount = 0;

    chainStorageClient = mock(ChainStorageClient.class);
    beaconState = mock(BeaconState.class);

    depositMerkleTree = new OptimizedMerkleTree(Constants.DEPOSIT_CONTRACT_TREE_DEPTH);
    depositProvider = new DepositProvider(chainStorageClient);

    createDepositEvents(100);
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
    mockStateEth1DepositIndex(10);
    mockEth1DataDepositCount(15);
    Constants.MAX_DEPOSITS = 10;
    mockDepositsFromEth1Block(0, 40);
    SSZList<Deposit> deposits = depositProvider.getDeposits(beaconState);
    assertThat(deposits).hasSize(5);
    checkThatDepositProofIsValid(deposits);
  }

  @Test
  void numberOfDepositsThatCanBeIncludedMoreThanMaxDeposits() {
  }

  @Test
  void depositsWithFinalizedIndicesGetPrunedFromMap() {
  }

  private void checkThatDepositProofIsValid(List<Deposit> deposits) {
    deposits.forEach(deposit -> {
      System.out.println(((DepositWithIndex) deposit).getIndex().intValue());
              assertThat(
              is_valid_merkle_branch(
                      deposit.getData().hash_tree_root(),
                      deposit.getProof(),
                      Constants.DEPOSIT_CONTRACT_TREE_DEPTH + 1,
                      ((DepositWithIndex) deposit).getIndex().intValue(),
                      depositMerkleTree.getRoot())
      ).isTrue();
    });
  }

  private void createDepositEvents(int n) {
    allSeenDepositsList =  IntStream.range(0, n)
            .mapToObj(i -> DataStructureUtil.randomDepositEvent(seed++, UnsignedLong.valueOf(i)))
            .collect(Collectors.toList());
  }

  private void mockDepositsFromEth1Block(int startIndex, int n) {
    DepositsFromBlockEvent depositsFromBlockEvent = mock(DepositsFromBlockEvent.class);
    when(depositsFromBlockEvent.getDeposits()).thenReturn(allSeenDepositsList.subList(startIndex, startIndex + n));
    depositProvider.onDepositsFromBlock(depositsFromBlockEvent);
  }

  private void mockEth1DataDepositCount(int n) {
    allSeenDepositsList.subList(0, n)
            .stream()
            .map(DepositUtil::convertDepositEventToOperationDeposit)
            .peek(System.out::println)
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
