/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.spec.schemas;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.SpecVersion;

public class SchemaDefinitionCache {
  private final Spec spec;
  private final Map<SpecMilestone, SchemaDefinitions> schemas = new ConcurrentHashMap<>();

  public SchemaDefinitionCache(final Spec spec) {
    this.spec = spec;
  }

  // TODO: Optional
  public SchemaDefinitions getSchemaDefinition(final SpecMilestone milestone) {
    return schemas.computeIfAbsent(
        milestone,
        milestone1 -> {
          final Optional<SchemaDefinitions> maybeSchemaDefinition =
              createSchemaDefinition(milestone1);
          return maybeSchemaDefinition.orElse(null);
        });
  }

  public SchemaDefinitions atSlot(final UInt64 slot) {
    return getSchemaDefinition(milestoneAtSlot(slot));
  }

  public final SpecMilestone milestoneAtSlot(final UInt64 slot) {
    return spec.atSlot(slot).getMilestone();
  }

  private Optional<SchemaDefinitions> createSchemaDefinition(final SpecMilestone milestone) {
    final SpecVersion specVersion = spec.forMilestone(milestone);
    if (specVersion != null) {
      return Optional.of(specVersion.getSchemaDefinitions());
    }
    // FIXME: genesis spec config??
    return SpecVersion.create(milestone, spec.getGenesisSpecConfig())
        .map(SpecVersion::getSchemaDefinitions);
  }

  public List<SpecMilestone> getSupportedMilestones() {
    return spec.getForkSchedule().getSupportedMilestones();
  }
}
