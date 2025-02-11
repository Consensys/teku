/*
 * Copyright Consensys Software Inc., 2022
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

package tech.pegasys.teku.infrastructure.ssz.schema;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.List;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import tech.pegasys.teku.infrastructure.ssz.SszCollection;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.tree.BranchNode;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNodeStore;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeUtil;

public interface SszCollectionSchema<
        SszElementT extends SszData, SszCollectionT extends SszCollection<SszElementT>>
    extends SszCompositeSchema<SszCollectionT> {

  SszSchema<SszElementT> getElementSchema();

  @Override
  default void storeChildNode(
      final TreeNodeStore nodeStore,
      final int maxBranchLevelsSkipped,
      final long gIndex,
      final TreeNode node) {
    getElementSchema().storeBackingNodes(nodeStore, maxBranchLevelsSkipped, gIndex, node);
  }

  @SuppressWarnings("unchecked")
  default SszCollectionT of(final SszElementT... elements) {
    return createFromElements(List.of(elements));
  }

  default SszCollectionT createFromElements(final List<? extends SszElementT> elements) {
    return createFromBackingNode(createTreeFromElements(elements));
  }

  default TreeNode createTreeFromElements(final List<? extends SszElementT> elements) {
    checkArgument(elements.size() <= getMaxLength(), "Too many elements for this collection type");

    if (elements == null || elements.isEmpty()) {
      return TreeUtil.ZERO_TREES[0];
    }

    try {
      // Create nodes for all elements
      TreeNode[] elementNodes = new TreeNode[elements.size()];
      for (int i = 0; i < elements.size(); i++) {
        SszElementT element = elements.get(i);
        if (element == null) {
          elementNodes[i] = TreeUtil.ZERO_TREES[0];
        } else {
          elementNodes[i] = element.getBackingNode();
        }
      }

      // Calculate number of levels needed
      int totalLevels = 32 - Integer.numberOfLeadingZeros(Math.max(1, elementNodes.length - 1));

      // Build the binary tree bottom-up
      for (int level = 0; level < totalLevels; level++) {
        int nodesAtLevel = (elementNodes.length + (1 << level) - 1) >> level;
        TreeNode[] newNodes = new TreeNode[(nodesAtLevel + 1) / 2];

        for (int i = 0; i < nodesAtLevel; i += 2) {
          if (i + 1 < nodesAtLevel) {
            // Combine pair of nodes
            TreeNode left = elementNodes[i];
            TreeNode right = elementNodes[i + 1];
            if (left == null) left = TreeUtil.ZERO_TREES[0];
            if (right == null) right = TreeUtil.ZERO_TREES[0];
            newNodes[i / 2] = BranchNode.create(left, right);
          } else {
            // Handle odd number of nodes - promote single node
            TreeNode node = elementNodes[i];
            if (node == null) node = TreeUtil.ZERO_TREES[0];
            newNodes[i / 2] = node;
          }
        }
        elementNodes = newNodes;
      }

      return elementNodes[0] != null ? elementNodes[0] : TreeUtil.ZERO_TREES[0];
    } catch (Exception e) {
      // Return zero tree in case of any error
      return TreeUtil.ZERO_TREES[0];
    }
  }

  default Collector<SszElementT, ?, SszCollectionT> collector() {
    return Collectors.collectingAndThen(Collectors.<SszElementT>toList(), this::createFromElements);
  }
}
