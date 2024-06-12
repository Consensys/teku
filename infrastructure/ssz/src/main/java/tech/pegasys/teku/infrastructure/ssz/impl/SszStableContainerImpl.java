/*
 * Copyright Consensys Software Inc., 2024
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

import java.util.stream.Collectors;
import java.util.stream.IntStream;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.SszStableContainer;
import tech.pegasys.teku.infrastructure.ssz.cache.IntCache;
import tech.pegasys.teku.infrastructure.ssz.schema.SszCompositeSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszStableContainerSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

public abstract class SszStableContainerImpl extends SszContainerImpl
    implements SszStableContainer {

  public SszStableContainerImpl(
      final SszStableContainerSchema<? extends SszStableContainerImpl> type) {
    super(type);
  }

  public SszStableContainerImpl(
      final SszStableContainerSchema<? extends SszStableContainerImpl> type,
      final TreeNode backingNode) {
    super(type, backingNode);
  }

  public SszStableContainerImpl(
      final SszCompositeSchema<?> type, final TreeNode backingNode, final IntCache<SszData> cache) {
    super(type, backingNode, cache);
  }

  @Override
  public boolean isFieldActive(final int index) {
    return getStableSchema().isActiveField(index);
  }

  @Override
  protected void checkIndex(final int index) {
    if (!isFieldActive(index)) {
      throw new IndexOutOfBoundsException(
          "Index " + index + " is not active in the stable container");
    }
  }

  @Override
  public SszStableContainerSchema<?> getStableSchema() {
    return ((SszStableContainerSchema<?>) super.getSchema());
  }

  @Override
  public String toString() {
    final SszStableContainerSchema<?> schema = this.getStableSchema();
    return schema.getContainerName()
        + "{activeFields="
        + schema.getActiveFieldsBitvector()
        + ", "
        + IntStream.range(0, schema.getActiveFieldCount())
            .filter(schema::isActiveField)
            .mapToObj(idx -> schema.getFieldNames().get(idx) + "=" + get(idx))
            .collect(Collectors.joining(", "))
        + "}";
  }
}
