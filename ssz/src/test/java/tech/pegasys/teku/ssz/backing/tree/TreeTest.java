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

package tech.pegasys.teku.ssz.backing.tree;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.ssz.TestUtil;
import tech.pegasys.teku.ssz.backing.tree.TreeNode.BranchNode;
import tech.pegasys.teku.ssz.backing.tree.TreeNode.LeafNode;
import tech.pegasys.teku.ssz.backing.tree.TreeUpdates.Update;

public class TreeTest {

  public static LeafNode newTestLeaf(long l) {
    return TreeNode.createLeafNode(Bytes32.leftPad(Bytes.ofUnsignedLong(l, ByteOrder.BIG_ENDIAN)));
  }

  @Test
  public void testCreateTreeFromLeafNodes() {
    BranchNode n1 =
        (BranchNode)
            TreeUtil.createTree(
                IntStream.range(0, 5).mapToObj(TreeTest::newTestLeaf).collect(Collectors.toList()));

    BranchNode n10 = (BranchNode) n1.left();
    BranchNode n11 = (BranchNode) n1.right();
    BranchNode n100 = (BranchNode) n10.left();
    BranchNode n101 = (BranchNode) n10.right();
    BranchNode n110 = (BranchNode) n11.left();
    BranchNode n111 = (BranchNode) n11.right();

    assertThat(n100.left()).isEqualTo(newTestLeaf(0));
    assertThat(n100.right()).isEqualTo(newTestLeaf(1));
    assertThat(n101.left()).isEqualTo(newTestLeaf(2));
    assertThat(n101.right()).isEqualTo(newTestLeaf(3));
    assertThat(n110.left()).isEqualTo(newTestLeaf(4));
    assertThat(n110.right()).isSameAs(TreeUtil.ZERO_LEAF);
    assertThat(n111.left()).isSameAs(TreeUtil.ZERO_LEAF);
    assertThat(n111.right()).isSameAs(TreeUtil.ZERO_LEAF);

    assertThat(n1.get(0b1)).isSameAs(n1);
    assertThat(n1.get(0b10)).isSameAs(n10);
    assertThat(n1.get(0b111)).isSameAs(n111);
    assertThat(n1.get(0b1000)).isSameAs(n100.left());
    assertThat(n1.get(0b1100)).isSameAs(n110.left());
    assertThat(n10.get(0b100)).isSameAs(n100.left());
    assertThat(n11.get(0b100)).isSameAs(n110.left());
  }

  @Test
  public void testZeroLeafDefaultTree() {
    TreeNode n1 = TreeUtil.createDefaultTree(5, TreeUtil.ZERO_LEAF);
    assertThat(n1.get(0b1000)).isSameAs(TreeUtil.ZERO_LEAF);
    assertThat(n1.get(0b1111)).isSameAs(TreeUtil.ZERO_LEAF);
    assertThat(n1.get(0b100)).isSameAs(n1.get(0b101));
    assertThat(n1.get(0b100)).isSameAs(n1.get(0b110));
    assertThat(n1.get(0b100)).isSameAs(n1.get(0b111));
    assertThat(n1.get(0b10)).isSameAs(n1.get(0b11));
  }

  @Test
  public void testNonZeroLeafDefaultTree() {
    TreeNode zeroTree = TreeUtil.createDefaultTree(5, TreeUtil.ZERO_LEAF);

    TreeNode defaultLeaf = newTestLeaf(111);
    BranchNode n1 = (BranchNode) TreeUtil.createDefaultTree(5, defaultLeaf);
    assertThat(n1.get(0b1000)).isSameAs(defaultLeaf);
    assertThat(n1.get(0b1001)).isSameAs(defaultLeaf);
    assertThat(n1.get(0b1100)).isSameAs(defaultLeaf);
    assertThat(n1.get(0b1101)).isSameAs(TreeUtil.ZERO_LEAF);
    assertThat(n1.get(0b1111)).isSameAs(TreeUtil.ZERO_LEAF);
    assertThat(n1.get(0b111)).isSameAs(zeroTree.get(0b111));
  }

