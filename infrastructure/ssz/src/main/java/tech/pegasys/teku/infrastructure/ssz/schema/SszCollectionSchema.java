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

    // Create nodes for all elements
    TreeNode[] elementNodes = new TreeNode[elements.size()];
    for (int i = 0; i < elements.size(); i++) {
      elementNodes[i] = elements.get(i).getBackingNode();
    }

    // Build the binary tree bottom-up
    int level = 0;
    while (elementNodes.length > (1 << level)) {
      int nodesAtLevel = (elementNodes.length + (1 << level) - 1) >> level;
      TreeNode[] newNodes = new TreeNode[nodesAtLevel];

      for (int i = 0; i < nodesAtLevel; i += 2) {
        if (i + 1 < nodesAtLevel) {
          // Combine pair of nodes
          newNodes[i / 2] = BranchNode.create(elementNodes[i], elementNodes[i + 1]);
        } else {
          // Handle odd number of nodes
          newNodes[i / 2] = elementNodes[i];
        }
      }
      elementNodes = newNodes;
      level++;
    }

    return elementNodes[0];
  }

  default Collector<SszElementT, ?, SszCollectionT> collector() {
    return Collectors.collectingAndThen(Collectors.<SszElementT>toList(), this::createFromElements);
  }
}
