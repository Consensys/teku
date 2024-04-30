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

public class SpecConfigEip7594Test {
  private final Spec spec = TestSpecFactory.createMinimalDeneb();

  @Test
  public void equals_mainnet() {
    final SpecConfigEip7594 configA =
        SpecConfigLoader.loadConfig("mainnet").toVersionEip7594().orElseThrow();
    final SpecConfigEip7594 configB =
        SpecConfigLoader.loadConfig("mainnet").toVersionEip7594().orElseThrow();

    assertThat(configA).isEqualTo(configB);
    assertThat(configA.hashCode()).isEqualTo(configB.hashCode());
  }

  @Test
  public void equals_sameRandomValues() {
    final SpecConfigDeneb specConfigDeneb =
        SpecConfigLoader.loadConfig("mainnet").toVersionDeneb().orElseThrow();
    final SpecConfigEip7594 configA = createRandomEip7594Config(specConfigDeneb, 1);
    final SpecConfigEip7594 configB = createRandomEip7594Config(specConfigDeneb, 1);

    assertThat(configA).isEqualTo(configB);
    assertThat(configA.hashCode()).isEqualTo(configB.hashCode());
  }

  @Test
  public void equals_differentRandomValues() {
    final SpecConfigDeneb specConfigDeneb =
        SpecConfigLoader.loadConfig("mainnet").toVersionDeneb().orElseThrow();
    final SpecConfigEip7594 configA = createRandomEip7594Config(specConfigDeneb, 1);
    final SpecConfigEip7594 configB = createRandomEip7594Config(specConfigDeneb, 2);

    assertThat(configA).isNotEqualTo(configB);
    assertThat(configA.hashCode()).isNotEqualTo(configB.hashCode());
  }

  @Test
  public void equals_denebConfigDiffer() {
    final SpecConfigDeneb denebA =
        SpecConfigLoader.loadConfig("mainnet").toVersionDeneb().orElseThrow();
    final SpecConfigDeneb denebB =
        SpecConfigLoader.loadConfig(
                "mainnet",
                b -> b.denebBuilder(db -> db.maxBlobsPerBlock(denebA.getMaxBlobsPerBlock() + 4)))
            .toVersionDeneb()
            .orElseThrow();

    final SpecConfigEip7594 configA = createRandomEip7594Config(denebA, 1);
    final SpecConfigEip7594 configB = createRandomEip7594Config(denebB, 1);

    assertThat(configA).isNotEqualTo(configB);
    assertThat(configA.hashCode()).isNotEqualTo(configB.hashCode());
  }

  private SpecConfigEip7594 createRandomEip7594Config(
      final SpecConfigDeneb denebConfig, final int seed) {
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(seed, spec);

    return new SpecConfigEip7594Impl(
        denebConfig,
        dataStructureUtil.randomBytes4(),
        dataStructureUtil.randomUInt64(999_999),
        dataStructureUtil.randomUInt64(64),
        dataStructureUtil.randomUInt64(8192),
        dataStructureUtil.randomUInt64(10),
        dataStructureUtil.randomPositiveInt(64),
        dataStructureUtil.randomPositiveInt(64),
        dataStructureUtil.randomPositiveInt(4096),
        dataStructureUtil.randomPositiveInt(16384)) {};
  }
}
