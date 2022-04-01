/*
 * Copyright 2022 ConsenSys AG.
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

package tech.pegasys.teku.beaconrestapi;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;

public class SchemaDefinitionCache {
  private final Spec spec;
  private final Map<SpecMilestone, SchemaDefinitions> schemas = new ConcurrentHashMap<>();

  public SchemaDefinitionCache(final Spec spec) {
    this.spec = spec;
  }

  public SchemaDefinitions getSchemaDefinition(final SpecMilestone milestone) {
    return schemas.computeIfAbsent(milestone, this::createSchemaDefinition);
  }

  public final SpecMilestone milestoneAtSlot(final UInt64 slot) {
    return spec.atSlot(slot).getMilestone();
  }

  private SchemaDefinitions createSchemaDefinition(final SpecMilestone milestone) {
    final SpecVersion specVersion = spec.forMilestone(milestone);
    if (specVersion != null) {
      return specVersion.getSchemaDefinitions();
    }
    return SpecVersion.create(milestone, spec.getGenesisSpecConfig())
        .orElseThrow()
        .getSchemaDefinitions();
  }
}
