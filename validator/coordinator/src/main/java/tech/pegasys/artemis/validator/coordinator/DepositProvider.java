package tech.pegasys.artemis.validator.coordinator;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.common.primitives.UnsignedLong;
import com.google.common.primitives.UnsignedLongs;
import org.antlr.v4.runtime.tree.Tree;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlockBodyLists;
import tech.pegasys.artemis.datastructures.blocks.Eth1Data;
import tech.pegasys.artemis.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.operations.Deposit;
import tech.pegasys.artemis.datastructures.operations.DepositData;
import tech.pegasys.artemis.datastructures.operations.DepositWithIndex;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.BeaconStateWithCache;
import tech.pegasys.artemis.datastructures.util.DepositUtil;
import tech.pegasys.artemis.datastructures.util.MerkleTree;
import tech.pegasys.artemis.datastructures.util.OptimizedMerkleTree;
import tech.pegasys.artemis.pow.api.DepositEventChannel;
import tech.pegasys.artemis.pow.event.DepositsFromBlockEvent;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.storage.api.FinalizedCheckpointEventChannel;
import tech.pegasys.artemis.storage.events.FinalizedCheckpointEvent;
import tech.pegasys.artemis.util.SSZTypes.SSZList;

import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static java.lang.StrictMath.toIntExact;
import static tech.pegasys.artemis.util.config.Constants.DEPOSIT_CONTRACT_TREE_DEPTH;
import static tech.pegasys.artemis.util.config.Constants.MAX_DEPOSITS;

public class DepositProvider implements DepositEventChannel, FinalizedCheckpointEventChannel {

  private final ChainStorageClient chainStorageClient;
  private final MerkleTree depositMerkleTree = new OptimizedMerkleTree(DEPOSIT_CONTRACT_TREE_DEPTH);

  @VisibleForTesting
  NavigableMap<UnsignedLong, DepositWithIndex> depositNavigableMap = new TreeMap<>();

  public DepositProvider(ChainStorageClient chainStorageClient) {
    this.chainStorageClient = chainStorageClient;
  }

  @Override
  public void onDepositsFromBlock(DepositsFromBlockEvent event) {
    event.getDeposits().stream()
            .map(DepositUtil::convertDepositEventToOperationDeposit)
            .peek(deposit -> depositNavigableMap.put(deposit.getIndex(), deposit))
            .map(Deposit::getData)
            .map(DepositData::hash_tree_root)
            .forEachOrdered(depositMerkleTree::add);
  }

  @Override
  public void onFinalizedCheckpoint(FinalizedCheckpointEvent event) {
    BeaconState finalizedState = chainStorageClient.getBlockState(event.getCheckpoint().getRoot())
            .orElseThrow(() -> new IllegalArgumentException("Finalized Checkpoint state can not be find."));

    depositNavigableMap = depositNavigableMap.tailMap(
            finalizedState.getEth1_deposit_index(),
            true
    );
  }

  public SSZList<Deposit> getDeposits(BeaconState state) {
    UnsignedLong eth1DepositCount = state.getEth1_data().getDeposit_count();

    UnsignedLong fromDepositIndex = state.getEth1_deposit_index();
    UnsignedLong latestDepositIndexWithMaxBlock = fromDepositIndex.plus(UnsignedLong.valueOf(MAX_DEPOSITS));

    UnsignedLong toDepositIndex = latestDepositIndexWithMaxBlock.compareTo(eth1DepositCount) > 0
            ? eth1DepositCount : latestDepositIndexWithMaxBlock;

    return new SSZList<>(
            getDepositsWithProof(fromDepositIndex, toDepositIndex, eth1DepositCount),
            MAX_DEPOSITS,
            Deposit.class);
  }

  // TODO: switch the MerkleTree to use UnsignedLongs instead of using toIntExact() here,
  //  it will result in an overflow at some point
  /**
   *
   * @param fromDepositIndex inclusive
   * @param toDepositIndex exclusive
   * @param eth1DepositCount number of deposits in the merkle tree according to Eth1Data in state
   * @return
   */
  private List<Deposit> getDepositsWithProof(
          UnsignedLong fromDepositIndex,
          UnsignedLong toDepositIndex,
          UnsignedLong eth1DepositCount) {
    return depositNavigableMap.subMap(fromDepositIndex, toDepositIndex)
            .values()
            .stream()
            .peek(deposit -> deposit.setProof(
                    depositMerkleTree.getProofWithViewBoundary(
                            toIntExact(deposit.getIndex().longValue()),
                            toIntExact(eth1DepositCount.longValue()))))
            .map(Deposit.class::cast)
            .collect(Collectors.toList());
  }
}
