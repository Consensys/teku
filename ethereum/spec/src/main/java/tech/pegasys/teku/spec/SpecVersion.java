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

public class SpecVersion extends DelegatingSpecLogic {
  private final SpecConfig constants;
  private final SchemaDefinitions schemaDefinitions;

  private SpecVersion(
      final SpecConfig constants,
      final SchemaDefinitions schemaDefinitions,
      final SpecLogic specLogic) {
    super(specLogic);
    this.constants = constants;
    this.schemaDefinitions = schemaDefinitions;
  }

  public static SpecVersion createPhase0(final SpecConfig specConfig) {
    final SchemaDefinitions schemaDefinitions = new SchemaDefinitionsPhase0(specConfig);
    final SpecLogic specLogic = SpecLogicPhase0.create(specConfig, schemaDefinitions);
    return new SpecVersion(specConfig, schemaDefinitions, specLogic);
  }

  public static SpecVersion createAltair(final SpecConfigAltair specConstants) {
    final SchemaDefinitions schemaDefinitions = new SchemaDefinitionsAltair(specConstants);
    final SpecLogic specLogic = SpecLogicAltair.create(specConstants, schemaDefinitions);
    return new SpecVersion(specConstants, schemaDefinitions, specLogic);
  }

  public SpecConfig getConstants() {
    return constants;
  }

  public SchemaDefinitions getSchemaDefinitions() {
    return schemaDefinitions;
  }
}
