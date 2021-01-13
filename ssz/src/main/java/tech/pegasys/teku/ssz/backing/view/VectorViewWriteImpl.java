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

package tech.pegasys.teku.ssz.backing.view;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import tech.pegasys.teku.ssz.backing.VectorViewWrite;
import tech.pegasys.teku.ssz.backing.VectorViewWriteRef;
import tech.pegasys.teku.ssz.backing.ViewRead;
import tech.pegasys.teku.ssz.backing.cache.IntCache;
import tech.pegasys.teku.ssz.backing.tree.LeafNode;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.tree.TreeUpdates;
import tech.pegasys.teku.ssz.backing.type.VectorViewType;
import tech.pegasys.teku.ssz.backing.type.ViewType;

public class VectorViewWriteImpl<
        ElementReadType extends ViewRead, ElementWriteType extends ElementReadType>
    extends AbstractCompositeViewWrite<ElementReadType, ElementWriteType>
    implements VectorViewWriteRef<ElementReadType, ElementWriteType> {

  public VectorViewWriteImpl(AbstractCompositeViewRead<ElementReadType> backingImmutableView) {
    super(backingImmutableView);
  }

  @Override
  protected AbstractCompositeViewRead<ElementReadType> createViewRead(
      TreeNode backingNode, IntCache<ElementReadType> viewCache) {
    return new VectorViewReadImpl<>(getType(), backingNode, viewCache);
  }

  @Override
  @SuppressWarnings("unchecked")
  public VectorViewType<ElementReadType> getType() {
    return (VectorViewType<ElementReadType>) super.getType();
  }

  @Override
  @SuppressWarnings("unchecked")
  public VectorViewReadImpl<ElementReadType> commitChanges() {
    return (VectorViewReadImpl<ElementReadType>) super.commitChanges();
  }

  @Override
  protected TreeUpdates packChanges(
      List<Map.Entry<Integer, ElementReadType>> newChildValues, TreeNode original) {
    VectorViewType<ElementReadType> type = getType();
    ViewType<?> elementType = type.getElementType();
    int elementsPerChunk = type.getElementsPerChunk();

    return newChildValues.stream()
        .collect(Collectors.groupingBy(e -> e.getKey() / elementsPerChunk))
        .entrySet()
        .stream()
        .sorted(Map.Entry.comparingByKey())
        .map(
            e -> {
              int nodeIndex = e.getKey();
              List<Map.Entry<Integer, ElementReadType>> nodeVals = e.getValue();
              long gIndex = type.getGeneralizedIndex(nodeIndex);
              // optimization: when all packed values changed no need to retrieve original node to
              // merge with
              TreeNode node =
                  nodeVals.size() == elementsPerChunk ? LeafNode.EMPTY_LEAF : original.get(gIndex);
              for (Map.Entry<Integer, ElementReadType> entry : nodeVals) {
                node =
                    elementType.updateBackingNode(
                        node, entry.getKey() % elementsPerChunk, entry.getValue());
              }
              return new TreeUpdates.Update(gIndex, node);
            })
        .collect(TreeUpdates.collector());
  }

  @Override
  protected void checkIndex(int index, boolean set) {
    if (index >= size()) {
      throw new IndexOutOfBoundsException(
          "Invalid index " + index + " for vector with size " + size());
    }
  }

  @Override
  public VectorViewWrite<ElementReadType> createWritableCopy() {
    throw new UnsupportedOperationException();
  }
}
