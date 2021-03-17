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

package tech.pegasys.teku.ssz.impl;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import tech.pegasys.teku.ssz.InvalidValueSchemaException;
import tech.pegasys.teku.ssz.SszComposite;
import tech.pegasys.teku.ssz.SszData;
import tech.pegasys.teku.ssz.SszMutableComposite;
import tech.pegasys.teku.ssz.SszMutableData;
import tech.pegasys.teku.ssz.SszMutableRefComposite;
import tech.pegasys.teku.ssz.cache.IntCache;
import tech.pegasys.teku.ssz.schema.SszCompositeSchema;
import tech.pegasys.teku.ssz.tree.TreeNode;
import tech.pegasys.teku.ssz.tree.TreeUpdates;

/**
 * Base backing {@link SszMutableData} class for mutable composite ssz structures (lists, vectors,
 * containers)
 *
 * <p>It has corresponding backing immutable {@link SszData} and the set of changed children. When
 * the {@link #commitChanges()} is called a new immutable {@link SszData} instance is created where
 * changes accumulated in this instance are merged with cached backing {@link SszData} instance
 * which weren't changed.
 *
 * <p>If this ssz data is get by reference from its parent composite view ({@link
 * SszMutableRefComposite#getByRef(int)} then all the changes are notified to the parent view (see
 * {@link SszMutableComposite#setInvalidator(Consumer)}
 *
 * <p>The mutable structures based on this class are inherently NOT thread safe
 */
