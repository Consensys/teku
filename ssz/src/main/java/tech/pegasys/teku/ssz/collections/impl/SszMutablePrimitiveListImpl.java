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

package tech.pegasys.teku.ssz.collections.impl;

import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.SszPrimitive;
import tech.pegasys.teku.ssz.cache.IntCache;
import tech.pegasys.teku.ssz.collections.SszMutablePrimitiveList;
import tech.pegasys.teku.ssz.collections.SszPrimitiveList;
import tech.pegasys.teku.ssz.primitive.SszUInt64;
import tech.pegasys.teku.ssz.schema.collections.SszPrimitiveListSchema;
import tech.pegasys.teku.ssz.tree.BranchNode;
import tech.pegasys.teku.ssz.tree.GIndexUtil;
import tech.pegasys.teku.ssz.tree.TreeNode;

public class SszMutablePrimitiveListImpl<
        ElementT, SszElementT extends SszPrimitive<ElementT, SszElementT>>
    extends AbstractSszMutablePrimitiveCollection<ElementT, SszElementT>
    implements SszMutablePrimitiveList<ElementT, SszElementT> {

  private int cachedSize;
  private final long cachedMaxLength;

  @SuppressWarnings("unchecked")
  public SszMutablePrimitiveListImpl(
      SszPrimitiveListImpl<ElementT, SszElementT> backingImmutableList) {
    super(backingImmutableList);
    cachedSize = backingImmutableList.size();
    cachedMaxLength = getSchema().getMaxLength();
  }

  @Override
  protected void checkIndex(int index, boolean set) {
    if (index < 0
        || (!set && index >= size())
        || (set && (index > size() || index >= cachedMaxLength))) {
      throw new IndexOutOfBoundsException(
          "Invalid index " + index + " for list with size " + size());
    }
  }

  @Override
  protected TreeNode doFinalTreeUpdates(TreeNode updatedTree) {
    return updateSize(updatedTree);
  }

  @Override
  public int size() {
    return cachedSize;
  }

  private TreeNode updateSize(TreeNode root) {
    return BranchNode.create(root.get(GIndexUtil.LEFT_CHILD_G_INDEX), createSizeNode());
  }

  private TreeNode createSizeNode() {
    return SszUInt64.of(UInt64.fromLongBits(size())).getBackingNode();
  }

  @Override
  public void set(int index, SszElementT value) {
    super.set(index, value);
    if (index == size()) {
      cachedSize++;
    }
  }

  @Override
  public void clear() {
    super.clear();
    cachedSize = 0;
  }

  @Override
  @SuppressWarnings("unchecked")
  public SszPrimitiveListSchema<ElementT, SszElementT, ?> getSchema() {
    return (SszPrimitiveListSchema<ElementT, SszElementT, ?>) super.getSchema();
  }

  @Override
  @SuppressWarnings("unchecked")
  public SszPrimitiveList<ElementT, SszElementT> commitChanges() {
    return (SszPrimitiveListImpl<ElementT, SszElementT>) super.commitChanges();
  }

  @Override
  protected SszPrimitiveListImpl<ElementT, SszElementT> createImmutableSszComposite(
      TreeNode backingNode, IntCache<SszElementT> childrenCache) {
    return new SszPrimitiveListImpl<>(getSchema(), backingNode, childrenCache);
  }

  @Override
  public SszMutablePrimitiveList<ElementT, SszElementT> createWritableCopy() {
    throw new UnsupportedOperationException(
        "Creating a writable copy from writable instance is not supported");
  }
}
