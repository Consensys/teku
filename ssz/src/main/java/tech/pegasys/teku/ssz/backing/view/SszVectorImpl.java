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

import java.util.stream.Collectors;
import tech.pegasys.teku.ssz.backing.SszData;
import tech.pegasys.teku.ssz.backing.SszVector;
import tech.pegasys.teku.ssz.backing.cache.ArrayIntCache;
import tech.pegasys.teku.ssz.backing.cache.IntCache;
import tech.pegasys.teku.ssz.backing.schema.SszCompositeSchema;
import tech.pegasys.teku.ssz.backing.schema.SszVectorSchema;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;

public class SszVectorImpl<SszElementT extends SszData> extends AbstractSszCollection<SszElementT>
    implements SszVector<SszElementT> {

  public SszVectorImpl(SszCompositeSchema<?> schema, TreeNode backingNode) {
    super(schema, backingNode);
  }

  public SszVectorImpl(
      SszCompositeSchema<?> schema, TreeNode backingNode, IntCache<SszElementT> cache) {
    super(schema, backingNode, cache);
  }

  @Override
  protected int sizeImpl() {
    return (int) Long.min(Integer.MAX_VALUE, this.getSchema().getMaxLength());
  }

  @Override
  public SszMutableVectorImpl<SszElementT, ?> createWritableCopy() {
    return new SszMutableVectorImpl<>(this);
  }

  @SuppressWarnings("unchecked")
  @Override
  public SszVectorSchema<SszElementT> getSchema() {
    return (SszVectorSchema<SszElementT>) super.getSchema();
  }

  @Override
  protected IntCache<SszElementT> createCache() {
    return size() > 16384 ? new ArrayIntCache<>() : new ArrayIntCache<>(size());
  }

  @Override
  protected void checkIndex(int index) {
    if (index >= size()) {
      throw new IndexOutOfBoundsException(
          "Invalid index " + index + " for vector with size " + size());
    }
  }

  @Override
  public String toString() {
    return "SszVector{" + stream().map(Object::toString).collect(Collectors.joining(", ")) + "}";
  }
}
