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

import java.util.function.Supplier;
import java.util.stream.Collectors;
import tech.pegasys.teku.ssz.backing.SszData;
import tech.pegasys.teku.ssz.backing.SszList;
import tech.pegasys.teku.ssz.backing.SszMutableList;
import tech.pegasys.teku.ssz.backing.cache.IntCache;
import tech.pegasys.teku.ssz.backing.schema.SszListSchema;
import tech.pegasys.teku.ssz.backing.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.ssz.backing.tree.GIndexUtil;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;

/**
 * Generic {@link SszList} implementation. This ssz structure is compatible with and implemented as
 * a <code>
 * Container[Vector(maxLength), size]</code> under the cover.
 */
public class SszListImpl<SszElementT extends SszData> extends AbstractSszCollection<SszElementT>
    implements SszList<SszElementT> {

  protected SszListImpl(SszListSchema<SszElementT, ?> schema, Supplier<TreeNode> lazyBackingNode) {
    super(schema, lazyBackingNode);
  }

  public SszListImpl(SszListSchema<SszElementT, ?> schema, TreeNode backingNode) {
    super(schema, backingNode);
  }

  public SszListImpl(
      SszListSchema<SszElementT, ?> schema, TreeNode backingNode, IntCache<SszElementT> cache) {
    super(schema, backingNode, cache);
  }

  @Override
  @SuppressWarnings("unchecked")
  public SszListSchema<SszElementT, ?> getSchema() {
    return (SszListSchema<SszElementT, ?>) super.getSchema();
  }

  @Override
  protected int sizeImpl() {
    return SszPrimitiveSchemas.UINT64_SCHEMA.createFromBackingNode(getSizeNode()).get().intValue();
  }

  private TreeNode getSizeNode() {
    return getBackingNode().get(GIndexUtil.RIGHT_CHILD_G_INDEX);
  }

  @Override
  public SszMutableList<SszElementT> createWritableCopy() {
    return new SszMutableListImpl<>(this);
  }

  @Override
  protected void checkIndex(int index) {
    if (index >= size()) {
      throw new IndexOutOfBoundsException(
          "Invalid index " + index + " for list with size " + size());
    }
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
