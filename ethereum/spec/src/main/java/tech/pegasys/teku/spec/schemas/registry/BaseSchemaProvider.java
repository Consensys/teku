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
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BiFunction;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.schemas.registry.SchemaTypes.SchemaId;

class BaseSchemaProvider<T> implements SchemaProvider<T> {
  private final TreeMap<SpecMilestone, SchemaProviderCreator<T>> milestoneToSchemaCreator =
      new TreeMap<>();
  private final SchemaId<T> schemaId;

  private BaseSchemaProvider(
      final SchemaId<T> schemaId,
      final List<SchemaProviderCreator<T>> schemaProviderCreators,
      final SpecMilestone untilMilestone,
      final boolean isConstant) {
    this.schemaId = schemaId;
    final List<SchemaProviderCreator<T>> creatorsList = new ArrayList<>(schemaProviderCreators);

    SchemaProviderCreator<T> lastCreator = null;

    for (final SpecMilestone milestone : SpecMilestone.getMilestonesUpTo(untilMilestone)) {
      if (!creatorsList.isEmpty() && creatorsList.getFirst().milestone == milestone) {
        lastCreator = creatorsList.removeFirst();
      }

      if (lastCreator != null) {
        milestoneToSchemaCreator.put(
            milestone, isConstant ? lastCreator : lastCreator.withMilestone(milestone));
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
      SpecMilestone milestone, BiFunction<SchemaRegistry, SpecConfig, T> creator) {

    private SchemaProviderCreator<T> withMilestone(final SpecMilestone milestone) {
      return new SchemaProviderCreator<>(milestone, creator);
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this).add("milestone", milestone).toString();
    }
  }

  static <T> Builder<T> constantProviderBuilder(final SchemaId<T> schemaId) {
    return new Builder<>(schemaId, true);
  }

  static <T> Builder<T> variableProviderBuilder(final SchemaId<T> schemaId) {
    return new Builder<>(schemaId, false);
  }

  static class Builder<T> {
    private final SchemaId<T> schemaId;
    private final boolean isConstant;
    final List<SchemaProviderCreator<T>> schemaProviderCreators = new ArrayList<>();
    private SpecMilestone untilMilestone = SpecMilestone.getHighestMilestone();

    private Builder(final SchemaId<T> schemaId, final boolean isConstant) {
      this.schemaId = schemaId;
      this.isConstant = isConstant;
    }

    public Builder<T> withCreator(
        final SpecMilestone milestone,
        final BiFunction<SchemaRegistry, SpecConfig, T> creationSchema) {
      checkArgument(
          schemaProviderCreators.isEmpty()
              || milestone.isGreaterThan(schemaProviderCreators.getLast().milestone),
          "Creator's milestones must added in strict ascending order for %s",
          schemaId);

      schemaProviderCreators.add(new SchemaProviderCreator<>(milestone, creationSchema));
      return this;
    }

    public Builder<T> until(final SpecMilestone untilMilestone) {
      this.untilMilestone = untilMilestone;
      return this;
    }

    public BaseSchemaProvider<T> build() {
      checkArgument(
          !schemaProviderCreators.isEmpty(), "There should be at least 1 creator for %s", schemaId);

      checkArgument(
          untilMilestone.isGreaterThanOrEqualTo(schemaProviderCreators.getLast().milestone),
          "until must be greater or equal than last creator milestone in %s",
          schemaId);
      return new BaseSchemaProvider<>(schemaId, schemaProviderCreators, untilMilestone, isConstant);
    }
  }
}
