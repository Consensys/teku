package tech.pegasys.teku.core;

import com.google.common.primitives.UnsignedLong;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.operations.Deposit;
import tech.pegasys.teku.datastructures.operations.DepositWithIndex;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.util.MerkleTree;
import tech.pegasys.teku.datastructures.util.OptimizedMerkleTree;
import tech.pegasys.teku.ssz.SSZTypes.SSZList;

import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static java.lang.StrictMath.toIntExact;
import static tech.pegasys.teku.util.config.Constants.DEPOSIT_CONTRACT_TREE_DEPTH;
import static tech.pegasys.teku.util.config.Constants.MAX_DEPOSITS;

public class DepositMerkleTree {
  private final MerkleTree depositMerkleTree = new OptimizedMerkleTree(DEPOSIT_CONTRACT_TREE_DEPTH);
  private final NavigableMap<UnsignedLong, DepositWithIndex> depositNavigableMap = new TreeMap<>();

  public void addDeposit(DepositWithIndex deposit) {
    depositNavigableMap.put(deposit.getIndex(), deposit);
    depositMerkleTree.add(deposit.getData().hash_tree_root());
  }

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
   * @param toDepositIndex   exclusive
   * @param eth1DepositCount number of deposits in the merkle tree according to Eth1Data in state
   * @return
   */
  synchronized List<Deposit> getDepositsWithProof(
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

  public void prune(UnsignedLong finalizedStateDepositIndex) {
    depositNavigableMap.headMap(finalizedStateDepositIndex).clear();
  }

  public Bytes32 getRoot() {
    return depositMerkleTree.getRoot();
  }

  public int getNumberOfLeaves() {
    return depositMerkleTree.getNumberOfLeaves();
  }
}
