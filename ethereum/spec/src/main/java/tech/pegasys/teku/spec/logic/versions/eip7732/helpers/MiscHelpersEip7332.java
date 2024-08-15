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

package tech.pegasys.teku.spec.logic.versions.eip7732.helpers;

import java.util.Optional;
import tech.pegasys.teku.spec.config.SpecConfigEip7732;
import tech.pegasys.teku.spec.config.SpecConfigElectra;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;
import tech.pegasys.teku.spec.logic.versions.electra.helpers.MiscHelpersElectra;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsElectra;

public class MiscHelpersEip7332 extends MiscHelpersElectra {
  public MiscHelpersEip7332(
      final SpecConfigEip7732 specConfig,
      final PredicatesEip7732 predicates,
      final SchemaDefinitions schemaDefinitions) {
    super(
        SpecConfigElectra.required(specConfig),
        predicates,
        SchemaDefinitionsElectra.required(schemaDefinitions));
  }

  public static MiscHelpersEip7332 required(final MiscHelpers miscHelpers) {
    return miscHelpers
        .toVersionEip7332()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Expected Eip7332 misc helpers but got: "
                        + miscHelpers.getClass().getSimpleName()));
  }

  public byte removeFlag(final byte participationFlags, final int flagIndex) {
    final byte flag = (byte) (1 << flagIndex);
    return (byte) (participationFlags & ~flag);
  }

  @Override
  public Optional<MiscHelpersEip7332> toVersionEip7332() {
    return Optional.of(this);
  }
}
