/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.ethereum.pow.merkletree;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static java.util.Collections.emptyList;
import static tech.pegasys.teku.spec.constants.NetworkConstants.DEPOSIT_CONTRACT_TREE_DEPTH;
import static tech.pegasys.teku.spec.logic.common.helpers.MathHelpers.uintToBytes32;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ethereum.pow.api.DepositTreeSnapshot;
import tech.pegasys.teku.infrastructure.crypto.Hash;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;

public class DepositTree {
  private MerkleTree tree;
  private long totalDepositCount;
  private long finalizedDepositCount;
  private Optional<Bytes32> finalizedExecutionBlock;

  public DepositTree() {
    tree = MerkleTree.create(emptyList(), DEPOSIT_CONTRACT_TREE_DEPTH);
    totalDepositCount = 0;
    finalizedDepositCount = 0;
    finalizedExecutionBlock = Optional.empty();
  }

  public DepositTree(final DepositTreeSnapshot snapshot) {
    this.tree =
        MerkleTree.fromSnapshotParts(
            snapshot.getFinalized(), snapshot.getDeposits(), DEPOSIT_CONTRACT_TREE_DEPTH);
    this.totalDepositCount = snapshot.getDeposits();
    this.finalizedDepositCount = snapshot.getDeposits();
    this.finalizedExecutionBlock = Optional.of(snapshot.getExecutionBlockHash());
  }

  public DepositTreeSnapshot getSnapshot() {
    checkState(
        finalizedExecutionBlock.isPresent(),
        "Cannot create snapshot before DepositTree has been finalized");
    final List<Bytes32> finalized = new ArrayList<>();
    final long deposits = tree.getFinalized(finalized);
    return new DepositTreeSnapshot(finalized, deposits, finalizedExecutionBlock.get());
  }

  public static DepositTree fromSnapshot(final DepositTreeSnapshot snapshot) {
    return new DepositTree(snapshot);
  }

  public void finalize(final Eth1Data eth1Data) {
    checkArgument(
        totalDepositCount >= eth1Data.getDepositCount().longValue(),
        "Merkle tree does not contain all deposits to be finalized");
    finalizedExecutionBlock = Optional.of(eth1Data.getBlockHash());
    finalizedDepositCount = eth1Data.getDepositCount().longValue();
    tree = tree.finalize(finalizedDepositCount, DEPOSIT_CONTRACT_TREE_DEPTH);
  }

  public List<Bytes32> getProof(final long index) {
    checkArgument(index >= finalizedDepositCount, "Cannot get proof for finalized deposits");
    final List<Bytes32> proof = tree.generateProof(index, DEPOSIT_CONTRACT_TREE_DEPTH);
    proof.add(uintToBytes32(totalDepositCount));
    return proof;
  }

  public Bytes32 getRoot() {
    return Hash.sha256(tree.getRoot(), uintToBytes32(totalDepositCount));
  }

  public void pushLeaf(final Bytes32 leaf) {
    totalDepositCount++;
    tree = tree.pushLeaf(leaf, DEPOSIT_CONTRACT_TREE_DEPTH);
  }

  public long getDepositCount() {
    return totalDepositCount;
  }

  public DepositTree getTreeAtDepositIndex(final long lastDepositIndex) {
    checkArgument(
        lastDepositIndex >= finalizedDepositCount,
        "Cannot recreate tree before finalized deposit count");
    final DepositTree treeForProof;
    if (finalizedExecutionBlock.isPresent()) {
      treeForProof = fromSnapshot(getSnapshot());
    } else {
      treeForProof = new DepositTree();
    }
    if (lastDepositIndex > 0) {
      tree.forEachLeafInclusive(
          treeForProof.getDepositCount(),
          lastDepositIndex,
          DEPOSIT_CONTRACT_TREE_DEPTH,
          treeForProof::pushLeaf);
    }
    checkState(
        treeForProof.getDepositCount() == lastDepositIndex,
        "Expected tree to have %s deposits but actually had %s",
        lastDepositIndex,
        treeForProof.getDepositCount());
    return treeForProof;
  }
}
