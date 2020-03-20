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

import static com.google.common.base.Preconditions.checkArgument;

import tech.pegasys.artemis.util.backing.ContainerViewWrite;
import tech.pegasys.artemis.util.backing.ViewRead;
import tech.pegasys.artemis.util.backing.tree.TreeNode;
import tech.pegasys.artemis.util.backing.tree.TreeNodes;
import tech.pegasys.artemis.util.backing.type.ContainerViewType;
import tech.pegasys.artemis.util.cache.ArrayCache;
import tech.pegasys.artemis.util.cache.IntCache;

public abstract class AbstractImmutableContainer extends ContainerViewReadImpl {

  public AbstractImmutableContainer(
      ContainerViewType<? extends AbstractImmutableContainer> type) {
    this(type, type.getDefaultTree());
  }

  public AbstractImmutableContainer(
      ContainerViewType<? extends AbstractImmutableContainer> type, TreeNode backingNode) {
    super(type, backingNode);
  }

  public AbstractImmutableContainer(
      ContainerViewType<? extends AbstractImmutableContainer> type, ViewRead... memberValues) {
    super(type, createBackingTree(type, memberValues), createCache(memberValues));
    checkArgument(
        memberValues.length == getType().getMaxLength(),
        "Wrong number of member values: %s",
        memberValues.length);
    for (int i = 0; i < memberValues.length; i++) {
      checkArgument(
          memberValues[i].getType().equals(type.getChildType(i)),
          "Wrong child type at index %s. Expected: %s, was %s",
          i,
          type.getChildType(i),
          memberValues[i].getType());
    }
  }

  private static IntCache<ViewRead> createCache(ViewRead... memberValues) {
    ArrayCache<ViewRead> cache = new ArrayCache<>(memberValues.length);
    for (int i = 0; i < memberValues.length; i++) {
      cache.invalidateWithNewValue(i, memberValues[i]);
    }
    return cache;
  }

  private static TreeNode createBackingTree(ContainerViewType<?> type, ViewRead... memberValues) {
    TreeNodes nodes = new TreeNodes();
    for (int i = 0; i < memberValues.length; i++) {
      nodes.add(type.getGeneralizedIndex(i), memberValues[i].getBackingNode());
    }
    return type.getDefaultTree().updated(nodes);
  }

  @Override
  public ContainerViewWrite createWritableCopy() {
    throw new UnsupportedOperationException("This container doesn't support mutable View");
  }
}