  @Test
  public void testUpdated() {
    TreeNode zeroTree = TreeUtil.createDefaultTree(8, TreeUtil.ZERO_LEAF);
    TreeNode t1 = zeroTree.updated(8 + 0, newTestLeaf(111));
    TreeNode t1_ = zeroTree.updated(8 + 0, newTestLeaf(111));
    assertThat(t1).isNotSameAs(t1_);
    assertThat(t1.get(8 + 0)).isEqualTo(newTestLeaf(111));
    assertThat(IntStream.range(1, 8).mapToObj(idx -> t1.get(8 + idx)))
        .containsOnly(TreeUtil.ZERO_LEAF);
    assertThat(t1.hashTreeRoot()).isEqualTo(t1_.hashTreeRoot());

    TreeNode t2 = t1.updated(8 + 3, newTestLeaf(222));
    TreeNode t2_ =
        zeroTree.updated(
            new TreeUpdates(
                List.of(new Update(8 + 0, newTestLeaf(111)), new Update(8 + 3, newTestLeaf(222)))));
    assertThat(t2).isNotSameAs(t2_);
    assertThat(t2.get(8 + 0)).isEqualTo(newTestLeaf(111));
    assertThat(t2.get(8 + 3)).isEqualTo(newTestLeaf(222));
    assertThat(IntStream.of(1, 2, 4, 5, 6, 7).mapToObj(idx -> t2.get(8 + idx)))
        .containsOnly(TreeUtil.ZERO_LEAF);
    assertThat(t2.hashTreeRoot()).isEqualTo(t2_.hashTreeRoot());

    TreeNode zeroTree_ =
        t2.updated(
            new TreeUpdates(
                List.of(
                    new Update(8 + 0, TreeUtil.ZERO_LEAF), new Update(8 + 3, TreeUtil.ZERO_LEAF))));
    assertThat(zeroTree.hashTreeRoot()).isEqualTo(zeroTree_.hashTreeRoot());
  }

  @Test
  // The threading test is probabilistic and may have false positives
  // (i.e. pass on incorrect implementation)
  public void testHashThreadSafe() {
    // since the hash can be calculated lazily and cached inside TreeNode there are
    // potential threading issues
    TreeNode tree = TreeUtil.createDefaultTree(32 * 1024, newTestLeaf(111));
    List<Future<Bytes32>> hasheFuts = TestUtil.executeParallel(() -> tree.hashTreeRoot(), 512);
    assertThat(TestUtil.waitAll(hasheFuts)).containsOnly(tree.hashTreeRoot());
  }

  @Test
  void testLeavesIterator() {
    BranchNode n1 =
        (BranchNode)
            TreeUtil.createTree(
                IntStream.range(0, 8).mapToObj(TreeTest::newTestLeaf).collect(Collectors.toList()));
    assertThat(collectLeaves(n1, 0b1000, 0b1000)).containsExactly(newTestLeaf(0));
    assertThat(collectLeaves(n1, 0b1000, 0b1001)).containsExactly(
        newTestLeaf(0), newTestLeaf(1));
    assertThat(collectLeaves(n1, 0b100, 0b100)).containsExactly(
        newTestLeaf(0), newTestLeaf(1));
    assertThat(collectLeaves(n1, 0b101, 0b1100)).containsExactly(
        newTestLeaf(2), newTestLeaf(3), newTestLeaf(4));
    assertThat(collectLeaves(n1, 0b101, 0b110)).containsExactly(
        newTestLeaf(2), newTestLeaf(3), newTestLeaf(4), newTestLeaf(5));
    assertThat(collectLeaves(n1, 0b100, 0b110))
        .containsExactly(
            newTestLeaf(0),
            newTestLeaf(1),
            newTestLeaf(2),
            newTestLeaf(3),
            newTestLeaf(4),
            newTestLeaf(5));
  }

  static List<LeafNode> collectLeaves(TreeNode n, long from, long to) {
    List<LeafNode> ret = new ArrayList<>();
    TreeUtil.iterateLeaves(n , from, to, ret::add);
    return ret;
  }
}
