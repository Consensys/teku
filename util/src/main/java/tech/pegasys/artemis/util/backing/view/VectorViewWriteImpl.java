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

package tech.pegasys.artemis.util.backing.view;

import java.util.List;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import tech.pegasys.artemis.util.backing.VectorViewWrite;
import tech.pegasys.artemis.util.backing.VectorViewWriteRef;
import tech.pegasys.artemis.util.backing.ViewRead;
import tech.pegasys.artemis.util.backing.tree.TreeNode;
import tech.pegasys.artemis.util.backing.tree.TreeNodes;
import tech.pegasys.artemis.util.backing.tree.TreeUtil;
import tech.pegasys.artemis.util.backing.type.VectorViewType;
import tech.pegasys.artemis.util.backing.type.ViewType;
import tech.pegasys.artemis.util.cache.IntCache;

public class VectorViewWriteImpl<R extends ViewRead, W extends R>
    extends AbstractCompositeViewWrite<VectorViewWriteImpl<R, W>, R, W>
    implements VectorViewWriteRef<R, W> {

  public VectorViewWriteImpl(AbstractCompositeViewRead<?, R> backingImmutableView) {
    super(backingImmutableView);
  }

  @Override
  protected AbstractCompositeViewRead<?, R> createViewRead(
      TreeNode backingNode, IntCache<R> viewCache) {
    return new VectorViewReadImpl<>(getType(), backingNode, viewCache);
  }

  @Override
  @SuppressWarnings("unchecked")
  public VectorViewType<R> getType() {
    return (VectorViewType<R>) super.getType();
  }

  @Override
  @SuppressWarnings("unchecked")
  public VectorViewReadImpl<R> commitChanges() {
    return (VectorViewReadImpl<R>) super.commitChanges();
  }

  @Override
  protected TreeNodes packChanges(List<Entry<Integer, R>> newChildValues, TreeNode original) {
    VectorViewType<R> type = getType();
    ViewType elementType = type.getElementType();
    int elementsPerChunk = type.getElementsPerChunk();
    TreeNodes ret = new TreeNodes();

    newChildValues.stream()
        .collect(Collectors.groupingBy(e -> e.getKey() / elementsPerChunk))
        .forEach(
            (nodeIndex, nodeVals) -> {
              long gIndex = type.getGeneralizedIndex(nodeIndex);
              // optimization: when all packed values changed no need to retrieve original node to
              // merge with
              TreeNode node =
                  nodeVals.size() == elementsPerChunk ? TreeUtil.ZERO_LEAF : original.get(gIndex);
              for (Entry<Integer, R> entry : nodeVals) {
                node =
                    elementType.updateBackingNode(
                        node, entry.getKey() % elementsPerChunk, entry.getValue());
              }
              ret.add(gIndex, node);
            });
    return ret;
  }

  @Override
  protected void checkIndex(int index, boolean set) {
    if (index >= size()) {
      throw new IndexOutOfBoundsException(
          "Invalid index " + index + " for vector with size " + size());
    }
  }

  @Override
  public VectorViewWrite<R> createWritableCopy() {
    throw new UnsupportedOperationException();
  }
}
