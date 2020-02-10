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

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.datastructures.util.OptimizedMerkleTree;
import tech.pegasys.artemis.datastructures.util.SimpleMerkleTree;

public class OptimizedMerkleTreeTests {

  private int seed = 0;

  private final int treeDepth = 32;
  private OptimizedMerkleTree optimizedMT;
  private SimpleMerkleTree simpleMT;

  @BeforeEach
  void setUp() {
    optimizedMT = new OptimizedMerkleTree(treeDepth);
    simpleMT = new SimpleMerkleTree(treeDepth);
  }

  @Test
  void test() {
    Bytes32 leaf = DataStructureUtil.randomBytes32(seed);
    optimizedMT.add(leaf);
    simpleMT.add(leaf);
    assertThat(optimizedMT.getProofTreeByValue(leaf)).isEqualTo(simpleMT.getProofTreeByValue(leaf));
  }

  @Test
  void makeSureAllProofsAndRootsMatch() {

    List<Bytes32> leaves =
        IntStream.range(0, 1000)
            .mapToObj(
                i -> {
                  Bytes32 leaf = DataStructureUtil.randomBytes32(seed++);
                  optimizedMT.add(leaf);
                  simpleMT.add(leaf);

                  assertThat(optimizedMT.getRoot()).isEqualTo(simpleMT.getRoot());
                  return leaf;
                })
            .collect(Collectors.toList());

    leaves.forEach(
        (leaf) ->
            assertThat(optimizedMT.getProofTreeByValue(leaf))
                .isEqualTo(simpleMT.getProofTreeByValue(leaf)));
  }

  @Test
  void benchmarkAddingLeafAndGettingProof() {
    List<Bytes32> leaves =
        IntStream.range(0, 1000)
            .mapToObj(i -> DataStructureUtil.randomBytes32(seed++))
            .collect(Collectors.toList());

    long startOfSimpleRun = System.currentTimeMillis();
    leaves.forEach(
        leaf -> {
          simpleMT.add(leaf);
          simpleMT.getProofTreeByValue(leaf);
        });
    long lengthOfSimpleRun = System.currentTimeMillis() - startOfSimpleRun;

    long startOfOptimizedRun = System.currentTimeMillis();
    leaves.forEach(
        leaf -> {
          optimizedMT.add(leaf);
          optimizedMT.getProofTreeByValue(leaf);
        });
    long lengthOfOptimizedRun = System.currentTimeMillis() - startOfOptimizedRun;

    System.out.println(
        "Length of optimized run: "
            + lengthOfOptimizedRun
            + "\nLength of simple run: "
            + lengthOfSimpleRun);
  }

  @Test
  void benchmarkOnSingleAddition() {
    List<Bytes32> leaves =
        IntStream.range(0, 100000)
            .mapToObj(i -> DataStructureUtil.randomBytes32(seed++))
            .collect(Collectors.toList());
    leaves.forEach(
        leaf -> {
          simpleMT.add(leaf);
          optimizedMT.add(leaf);
        });

    Bytes32 lastLeaf = DataStructureUtil.randomBytes32(seed++);
    long startOfSimpleRun = System.currentTimeMillis();
    simpleMT.add(lastLeaf);
    simpleMT.getProofTreeByValue(lastLeaf);
    long lengthOfSimpleRun = System.currentTimeMillis() - startOfSimpleRun;

    long startOfOptimizedRun = System.currentTimeMillis();
    optimizedMT.add(lastLeaf);
    optimizedMT.getProofTreeByValue(lastLeaf);
    long lengthOfOptimizedRun = System.currentTimeMillis() - startOfOptimizedRun;

    System.out.println(
        "Length of optimized run: "
            + lengthOfOptimizedRun
            + "\nLength of simple run: "
            + lengthOfSimpleRun);
  }
}
