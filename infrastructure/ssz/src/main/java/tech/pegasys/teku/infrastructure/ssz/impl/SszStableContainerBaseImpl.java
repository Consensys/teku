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

import java.util.NoSuchElementException;
import java.util.stream.Collectors;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.SszStableContainerBase;
import tech.pegasys.teku.infrastructure.ssz.cache.IntCache;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.ssz.schema.SszCompositeSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszStableContainerBaseSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

public class SszStableContainerBaseImpl extends SszContainerImpl implements SszStableContainerBase {

  private final SszBitvector activeFields;

  public SszStableContainerBaseImpl(
      final SszStableContainerBaseSchema<? extends SszStableContainerBase> type) {
    super(type, type.getDefaultTree());
    this.activeFields = getSchema().toStableContainerSchemaBaseRequired().getRequiredFields();
  }

  public SszStableContainerBaseImpl(
      final SszStableContainerBaseSchema<? extends SszStableContainerBase> type,
      final TreeNode backingNode) {
    super(type, backingNode);
    this.activeFields =
        getSchema()
            .toStableContainerSchemaBaseRequired()
            .getActiveFieldsBitvectorFromBackingNode(backingNode);
  }

  public SszStableContainerBaseImpl(
      final SszCompositeSchema<?> type, final TreeNode backingNode, final IntCache<SszData> cache) {
    super(type, backingNode, cache);
    this.activeFields =
        getSchema()
            .toStableContainerSchemaBaseRequired()
            .getActiveFieldsBitvectorFromBackingNode(backingNode);
  }

  @Override
  public boolean isFieldActive(final int index) {
    return activeFields.getBit(index);
  }

  @Override
  public SszBitvector getActiveFields() {
    return activeFields;
  }

  @Override
  protected void checkIndex(final int index) {
    // note: isFieldActive will also throw IndexOutOfBounds if checking an index greater than the
    // expected size of the activeFields schema (which is the maxFieldCount)
    if (!isFieldActive(index)) {
      throw new NoSuchElementException("Index " + index + " is not active in the stable container");
    }
  }

  @Override
  public String toString() {
    return getSchema().getContainerName()
        + "{activeFields="
        + activeFields
        + ", "
        + activeFields
            .streamAllSetBits()
            .mapToObj(idx -> getSchema().getFieldNames().get(idx) + "=" + get(idx))
            .collect(Collectors.joining(", "))
        + "}";
  }
}
