/*
 * Copyright Consensys Software Inc., 2025
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

package tech.pegasys.teku.spec.logic.versions.fulu.helpers;

import java.util.Optional;
import tech.pegasys.teku.spec.config.SpecConfigElectra;
import tech.pegasys.teku.spec.config.SpecConfigFulu;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;
import tech.pegasys.teku.spec.logic.common.helpers.Predicates;
import tech.pegasys.teku.spec.logic.versions.electra.helpers.MiscHelpersElectra;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsElectra;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsFulu;

public class MiscHelpersFulu extends MiscHelpersElectra {

  public static MiscHelpersFulu required(final MiscHelpers miscHelpers) {
    return miscHelpers
        .toVersionFulu()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Expected Fulu misc helpers but got: "
                        + miscHelpers.getClass().getSimpleName()));
  }

  @SuppressWarnings("unused")
  private final SpecConfigFulu specConfigFulu;

  @SuppressWarnings("unused")
  private final Predicates predicates;

  @SuppressWarnings("unused")
  private final SchemaDefinitionsFulu schemaDefinitions;

  public MiscHelpersFulu(
      final SpecConfigFulu specConfigFulu,
      final Predicates predicates,
      final SchemaDefinitions schemaDefinitions) {
    super(
        SpecConfigElectra.required(specConfigFulu),
        predicates,
        SchemaDefinitionsElectra.required(schemaDefinitions));
    this.predicates = predicates;
    this.specConfigFulu = specConfigFulu;
    this.schemaDefinitions = SchemaDefinitionsFulu.required(schemaDefinitions);
  }

  @Override
  public Optional<MiscHelpersFulu> toVersionFulu() {
    return Optional.of(this);
  }
}
