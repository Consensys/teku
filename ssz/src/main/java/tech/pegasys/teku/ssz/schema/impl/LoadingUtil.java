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

package tech.pegasys.teku.ssz.schema.impl;

import static com.google.common.base.Preconditions.checkState;

import java.util.ArrayList;
import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ssz.tree.GIndexUtil;
import tech.pegasys.teku.ssz.tree.TreeNode;
import tech.pegasys.teku.ssz.tree.TreeNodeSource;
import tech.pegasys.teku.ssz.tree.TreeNodeSource.CompressedBranchInfo;
import tech.pegasys.teku.ssz.tree.TreeUtil;

public class LoadingUtil {

  public static TreeNode loadNodesToDepth(
      final TreeNodeSource nodeSource,
      final Bytes32 rootHash,
      final long rootGIndex,
      final int depthToLoad,
      final TreeNode defaultTree,
      final ChildLoader childLoader) {
    if (depthToLoad == 0) {
      // Only one child so wrapper is inlined
      return childLoader.loadChild(nodeSource, rootHash, rootGIndex);
    }
    if (TreeUtil.ZERO_TREES_BY_ROOT.containsKey(rootHash)) {
      return defaultTree;
    }
    final CompressedBranchInfo rootBranchInfo = nodeSource.loadBranchNode(rootHash, rootGIndex);
    final int branchDepth = rootBranchInfo.getDepth();
    checkState(
        branchDepth <= depthToLoad,
        "Stored branch node %s crosses schema boundary. Stored depth %s, max allowed depth %s",
        rootHash,
        branchDepth,
        depthToLoad);
    final Bytes32[] childHashes = rootBranchInfo.getChildren();
    final List<TreeNode> children = new ArrayList<>(childHashes.length);
    for (int childIndex = 0; childIndex < childHashes.length; childIndex++) {
      final long childGIndex =
          GIndexUtil.gIdxChildGIndex(GIndexUtil.SELF_G_INDEX, childIndex, branchDepth);
      if (branchDepth == depthToLoad) {
        children.add(
            childLoader.loadChild(
                nodeSource,
                childHashes[childIndex],
                GIndexUtil.gIdxCompose(rootGIndex, childGIndex)));
      } else {
        children.add(
            loadNodesToDepth(
                nodeSource,
                childHashes[childIndex],
                GIndexUtil.gIdxCompose(rootGIndex, childGIndex),
                depthToLoad - branchDepth,
                defaultTree.get(childGIndex),
                childLoader));
      }
    }
    return TreeUtil.createTree(children);
  }

  public interface ChildLoader {
    TreeNode loadChild(TreeNodeSource nodeSource, Bytes32 childHash, long childGIndex);
  }
}
