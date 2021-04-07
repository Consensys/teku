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

import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigAltair;
import tech.pegasys.teku.spec.logic.DelegatingSpecLogic;
import tech.pegasys.teku.spec.logic.SpecLogic;
import tech.pegasys.teku.spec.logic.versions.altair.SpecLogicAltair;
import tech.pegasys.teku.spec.logic.versions.phase0.SpecLogicPhase0;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsAltair;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsPhase0;
import tech.pegasys.teku.ssz.type.Bytes4;

public class SpecVersion extends DelegatingSpecLogic {
  private final SpecConfig config;
  private final SchemaDefinitions schemaDefinitions;

  private SpecVersion(
      final SpecConfig config,
      final SchemaDefinitions schemaDefinitions,
      final SpecLogic specLogic) {
    super(specLogic);
    this.config = config;
    this.schemaDefinitions = schemaDefinitions;
  }

  public static SpecVersion createForFork(final Bytes4 fork, final SpecConfig specConfig) {
    if (specConfig.getGenesisForkVersion().equals(fork)) {
      return createPhase0(specConfig);
    } else if (specConfig
        .toVersionAltair()
        .map(altairConfig -> altairConfig.getAltairForkVersion().equals(fork))
        .orElse(false)) {
      return createAltair(SpecConfigAltair.required(specConfig));
    } else {
      throw new IllegalArgumentException("Unsupported fork: " + fork);
    }
  }

  public static SpecVersion createPhase0(final SpecConfig specConfig) {
    final SchemaDefinitions schemaDefinitions = new SchemaDefinitionsPhase0(specConfig);
    final SpecLogic specLogic = SpecLogicPhase0.create(specConfig, schemaDefinitions);
    return new SpecVersion(specConfig, schemaDefinitions, specLogic);
  }

  public static SpecVersion createAltair(final SpecConfigAltair specConfig) {
    final SchemaDefinitionsAltair schemaDefinitions = new SchemaDefinitionsAltair(specConfig);
    final SpecLogic specLogic = SpecLogicAltair.create(specConfig, schemaDefinitions);
    return new SpecVersion(specConfig, schemaDefinitions, specLogic);
  }

  public SpecConfig getConfig() {
    return config;
  }

  public SchemaDefinitions getSchemaDefinitions() {
    return schemaDefinitions;
  }
}
