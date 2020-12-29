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

import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import tech.pegasys.teku.ssz.backing.CompositeViewWrite;
import tech.pegasys.teku.ssz.backing.CompositeViewWriteRef;
import tech.pegasys.teku.ssz.backing.ViewRead;
import tech.pegasys.teku.ssz.backing.ViewWrite;
import tech.pegasys.teku.ssz.backing.cache.IntCache;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.tree.TreeUpdates;
import tech.pegasys.teku.ssz.backing.type.CompositeViewType;

/**
 * Base backing view class for mutable composite views (lists, vectors, containers)
 *
 * <p>It has corresponding backing immutable view and the set of changed children. When the {@link
 * #commitChanges()} is called a new immutable view is created where changes accumulated in this
 * instance are merged with cached backing view instance which weren't changed.
 *
 * <p>If this view is get by reference from its parent composite view ({@link
 * CompositeViewWriteRef#getByRef(int)} then all the changes are notified to the parent view (see
 * {@link CompositeViewWrite#setInvalidator(Consumer)}
 *
 * <p>The mutable views based on this class are inherently NOT thread safe
 */
public abstract class AbstractCompositeViewWrite<
        ChildReadType extends ViewRead, ChildWriteType extends ChildReadType>
    implements CompositeViewWriteRef<ChildReadType, ChildWriteType> {

  protected AbstractCompositeViewRead<ChildReadType> backingImmutableView;
  private Consumer<ViewWrite> invalidator;
  private final Map<Integer, ChildReadType> childrenChanges = new HashMap<>();
  private final Map<Integer, ChildWriteType> childrenRefs = new HashMap<>();
  private final Set<Integer> childrenRefsChanged = new HashSet<>();
  private Integer sizeCache;

  /** Creates a new mutable instance with backing immutable view */
  protected AbstractCompositeViewWrite(
      AbstractCompositeViewRead<ChildReadType> backingImmutableView) {
    this.backingImmutableView = backingImmutableView;
    sizeCache = backingImmutableView.size();
  }

  @Override
  public void set(int index, ChildReadType value) {
    checkIndex(index, true);
    if (childrenRefs.containsKey(index)) {
      throw new IllegalStateException(
          "A child couldn't be simultaneously modified by value and accessed by ref");
    }
    childrenChanges.put(index, value);
    sizeCache = index >= sizeCache ? index + 1 : sizeCache;
    invalidate();
  }

  @Override
  public ChildReadType get(int index) {
    checkIndex(index, false);
    ChildReadType ret = childrenChanges.get(index);
    if (ret != null) {
      return ret;
    } else if (childrenRefs.containsKey(index)) {
      return childrenRefs.get(index);
    } else {
      return backingImmutableView.get(index);
    }
  }

  @Override
  public ChildWriteType getByRef(int index) {
    ChildWriteType ret = childrenRefs.get(index);
    if (ret == null) {
      ChildReadType readView = get(index);
      childrenChanges.remove(index);
      @SuppressWarnings("unchecked")
      ChildWriteType w = (ChildWriteType) readView.createWritableCopy();
      if (w instanceof CompositeViewWrite) {
        ((CompositeViewWrite<?>) w)
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
  public CompositeViewType<?> getType() {
    return backingImmutableView.getType();
  }

  @Override
  @SuppressWarnings("unchecked")
  public void clear() {
    backingImmutableView = (AbstractCompositeViewRead<ChildReadType>) getType().getDefault();
    childrenChanges.clear();
    childrenRefs.clear();
    childrenRefsChanged.clear();
    sizeCache = backingImmutableView.size();
    invalidate();
  }

  @Override
  public int size() {
    return sizeCache;
  }

  @Override
  @SuppressWarnings("unchecked")
  public ViewRead commitChanges() {
    if (childrenChanges.isEmpty() && childrenRefsChanged.isEmpty()) {
      return backingImmutableView;
    } else {
      IntCache<ChildReadType> cache = backingImmutableView.transferCache();
      List<Map.Entry<Integer, ChildReadType>> changesList =
          Stream.concat(
                  childrenChanges.entrySet().stream(),
                  childrenRefsChanged.stream()
                      .map(
                          idx ->
                              new SimpleImmutableEntry<>(
                                  idx,
                                  (ChildReadType)
                                      ((ViewWrite) childrenRefs.get(idx)).commitChanges())))
              .sorted(Map.Entry.comparingByKey())
              .collect(Collectors.toList());
      // pre-fill the read cache with changed values
      changesList.forEach(e -> cache.invalidateWithNewValue(e.getKey(), e.getValue()));
      TreeNode originalBackingTree = backingImmutableView.getBackingNode();
      TreeUpdates changes = changesToNewNodes(changesList, originalBackingTree);
      TreeNode newBackingTree = originalBackingTree.updated(changes);
      return createViewRead(newBackingTree, cache);
    }
  }

  /** Converts a set of changed view with their indexes to the {@link TreeUpdates} instance */
  protected TreeUpdates changesToNewNodes(
      List<Map.Entry<Integer, ChildReadType>> newChildValues, TreeNode original) {
    CompositeViewType<?> type = getType();
    int elementsPerChunk = type.getElementsPerChunk();
    if (elementsPerChunk == 1) {
      return newChildValues.stream()
          .map(
              e ->
                  new TreeUpdates.Update(
                      type.getGeneralizedIndex(e.getKey()), e.getValue().getBackingNode()))
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
      List<Map.Entry<Integer, ChildReadType>> newChildValues, TreeNode original);

  /**
   * Should be implemented by subclasses to create respectful immutable view with backing tree and
   * views cache
   */
  protected abstract AbstractCompositeViewRead<ChildReadType> createViewRead(
      TreeNode backingNode, IntCache<ChildReadType> viewCache);

  @Override
  public void setInvalidator(Consumer<ViewWrite> listener) {
    invalidator = listener;
  }

  protected void invalidate() {
    if (invalidator != null) {
      invalidator.accept(this);
    }
  }

  /**
   * Backing node is assumed to be retrieved from committed immutable view only for the sake of
   * speed to restrict accidental non-optimal usages
   */
  @Override
  public TreeNode getBackingNode() {
    throw new IllegalStateException("Call commitChanges().getBackingNode()");
  }

  /** Creating nested mutable copies is not supported yet */
  @Override
  public ViewWrite createWritableCopy() {
    throw new UnsupportedOperationException(
        "createWritableCopy() is now implemented for immutable views only");
  }

  /**
   * Checks the child index for get or set
   *
   * @throws IndexOutOfBoundsException is index is not valid
   */
  protected abstract void checkIndex(int index, boolean set);
}
