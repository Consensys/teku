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

import tech.pegasys.teku.ssz.backing.ContainerViewRead;
import tech.pegasys.teku.ssz.backing.ContainerViewWrite;
import tech.pegasys.teku.ssz.backing.ViewRead;
import tech.pegasys.teku.ssz.backing.cache.ArrayIntCache;
import tech.pegasys.teku.ssz.backing.cache.IntCache;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.type.CompositeViewType;
import tech.pegasys.teku.ssz.backing.type.ContainerViewType;

public class ContainerViewReadImpl extends AbstractCompositeViewRead<ViewRead>
    implements ContainerViewRead {

  public ContainerViewReadImpl(ContainerViewType<?> type) {
    this(type, type.getDefaultTree());
  }

  public ContainerViewReadImpl(ContainerViewType<?> type, TreeNode backingNode) {
    super(type, backingNode);
  }

  public ContainerViewReadImpl(
      CompositeViewType<?> type, TreeNode backingNode, IntCache<ViewRead> cache) {
    super(type, backingNode, cache);
  }

  @Override
  protected ViewRead getImpl(int index) {
    CompositeViewType<?> type = getType();
    TreeNode node = getBackingNode().get(type.getGeneralizedIndex(index));
    return type.getChildType(index).createFromBackingNode(node);
  }

  @Override
  public ContainerViewWrite createWritableCopy() {
    return new ContainerViewWriteImpl(this);
  }

  @Override
  protected int sizeImpl() {
    return (int) getType().getMaxLength();
  }

  @Override
  protected IntCache<ViewRead> createCache() {
    return new ArrayIntCache<>(size());
  }

  @Override
  protected void checkIndex(int index) {
    if (index >= size()) {
      throw new IndexOutOfBoundsException(
          "Invalid index " + index + " for container with size " + size());
    }
  }
}
