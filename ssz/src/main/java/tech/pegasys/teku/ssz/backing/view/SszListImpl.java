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
import tech.pegasys.teku.ssz.backing.SszData;
import tech.pegasys.teku.ssz.backing.SszList;
import tech.pegasys.teku.ssz.backing.SszMutableList;
import tech.pegasys.teku.ssz.backing.SszVector;
import tech.pegasys.teku.ssz.backing.cache.IntCache;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.schema.SszContainerSchema;
import tech.pegasys.teku.ssz.backing.schema.SszListSchema;
import tech.pegasys.teku.ssz.backing.view.SszMutableListImpl.ListContainerWrite;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives.SszUInt64;

/**
 * Generic {@link SszList} implementation. This ssz structure is compatible with and implemented as a <code>
 * Container[Vector(maxLength), size]</code> under the cover.
 */
public class SszListImpl<SszElementT extends SszData> implements SszList<SszElementT> {

  public static class ListContainerRead<ElementType extends SszData> extends SszContainerImpl {
    private final SszListSchema<ElementType> schema;

    public ListContainerRead(
        SszListSchema<ElementType> schema,
        SszContainerSchema<?> containerType,
        TreeNode backingNode) {
      super(containerType, backingNode);
      this.schema = schema;
    }

    public ListContainerRead(SszListSchema<ElementType> schema, TreeNode backingNode) {
      super(schema.getCompatibleListContainerType(), backingNode);
      this.schema = schema;
    }

    public ListContainerRead(
        SszListSchema<ElementType> schema, TreeNode backingNode, IntCache<SszData> cache) {
      super(schema.getCompatibleListContainerType(), backingNode, cache);
      this.schema = schema;
    }

    public int getSize() {
      return (int) ((SszUInt64) get(1)).longValue();
    }

    public SszVector<ElementType> getData() {
      return getAny(0);
    }

    @Override
    public ListContainerWrite<ElementType, ?> createWritableCopy() {
      return new ListContainerWrite<>(this, schema);
    }
  }

  private final SszListSchema<SszElementT> schema;
  private final ListContainerRead<SszElementT> container;
  private final int cachedSize;

  protected SszListImpl(SszList<SszElementT> other) {
    if (other instanceof SszListImpl) {
      // optimization to preserve child view caches
      SszListImpl<SszElementT> otherImpl = (SszListImpl<SszElementT>) other;
      this.schema = otherImpl.schema;
      this.container = otherImpl.container;
      this.cachedSize = otherImpl.cachedSize;
    } else {
      this.schema = other.getSchema();
      this.container = new ListContainerRead<>(schema, other.getBackingNode());
      this.cachedSize = container.getSize();
    }
  }

  public SszListImpl(SszListSchema<SszElementT> schema, TreeNode node) {
    this.schema = schema;
    this.container = new ListContainerRead<>(schema, node);
    this.cachedSize = container.getSize();
  }

  public SszListImpl(SszListSchema<SszElementT> schema, ListContainerRead<SszElementT> container) {
    this.schema = schema;
    this.container = container;
    this.cachedSize = container.getSize();
  }

  @Override
  public SszElementT get(int index) {
    checkIndex(index);
    return container.getData().get(index);
  }

  @Override
  public SszMutableList<SszElementT> createWritableCopy() {
    return new SszMutableListImpl<>(getSchema(), container.createWritableCopy());
  }

  @Override
  public SszListSchema<SszElementT> getSchema() {
    return schema;
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
