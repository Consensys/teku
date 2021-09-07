/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.ssz.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import org.assertj.core.api.AbstractAssert;
import tech.pegasys.teku.ssz.tree.BranchNode;
import tech.pegasys.teku.ssz.tree.GIndexUtil;
import tech.pegasys.teku.ssz.tree.LeafDataNode;
import tech.pegasys.teku.ssz.tree.TreeNode;

public class TreeNodeAssert extends AbstractAssert<TreeNodeAssert, TreeNode> {

  private TreeNodeAssert(final TreeNode treeNode) {
    super(treeNode, TreeNodeAssert.class);
  }

  public static TreeNodeAssert assertThatTreeNode(final TreeNode actual) {
    return new TreeNodeAssert(actual);
  }

  public TreeNodeAssert isTreeEqual(final TreeNode expected) {
    assertTreeEqual(actual, expected, GIndexUtil.SELF_G_INDEX);
    return this;
  }

  private void assertTreeEqual(final TreeNode actual, final TreeNode expected, final long gIndex) {
    assertThat(actual.hashTreeRoot())
        .describedAs("node root at gIndex %s", gIndex)
        .isEqualTo(expected.hashTreeRoot());
    if (actual instanceof BranchNode) {
      final BranchNode actualBranch = (BranchNode) actual;
      final BranchNode expectedBranch = (BranchNode) expected;
      assertTreeEqual(
          actualBranch.left(), expectedBranch.left(), GIndexUtil.gIdxLeftGIndex(gIndex));
      assertTreeEqual(
          actualBranch.right(), expectedBranch.right(), GIndexUtil.gIdxRightGIndex(gIndex));
    } else if (actual instanceof LeafDataNode) {
      assertThat(((LeafDataNode) actual).getData())
          .describedAs("leaf data at gIndex %s", gIndex)
          .isEqualTo(((LeafDataNode) expected).getData());
    } else {
      fail("Unknown type of TreeNode (%s) at gIndex %s", actual.getClass(), gIndex);
    }
    assertThat(actual.getClass())
        .describedAs("node class at gIndex %s", gIndex)
        .isEqualTo(expected.getClass());
  }
}
