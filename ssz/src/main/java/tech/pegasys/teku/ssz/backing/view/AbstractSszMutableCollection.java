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
import java.util.stream.Collectors;
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
      List<Map.Entry<Integer, SszElementT>> newChildValues, TreeNode original) {
    SszCollectionSchema<?, ?> type = getSchema();
    SszSchema<?> elementType = type.getElementSchema();
    int elementsPerChunk = type.getElementsPerChunk();

    return newChildValues.stream()
        .collect(Collectors.groupingBy(e -> e.getKey() / elementsPerChunk))
        .entrySet()
        .stream()
        .sorted(Map.Entry.comparingByKey())
        .map(
            e -> {
              int nodeIndex = e.getKey();
              List<Map.Entry<Integer, SszElementT>> nodeVals = e.getValue();
              long gIndex = type.getChildGeneralizedIndex(nodeIndex);
              // optimization: when all packed values changed no need to retrieve original node to
              // merge with
              TreeNode node =
                  nodeVals.size() == elementsPerChunk ? LeafNode.EMPTY_LEAF : original.get(gIndex);
              for (Map.Entry<Integer, SszElementT> entry : nodeVals) {
                node =
                    elementType.updateBackingNode(
                        node, entry.getKey() % elementsPerChunk, entry.getValue());
              }
              return new TreeUpdates.Update(gIndex, node);
            })
        .collect(TreeUpdates.collector());
  }
}
