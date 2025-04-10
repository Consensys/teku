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
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class SpecConfigFuluTest {
  private final Spec spec = TestSpecFactory.createMinimalElectra();

  @Test
  public void equals_mainnet() {
    final SpecConfigFulu configA =
        SpecConfigLoader.loadConfig("mainnet").specConfig().toVersionFulu().orElseThrow();
    final SpecConfigFulu configB =
        SpecConfigLoader.loadConfig("mainnet").specConfig().toVersionFulu().orElseThrow();

    assertThat(configA).isEqualTo(configB);
    assertThat(configA.hashCode()).isEqualTo(configB.hashCode());
  }

  @Test
  public void equals_sameRandomValues() {
    final SpecConfigElectra specConfigElectra =
        SpecConfigLoader.loadConfig("mainnet").specConfig().toVersionElectra().orElseThrow();
    final SpecConfigFulu configA = createRandomFuluConfig(specConfigElectra, 1);
    final SpecConfigFulu configB = createRandomFuluConfig(specConfigElectra, 1);

    assertThat(configA).isEqualTo(configB);
    assertThat(configA.hashCode()).isEqualTo(configB.hashCode());
  }

  @Test
  public void equals_differentRandomValues() {
    final SpecConfigElectra specConfigElectra =
        SpecConfigLoader.loadConfig("mainnet").specConfig().toVersionElectra().orElseThrow();
    final SpecConfigFulu configA = createRandomFuluConfig(specConfigElectra, 1);
    final SpecConfigFulu configB = createRandomFuluConfig(specConfigElectra, 2);

    assertThat(configA).isNotEqualTo(configB);
    assertThat(configA.hashCode()).isNotEqualTo(configB.hashCode());
  }

  @Test
  @SuppressWarnings("unchecked")
  public void shouldOverrideBlobRelatedValuesValues() {
    final SpecConfigAndParent<SpecConfigFulu> specConfigAndParent =
        (SpecConfigAndParent<SpecConfigFulu>)
            SpecConfigLoader.loadConfig(
                "mainnet",
                b -> {
                  b.denebBuilder(eb -> eb.maxBlobsPerBlock(4));

                  b.electraBuilder(eb -> eb.maxBlobsPerBlockElectra(8));
                  b.fuluBuilder(fb -> fb.maxBlobsPerBlockFulu(12));
                });

    final SpecConfigDeneb denebConfig =
        specConfigAndParent.forMilestone(SpecMilestone.DENEB).toVersionDeneb().orElseThrow();

    final SpecConfigElectra electraConfig =
        specConfigAndParent.forMilestone(SpecMilestone.ELECTRA).toVersionElectra().orElseThrow();

    final SpecConfigFulu fuluConfig =
        specConfigAndParent.forMilestone(SpecMilestone.FULU).toVersionFulu().orElseThrow();

    assertThat(denebConfig.getMaxBlobsPerBlock()).isEqualTo(4);
    assertThat(electraConfig.getMaxBlobsPerBlock()).isEqualTo(8);
    assertThat(fuluConfig.getMaxBlobsPerBlock()).isEqualTo(12);
  }

  @Test
  public void equals_electraConfigDiffer() {
    final SpecConfigElectra electraA =
        SpecConfigLoader.loadConfig("mainnet").specConfig().toVersionElectra().orElseThrow();
    final SpecConfigElectra electraB =
        SpecConfigLoader.loadConfig(
                "mainnet",
                b ->
                    b.electraBuilder(
                        eb -> eb.maxBlobsPerBlockElectra(electraA.getMaxBlobsPerBlock() + 4)))
            .specConfig()
            .toVersionElectra()
            .orElseThrow();

    final SpecConfigFulu configA = createRandomFuluConfig(electraA, 1);
    final SpecConfigFulu configB = createRandomFuluConfig(electraB, 1);

    assertThat(configA).isNotEqualTo(configB);
    assertThat(configA.hashCode()).isNotEqualTo(configB.hashCode());
  }

  @Test
  public void mainnetShouldHave12MaxBlobs() {
    final SpecConfigFulu specConfigFulu =
        SpecConfigLoader.loadConfig("mainnet").specConfig().toVersionFulu().orElseThrow();
    assertThat(specConfigFulu.getMaxBlobsPerBlock()).isEqualTo(12);
  }

  private SpecConfigFulu createRandomFuluConfig(
      final SpecConfigElectra electraConfig, final int seed) {
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(seed, spec);

    return new SpecConfigFuluImpl(
        electraConfig,
        dataStructureUtil.randomBytes4(),
        dataStructureUtil.randomUInt64(999_999),
        dataStructureUtil.randomUInt64(8192),
        dataStructureUtil.randomUInt64(8192),
        dataStructureUtil.randomUInt64(8192),
        dataStructureUtil.randomPositiveInt(134217728),
        dataStructureUtil.randomPositiveInt(134217728),
        dataStructureUtil.randomPositiveInt(262144),
        dataStructureUtil.randomPositiveInt(4096),
        dataStructureUtil.randomPositiveInt(4096),
        dataStructureUtil.randomPositiveInt(4096),
        dataStructureUtil.randomPositiveInt(8192),
        dataStructureUtil.randomPositiveInt(8192),
        dataStructureUtil.randomPositiveInt(8192),
        dataStructureUtil.randomUInt64(32000000000L)) {};
  }
}
