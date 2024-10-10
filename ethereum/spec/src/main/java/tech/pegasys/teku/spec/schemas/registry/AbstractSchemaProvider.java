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

package tech.pegasys.teku.spec.schemas.registry;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.MoreObjects;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BiFunction;
import java.util.stream.Stream;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.schemas.registry.SchemaTypes.SchemaId;

abstract class AbstractSchemaProvider<T> implements SchemaProvider<T> {
  private final TreeMap<SpecMilestone, SchemaProviderCreator<T>> milestoneToSchemaCreator =
      new TreeMap<>();
  private final SchemaId<T> schemaId;

  @SafeVarargs
  protected AbstractSchemaProvider(
      final SchemaId<T> schemaId, final SchemaProviderCreator<T>... schemaProviderCreators) {
    this.schemaId = schemaId;
    final List<SchemaProviderCreator<T>> creatorsList = Arrays.asList(schemaProviderCreators);
    checkArgument(!creatorsList.isEmpty(), "There should be at least 1 creator");

    Arrays.stream(schemaProviderCreators)
        .forEach(
            creator ->
                creator
                    .streamSupporterMilestones()
                    .forEach(milestone -> putCreatorAtMilestone(milestone, creator)));
  }

  private void putCreatorAtMilestone(
      final SpecMilestone milestone, final SchemaProviderCreator<T> creator) {
    if (!milestoneToSchemaCreator.isEmpty()) {
      final SpecMilestone lastMilestone = milestoneToSchemaCreator.lastKey();
      if (lastMilestone.isGreaterThanOrEqualTo(milestone)) {
        throw new IllegalArgumentException(
            "Milestones ascending ordering error: from " + lastMilestone + " to " + milestone);
      }

      if (lastMilestone != milestone.getPreviousMilestone()) {
        throw new IllegalArgumentException(
            "Milestones gap detected: from "
                + lastMilestone
                + " to "
                + milestone.getPreviousMilestone());
      }
    }

    if (milestoneToSchemaCreator.put(milestone, creator) != null) {
      // this can't happen due to preceded checks
      throw new IllegalArgumentException("Overlapping creators detected");
    }
  }

  @Override
  public SpecMilestone getEffectiveMilestone(final SpecMilestone milestone) {
    final SchemaProviderCreator<T> maybeSchemaCreator = milestoneToSchemaCreator.get(milestone);
    if (maybeSchemaCreator == null) {
      throw new IllegalArgumentException(
          "It is not supposed to create a specific version for " + milestone);
    }

    return maybeSchemaCreator.milestone;
  }

  @Override
  public T getSchema(final SchemaRegistry registry) {
    final SpecMilestone milestone = registry.getMilestone();
    return createSchema(registry, milestone, registry.getSpecConfig());
  }

  @Override
  public SchemaId<T> getSchemaId() {
    return schemaId;
  }

  static <T> SchemaProviderCreator<T> schemaCreator(
      final SpecMilestone milestone,
      final BiFunction<SchemaRegistry, SpecConfig, T> creationSchema) {
    return new SchemaProviderCreator<>(milestone, Optional.empty(), creationSchema);
  }

  static <T> SchemaProviderCreator<T> schemaCreator(
      final SpecMilestone milestone,
      final SpecMilestone untilMilestone,
      final BiFunction<SchemaRegistry, SpecConfig, T> creationSchema) {
    checkArgument(
        untilMilestone.isGreaterThan(milestone), "untilMilestone should be greater than milestone");
    return new SchemaProviderCreator<>(milestone, Optional.of(untilMilestone), creationSchema);
  }

  protected T createSchema(
      SchemaRegistry registry, SpecMilestone effectiveMilestone, SpecConfig specConfig) {
    final SchemaProviderCreator<T> maybeSchemaCreator =
        milestoneToSchemaCreator.get(effectiveMilestone);
    if (maybeSchemaCreator == null) {
      throw new IllegalArgumentException(
          "It is not supposed to create a specific version for " + effectiveMilestone);
    }
    return maybeSchemaCreator.creator.apply(registry, specConfig);
  }

  @Override
  public Set<SpecMilestone> getSupportedMilestones() {
    return milestoneToSchemaCreator.keySet();
  }

  protected record SchemaProviderCreator<T>(
      SpecMilestone milestone,
      Optional<SpecMilestone> untilMilestone,
      BiFunction<SchemaRegistry, SpecConfig, T> creator) {
    Stream<SpecMilestone> streamSupporterMilestones() {
      if (untilMilestone.isEmpty()) {
        return Stream.of(milestone);
      }
      return SpecMilestone.getMilestonesUpTo(untilMilestone.get()).stream()
          .filter(m -> m.isGreaterThanOrEqualTo(milestone));
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("milestone", milestone)
          .add("untilMilestone", untilMilestone)
          .toString();
    }
  }
}
