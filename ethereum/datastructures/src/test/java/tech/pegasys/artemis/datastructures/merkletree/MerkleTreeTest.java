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

package tech.pegasys.artemis.datastructures.merkletree;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.is_valid_merkle_branch;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.datastructures.util.MerkleTree;
import tech.pegasys.artemis.datastructures.util.OptimizedMerkleTree;
import tech.pegasys.artemis.datastructures.util.SimpleMerkleTree;

public class MerkleTreeTest {

  private MerkleTree merkleTree1;
  private MerkleTree merkleTree2;
  private final int treeDepth = 32;
  private int seed = 0;
  private int numDeposits = 1000;

  private final List<Bytes32> leaves =
      IntStream.range(0, numDeposits)
          .mapToObj(i -> DataStructureUtil.randomBytes32(++seed))
          .collect(Collectors.toList());

  @Test
  void ProofSimpleTree() {
    merkleTree1 = new SimpleMerkleTree(treeDepth);

    for (int index = 0; index < numDeposits; index++) {
      Bytes32 leaf = leaves.get(index);
      merkleTree1.add(leaf);
      Bytes32 root = merkleTree1.getRoot();

      assertThat(
              is_valid_merkle_branch(
                  leaf,
                  merkleTree1.getProof(leaf),
                  treeDepth + 1, // Add 1 for the `List` length mix-in
                  index,
                  root))
          .isTrue();
    }
  }

  @Test
  void ProofOptimizedTree() {
    merkleTree1 = new OptimizedMerkleTree(treeDepth);

    for (int index = 0; index < numDeposits; index++) {
      Bytes32 leaf = leaves.get(index);
      merkleTree1.add(leaf);
      Bytes32 root = merkleTree1.getRoot();

      assertThat(
              is_valid_merkle_branch(
                  leaf,
                  merkleTree1.getProof(leaf),
                  treeDepth + 1, // Add 1 for the `List` length mix-in
                  index,
                  root))
          .isTrue();
    }
  }

  @Test
  void ProofsWithViewBoundarySimpleTree() {
    merkleTree1 = new SimpleMerkleTree(treeDepth);
    merkleTree2 = new SimpleMerkleTree(treeDepth);

    for (int i = 0; i < numDeposits; i++) {
      merkleTree2.add(leaves.get(i));
    }

    for (int index = 0; index < numDeposits; index++) {
      Bytes32 leaf = leaves.get(index);
      merkleTree1.add(leaf);
      Bytes32 root = merkleTree1.getRoot();

      assertThat(
              is_valid_merkle_branch(
                  leaf,
                  merkleTree2.getProofWithViewBoundary(leaf, index + 1),
                  treeDepth + 1, // Add 1 for the `List` length mix-in
                  index,
                  root))
          .isTrue();
    }
  }

  @Test
  void ProofsWithViewBoundaryOptimizedTree() {
    merkleTree1 = new OptimizedMerkleTree(treeDepth);
    merkleTree2 = new OptimizedMerkleTree(treeDepth);

    for (int i = 0; i < numDeposits; i++) {
      merkleTree2.add(leaves.get(i));
    }

    for (int index = 0; index < numDeposits; index++) {
      Bytes32 leaf = leaves.get(index);
      merkleTree1.add(leaf);
      Bytes32 root = merkleTree1.getRoot();

      assertThat(
              is_valid_merkle_branch(
                  leaf,
                  merkleTree2.getProofWithViewBoundary(leaf, index + 1),
                  treeDepth + 1, // Add 1 for the `List` length mix-in
                  index,
                  root))
          .isTrue();
    }
  }

  @Test
  void ProofsWithViewBoundary_viewBoundaryAndStartIndex20ItemsApart_OptimizedTree() {
    merkleTree1 = new OptimizedMerkleTree(treeDepth);
    merkleTree2 = new OptimizedMerkleTree(treeDepth);

    for (int i = 0; i < numDeposits; i++) {
      merkleTree2.add(leaves.get(i));
    }

    for (int i = 0; i < 20; i++) {
      merkleTree1.add(leaves.get(i));
    }

    Bytes32 root = merkleTree1.getRoot();

    // fails when index = 0;
    // runs when index = 17;
    for (int index = 0; index < 20; index++) {
      System.out.println(index);
      assertThat(
              is_valid_merkle_branch(
                      leaves.get(index),
                      merkleTree2.getProofWithViewBoundary(index,  20),
                      treeDepth + 1, // Add 1 for the `List` length mix-in
                      index,
                      root))
              .isTrue();
    }
  }
}
