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

package tech.pegasys.teku.spec.logic.versions.eip7594.helpers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.logic.common.helpers.Predicates;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsEip7594;

public class MiscHelpersEip7594Test {

  private final Spec spec =
      TestSpecFactory.createMinimalEip7594(
          builder ->
              builder.eip7594Builder(
                  eip7594Builder -> eip7594Builder.numberOfColumns(128).samplesPerSlot(16)));
  private final Predicates predicates = new Predicates(spec.getGenesisSpecConfig());
  private final SchemaDefinitionsEip7594 schemaDefinitionsEip7594 =
      SchemaDefinitionsEip7594.required(spec.getGenesisSchemaDefinitions());
  private final MiscHelpersEip7594 miscHelpersEip7594 =
      new MiscHelpersEip7594(
          spec.getGenesisSpecConfig().toVersionEip7594().orElseThrow(),
          predicates,
          schemaDefinitionsEip7594);

  @ParameterizedTest(name = "{0} allowed failure(s)")
  @MethodSource("getExtendedSampleCountFixtures")
  public void getExtendedSampleCountReturnsCorrectValues(
      final int allowedFailures, final int numberOfSamples) {
    assertThat(miscHelpersEip7594.getExtendedSampleCount(UInt64.valueOf(allowedFailures)))
        .isEqualTo(UInt64.valueOf(numberOfSamples));
  }

  @Test
  public void getExtendedSampleCountShouldThrowWhenAllowedFailuresTooBig() {
    assertThatThrownBy(() -> miscHelpersEip7594.getExtendedSampleCount(UInt64.valueOf(65)))
        .isOfAnyClassIn(IllegalArgumentException.class)
        .hasMessageStartingWith(
            "Allowed failures (65) should be less than half of columns number (128)");
  }

  static Stream<Arguments> getExtendedSampleCountFixtures() throws IOException {
    return Stream.of(
        Arguments.of(0, 16),
        Arguments.of(1, 20),
        Arguments.of(2, 24),
        Arguments.of(3, 27),
        Arguments.of(4, 29),
        Arguments.of(5, 32),
        Arguments.of(6, 35),
        Arguments.of(7, 37),
        Arguments.of(8, 40),
        Arguments.of(64, 128));
  }
}
