/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.spec;

import java.util.Optional;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigAltair;
import tech.pegasys.teku.spec.config.SpecConfigMerge;
import tech.pegasys.teku.spec.logic.DelegatingSpecLogic;
import tech.pegasys.teku.spec.logic.SpecLogic;
import tech.pegasys.teku.spec.logic.versions.altair.SpecLogicAltair;
import tech.pegasys.teku.spec.logic.versions.merge.SpecLogicMerge;
import tech.pegasys.teku.spec.logic.versions.phase0.SpecLogicPhase0;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsAltair;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsMerge;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsPhase0;

public class SpecVersion extends DelegatingSpecLogic {
  private final SpecMilestone milestone;
  private final SpecConfig config;
  private final SchemaDefinitions schemaDefinitions;

  private SpecVersion(
      final SpecMilestone milestone,
      final SpecConfig config,
      final SchemaDefinitions schemaDefinitions,
      final SpecLogic specLogic) {
    super(specLogic);
    this.milestone = milestone;
    this.config = config;
    this.schemaDefinitions = schemaDefinitions;
  }

  static Optional<SpecVersion> create(final SpecMilestone milestone, final SpecConfig specConfig) {
    switch (milestone) {
      case PHASE0:
        return Optional.of(createPhase0(specConfig));
      case ALTAIR:
        return specConfig.toVersionAltair().map(SpecVersion::createAltair);
      case MERGE:
        return specConfig.toVersionMerge().map(SpecVersion::createMerge);
      default:
        throw new UnsupportedOperationException("Unknown milestone requested: " + milestone);
    }
  }

  static SpecVersion createPhase0(final SpecConfig specConfig) {
    final SchemaDefinitions schemaDefinitions = new SchemaDefinitionsPhase0(specConfig);
    final SpecLogic specLogic = SpecLogicPhase0.create(specConfig, schemaDefinitions);
    return new SpecVersion(SpecMilestone.PHASE0, specConfig, schemaDefinitions, specLogic);
  }

  static SpecVersion createAltair(final SpecConfigAltair specConfig) {
    final SchemaDefinitionsAltair schemaDefinitions = new SchemaDefinitionsAltair(specConfig);
    final SpecLogic specLogic = SpecLogicAltair.create(specConfig, schemaDefinitions);
    return new SpecVersion(SpecMilestone.ALTAIR, specConfig, schemaDefinitions, specLogic);
  }

  static SpecVersion createMerge(final SpecConfigMerge specConfig) {
    final SchemaDefinitionsMerge schemaDefinitions = new SchemaDefinitionsMerge(specConfig);
    final SpecLogic specLogic = SpecLogicMerge.create(specConfig, schemaDefinitions);
    return new SpecVersion(SpecMilestone.MERGE, specConfig, schemaDefinitions, specLogic);
  }

  public SpecMilestone getMilestone() {
    return milestone;
  }

  public SpecConfig getConfig() {
    return config;
  }

  public int getSlotsPerEpoch() {
    return config.getSlotsPerEpoch();
  }

  public int getSlotsPerHistoricalRoot() {
    return config.getSlotsPerHistoricalRoot();
  }

  public SchemaDefinitions getSchemaDefinitions() {
    return schemaDefinitions;
  }
}
