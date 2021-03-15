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

package tech.pegasys.teku.ssz.backing.view;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import tech.pegasys.teku.ssz.backing.SszData;
import tech.pegasys.teku.ssz.backing.schema.SszCollectionSchema;
import tech.pegasys.teku.ssz.backing.schema.SszSchema;
import tech.pegasys.teku.ssz.backing.tree.LeafNode;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.tree.TreeUpdates;

public abstract class AbstractSszMutableCollection<
        SszElementT extends SszData, SszMutableElementT extends SszElementT>
    extends AbstractSszMutableComposite<SszElementT, SszMutableElementT> {

  protected AbstractSszMutableCollection(AbstractSszComposite<SszElementT> backingImmutableData) {
    super(backingImmutableData);
  }

  @Override
  public SszCollectionSchema<?, ?> getSchema() {
    return (SszCollectionSchema<?, ?>) super.getSchema();
  }

  @Override
  protected TreeUpdates packChanges(
      Stream<Entry<Integer, SszElementT>> newChildValues, TreeNode original) {
    SszCollectionSchema<?, ?> type = getSchema();
    SszSchema<?> elementType = type.getElementSchema();
    int elementsPerChunk = type.getElementsPerChunk();

    List<Entry<Integer, SszElementT>> newChildren = newChildValues.collect(Collectors.toList());
    int[] internalIdxs = new int[elementsPerChunk];
    SszData[] newVals = new SszData[elementsPerChunk];
    int nodeUpdatesCount = 0;
    int prevChildNodeIndex = 0;
    List<Long> gIndexes = new ArrayList<>();
    List<TreeNode> newValues = new ArrayList<>();
    for (int i = 0; i < newChildren.size(); i++) {
      Entry<Integer, SszElementT> entry = newChildren.get(i);
      int childIndex = entry.getKey();
      int childNodeIndex = childIndex / elementsPerChunk;

      if (childNodeIndex != prevChildNodeIndex && nodeUpdatesCount > 0) {
        long gIndex = type.getChildGeneralizedIndex(prevChildNodeIndex);
        TreeNode originalNode =
            nodeUpdatesCount < elementsPerChunk ? original.get(gIndex) : LeafNode.EMPTY_LEAF;
        TreeNode newNode = elementType
            .updateBackingNode(originalNode, internalIdxs, newVals, nodeUpdatesCount);
        newValues.add(newNode);
        gIndexes.add(gIndex);
        nodeUpdatesCount = 0;
        prevChildNodeIndex = childNodeIndex;
      }

      internalIdxs[nodeUpdatesCount] = childIndex % elementsPerChunk;
      newVals[nodeUpdatesCount] = entry.getValue();
      nodeUpdatesCount++;
    }

    if (nodeUpdatesCount > 0) {
      long gIndex = type.getChildGeneralizedIndex(prevChildNodeIndex);
      TreeNode originalNode =
          nodeUpdatesCount < elementsPerChunk ? original.get(gIndex) : LeafNode.EMPTY_LEAF;
      TreeNode newNode = elementType
          .updateBackingNode(originalNode, internalIdxs, newVals, nodeUpdatesCount);
      newValues.add(newNode);
      gIndexes.add(gIndex);
    }

    return new TreeUpdates(gIndexes, newValues);
  }
}
