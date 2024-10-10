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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BiFunction;
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
    validateCreators(schemaProviderCreators);
    final List<SchemaProviderCreator<T>> creatorsList =
        new ArrayList<>(Arrays.asList(schemaProviderCreators));

    SchemaProviderCreator<T> lastCreator = null;
    for (final SpecMilestone milestone : SpecMilestone.values()) {
      if (!creatorsList.isEmpty() && creatorsList.getFirst().milestone == milestone) {
        lastCreator = creatorsList.removeFirst();
      }

      if (lastCreator != null) {
        milestoneToSchemaCreator.put(milestone, lastCreator);

        final boolean untilSpecReached =
            lastCreator
                .untilMilestone
                .map(untilMilestone -> untilMilestone == milestone)
                .orElse(false);

        if (untilSpecReached) {
          break;
        }
      }
    }
  }

  @SafeVarargs
  private void validateCreators(final SchemaProviderCreator<T>... schemaProviderCreators) {
    checkArgument(
        schemaProviderCreators.length > 0, "There should be at least 1 creator for %s", schemaId);
    for (int i = 0; i < schemaProviderCreators.length; i++) {
      final SchemaProviderCreator<T> currentCreator = schemaProviderCreators[i];
      if (i > 0) {
        checkArgument(
            currentCreator.milestone.isGreaterThan(schemaProviderCreators[i - 1].milestone),
            "Creator's milestones must be in order for %s",
            schemaId);
      }
      final boolean isLast = i == schemaProviderCreators.length - 1;
      if (isLast) {
        checkArgument(
            currentCreator.untilMilestone.isEmpty()
                || currentCreator.untilMilestone.get().isGreaterThan(currentCreator.milestone),
            "Last creator untilMilestone must be greater than milestone in %s",
            schemaId);
      } else {
        checkArgument(
            currentCreator.untilMilestone.isEmpty(),
            "Only last creator is allowed to use until for %s",
            schemaId);
      }
    }
  }

  @Override
  public SpecMilestone getEffectiveMilestone(final SpecMilestone milestone) {
    return getSchemaCreator(milestone).milestone;
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
    return new SchemaProviderCreator<>(milestone, Optional.of(untilMilestone), creationSchema);
  }

  protected T createSchema(
      final SchemaRegistry registry,
      final SpecMilestone effectiveMilestone,
      final SpecConfig specConfig) {
    return getSchemaCreator(effectiveMilestone).creator.apply(registry, specConfig);
  }

  private SchemaProviderCreator<T> getSchemaCreator(final SpecMilestone milestone) {
    final SchemaProviderCreator<T> maybeSchemaCreator = milestoneToSchemaCreator.get(milestone);
    if (maybeSchemaCreator == null) {
      throw new IllegalArgumentException(
          "It is not supposed to create a specific version for " + milestone);
    }
    return maybeSchemaCreator;
  }

  @Override
  public Set<SpecMilestone> getSupportedMilestones() {
    return milestoneToSchemaCreator.keySet();
  }

  protected record SchemaProviderCreator<T>(
      SpecMilestone milestone,
      Optional<SpecMilestone> untilMilestone,
      BiFunction<SchemaRegistry, SpecConfig, T> creator) {
    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("milestone", milestone)
          .add("untilMilestone", untilMilestone)
          .toString();
    }
  }
}
