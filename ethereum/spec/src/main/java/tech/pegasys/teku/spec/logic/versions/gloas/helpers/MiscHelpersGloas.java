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

package tech.pegasys.teku.spec.logic.versions.gloas.helpers;

import java.util.Optional;
import tech.pegasys.teku.spec.config.SpecConfigGloas;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;
import tech.pegasys.teku.spec.logic.versions.fulu.helpers.MiscHelpersFulu;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;

public class MiscHelpersGloas extends MiscHelpersFulu {

  public static MiscHelpersGloas required(final MiscHelpers miscHelpers) {
    return miscHelpers
        .toVersionGloas()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Expected Gloas misc helpers but got: "
                        + miscHelpers.getClass().getSimpleName()));
  }

  public MiscHelpersGloas(
      final SpecConfigGloas specConfig,
      final PredicatesGloas predicates,
      final SchemaDefinitionsGloas schemaDefinitions) {
    super(specConfig, predicates, schemaDefinitions);
  }

  @Override
  public Optional<MiscHelpersGloas> toVersionGloas() {
    return Optional.of(this);
  }
}
