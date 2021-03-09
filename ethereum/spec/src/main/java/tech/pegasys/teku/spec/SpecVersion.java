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

import tech.pegasys.teku.spec.constants.SpecConstants;
import tech.pegasys.teku.spec.logic.DelegatingSpecLogic;
import tech.pegasys.teku.spec.logic.SpecLogic;
import tech.pegasys.teku.spec.logic.versions.phase0.SpecLogicPhase0;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsPhase0;

public class SpecVersion extends DelegatingSpecLogic {
  private final SpecConstants constants;
  private final SchemaDefinitions schemaDefinitions;

  private SpecVersion(
      final SpecConstants constants,
      final SchemaDefinitions schemaDefinitions,
      final SpecLogic specLogic) {
    super(specLogic);
    this.constants = constants;
    this.schemaDefinitions = schemaDefinitions;
  }

  public static SpecVersion createPhase0(final SpecConstants specConstants) {
    final SchemaDefinitions schemaDefinitions = new SchemaDefinitionsPhase0(specConstants);
    final SpecLogic specLogic = new SpecLogicPhase0(specConstants, schemaDefinitions);
    return new SpecVersion(specConstants, schemaDefinitions, specLogic);
  }

  public SpecConstants getConstants() {
    return constants;
  }

  public SchemaDefinitions getSchemaDefinitions() {
    return schemaDefinitions;
  }
}
