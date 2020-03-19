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

import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import tech.pegasys.artemis.util.backing.CompositeViewWrite;
import tech.pegasys.artemis.util.backing.CompositeViewWriteRef;
import tech.pegasys.artemis.util.backing.ViewRead;
import tech.pegasys.artemis.util.backing.ViewWrite;
import tech.pegasys.artemis.util.backing.tree.TreeNode;
import tech.pegasys.artemis.util.backing.tree.TreeNodes;
import tech.pegasys.artemis.util.backing.type.CompositeViewType;
import tech.pegasys.artemis.util.cache.Cache;

public abstract class AbstractCompositeViewWrite1<
        C extends AbstractCompositeViewWrite1<C, R, W>, R extends ViewRead, W extends R>
    implements CompositeViewWriteRef<R, W> {

  private AbstractCompositeViewRead<?, R> backingImmutableView;
  private Consumer<ViewWrite> invalidator;
  private final Map<Integer, R> childrenChanges = new HashMap<>();
  private final Map<Integer, W> childrenRefs = new HashMap<>();
  private final Set<Integer> childrenRefsChanged = new HashSet<>();
  private Integer sizeCache;

  public AbstractCompositeViewWrite1(AbstractCompositeViewRead<?, R> backingImmutableView) {
    this.backingImmutableView = backingImmutableView;
    sizeCache = backingImmutableView.size();
  }

  @Override
  public void set(int index, R value) {
    checkIndex(index, true);
    if (childrenRefs.containsKey(index)){
      throw new IllegalStateException(
          "A child couldn't be simultaneously modified by value and accessed by ref");
    }
    childrenChanges.put(index, value);
    sizeCache = index >= sizeCache ? index + 1 : sizeCache;
    invalidate();
  }

  @Override
  public R get(int index) {
    checkIndex(index, false);
    R ret = childrenChanges.get(index);
    if (ret != null) {
      return ret;
    } else if (childrenRefs.containsKey(index)){
      return childrenRefs.get(index);
    } else {
      return backingImmutableView.get(index);
    }
  }

  @Override
  public W getByRef(int index) {
    W ret = childrenRefs.get(index);
    if (ret == null) {
      R readView = get(index);
      childrenChanges.remove(index);
      ret = (W) readView.createWritableCopy();
      if (ret instanceof CompositeViewWrite) {
        ((CompositeViewWrite<?>) ret)
            .setInvalidator(
                viewWrite -> {
                  childrenRefsChanged.add(index);
                  invalidate();
                });
      }
      childrenRefs.put(index, ret);
    }
    return ret;
  }

  @Override
  public CompositeViewType getType() {
    return backingImmutableView.getType();
  }

  @Override
  @SuppressWarnings("unchecked")
  public void clear() {
    backingImmutableView = (AbstractCompositeViewRead<?, R>) getType().getDefault();
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
      Cache<Integer, R> cache = backingImmutableView.transferCache();
      List<Entry<Integer, R>> changesList = Stream.concat(
          childrenChanges.entrySet().stream(),
          childrenRefsChanged.stream()
              .map(
                  idx ->
                      new SimpleImmutableEntry<>(
                          idx, (R) ((ViewWrite) childrenRefs.get(idx)).commitChanges())))
          .sorted(Entry.comparingByKey())
          .collect(Collectors.toList());
      // pre-fill the read cache with changed values
      changesList.forEach(e -> cache.get(e.getKey(), i -> e.getValue()));
      TreeNode originalBackingTree = backingImmutableView.getBackingNode();
      TreeNodes changes = changesToNewNodes(changesList, originalBackingTree);
      TreeNode newBackingTree = originalBackingTree.updated(changes);
      return createViewRead(newBackingTree, cache);
    }
  }

  protected TreeNodes changesToNewNodes(List<Entry<Integer, R>> newChildValues, TreeNode original) {
    CompositeViewType type = getType();
    int elementsPerChunk = type.getElementsPerChunk();
    if  (elementsPerChunk == 1) {
      TreeNodes ret = new TreeNodes();
      newChildValues.forEach(
          e -> ret.add(type.getGeneralizedIndex(e.getKey()), e.getValue().getBackingNode()));
      return ret;
    } else {
      return packChanges(newChildValues, original);
    }
  }

  protected TreeNodes packChanges(List<Entry<Integer, R>> newChildValues, TreeNode original) {
    throw new UnsupportedOperationException("Packed values are not supported");
  }

  protected abstract AbstractCompositeViewRead<?, R> createViewRead(
      TreeNode backingNode, Cache<Integer, R> viewCache);

  @Override
  public void setInvalidator(Consumer<ViewWrite> listener) {
    invalidator = listener;
  }

  protected void invalidate() {
    if (invalidator != null) {
      invalidator.accept(this);
    }
  }

  @Override
  public TreeNode getBackingNode() {
    throw new IllegalStateException("Call commitChanges().getBackingNode()");
  }

  @Override
  public ViewWrite createWritableCopy() {
    throw new UnsupportedOperationException("createWritableCopy() is now implemented for immutable views only");
  }

  protected abstract void checkIndex(int index, boolean set);
}
