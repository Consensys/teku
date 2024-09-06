/*
 * Copyright Consensys Software Inc., 2022
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
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class SpecConfigEip7732Test {
  private final Spec spec = TestSpecFactory.createMinimalElectra();

  @Test
  public void equals_mainnet() {
    final SpecConfigEip7732 configA =
        SpecConfigLoader.loadConfig("mainnet").toVersionEip7732().orElseThrow();
    final SpecConfigEip7732 configB =
        SpecConfigLoader.loadConfig("mainnet").toVersionEip7732().orElseThrow();

    assertThat(configA).isEqualTo(configB);
    assertThat(configA.hashCode()).isEqualTo(configB.hashCode());
  }

  @Test
  public void equals_sameRandomValues() {
    final SpecConfigElectra specConfigElectra =
        SpecConfigLoader.loadConfig("mainnet").toVersionElectra().orElseThrow();
    final SpecConfigEip7732 configA = createRandomEip7732Config(specConfigElectra, 1);
    final SpecConfigEip7732 configB = createRandomEip7732Config(specConfigElectra, 1);

    assertThat(configA).isEqualTo(configB);
    assertThat(configA.hashCode()).isEqualTo(configB.hashCode());
  }

  @Test
  public void equals_differentRandomValues() {
    final SpecConfigElectra specConfigElectra =
        SpecConfigLoader.loadConfig("mainnet").toVersionElectra().orElseThrow();
    final SpecConfigEip7732 configA = createRandomEip7732Config(specConfigElectra, 1);
    final SpecConfigEip7732 configB = createRandomEip7732Config(specConfigElectra, 2);

    assertThat(configA).isNotEqualTo(configB);
    assertThat(configA.hashCode()).isNotEqualTo(configB.hashCode());
  }

  @Test
  public void equals_electraConfigDiffer() {
    final SpecConfigElectra electraA =
        SpecConfigLoader.loadConfig("mainnet").toVersionElectra().orElseThrow();
    final SpecConfigElectra electraB =
        SpecConfigLoader.loadConfig(
                "mainnet",
                b ->
                    b.electraBuilder(
                        eb -> eb.maxAttestationsElectra(electraA.getMaxAttestationsElectra() + 4)))
            .toVersionElectra()
            .orElseThrow();

    final SpecConfigEip7732 configA = createRandomEip7732Config(electraA, 1);
    final SpecConfigEip7732 configB = createRandomEip7732Config(electraB, 1);

    assertThat(configA).isNotEqualTo(configB);
    assertThat(configA.hashCode()).isNotEqualTo(configB.hashCode());
  }

  private SpecConfigEip7732 createRandomEip7732Config(
      final SpecConfigElectra electraConfig, final int seed) {
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(seed, spec);

    return new SpecConfigEip7732Impl(
        electraConfig,
        dataStructureUtil.randomBytes4(),
        dataStructureUtil.randomUInt64(999_999),
        dataStructureUtil.randomPositiveInt(2),
        dataStructureUtil.randomPositiveInt(4),
        dataStructureUtil.randomPositiveInt(13),
        dataStructureUtil.randomPositiveInt(128)) {};
  }
}
