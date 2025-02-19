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

package tech.pegasys.teku.infrastructure.ssz;

import it.unimi.dsi.fastutil.Pair;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.stream.Stream;
import tech.pegasys.teku.infrastructure.ssz.impl.SszProfileImpl;
import tech.pegasys.teku.infrastructure.ssz.schema.SszProfileSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszStableContainerSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.impl.AbstractSszProfileSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

public class RandomSszProfileSchemaGenerator {
  private final Random random;
  private final SszStableContainerSchema<?> stableContainerSchema;

  private final int minRequiredFields;
  private final int maxRequiredFields;
  private final int minOptionalFields;
  private final int maxOptionalFields;

  public RandomSszProfileSchemaGenerator(final SszStableContainerSchema<?> stableContainerSchema) {
    this(
        new Random(1),
        stableContainerSchema,
        0,
        stableContainerSchema.getFieldsCount(),
        0,
        stableContainerSchema.getFieldsCount());
  }

  public RandomSszProfileSchemaGenerator(
      final Random random,
      final SszStableContainerSchema<?> stableContainerSchema,
      final int minRequiredFields,
      final int maxRequiredFields,
      final int minOptionalFields,
      final int maxOptionalFields) {
    this.stableContainerSchema = stableContainerSchema;
    this.random = random;

    this.minRequiredFields = minRequiredFields;
    this.maxRequiredFields = maxRequiredFields;
    this.minOptionalFields = minOptionalFields;
    this.maxOptionalFields = maxOptionalFields;
  }

  public Stream<SszProfileSchema<?>> randomProfileSchemasStream() {
    return generateDistinctRandomRequiredOptionalSets()
        .map(sets -> generateProfile(sets.left(), sets.right()));
  }

  public RandomSszProfileSchemaGenerator withMinRequiredFields(final int minRequiredFields) {
    return new RandomSszProfileSchemaGenerator(
        random,
        stableContainerSchema,
        minRequiredFields,
        maxRequiredFields,
        minOptionalFields,
        maxOptionalFields);
  }

  public RandomSszProfileSchemaGenerator withMaxRequiredFields(final int maxRequiredFields) {
    return new RandomSszProfileSchemaGenerator(
        random,
        stableContainerSchema,
        minRequiredFields,
        maxRequiredFields,
        minOptionalFields,
        maxOptionalFields);
  }

  public RandomSszProfileSchemaGenerator withMinOptionalFields(final int minOptionalFields) {
    return new RandomSszProfileSchemaGenerator(
        random,
        stableContainerSchema,
        minRequiredFields,
        maxRequiredFields,
        minOptionalFields,
        maxOptionalFields);
  }

  public RandomSszProfileSchemaGenerator withMaxOptionalFields(final int maxOptionalFields) {
    return new RandomSszProfileSchemaGenerator(
        random,
        stableContainerSchema,
        minRequiredFields,
        maxRequiredFields,
        minOptionalFields,
        maxOptionalFields);
  }

  private Stream<Pair<Set<Integer>, Set<Integer>>> generateDistinctRandomRequiredOptionalSets() {
    return Stream.generate(
            () -> {
              final Set<Integer> requiredFields = new HashSet<>();
              final Set<Integer> optionalFields = new HashSet<>();

              generateRandomNonIntersectingSets(requiredFields, optionalFields);
              return Pair.of(requiredFields, optionalFields);
            })
        // let's have unique pairs with at least an element as required or optional
        .distinct()
        .filter(pair -> !(pair.left().isEmpty() && pair.right().isEmpty()));
  }

  private void generateRandomNonIntersectingSets(
      final Set<Integer> requiredFields, final Set<Integer> optionalFields) {

    final int rangeSize = stableContainerSchema.getFieldsCount();

    // Ensure the total minimum size does not exceed the range size
    if (minRequiredFields + minOptionalFields > rangeSize) {
      throw new IllegalArgumentException(
          "The total minimum size of the subsets exceeds the range size.");
    }

    // Randomly determine the sizes of the two subsets within the given constraints
    final int requiredFieldsSize =
        (maxRequiredFields > 0)
            ? minRequiredFields
                + random.nextInt(Math.min(maxRequiredFields, rangeSize) - minRequiredFields + 1)
            : 0;
    final int optionalFieldsSize =
        (maxOptionalFields > 0)
            ? minOptionalFields
                + random.nextInt(
                    Math.min(maxOptionalFields, rangeSize - requiredFieldsSize)
                        - minOptionalFields
                        + 1)
            : 0;

    final Set<Integer> allNumbers = new HashSet<>();
    for (int i = 0; i < rangeSize; i++) {
      allNumbers.add(i);
    }

    while (requiredFields.size() < requiredFieldsSize) {
      final int number = getRandomElement(allNumbers, random);
      requiredFields.add(number);
      allNumbers.remove(number);
    }

    while (optionalFields.size() < optionalFieldsSize) {
      final int number = getRandomElement(allNumbers, random);
      optionalFields.add(number);
      allNumbers.remove(number);
    }
  }

  private static int getRandomElement(final Set<Integer> set, final Random random) {
    final int index = random.nextInt(set.size());
    return set.stream().skip(index).findFirst().orElseThrow(IllegalStateException::new);
  }

  private SszProfileSchema<?> generateProfile(
      final Set<Integer> requiredFieldIndices, final Set<Integer> optionalFieldIndices) {

    return new AbstractSszProfileSchema<>(
        stableContainerSchema.getContainerName()
            + "-Profile-Req"
            + requiredFieldIndices
            + "-Opt"
            + optionalFieldIndices,
        stableContainerSchema,
        requiredFieldIndices,
        optionalFieldIndices) {

      @Override
      public SszProfileImpl createFromBackingNode(final TreeNode node) {
        return new SszProfileImpl(this, node);
      }
    };
  }
}
