/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.infrastructure.ssz.impl;

import java.util.stream.Collectors;
import java.util.stream.IntStream;
import tech.pegasys.teku.infrastructure.ssz.SszContainer;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.SszMutableContainer;
import tech.pegasys.teku.infrastructure.ssz.cache.ArrayIntCache;
import tech.pegasys.teku.infrastructure.ssz.cache.IntCache;
import tech.pegasys.teku.infrastructure.ssz.schema.SszProgressiveContainerSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

/**
 * Immutable implementation of SszContainer backed by a progressive merkle tree. The backing tree
 * has the structure: BranchNode(progressiveDataTree, activeFieldsLeafNode)
 */
public class SszProgressiveContainerImpl extends AbstractSszComposite<SszData>
    implements SszContainer {

  public SszProgressiveContainerImpl(
      final SszProgressiveContainerSchema<?> type, final TreeNode backingNode) {
    super(type, backingNode);
  }

  public SszProgressiveContainerImpl(
      final SszProgressiveContainerSchema<?> type,
      final TreeNode backingNode,
      final IntCache<SszData> cache) {
    super(type, backingNode, cache);
  }

  @Override
  protected SszData getImpl(final int index) {
    SszProgressiveContainerSchema<?> schema = getSchema();
    TreeNode node = getBackingNode().get(schema.getChildGeneralizedIndex(index));
    return schema.getChildSchema(index).createFromBackingNode(node);
  }

  @Override
  public SszProgressiveContainerSchema<?> getSchema() {
    return (SszProgressiveContainerSchema<?>) super.getSchema();
  }

  @Override
  public SszMutableContainer createWritableCopy() {
    return new SszMutableProgressiveContainerImpl(this);
  }

  @Override
  public boolean isWritableSupported() {
    return true;
  }

  @Override
  protected int sizeImpl() {
    return getSchema().getFieldsCount();
  }

  @Override
  protected IntCache<SszData> createCache() {
    return new ArrayIntCache<>(size());
  }

  @Override
  protected void checkIndex(final int index) {
    if (index >= size()) {
      throw new IndexOutOfBoundsException(
          "Invalid index " + index + " for progressive container with size " + size());
    }
  }

  @Override
  public String toString() {
    return getSchema().getContainerName()
        + "{"
        + IntStream.range(0, getSchema().getFieldsCount())
            .mapToObj(idx -> getSchema().getFieldNames().get(idx) + "=" + get(idx))
            .collect(Collectors.joining(", "))
        + "}";
  }
}
