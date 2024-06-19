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

package tech.pegasys.teku.infrastructure.ssz.schema.impl;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.infrastructure.ssz.schema.impl.AbstractSszStableContainerSchema.continuousActiveNamedSchemas;

import it.unimi.dsi.fastutil.ints.IntList;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.SszProfile;
import tech.pegasys.teku.infrastructure.ssz.SszStableContainer;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszNone;
import tech.pegasys.teku.infrastructure.ssz.schema.SszProfileSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszStableContainerSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBitvectorSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.impl.AbstractSszStableContainerSchema.NamedIndexedSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.BranchNode;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

public abstract class AbstractSszStableProfileSchema<C extends SszProfile>
    extends AbstractSszContainerSchema<C> implements SszProfileSchema<C> {

  private final IntList activeFieldIndicesCache;
  private final SszBitvector activeFieldsBitvector;
  private final SszStableContainerSchema<? extends SszStableContainer> stableContainer;

  public AbstractSszStableProfileSchema(
      final String name,
      final SszStableContainerSchema<? extends SszStableContainer> stableContainer,
      final List<Integer> activeFieldIndices) {
    super(name, stableContainer.getDefinedChildrenSchemas());

    this.stableContainer = stableContainer;
    // TODO validate activeFieldIndices

    this.activeFieldIndicesCache =
        IntList.of(
            activeFieldIndices.stream()
                .mapToInt(
                    index -> stableContainer.getDefinedChildrenSchemas().get(index).getIndex())
                .toArray());
    this.activeFieldsBitvector = getActiveFieldsSchema().ofBits(activeFieldIndices);
  }

  public AbstractSszStableProfileSchema(
      final String name,
      final List<? extends NamedSchema<?>> childrenSchemas,
      final int maxFieldCount) {
    this(
        name,
        new AbstractSszStableContainerSchema<C>(
            "", continuousActiveNamedSchemas(childrenSchemas), maxFieldCount) {

          @Override
          public C createFromBackingNode(final TreeNode node) {
            return null;
          }
        },
        IntList.of(IntStream.range(0, childrenSchemas.size()).toArray()));
  }

  @Override
  public List<? extends NamedIndexedSchema<?>> getDefinedChildrenSchemas() {
    return stableContainer.getDefinedChildrenSchemas();
  }

  @Override
  public SszStableContainerSchema<? extends SszStableContainer> getStableContainerSchema() {
    return stableContainer;
  }

  @Override
  public TreeNode createTreeFromFieldValues(final List<? extends SszData> fieldValues) {
    checkArgument(
        fieldValues.size() == getDefaultActiveFieldsBitvector().getBitCount(),
        "Wrong number of filed values");
    final int allFieldsSize = Math.toIntExact(getMaxLength());
    final List<SszData> allFields = new ArrayList<>(allFieldsSize);

    for (int index = 0, fieldIndex = 0; index < allFieldsSize; index++) {
      allFields.add(
          getDefaultActiveFieldsBitvector().getBit(index)
              ? fieldValues.get(fieldIndex++)
              : SszNone.INSTANCE);
    }

    return BranchNode.create(
        super.createTreeFromFieldValues(allFields),
        getDefaultActiveFieldsBitvector().getBackingNode());
  }

  @Override
  public int getMaxFieldCount() {
    return stableContainer.getMaxFieldCount();
  }

  @Override
  public SszBitvector getDefaultActiveFieldsBitvector() {
    return activeFieldsBitvector;
  }

  @Override
  public SszBitvector getActiveFieldsBitvectorFromBackingNode(final TreeNode node) {
    throw new UnsupportedOperationException("Should not be called on profile schema");
  }

  @Override
  public SszBitvectorSchema<SszBitvector> getActiveFieldsSchema() {
    return stableContainer.getActiveFieldsSchema();
  }

  @Override
  public int getActiveFieldCount() {
    return activeFieldIndicesCache.size();
  }

  @Override
  public int getNthActiveFieldIndex(final int nthActiveField) {
    return activeFieldIndicesCache.getInt(nthActiveField);
  }

  @Override
  public boolean isActiveField(final int index) {
    checkArgument(index < getActiveFieldsSchema().getMaxLength(), "Wrong number of filed values");
    return activeFieldsBitvector.getBit(index);
  }

  @Override
  public SszBitvector getActiveFields() {
    return activeFieldsBitvector;
  }
}
