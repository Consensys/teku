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

import tech.pegasys.teku.ssz.backing.CompositeViewRead;
import tech.pegasys.teku.ssz.backing.ViewRead;
import tech.pegasys.teku.ssz.backing.cache.ArrayIntCache;
import tech.pegasys.teku.ssz.backing.cache.IntCache;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.type.CompositeViewType;

/**
 * Base backing view class for immutable composite views (lists, vectors, containers)
 *
 * <p>It caches it's child view instances so that if the underlying tree nodes are not changed (in
 * the corresponding mutable classes) the instances are not recreated from tree nodes on later
 * access.
 *
 * <p>Though internally this class has a mutable cache it may be thought of as immutable instance
 * and used safely across threads
 *
 * @param <ChildType> the type of children. For heterogeneous composites (like container) this type
 *     would be just generic {@link ViewRead}
 */
public abstract class AbstractCompositeViewRead<ChildType extends ViewRead>
    implements CompositeViewRead<ChildType> {

  private IntCache<ChildType> childrenViewCache;
  private final int sizeCache;
  private final CompositeViewType<?> type;
  private final TreeNode backingNode;

  /** Creates an instance from a type and a backing node */
  protected AbstractCompositeViewRead(CompositeViewType<?> type, TreeNode backingNode) {
    this.type = type;
    this.backingNode = backingNode;
    this.sizeCache = sizeImpl();
    this.childrenViewCache = createCache();
  }

  /**
   * Creates an instance from a type and a backing node.
   *
   * <p>View instances cache is supplied for optimization to shortcut children views creation from
   * backing nodes. The cache should correspond to the supplied backing tree.
   */
  protected AbstractCompositeViewRead(
      CompositeViewType<?> type, TreeNode backingNode, IntCache<ChildType> cache) {
    this.type = type;
    this.backingNode = backingNode;
    this.sizeCache = sizeImpl();
    this.childrenViewCache = cache;
  }

  /**
   * 'Transfers' the cache to a new Cache instance eliminating all the cached values from the
   * current view cache. This is made under assumption that the view instance this cache is
   * transferred to would be used further with high probability and this view instance would be
   * either GCed or used with lower probability
   */
  IntCache<ChildType> transferCache() {
    return childrenViewCache.transfer();
  }

  /**
   * Creates a new empty children views cache. Could be overridden by subclasses for fine tuning of
   * the initial cache size
   */
  protected IntCache<ChildType> createCache() {
    return new ArrayIntCache<>();
  }

  @Override
  public final ChildType get(int index) {
    checkIndex(index);
    return childrenViewCache.getInt(index, this::getImpl);
  }

  /** Cache miss fallback child getter. This is where child is created from the backing tree node */
  protected abstract ChildType getImpl(int index);

  @Override
  public CompositeViewType<?> getType() {
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

  /** Size value is normally cached. This method calculates the size from backing tree */
  protected abstract int sizeImpl();

  /**
   * Checks the child index
   *
   * @throws IndexOutOfBoundsException if index is invalid
   */
  protected abstract void checkIndex(int index);
}
