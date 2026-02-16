/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.infrastructure.ssz.tree;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;

class ProgressiveTreeUtilTest {

  @Test
  void levelCapacity_returnsCorrectValues() {
    assertThat(ProgressiveTreeUtil.levelCapacity(0)).isEqualTo(1);
    assertThat(ProgressiveTreeUtil.levelCapacity(1)).isEqualTo(4);
    assertThat(ProgressiveTreeUtil.levelCapacity(2)).isEqualTo(16);
    assertThat(ProgressiveTreeUtil.levelCapacity(3)).isEqualTo(64);
    assertThat(ProgressiveTreeUtil.levelCapacity(4)).isEqualTo(256);
  }

  @Test
  void cumulativeCapacity_returnsCorrectValues() {
    assertThat(ProgressiveTreeUtil.cumulativeCapacity(0)).isEqualTo(1);
    assertThat(ProgressiveTreeUtil.cumulativeCapacity(1)).isEqualTo(5);
    assertThat(ProgressiveTreeUtil.cumulativeCapacity(2)).isEqualTo(21);
    assertThat(ProgressiveTreeUtil.cumulativeCapacity(3)).isEqualTo(85);
    assertThat(ProgressiveTreeUtil.cumulativeCapacity(4)).isEqualTo(341);
    assertThat(ProgressiveTreeUtil.cumulativeCapacity(5)).isEqualTo(1365);
  }

  @Test
  void levelDepth_returnsCorrectValues() {
    assertThat(ProgressiveTreeUtil.levelDepth(0)).isEqualTo(0);
    assertThat(ProgressiveTreeUtil.levelDepth(1)).isEqualTo(2);
    assertThat(ProgressiveTreeUtil.levelDepth(2)).isEqualTo(4);
    assertThat(ProgressiveTreeUtil.levelDepth(3)).isEqualTo(6);
  }

  @Test
  void levelForIndex_returnsCorrectLevel() {
    // Level 0: indices [0, 0]
    assertThat(ProgressiveTreeUtil.levelForIndex(0)).isEqualTo(0);
    // Level 1: indices [1, 4]
    assertThat(ProgressiveTreeUtil.levelForIndex(1)).isEqualTo(1);
    assertThat(ProgressiveTreeUtil.levelForIndex(4)).isEqualTo(1);
    // Level 2: indices [5, 20]
    assertThat(ProgressiveTreeUtil.levelForIndex(5)).isEqualTo(2);
    assertThat(ProgressiveTreeUtil.levelForIndex(20)).isEqualTo(2);
    // Level 3: indices [21, 84]
    assertThat(ProgressiveTreeUtil.levelForIndex(21)).isEqualTo(3);
    assertThat(ProgressiveTreeUtil.levelForIndex(84)).isEqualTo(3);
  }

  @Test
  void createProgressiveTree_emptyChunks_returnsEmptyLeaf() {
    TreeNode tree = ProgressiveTreeUtil.createProgressiveTree(List.of());
    assertThat(tree).isEqualTo(LeafNode.EMPTY_LEAF);
  }

  @Test
  void createProgressiveTree_singleChunk_createsCorrectTree() {
    LeafNode leaf = LeafNode.create(Bytes.fromHexString("0x01"));
    TreeNode tree = ProgressiveTreeUtil.createProgressiveTree(List.of(leaf));
    // Should be BranchNode(leaf, EMPTY_LEAF)
    assertThat(tree).isInstanceOf(BranchNode.class);
    BranchNode branch = (BranchNode) tree;
    assertThat(branch.left()).isEqualTo(leaf);
    assertThat(branch.right()).isEqualTo(LeafNode.EMPTY_LEAF);
  }

  @Test
  void createProgressiveTree_twoChunks_createsCorrectStructure() {
    LeafNode leaf0 = LeafNode.create(Bytes.fromHexString("0x01"));
    LeafNode leaf1 = LeafNode.create(Bytes.fromHexString("0x02"));
    TreeNode tree = ProgressiveTreeUtil.createProgressiveTree(List.of(leaf0, leaf1));
    // Level 0 (cap=1): leaf0
    // Level 1 (cap=4): [leaf1, zero, zero, zero] (depth=2 balanced tree)
    // Result: BranchNode(leaf0, BranchNode(level1_tree, EMPTY_LEAF))
    assertThat(tree).isInstanceOf(BranchNode.class);
    BranchNode root = (BranchNode) tree;
    assertThat(root.left()).isEqualTo(leaf0);
    assertThat(root.right()).isInstanceOf(BranchNode.class);

    BranchNode rightBranch = (BranchNode) root.right();
    // Left child of right branch is the level-1 balanced subtree with leaf1
    // Right child is EMPTY_LEAF (no more levels)
    assertThat(rightBranch.right()).isEqualTo(LeafNode.EMPTY_LEAF);
  }

  @Test
  void createProgressiveTree_fiveChunks_usesThreeLevels() {
    List<LeafNode> chunks = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      chunks.add(LeafNode.create(Bytes.of((byte) (i + 1))));
    }
    TreeNode tree = ProgressiveTreeUtil.createProgressiveTree(chunks);
    // Level 0 (cap=1): chunks[0]
    // Level 1 (cap=4): chunks[1..4]
    // Level 2 (cap=16): chunks[5..] -> empty -> not created, just EMPTY_LEAF as right
    // Structure: BranchNode(chunk0, BranchNode(level1_tree, EMPTY_LEAF))
    assertThat(tree).isInstanceOf(BranchNode.class);
  }

  @Test
  void getElementGeneralizedIndex_element0_isLeftChild() {
    long gIdx = ProgressiveTreeUtil.getElementGeneralizedIndex(0);
    // Element 0 is at level 0, which is the left child of root.
    // Level 0 has depth 0, so the element is the left child directly.
    assertThat(gIdx).isEqualTo(GIndexUtil.LEFT_CHILD_G_INDEX); // 0b10
  }

  @Test
  void getElementGeneralizedIndex_element1_isAtLevel1() {
    long gIdx = ProgressiveTreeUtil.getElementGeneralizedIndex(1);
    // Element 1 is at level 1, position 0 in a 4-element balanced subtree (depth=2)
    // Path: right, left, then position 0 at depth 2
    // right from root = 0b11
    // left = 0b110
    // position 0 at depth 2 = 0b11000
    assertThat(gIdx).isEqualTo(0b11000);
  }

  @Test
  void getElementGeneralizedIndex_canNavigateTree() {
    // Create a tree with known values and verify we can navigate to each element
    List<LeafNode> chunks = new ArrayList<>();
    for (int i = 0; i < 6; i++) {
      byte[] data = new byte[32];
      data[0] = (byte) (i + 1);
      chunks.add(LeafNode.create(Bytes32.wrap(data)));
    }
    TreeNode tree = ProgressiveTreeUtil.createProgressiveTree(chunks);

    for (int i = 0; i < 6; i++) {
      long gIdx = ProgressiveTreeUtil.getElementGeneralizedIndex(i);
      TreeNode node = tree.get(gIdx);
      assertThat(node).isInstanceOf(LeafNode.class);
      assertThat(((LeafNode) node).getData().get(0)).isEqualTo((byte) (i + 1));
    }
  }

  @Test
  void hashTreeRoot_emptyTree_isZero() {
    TreeNode tree = ProgressiveTreeUtil.createProgressiveTree(List.of());
    assertThat(tree.hashTreeRoot()).isEqualTo(Bytes32.ZERO);
  }
}
