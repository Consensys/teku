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

import tech.pegasys.artemis.util.backing.CompositeViewRead;
import tech.pegasys.artemis.util.backing.ViewRead;
import tech.pegasys.artemis.util.backing.tree.TreeNode;
import tech.pegasys.artemis.util.backing.type.CompositeViewType;
import tech.pegasys.artemis.util.cache.Cache;
import tech.pegasys.artemis.util.cache.HashMapCache;

public abstract class AbstractCompositeViewRead<
        C extends AbstractCompositeViewRead<C, R>, R extends ViewRead>
    implements CompositeViewRead<R> {

  private Cache<Integer, R> childrenViewCache;
  private final int sizeCache;
  private final CompositeViewType type;
  private final TreeNode backingNode;

  public AbstractCompositeViewRead(CompositeViewType type,
      TreeNode backingNode) {
    this.type = type;
    this.backingNode = backingNode;
    sizeCache = sizeImpl();
    childrenViewCache = createCache();
  }

  public AbstractCompositeViewRead(CompositeViewType type,
      TreeNode backingNode, Cache<Integer, R> cache) {
    this.type = type;
    this.backingNode = backingNode;
    sizeCache = sizeImpl();
    childrenViewCache = cache;
  }

  synchronized Cache<Integer, R> transferCache() {
    return childrenViewCache.transfer();
  }

  private Cache<Integer, R> createCache() {
    return new HashMapCache<>();
  }

  @Override
  public final R get(int index) {
    checkIndex(index);
    return childrenViewCache.get(index, this::getImpl);
  }

  protected abstract R getImpl(int index);

  @Override
  public CompositeViewType getType() {
    return type;
  }

  @Override
  public TreeNode getBackingNode() {
    return backingNode;
  }

  @Override
  public final int size() {
    return sizeCache;
  }

  protected abstract int sizeImpl();

  protected abstract void checkIndex(int index);
}
