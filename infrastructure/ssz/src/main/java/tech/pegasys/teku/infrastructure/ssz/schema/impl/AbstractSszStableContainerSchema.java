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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.SszStableContainer;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszNone;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszStableContainerSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBitvectorSchema;
import tech.pegasys.teku.infrastructure.ssz.sos.SszDeserializeException;
import tech.pegasys.teku.infrastructure.ssz.sos.SszLengthBounds;
import tech.pegasys.teku.infrastructure.ssz.sos.SszReader;
import tech.pegasys.teku.infrastructure.ssz.sos.SszWriter;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

public abstract class AbstractSszStableContainerSchema<C extends SszStableContainer>
    extends AbstractSszContainerSchema<C> implements SszStableContainerSchema<C> {

  private final Map<Integer, NamedSchema<?>> childrenActiveSchemas;
  private final SszBitvectorSchema<SszBitvector> activeFieldsBitvectorSchema;
  private final SszBitvector activeFieldsBitvector;

  public static Map<Integer, NamedSchema<?>> continuousActiveNamedSchemas(
      final List<? extends NamedSchema<?>> namedSchemas) {
    return IntStream.range(0, namedSchemas.size())
        .boxed()
        .collect(Collectors.toMap(Function.identity(), namedSchemas::get));
  }

  public static Map<Integer, NamedSchema<?>> continuousActiveSchemas(
      final List<SszSchema<?>> schemas) {
    return IntStream.range(0, schemas.size())
        .boxed()
        .collect(
            Collectors.toMap(Function.identity(), i -> namedSchema("field-" + i, schemas.get(i))));
  }

  public AbstractSszStableContainerSchema(
      final String name,
      final Map<Integer, NamedSchema<?>> childrenSchemas,
      final int maxFieldCount) {
    super(name, createAllSchemas(childrenSchemas, maxFieldCount));

    this.childrenActiveSchemas = childrenSchemas;
    this.activeFieldsBitvectorSchema = SszBitvectorSchema.create(maxFieldCount);
    this.activeFieldsBitvector = activeFieldsBitvectorSchema.ofBits(childrenSchemas.keySet());
  }

  @Override
  public TreeNode createTreeFromFieldValues(final List<? extends SszData> fieldValues) {
    checkArgument(
        fieldValues.size() == childrenActiveSchemas.size(), "Wrong number of filed values");
    final int allFieldsSize = Math.toIntExact(getMaxLength());
    final List<SszData> allFields = new ArrayList<>(allFieldsSize);

    for (int index = 0, fieldIndex = 0; index < allFieldsSize; index++) {
      allFields.add(
          childrenActiveSchemas.containsKey(index)
              ? fieldValues.get(fieldIndex++)
              : SszNone.INSTANCE);
    }

    return super.createTreeFromFieldValues(allFields);
  }

  @Override
  public SszBitvector getActiveFields() {
    return activeFieldsBitvector;
  }

  @Override
  public int getActiveFieldCount() {
    return childrenActiveSchemas.size();
  }

  @Override
  public boolean isActiveField(final int index) {
    checkArgument(
        index < activeFieldsBitvectorSchema.getMaxLength(), "Wrong number of filed values");
    return childrenActiveSchemas.containsKey(index);
  }

  @Override
  public int sszSerializeTree(final TreeNode node, final SszWriter writer) {
    int size1 = activeFieldsBitvector.sszSerialize(writer);
    int size2 = super.sszSerializeTree(node, writer);
    return size1 + size2;
  }

  public int sszSerializeTreeAsProfile(final TreeNode node, final SszWriter writer) {
    return super.sszSerializeTree(node, writer);
  }

  @Override
  public TreeNode sszDeserializeTree(final SszReader reader) {
    final SszReader activeFieldsReader =
        reader.slice(activeFieldsBitvectorSchema.getSszFixedPartSize());
    final SszBitvector bitvector = activeFieldsBitvectorSchema.sszDeserialize(activeFieldsReader);
    if (!bitvector.equals(activeFieldsBitvector)) {
      throw new SszDeserializeException(
          "Invalid StableContainer bitvector: "
              + bitvector
              + ", expected "
              + activeFieldsBitvector
              + " for the stable container of type "
              + this);
    }
    return super.sszDeserializeTree(reader);
  }

  public TreeNode sszDeserializeTreeAsProfile(final SszReader reader) {
    return super.sszDeserializeTree(reader);
  }

  @Override
  public SszLengthBounds getSszLengthBounds() {
    return super.getSszLengthBounds().add(activeFieldsBitvectorSchema.getSszLengthBounds());
  }

  public SszLengthBounds getSszLengthBoundsAsProfile() {
    return super.getSszLengthBounds();
  }

  @Override
  public int getSszSize(final TreeNode node) {
    return super.getSszSize(node) + activeFieldsBitvectorSchema.getSszFixedPartSize();
  }

  public int getSszSizeAsProfile(final TreeNode node) {
    return super.getSszSize(node);
  }

  private static List<? extends NamedSchema<?>> createAllSchemas(
      final Map<Integer, NamedSchema<?>> childrenSchemas, final int maxFieldCount) {

    checkArgument(childrenSchemas.keySet().stream().allMatch(i -> i < maxFieldCount));

    return IntStream.range(0, maxFieldCount)
        .mapToObj(
            idx ->
                childrenSchemas.getOrDefault(
                    idx, NamedSchema.of("__none_" + idx, SszPrimitiveSchemas.NONE_SCHEMA)))
        .toList();
  }
}
