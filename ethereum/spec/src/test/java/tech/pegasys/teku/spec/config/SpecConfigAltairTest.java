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

package tech.pegasys.teku.spec.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class SpecConfigAltairTest {
  private final Spec spec = SpecFactory.createMinimal();

  @Test
  public void equals_mainnet() {
    SpecConfigAltair constantsA =
        SpecConfigLoader.loadConstants("mainnet").toVersionAltair().orElseThrow();
    SpecConfigAltair constantsB =
        SpecConfigLoader.loadConstants("mainnet").toVersionAltair().orElseThrow();

    assertThat(constantsA).isEqualTo(constantsB);
    assertThat(constantsA.hashCode()).isEqualTo(constantsB.hashCode());
  }

  @Test
  public void equals_sameRandomValues() {
    SpecConfig phase0 = SpecConfigLoader.loadConstants("mainnet");
    SpecConfigAltair constantsA = createRandomAltairConstants(phase0, 1);
    SpecConfigAltair constantsB = createRandomAltairConstants(phase0, 1);

    assertThat(constantsA).isEqualTo(constantsB);
    assertThat(constantsA.hashCode()).isEqualTo(constantsB.hashCode());
  }

  @Test
  public void equals_differentRandomValues() {
    SpecConfig phase0 = SpecConfigLoader.loadConstants("mainnet");
    SpecConfigAltair constantsA = createRandomAltairConstants(phase0, 1);
    SpecConfigAltair constantsB = createRandomAltairConstants(phase0, 2);

    assertThat(constantsA).isNotEqualTo(constantsB);
    assertThat(constantsA.hashCode()).isNotEqualTo(constantsB.hashCode());
  }

  @Test
  public void equals_phase0ConstantsDiffer() {
    SpecConfig phase0A = TestConfigLoader.loadConstants("swift", b -> {});
    SpecConfig phase0B =
        TestConfigLoader.loadConstants("swift", b -> b.maxValidatorsPerCommittee(1));

    SpecConfigAltair constantsA = createRandomAltairConstants(phase0A, 1);
    SpecConfigAltair constantsB = createRandomAltairConstants(phase0B, 1);

    assertThat(constantsA).isNotEqualTo(constantsB);
    assertThat(constantsA.hashCode()).isNotEqualTo(constantsB.hashCode());
  }

  private SpecConfigAltair createRandomAltairConstants(
      final SpecConfig phase0Constants, final int seed) {
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(seed, spec);

    return new SpecConfigAltair(
        phase0Constants,
        dataStructureUtil.randomUInt64(),
        dataStructureUtil.randomPositiveInt(),
        dataStructureUtil.randomPositiveInt(),
        dataStructureUtil.randomPositiveInt(),
        dataStructureUtil.randomPositiveInt(),
        dataStructureUtil.randomPositiveInt(),
        dataStructureUtil.randomPositiveInt(),
        dataStructureUtil.randomBytes4(),
        dataStructureUtil.randomBytes4(),
        dataStructureUtil.randomPositiveInt(),
        dataStructureUtil.randomPositiveInt(),
        dataStructureUtil.randomPositiveInt());
  }
}
