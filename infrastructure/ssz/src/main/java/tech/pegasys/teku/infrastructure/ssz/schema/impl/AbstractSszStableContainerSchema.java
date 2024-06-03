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

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import tech.pegasys.teku.infrastructure.ssz.SszContainer;
import tech.pegasys.teku.infrastructure.ssz.SszData;
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

public abstract class AbstractSszStableContainerSchema<C extends SszContainer>
    extends AbstractSszContainerSchema<C> implements SszStableContainerSchema<C> {

  private final List<NamedIndexedSchema<?>> childrenActiveSchemas;
  private final SszBitvectorSchema<SszBitvector> serializeBitvectorSchema;
  private final SszBitvector serializeBitvector;

  public record NamedIndexedSchema<T extends SszData>(String name, int index, SszSchema<T> schema) {

    public NamedSchema<T> toNamedSchema() {
      return NamedSchema.of(name, schema);
    }
  }

  public AbstractSszStableContainerSchema(
      String name, List<NamedIndexedSchema<?>> childrenSchemas, int maxFieldCount) {
    super(name, createAllSchemas(childrenSchemas, maxFieldCount));

    this.childrenActiveSchemas = childrenSchemas;

    serializeBitvectorSchema = SszBitvectorSchema.create(maxFieldCount);
    serializeBitvector =
        serializeBitvectorSchema.ofBits(
            childrenSchemas.stream().mapToInt(NamedIndexedSchema::index).toArray());
  }

  @Override
  public TreeNode createTreeFromFieldValues(final List<? extends SszData> fieldValues) {
    checkArgument(
        fieldValues.size() == childrenActiveSchemas.size(), "Wrong number of filed values");
    List<SszData> allFields =
        Stream.generate(() -> SszNone.INSTANCE).limit(getMaxLength()).collect(Collectors.toList());
    for (int i = 0; i < childrenActiveSchemas.size(); i++) {
      allFields.set(childrenActiveSchemas.get(i).index(), fieldValues.get(i));
    }
    return super.createTreeFromFieldValues(allFields);
  }

  @Override
  public int sszSerializeTree(TreeNode node, SszWriter writer) {
    int size1 = serializeBitvector.sszSerialize(writer);
    int size2 = super.sszSerializeTree(node, writer);
    return size1 + size2;
  }

  public int sszSerializeParentTree(TreeNode node, SszWriter writer) {
    return super.sszSerializeTree(node, writer);
  }

  @Override
  public TreeNode sszDeserializeTree(SszReader reader) {
    SszBitvector bitvector = serializeBitvectorSchema.sszDeserialize(reader);
    if (!bitvector.equals(serializeBitvector)) {
      throw new SszDeserializeException(
          "Invalid StableContainer bitvector: "
              + bitvector
              + ", expected "
              + serializeBitvector
              + " for the stable container of type "
              + this);
    }
    return super.sszDeserializeTree(reader);
  }

  public TreeNode sszDeserializeParentTree(SszReader reader) {
    return super.sszDeserializeTree(reader);
  }

  @Override
  public SszLengthBounds getSszLengthBounds() {
    return super.getSszLengthBounds().add(serializeBitvectorSchema.getSszLengthBounds());
  }

  public SszLengthBounds getParentSszLengthBounds() {
    return super.getSszLengthBounds();
  }

  @Override
  public int getSszSize(TreeNode node) {
    return super.getSszSize(node) + serializeBitvectorSchema.getSszFixedPartSize();
  }

  public int getParentSszSize(TreeNode node) {
    return super.getSszSize(node);
  }

  private static List<? extends NamedSchema<?>> createAllSchemas(
      List<? extends NamedIndexedSchema<?>> childrenSchemas, int maxFieldCount) {
    Map<Integer, NamedSchema<?>> childrenSchemasByIndex =
        childrenSchemas.stream()
            .collect(
                Collectors.toUnmodifiableMap(
                    NamedIndexedSchema::index, NamedIndexedSchema::toNamedSchema));
    checkArgument(childrenSchemasByIndex.size() == childrenSchemas.size());
    checkArgument(childrenSchemasByIndex.keySet().stream().allMatch(i -> i < maxFieldCount));

    return IntStream.range(0, maxFieldCount)
        .mapToObj(
            idx ->
                childrenSchemasByIndex.getOrDefault(
                    idx, NamedSchema.of("__none_" + idx, SszPrimitiveSchemas.NONE_SCHEMA)))
        .toList();
  }
}
