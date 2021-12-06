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

package tech.pegasys.teku.infrastructure.ssz.impl;

import java.util.Optional;
import java.util.function.Supplier;
import tech.pegasys.teku.infrastructure.ssz.SszComposite;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.cache.ArrayIntCache;
import tech.pegasys.teku.infrastructure.ssz.cache.IntCache;
import tech.pegasys.teku.infrastructure.ssz.schema.SszCompositeSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

/**
 * Base backing class for immutable composite ssz structures (lists, vectors, containers)
 *
 * <p>It caches it's child ssz instances so that if the underlying tree nodes are not changed (in
 * the corresponding mutable classes) the instances are not recreated from tree nodes on later
 * access.
 *
 * <p>Though internally this class has a mutable cache it may be thought of as immutable instance
 * and used safely across threads
 *
 * @param <SszChildT> the type of children. For heterogeneous composites (like container) this type
 *     would be just generic {@link SszData}
 */
public abstract class AbstractSszComposite<SszChildT extends SszData>
    implements SszComposite<SszChildT> {

  private final IntCache<SszChildT> childrenViewCache;
  private int sizeCache = -1;
  private final SszCompositeSchema<?> schema;
  private final Supplier<TreeNode> backingNode;

  /** Creates an instance from a schema and a backing node */
  protected AbstractSszComposite(SszCompositeSchema<?> schema, Supplier<TreeNode> lazyBackingNode) {
    this(schema, lazyBackingNode, Optional.empty());
  }

  protected AbstractSszComposite(SszCompositeSchema<?> schema, TreeNode backingNode) {
    this(schema, () -> backingNode, Optional.empty());
  }

  /**
   * Creates an instance from a schema and a backing node.
   *
   * <p>{@link SszData} instances cache is supplied for optimization to shortcut children creation
   * from backing nodes. The cache should correspond to the supplied backing tree.
   */
  protected AbstractSszComposite(
      SszCompositeSchema<?> schema, TreeNode backingNode, IntCache<SszChildT> cache) {
    this(schema, () -> backingNode, Optional.of(cache));
  }

  protected AbstractSszComposite(
      SszCompositeSchema<?> schema,
      Supplier<TreeNode> lazyBackingNode,
      Optional<IntCache<SszChildT>> cache) {
    this.schema = schema;
    this.backingNode = lazyBackingNode;
    this.childrenViewCache = cache.orElseGet(this::createCache);
  }

  /**
   * 'Transfers' the cache to a new Cache instance eliminating all the cached values from the
   * current view cache. This is made under assumption that the ssz data instance this cache is
   * transferred to would be used further with high probability and this ssz data instance would be
   * either GCed or used with lower probability
   */
  IntCache<SszChildT> transferCache() {
    return childrenViewCache.transfer();
  }

  /**
   * Creates a new empty children cache. Could be overridden by subclasses for fine tuning of the
   * initial cache size
   */
  protected IntCache<SszChildT> createCache() {
    return new ArrayIntCache<>();
  }

  @Override
  public final SszChildT get(int index) {
    return childrenViewCache.getInt(index, this::getImplWithIndexCheck);
  }

  private SszChildT getImplWithIndexCheck(int index) {
    checkIndex(index);
    return getImpl(index);
  }

  /** Cache miss fallback child getter. This is where child is created from the backing tree node */
  protected abstract SszChildT getImpl(int index);

  @Override
  public SszCompositeSchema<?> getSchema() {
    return schema;
  }

  @Override
  public TreeNode getBackingNode() {
    return backingNode.get();
  }

  @Override
  public final int size() {
    if (sizeCache == -1) {
      sizeCache = sizeImpl();
    }
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

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof SszComposite)) {
      return false;
    }
    SszComposite<?> that = (SszComposite<?>) o;
    return getSchema().equals(that.getSchema()) && hashTreeRoot().equals(that.hashTreeRoot());
  }

  @Override
  public int hashCode() {
    return hashTreeRoot().slice(28).toInt();
  }
}