public abstract class AbstractSszMutableComposite<
        SszChildT extends SszData, SszMutableChildT extends SszChildT>
    implements SszMutableRefComposite<SszChildT, SszMutableChildT> {

  protected AbstractSszComposite<SszChildT> backingImmutableData;
  private Consumer<SszMutableData> invalidator;
  private final Map<Integer, SszChildT> childrenChanges = new HashMap<>();
  private final Map<Integer, SszMutableChildT> childrenRefs = new HashMap<>();
  private final Set<Integer> childrenRefsChanged = new HashSet<>();
  private Integer sizeCache;

  /** Creates a new mutable instance with backing immutable data */
  protected AbstractSszMutableComposite(AbstractSszComposite<SszChildT> backingImmutableData) {
    this.backingImmutableData = backingImmutableData;
    sizeCache = backingImmutableData.size();
  }

  @Override
  public void set(int index, SszChildT value) {
    checkIndex(index, true);
    checkNotNull(value);
    if (!value.getSchema().equals(getSchema().getChildSchema(index))) {
      throw new InvalidValueSchemaException(
          "Expected child to have schema "
              + getSchema().getChildSchema(index)
              + ", but value has schema "
              + value.getSchema());
    }
    if (childrenRefs.containsKey(index)) {
      throw new IllegalStateException(
          "A child couldn't be simultaneously modified by value and accessed by ref");
    }
    childrenChanges.put(index, value);
    sizeCache = index >= sizeCache ? index + 1 : sizeCache;
    invalidate();
  }

  @Override
  public SszChildT get(int index) {
    checkIndex(index, false);
    SszChildT ret = childrenChanges.get(index);
    if (ret != null) {
      return ret;
    } else if (childrenRefs.containsKey(index)) {
      return childrenRefs.get(index);
    } else {
      return backingImmutableData.get(index);
    }
  }

  @Override
  public SszMutableChildT getByRef(int index) {
    SszMutableChildT ret = childrenRefs.get(index);
    if (ret == null) {
      SszChildT readView = get(index);
      childrenChanges.remove(index);
      @SuppressWarnings("unchecked")
      SszMutableChildT w = (SszMutableChildT) readView.createWritableCopy();
      if (w instanceof SszMutableComposite) {
        ((SszMutableComposite<?>) w)
            .setInvalidator(
                viewWrite -> {
                  childrenRefsChanged.add(index);
                  invalidate();
                });
      }
      childrenRefs.put(index, w);
      ret = w;
    }
    return ret;
  }

  @Override
  public SszCompositeSchema<?> getSchema() {
    return backingImmutableData.getSchema();
  }

  @Override
  @SuppressWarnings("unchecked")
  public void clear() {
    backingImmutableData = (AbstractSszComposite<SszChildT>) getSchema().getDefault();
    childrenChanges.clear();
    childrenRefs.clear();
    childrenRefsChanged.clear();
    sizeCache = backingImmutableData.size();
    invalidate();
  }

  @Override
  public int size() {
    return sizeCache;
  }

  @Override
  @SuppressWarnings("unchecked")
  public SszComposite<SszChildT> commitChanges() {
    if (childrenChanges.isEmpty() && childrenRefsChanged.isEmpty()) {
      return backingImmutableData;
    } else {
      IntCache<SszChildT> cache = backingImmutableData.transferCache();
      List<Map.Entry<Integer, SszChildT>> changesList =
          Stream.concat(
                  childrenChanges.entrySet().stream(),
                  childrenRefsChanged.stream()
                      .map(
                          idx ->
                              new SimpleImmutableEntry<>(
                                  idx,
                                  (SszChildT)
                                      ((SszMutableData) childrenRefs.get(idx)).commitChanges())))
              .sorted(Map.Entry.comparingByKey())
              .collect(Collectors.toList());
      // pre-fill the read cache with changed values
      changesList.forEach(e -> cache.invalidateWithNewValue(e.getKey(), e.getValue()));
      TreeNode originalBackingTree = backingImmutableData.getBackingNode();
      TreeUpdates changes = changesToNewNodes(changesList, originalBackingTree);
      TreeNode newBackingTree = originalBackingTree.updated(changes);
      TreeNode finalBackingTree = doFinalTreeUpdates(newBackingTree);
      return createImmutableSszComposite(finalBackingTree, cache);
    }
  }

  protected TreeNode doFinalTreeUpdates(TreeNode updatedTree) {
    return updatedTree;
  }

  /** Converts a set of changed view with their indexes to the {@link TreeUpdates} instance */
  protected TreeUpdates changesToNewNodes(
      List<Map.Entry<Integer, SszChildT>> newChildValues, TreeNode original) {
    SszCompositeSchema<?> type = getSchema();
    int elementsPerChunk = type.getElementsPerChunk();
    if (elementsPerChunk == 1) {
      return newChildValues.stream()
          .map(
              e ->
                  new TreeUpdates.Update(
                      type.getChildGeneralizedIndex(e.getKey()), e.getValue().getBackingNode()))
          .collect(TreeUpdates.collector());
    } else {
      return packChanges(newChildValues, original);
    }
  }

  /**
   * Converts a set of changed view with their indexes to the {@link TreeUpdates} instance for views
   * which support packed values (i.e. several child views per backing tree node)
   */
  protected abstract TreeUpdates packChanges(
      List<Map.Entry<Integer, SszChildT>> newChildValues, TreeNode original);

  /**
   * Should be implemented by subclasses to create respectful immutable view with backing tree and
   * views cache
   */
  protected abstract AbstractSszComposite<SszChildT> createImmutableSszComposite(
      TreeNode backingNode, IntCache<SszChildT> viewCache);

  @Override
  public void setInvalidator(Consumer<SszMutableData> listener) {
    invalidator = listener;
  }

  protected void invalidate() {
    if (invalidator != null) {
      invalidator.accept(this);
    }
  }

  /** Creating nested mutable copies is not supported yet */
  @Override
  public SszMutableComposite<SszChildT> createWritableCopy() {
    throw new UnsupportedOperationException(
        "createWritableCopy() is now implemented for immutable SszData only");
  }

  /**
   * Checks the child index for get or set
   *
   * @throws IndexOutOfBoundsException is index is not valid
   */
  protected abstract void checkIndex(int index, boolean set);
}
