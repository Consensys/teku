/*
 * Copyright Consensys Software Inc., 2026
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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;

public class MiscHelpersGloasTest {

  private final Spec spec = TestSpecFactory.createMinimalGloas();

  private final MiscHelpersGloas miscHelpers =
      MiscHelpersGloas.required(spec.getGenesisSpec().miscHelpers());

  @Test
  public void roundTrip_convertBetweenBuilderIndexAndValidatorIndex() {
    final UInt64 builderIndex = UInt64.valueOf(42);
    final UInt64 validatorIndex = miscHelpers.convertBuilderIndexToValidatorIndex(builderIndex);
    assertThat(miscHelpers.convertValidatorIndexToBuilderIndex(validatorIndex))
        .isEqualTo(builderIndex);
  }
}
