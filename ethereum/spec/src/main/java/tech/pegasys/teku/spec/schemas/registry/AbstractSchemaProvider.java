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

import java.util.EnumMap;
import java.util.Map;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.schemas.registry.SchemaTypes.SchemaId;

abstract class AbstractSchemaProvider<T> implements SchemaProvider<T> {
  private final Map<SpecMilestone, SpecMilestone> milestoneToEffectiveMilestone =
      new EnumMap<>(SpecMilestone.class);
  private final SchemaId<T> schemaId;

  protected AbstractSchemaProvider(final SchemaId<T> schemaId) {
    this.schemaId = schemaId;
  }

  protected void addMilestoneMapping(
      final SpecMilestone milestone, final SpecMilestone untilMilestone) {
    checkArgument(
        untilMilestone.isGreaterThan(milestone),
        "%s must be earlier than %s",
        milestone,
        untilMilestone);

    SpecMilestone currentMilestone = untilMilestone;
    while (!currentMilestone.equals(milestone)) {

      checkIfAlreadyMapped(currentMilestone);

      milestoneToEffectiveMilestone.put(currentMilestone, milestone);

      currentMilestone = currentMilestone.getPreviousMilestone();
    }

    checkIfAlreadyMapped(currentMilestone);
  }

  private void checkIfAlreadyMapped(final SpecMilestone milestone) {
    if (milestoneToEffectiveMilestone.containsKey(milestone)) {
      throw new IllegalArgumentException(
          String.format(
              "Milestone %s is already mapped to %s",
              milestone, milestoneToEffectiveMilestone.get(milestone)));
    }
  }

  @Override
  public SpecMilestone getEffectiveMilestone(final SpecMilestone milestone) {
    return milestoneToEffectiveMilestone.getOrDefault(milestone, milestone);
  }

  @Override
  public T getSchema(final SchemaRegistry registry) {
    final SpecMilestone milestone = registry.getMilestone();
    final SpecMilestone effectiveMilestone = getEffectiveMilestone(milestone);
    return createSchema(registry, effectiveMilestone, registry.getSpecConfig());
  }

  @Override
  public SchemaId<T> getSchemaId() {
    return schemaId;
  }

  protected abstract T createSchema(
      SchemaRegistry registry, SpecMilestone effectiveMilestone, SpecConfig specConfig);
}
