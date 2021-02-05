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

import static tech.pegasys.teku.ssz.backing.view.SszListImpl.ListContainerRead;
import static tech.pegasys.teku.ssz.backing.view.SszPrimitives.SszUInt64;

import java.util.function.Consumer;
import tech.pegasys.teku.ssz.backing.SszData;
import tech.pegasys.teku.ssz.backing.SszList;
import tech.pegasys.teku.ssz.backing.SszMutableData;
import tech.pegasys.teku.ssz.backing.SszMutableList;
import tech.pegasys.teku.ssz.backing.SszMutableRefList;
import tech.pegasys.teku.ssz.backing.SszMutableRefVector;
import tech.pegasys.teku.ssz.backing.cache.IntCache;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.schema.SszListSchema;

public class SszMutableListImpl<
        SszElementT extends SszData, SszMutableElementT extends SszElementT>
    implements SszMutableRefList<SszElementT, SszMutableElementT> {

  static class ListContainerWrite<
          ElementReadType extends SszData, ElementWriteType extends ElementReadType>
      extends SszMutableContainerImpl {
    private final SszListSchema<ElementReadType> listSchema;

    public ListContainerWrite(
        ListContainerRead<ElementReadType> backingImmutableView,
        SszListSchema<ElementReadType> listSchema) {
      super(backingImmutableView);
      this.listSchema = listSchema;
    }

    public int getSize() {
      return (int) ((SszUInt64) get(1)).longValue();
    }

    public void setSize(int size) {
      set(1, SszUInt64.fromLong(size));
    }

    public SszMutableRefVector<ElementReadType, ElementWriteType> getData() {
      return getAnyByRef(0);
    }

    @Override
    protected SszContainerImpl createImmutableSszComposite(TreeNode backingNode, IntCache<SszData> viewCache) {
      return new ListContainerRead<>(listSchema, backingNode, viewCache);
    }

    @Override
    @SuppressWarnings("unchecked")
    public ListContainerRead<ElementReadType> commitChanges() {
      return (ListContainerRead<ElementReadType>) super.commitChanges();
    }
  }

  private final SszListSchema<SszElementT> schema;
  private final ListContainerWrite<SszElementT, SszMutableElementT> container;
  private int cachedSize;

  public SszMutableListImpl(
      SszListSchema<SszElementT> schema,
      ListContainerWrite<SszElementT, SszMutableElementT> container) {
    this.schema = schema;
    this.container = container;
    this.cachedSize = this.container.getSize();
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
  public SszElementT get(int index) {
    checkIndex(index, false);
    return container.getData().get(index);
  }

  @Override
  public SszMutableElementT getByRef(int index) {
    checkIndex(index, false);
    return container.getData().getByRef(index);
  }

  @Override
  public SszList<SszElementT> commitChanges() {
    return new SszListImpl<>(getSchema(), container.commitChanges());
  }

  @Override
  public void setInvalidator(Consumer<SszMutableData> listener) {
    container.setInvalidator(listener);
  }

  @Override
  public void set(int index, SszElementT value) {
    checkIndex(index, true);
    if (index == size()) {
      cachedSize++;
      container.setSize(cachedSize);
    }
    container.getData().set(index, value);
  }

  @Override
  public void clear() {
    container.clear();
    cachedSize = 0;
  }

  protected void checkIndex(int index, boolean set) {
    if ((!set && index >= size())
        || (set && (index > size() || index >= getSchema().getMaxLength()))) {
      throw new IndexOutOfBoundsException(
          "Invalid index " + index + " for list with size " + size());
    }
  }

  @Override
  public SszMutableList<SszElementT> createWritableCopy() {
    throw new UnsupportedOperationException("Creating a copy from writable list is not supported");
  }
}
