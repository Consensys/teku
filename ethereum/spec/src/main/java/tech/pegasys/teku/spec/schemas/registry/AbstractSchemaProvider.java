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

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BiFunction;
import org.apache.commons.lang3.tuple.Triple;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.schemas.registry.SchemaTypes.SchemaId;

abstract class AbstractSchemaProvider<T> implements SchemaProvider<T> {
  private final NavigableMap<SpecMilestone, BiFunction<SchemaRegistry, SpecConfig, T>>
      milestoneToSchemaCreator = new TreeMap<>();
  private final SchemaId<T> schemaId;

  protected AbstractSchemaProvider(
      final SchemaId<T> schemaId,
      Triple<SpecMilestone, Optional<SpecMilestone>, BiFunction<SchemaRegistry, SpecConfig, T>>...
          milestoneCreators) {
    this.schemaId = schemaId;
    final List<
            Triple<
                SpecMilestone, Optional<SpecMilestone>, BiFunction<SchemaRegistry, SpecConfig, T>>>
        creatorsList = Arrays.stream(milestoneCreators).toList();
    checkArgument(!creatorsList.isEmpty(), "There should be at least 1 creator");
    checkArgument(
        creatorsList.size()
            == new HashSet<>(creatorsList.stream().map(Triple::getLeft).toList()).size(),
        "There shouldn't be duplicated milestones in boundary start for creator, but were: %s",
        creatorsList.stream().map(Triple::getLeft).toList());
    BiFunction<SchemaRegistry, SpecConfig, T> lastCreator = null;
    // TODO: add optional right bound SpecMilestone checks and logic
    for (SpecMilestone specMilestone : SpecMilestone.values()) {
      if (creatorsList.getFirst().getLeft() == specMilestone) {
        lastCreator = creatorsList.removeFirst().getRight();
        milestoneToSchemaCreator.put(specMilestone, lastCreator);
      } else {
        milestoneToSchemaCreator.put(specMilestone, lastCreator);
      }

      if (!creatorsList.isEmpty()) {
        throw new IllegalArgumentException("Overlapping creators detected: " + creatorsList);
      }
    }
  }

  // TODO: not needed
  @Override
  public SpecMilestone getEffectiveMilestone(final SpecMilestone milestone) {
    return milestone;
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

  static <T>
      Triple<SpecMilestone, Optional<SpecMilestone>, BiFunction<SchemaRegistry, SpecConfig, T>>
          milestoneSchema(
              SpecMilestone milestone, BiFunction<SchemaRegistry, SpecConfig, T> creationSchema) {
    return Triple.of(milestone, Optional.empty(), creationSchema);
  }

  static <T>
      Triple<SpecMilestone, Optional<SpecMilestone>, BiFunction<SchemaRegistry, SpecConfig, T>>
          milestoneSchema(
              SpecMilestone milestone,
              SpecMilestone untilMilestone,
              BiFunction<SchemaRegistry, SpecConfig, T> creationSchema) {
    return Triple.of(milestone, Optional.of(untilMilestone), creationSchema);
  }

  protected T createSchema(
      SchemaRegistry registry, SpecMilestone effectiveMilestone, SpecConfig specConfig) {
    final BiFunction<SchemaRegistry, SpecConfig, T> maybeSchemaCreator =
        milestoneToSchemaCreator.get(effectiveMilestone);
    if (maybeSchemaCreator == null) {
      throw new IllegalArgumentException(
          "It is not supposed to create a specific version for " + effectiveMilestone);
    }
    return maybeSchemaCreator.apply(registry, specConfig);
  }

  @Override
  public Set<SpecMilestone> getSupportedMilestones() {
    return milestoneToSchemaCreator.navigableKeySet();
  }
}
