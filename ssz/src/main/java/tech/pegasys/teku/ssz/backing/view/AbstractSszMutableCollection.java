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
    int[] internalIdxs = new int[elementsPerChunk];
    for (int i = 0; i < elementsPerChunk; i++) {
      internalIdxs[i] = i;
    }

    return newChildValues
        .collect(Collectors.groupingBy(e -> e.getKey() / elementsPerChunk))
        .entrySet()
        .stream()
        .map(
            e -> {
              int nodeIndex = e.getKey();
              List<Map.Entry<Integer, SszElementT>> nodeVals = e.getValue();
              long gIndex = type.getChildGeneralizedIndex(nodeIndex);
              if (nodeVals.size() == elementsPerChunk) {
                SszData[] newVals = new SszData[elementsPerChunk];
                for (int i = 0; i < elementsPerChunk; i++) {
                  newVals[i] = nodeVals.get(i).getValue();
                }
                TreeNode newNode = elementType
                    .updateBackingNode(LeafNode.EMPTY_LEAF, internalIdxs, newVals);
                return new TreeUpdates.Update(gIndex, newNode);
              } else {
                TreeNode node = original.get(gIndex);
                for (Map.Entry<Integer, SszElementT> entry : nodeVals) {
                  node =
                      elementType.updateBackingNode(
                          node, entry.getKey() % elementsPerChunk, entry.getValue());
                }
                return new TreeUpdates.Update(gIndex, node);
              }
            })
        .collect(TreeUpdates.collector());
  }
}
