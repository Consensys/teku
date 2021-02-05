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

import java.util.Objects;
import java.util.stream.Collectors;
import tech.pegasys.teku.ssz.backing.SszList;
import tech.pegasys.teku.ssz.backing.SszMutableList;
import tech.pegasys.teku.ssz.backing.SszData;
import tech.pegasys.teku.ssz.backing.SszVector;
import tech.pegasys.teku.ssz.backing.cache.IntCache;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.type.SszContainerSchema;
import tech.pegasys.teku.ssz.backing.type.SszListSchema;
import tech.pegasys.teku.ssz.backing.view.SszMutableListImpl.ListContainerWrite;

/**
 * View of SSZ List type. This view is compatible with and implemented as a <code>
 * Container[Vector(maxLength), size]</code> under the cover.
 */
public class SszListImpl<ElementType extends SszData> implements SszList<ElementType> {

  public static class ListContainerRead<ElementType extends SszData>
      extends SszContainerImpl {
    private final SszListSchema<ElementType> type;

    private ListContainerRead(SszListSchema<ElementType> type) {
      super(type.getCompatibleListContainerType());
      this.type = type;
    }

    public ListContainerRead(
        SszListSchema<ElementType> type, SszContainerSchema<?> containerType, TreeNode backingNode) {
      super(containerType, backingNode);
      this.type = type;
    }

    public ListContainerRead(SszListSchema<ElementType> type, TreeNode backingNode) {
      super(type.getCompatibleListContainerType(), backingNode);
      this.type = type;
    }

    public ListContainerRead(
        SszListSchema<ElementType> type, TreeNode backingNode, IntCache<SszData> cache) {
      super(type.getCompatibleListContainerType(), backingNode, cache);
      this.type = type;
    }

    public int getSize() {
      return (int) ((SszPrimitives.UInt64View) get(1)).longValue();
    }

    public SszVector<ElementType> getData() {
      return getAny(0);
    }

    @Override
    public ListContainerWrite<ElementType, ?> createWritableCopy() {
      return new ListContainerWrite<>(this, type);
    }
  }

  private final SszListSchema<ElementType> type;
  private final ListContainerRead<ElementType> container;
  private final int cachedSize;

  protected SszListImpl(SszList<ElementType> other) {
    if (other instanceof SszListImpl) {
      // optimization to preserve child view caches
      SszListImpl<ElementType> otherImpl = (SszListImpl<ElementType>) other;
      this.type = otherImpl.type;
      this.container = otherImpl.container;
      this.cachedSize = otherImpl.cachedSize;
    } else {
      this.type = other.getType();
      this.container = new ListContainerRead<>(type, other.getBackingNode());
      this.cachedSize = container.getSize();
    }
  }

  public SszListImpl(SszListSchema<ElementType> type, TreeNode node) {
    this.type = type;
    this.container = new ListContainerRead<>(type, node);
    this.cachedSize = container.getSize();
  }

  public SszListImpl(
      SszListSchema<ElementType> type, ListContainerRead<ElementType> container) {
    this.type = type;
    this.container = container;
    this.cachedSize = container.getSize();
  }

  @Override
  public ElementType get(int index) {
    checkIndex(index);
    return container.getData().get(index);
  }

  @Override
  public SszMutableList<ElementType> createWritableCopy() {
    return new SszMutableListImpl<>(getType(), container.createWritableCopy());
  }

  @Override
  public SszListSchema<ElementType> getType() {
    return type;
  }

  @Override
  public int size() {
    return cachedSize;
  }

  @Override
  public TreeNode getBackingNode() {
    return container.getBackingNode();
  }

  protected void checkIndex(int index) {
    if (index >= size()) {
      throw new IndexOutOfBoundsException(
          "Invalid index " + index + " for list with size " + size());
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof SszList)) {
      return false;
    }
    return hashTreeRoot().equals(((SszData) o).hashTreeRoot());
  }

  @Override
  public int hashCode() {
    return Objects.hash(hashTreeRoot());
  }

  @Override
  public String toString() {
    int maxToDisplay = 1024;
    String elements =
        stream().limit(maxToDisplay).map(Object::toString).collect(Collectors.joining(", "));
    if (size() > maxToDisplay) {
      elements += " ... more " + (size() - maxToDisplay) + " elements";
    }
    return "SszList{size=" + size() + ": " + elements + "}";
  }
}
