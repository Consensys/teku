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

public class SpecConfigElectraTest {
  private final Spec spec = TestSpecFactory.createMinimalDeneb();

  @Test
  public void equals_mainnet() {
    final SpecConfigElectra configA =
        SpecConfigLoader.loadConfig("mainnet").toVersionElectra().orElseThrow();
    final SpecConfigElectra configB =
        SpecConfigLoader.loadConfig("mainnet").toVersionElectra().orElseThrow();

    assertThat(configA).isEqualTo(configB);
    assertThat(configA.hashCode()).isEqualTo(configB.hashCode());
  }

  @Test
  public void equals_sameRandomValues() {
    final SpecConfigDeneb specConfigDeneb =
        SpecConfigLoader.loadConfig("mainnet").toVersionDeneb().orElseThrow();
    final SpecConfigElectra configA = createRandomElectraConfig(specConfigDeneb, 1);
    final SpecConfigElectra configB = createRandomElectraConfig(specConfigDeneb, 1);

    assertThat(configA).isEqualTo(configB);
    assertThat(configA.hashCode()).isEqualTo(configB.hashCode());
  }

  @Test
  public void equals_differentRandomValues() {
    final SpecConfigDeneb specConfigDeneb =
        SpecConfigLoader.loadConfig("mainnet").toVersionDeneb().orElseThrow();
    final SpecConfigElectra configA = createRandomElectraConfig(specConfigDeneb, 1);
    final SpecConfigElectra configB = createRandomElectraConfig(specConfigDeneb, 2);

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

    final SpecConfigElectra configA = createRandomElectraConfig(denebA, 1);
    final SpecConfigElectra configB = createRandomElectraConfig(denebB, 1);

    assertThat(configA).isNotEqualTo(configB);
    assertThat(configA.hashCode()).isNotEqualTo(configB.hashCode());
  }

  private SpecConfigElectra createRandomElectraConfig(
      final SpecConfigDeneb denebConfig, final int seed) {
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(seed, spec);

    return new SpecConfigElectraImpl(
        denebConfig,
        dataStructureUtil.randomBytes4(),
        dataStructureUtil.randomUInt64(999_999),
        dataStructureUtil.randomUInt64(128000000000L),
        dataStructureUtil.randomUInt64(32000000000L),
        dataStructureUtil.randomUInt64(2048000000000L),
        dataStructureUtil.randomPositiveInt(134217728),
        dataStructureUtil.randomPositiveInt(134217728),
        dataStructureUtil.randomPositiveInt(262144),
        dataStructureUtil.randomPositiveInt(4096),
        dataStructureUtil.randomPositiveInt(4096),
        dataStructureUtil.randomPositiveInt(1),
        dataStructureUtil.randomPositiveInt(8),
        dataStructureUtil.randomPositiveInt(1),
        dataStructureUtil.randomPositiveInt(8192),
        dataStructureUtil.randomPositiveInt(16),
        dataStructureUtil.randomPositiveInt(8),
        dataStructureUtil.randomPositiveInt(16)) {};
  }
}
