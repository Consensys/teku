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

import tech.pegasys.teku.ssz.backing.SszData;
import tech.pegasys.teku.ssz.backing.SszMutableList;
import tech.pegasys.teku.ssz.backing.SszMutableRefList;
import tech.pegasys.teku.ssz.backing.cache.IntCache;
import tech.pegasys.teku.ssz.backing.schema.SszListSchema;
import tech.pegasys.teku.ssz.backing.tree.BranchNode;
import tech.pegasys.teku.ssz.backing.tree.GIndexUtil;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives.SszUInt64;

public class SszMutableListImpl<SszElementT extends SszData, SszMutableElementT extends SszElementT>
    extends AbstractSszMutableCollection<SszElementT, SszMutableElementT>
    implements SszMutableRefList<SszElementT, SszMutableElementT> {

  private int cachedSize;

  public SszMutableListImpl(SszListImpl<SszElementT> backingImmutableList) {
    super(backingImmutableList);
    cachedSize = backingImmutableList.size();
  }

  @Override
  protected SszListImpl<SszElementT> createImmutableSszComposite(
      TreeNode backingNode, IntCache<SszElementT> childrenCache) {
    TreeNode nodeWithUpdatedSize = updateSize(backingNode);
    return new SszListImpl<>(getSchema(), nodeWithUpdatedSize, childrenCache);
  }

  @Override
  public int size() {
    return cachedSize;
  }

  private TreeNode updateSize(TreeNode root) {
    return BranchNode.create(root.get(GIndexUtil.LEFT_CHILD_G_INDEX), createSizeNode());
  }

  private TreeNode createSizeNode() {
    return SszUInt64.fromLong(size()).getBackingNode();
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
  protected void checkIndex(int index, boolean set) {
    if ((!set && index >= size())
        || (set && (index > size() || index >= getSchema().getMaxLength()))) {
      throw new IndexOutOfBoundsException(
          "Invalid index " + index + " for list with size " + size());
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  public SszListSchema<SszElementT, ?> getSchema() {
    return (SszListSchema<SszElementT, ?>) super.getSchema();
  }

  @Override
  @SuppressWarnings("unchecked")
  public SszListImpl<SszElementT> commitChanges() {
    return (SszListImpl<SszElementT>) super.commitChanges();
  }

  @Override
  public SszMutableList<SszElementT> createWritableCopy() {
    throw new UnsupportedOperationException("Creating a copy from writable list is not supported");
  }
}
