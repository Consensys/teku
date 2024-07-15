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
import java.util.Optional;
import java.util.stream.Collectors;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.SszMutableContainer;
import tech.pegasys.teku.infrastructure.ssz.SszStableContainerBase;
import tech.pegasys.teku.infrastructure.ssz.cache.ArrayIntCache;
import tech.pegasys.teku.infrastructure.ssz.cache.IntCache;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.ssz.schema.SszCompositeSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszStableContainerBaseSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

public class SszStableContainerBaseImpl extends SszContainerImpl implements SszStableContainerBase {

  private final SszBitvector activeFields;

  public SszStableContainerBaseImpl(
      final SszStableContainerBaseSchema<? extends SszStableContainerBase> type) {
    this(type, type.getDefaultTree());
  }

  public SszStableContainerBaseImpl(
      final SszStableContainerBaseSchema<? extends SszStableContainerBase> type,
      final TreeNode backingNode) {
    this(type, backingNode, Optional.empty());
  }

  public SszStableContainerBaseImpl(
      final SszStableContainerBaseSchema<? extends SszStableContainerBase> type,
      final TreeNode backingNode,
      final IntCache<SszData> cache) {
    this(type, backingNode, Optional.of(cache));
  }

  private SszStableContainerBaseImpl(
      final SszStableContainerBaseSchema<?> type,
      final TreeNode backingNode,
      final Optional<IntCache<SszData>> cache) {
    this(
        type,
        backingNode,
        cache,
        type.toStableContainerSchemaBaseRequired()
            .getActiveFieldsBitvectorFromBackingNode(backingNode));
  }

  /**
   * The composite class creates the cache with a capacity set as the size of the composite. In
   * Stable Container and Profile case, the actual the size will be the max theoretical size. To
   * avoid this waste we always pre-create the cache with the effective required size.
   */
  private SszStableContainerBaseImpl(
      final SszCompositeSchema<?> type,
      final TreeNode backingNode,
      final Optional<IntCache<SszData>> cache,
      final SszBitvector activeFields) {
    super(type, backingNode, cache.orElse(createCache(activeFields)));
    this.activeFields = activeFields;
  }

  private static IntCache<SszData> createCache(final SszBitvector activeFields) {
    return new ArrayIntCache<>(activeFields.getLastSetBitIndex() + 1);
  }

  @Override
  public SszMutableContainer createWritableCopy() {
    if (isWritableSupported()) {
      return new SszMutableStableContainerBaseImpl(this);
    }
    throw new UnsupportedOperationException(
        "Mutation on Stable Containers or Profiles with optional fields is not currently supported");
  }

  @Override
  public boolean isWritableSupported() {
    return !getSchema().toStableContainerSchemaBaseRequired().hasOptionalFields();
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
  protected int sizeImpl() {
    return activeFields.getBitCount();
  }

  @Override
  protected void checkIndex(final int index) {
    if (isFieldActive(index)) {
      return;
    }
    if (getSchema().toStableContainerSchemaBaseRequired().isFieldAllowed(index)) {
      throw new NoSuchElementException("Index " + index + " is not active in the stable container");
    }
    throw new IndexOutOfBoundsException("Index " + index + " is not allowed");
  }

  @Override
  public String toString() {
    return getSchema().getContainerName()
        + "{activeFields="
        + activeFields
        + ", optionalFields: "
        + getSchema().toStableContainerSchemaBaseRequired().getOptionalFields()
        + ", "
        + activeFields
            .streamAllSetBits()
            .mapToObj(idx -> getSchema().getFieldNames().get(idx) + "=" + get(idx))
            .collect(Collectors.joining(", "))
        + "}";
  }
}
