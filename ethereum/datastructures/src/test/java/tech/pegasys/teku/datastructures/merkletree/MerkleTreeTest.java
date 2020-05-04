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

package tech.pegasys.teku.datastructures.merkletree;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.is_valid_merkle_branch;

import java.util.ArrayList;
import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.datastructures.util.MerkleTree;
import tech.pegasys.teku.datastructures.util.OptimizedMerkleTree;

public class MerkleTreeTest {

  private MerkleTree merkleTree1;
  private MerkleTree merkleTree2;
  private final int treeDepth = 4;

  private final List<Bytes32> leaves =
      List.of(
          Bytes32.fromHexString("0x0001"),
          Bytes32.fromHexString("0x0002"),
          Bytes32.fromHexString("0x0003"),
          Bytes32.fromHexString("0x0004"),
          Bytes32.fromHexString("0x0005"),
          Bytes32.fromHexString("0x0006"),
          Bytes32.fromHexString("0x0007"),
          Bytes32.fromHexString("0x0008"),
          Bytes32.fromHexString("0x0009"),
          Bytes32.fromHexString("0x0010"),
          Bytes32.fromHexString("0x0011"),
          Bytes32.fromHexString("0x0012"),
          Bytes32.fromHexString("0x0013"),
          Bytes32.fromHexString("0x0014"),
          Bytes32.fromHexString("0x0015"),
          Bytes32.fromHexString("0x0016"));

  @Test
  void getProof() {
    merkleTree1 = new OptimizedMerkleTree(treeDepth);

    List<Boolean> results = new ArrayList<>();
    for (int index = 0; index < 7; index++) {
      Bytes32 leaf = leaves.get(index);
      merkleTree1.add(leaf);
      Bytes32 root = merkleTree1.getRoot();

      results.add(
          is_valid_merkle_branch(
              leaf,
              merkleTree1.getProof(leaf),
              treeDepth + 1, // Add 1 for the `List` length mix-in
              index,
              root));
    }
    assertThat(results).allSatisfy(Assertions::assertTrue);
  }

  @Test
  void proofsWithViewBoundaryOptimizedTree_getProofForIndexAlwaysSmallerThanLimit() {
    merkleTree1 = new OptimizedMerkleTree(treeDepth);
    merkleTree2 = new OptimizedMerkleTree(treeDepth);

    for (int i = 0; i < 8; i++) {
      merkleTree2.add(leaves.get(i));
    }

    List<Boolean> results = new ArrayList<>();
    for (int index = 0; index < 8; index++) {
      Bytes32 leaf = leaves.get(index);
      merkleTree1.add(leaf);
      Bytes32 root = merkleTree1.getRoot();

      results.add(
          is_valid_merkle_branch(
              leaf,
              merkleTree2.getProofWithViewBoundary(leaf, index + 1),
              treeDepth + 1, // Add 1 for the `List` length mix-in
              index,
              root));
    }
    assertThat(results).allSatisfy(Assertions::assertTrue);
  }

  @Test
  void proofsWithViewBoundary_getProofForEachIndexInTheSmallTree() {
    merkleTree1 = new OptimizedMerkleTree(treeDepth);
    merkleTree2 = new OptimizedMerkleTree(treeDepth);

    for (int i = 0; i < 16; i++) {
      merkleTree2.add(leaves.get(i));
    }

    for (int i = 0; i < 10; i++) {
      merkleTree1.add(leaves.get(i));
    }

    Bytes32 root = merkleTree1.getRoot();

    List<Boolean> results = new ArrayList<>();
    for (int index = 0; index < 10; index++) {
      results.add(
          is_valid_merkle_branch(
              leaves.get(index),
              merkleTree2.getProofWithViewBoundary(index, 10),
              treeDepth + 1, // Add 1 for the `List` length mix-in
              index,
              root));
    }
    assertThat(results).allSatisfy(Assertions::assertTrue);
  }
}
