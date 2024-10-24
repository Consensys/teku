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

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.Comparator;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;
import tech.pegasys.teku.infrastructure.ssz.SszProfile;
import tech.pegasys.teku.infrastructure.ssz.SszStableContainer;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.ssz.schema.SszProfileSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszStableContainerSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBitvectorSchema;
import tech.pegasys.teku.infrastructure.ssz.sos.SszLengthBounds;
import tech.pegasys.teku.infrastructure.ssz.sos.SszReader;
import tech.pegasys.teku.infrastructure.ssz.sos.SszWriter;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

/**
 * The Profile overrides the stable container logic by:
 *
 * <ol>
 *   <li>Skipping active bitvector serialization\deserialization when there are no optional fields
 *   <li>In case of allowed optional fields, the active bitvector will only represents the optional
 *       fields. Required fields are not represented
 * </ol>
 */
public abstract class AbstractSszProfileSchema<C extends SszProfile>
    extends AbstractSszStableContainerBaseSchema<C> implements SszProfileSchema<C> {

  private final IntList optionalFieldIndexToSchemaIndexCache;
  private final int[] schemaIndexToOptionalFieldIndexCache;
  private final Set<Integer> optionalFieldIndices;
  private final Optional<SszBitvectorSchema<SszBitvector>> optionalFieldsSchema;
  private final SszStableContainerSchema<? extends SszStableContainer> stableContainer;

  public AbstractSszProfileSchema(
      final String name,
      final SszStableContainerSchema<? extends SszStableContainer> stableContainerSchema,
      final Set<Integer> requiredFieldIndices,
      final Set<Integer> optionalFieldIndices) {
    super(
        name,
        stableContainerSchema.getChildrenNamedSchemas(),
        requiredFieldIndices,
        optionalFieldIndices,
        stableContainerSchema.getMaxFieldCount());
    this.optionalFieldIndices = optionalFieldIndices;
    if (optionalFieldIndices.isEmpty()) {
      this.optionalFieldsSchema = Optional.empty();
      this.schemaIndexToOptionalFieldIndexCache = new int[0];
      this.optionalFieldIndexToSchemaIndexCache = IntList.of();

    } else {
      // we need create a dedicated bitvector schema. The optional fields bitvector will represent
      // which of the optional fields are being used (i.e. first bit represent the first optional
      // field in order of declaration, the second bit the second one and so on)
      this.optionalFieldsSchema =
          Optional.of(SszBitvectorSchema.create(optionalFieldIndices.size()));

      // to support serialization and deserialization we need two caches be able to quickly get
      // schema field index of a given optional field index and vice versa.
      //
      // Example:
      //
      // optional index to schema index (optionalFieldIndexToSchemaIndexCache):
      // 0->2
      // 1->4
      // 2->5
      //
      // schema index to optional index (schemaIndexToOptionalFieldIndexCache):
      // 0->null
      // 1->null
      // 2->0
      // 3->null
      // 4->1
      // 5->1

      this.optionalFieldIndexToSchemaIndexCache =
          IntList.of(
              optionalFieldIndices.stream()
                  .sorted(Comparator.naturalOrder())
                  .mapToInt(i -> i)
                  .toArray());

      this.schemaIndexToOptionalFieldIndexCache =
          new int[optionalFieldIndexToSchemaIndexCache.getInt(optionalFieldIndices.size() - 1) + 1];
      optionalFieldIndices.stream()
          .sorted(Comparator.naturalOrder())
          .forEach(
              i ->
                  schemaIndexToOptionalFieldIndexCache[i] =
                      optionalFieldIndexToSchemaIndexCache.indexOf((int) i));
    }

    this.stableContainer = stableContainerSchema;
  }

  @Override
  public SszStableContainerSchema<? extends SszStableContainer> getStableContainerSchema() {
    return stableContainer;
  }

  @Override
  SszLengthBounds computeActiveFieldsSszLengthBounds() {
    return optionalFieldsSchema
        .map(SszBitvectorSchema::getSszLengthBounds)
        .orElse(SszLengthBounds.ZERO);
  }

  @Override
  int getSszActiveFieldsSize(final TreeNode node) {
    return optionalFieldsSchema.map(schema -> schema.getSszSize(node)).orElse(0);
  }

  @Override
  int sszSerializeActiveFields(final SszBitvector activeFieldsBitvector, final SszWriter writer) {
    if (optionalFieldsSchema.isEmpty()) {
      // without optional fields, a profile won't serialize the bitvector
      return 0;
    }
    final IntList optionalFieldsBits = new IntArrayList(optionalFieldIndices.size());
    optionalFieldIndices.stream()
        .filter(activeFieldsBitvector::getBit)
        .mapToInt(i -> schemaIndexToOptionalFieldIndexCache[i])
        .forEach(optionalFieldsBits::add);

    return optionalFieldsSchema
        .get()
        .sszSerializeTree(
            optionalFieldsSchema.get().ofBits(optionalFieldsBits).getBackingNode(), writer);
  }

  @Override
  SszBitvector sszDeserializeActiveFieldsTree(final SszReader reader) {
    if (optionalFieldsSchema.isEmpty()) {
      // without optional fields the active fields corresponds to the required fields of the schema
      return getRequiredFields();
    }
    final SszReader optionalFieldsReader =
        reader.slice(optionalFieldsSchema.get().getSszFixedPartSize());
    final SszBitvector optionalFields =
        optionalFieldsSchema.get().sszDeserialize(optionalFieldsReader);
    return getActiveFieldsSchema()
        .ofBits(
            IntStream.concat(
                    getRequiredFields().streamAllSetBits(),
                    optionalFields
                        .streamAllSetBits()
                        .map(optionalFieldIndexToSchemaIndexCache::getInt))
                .toArray());
  }
}
