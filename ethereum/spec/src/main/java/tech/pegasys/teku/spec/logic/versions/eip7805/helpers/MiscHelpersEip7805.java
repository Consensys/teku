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

package tech.pegasys.teku.spec.logic.versions.eip7805.helpers;

import java.util.Optional;
import tech.pegasys.teku.spec.config.SpecConfigEip7805;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.MiscHelpersGloas;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsEip7805;

public class MiscHelpersEip7805 extends MiscHelpersGloas {

  public MiscHelpersEip7805(
      final SpecConfigEip7805 specConfig,
      final PredicatesEip7805 predicates,
      final SchemaDefinitionsEip7805 schemaDefinitions) {
    super(specConfig, predicates, schemaDefinitions);
  }

  public static MiscHelpersEip7805 required(final MiscHelpers miscHelpers) {
    return miscHelpers
        .toVersionEip7805()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Expected Eip7805 misc helpers but got: "
                        + miscHelpers.getClass().getSimpleName()));
  }

  @Override
  public Optional<MiscHelpersEip7805> toVersionEip7805() {
    return Optional.of(this);
  }
}
