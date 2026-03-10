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

package tech.pegasys.teku.spec.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class SpecConfigElectraTest {
  private final Spec spec = TestSpecFactory.createMinimalDeneb();

  @Test
  public void equals_mainnet() {
    final SpecConfigElectra configA =
        SpecConfigLoader.loadConfig("mainnet").specConfig().toVersionElectra().orElseThrow();
    final SpecConfigElectra configB =
        SpecConfigLoader.loadConfig("mainnet").specConfig().toVersionElectra().orElseThrow();

    assertThat(configA).isEqualTo(configB);
    assertThat(configA.hashCode()).isEqualTo(configB.hashCode());
  }

  @Test
  public void equals_sameRandomValues() {
    final SpecConfigDeneb specConfigDeneb =
        SpecConfigLoader.loadConfig("mainnet").specConfig().toVersionDeneb().orElseThrow();
    final SpecConfigElectra configA = createRandomElectraConfig(specConfigDeneb, 1);
    final SpecConfigElectra configB = createRandomElectraConfig(specConfigDeneb, 1);

    assertThat(configA).isEqualTo(configB);
    assertThat(configA.hashCode()).isEqualTo(configB.hashCode());
  }

  @Test
  public void equals_differentRandomValues() {
    final SpecConfigDeneb specConfigDeneb =
        SpecConfigLoader.loadConfig("mainnet").specConfig().toVersionDeneb().orElseThrow();
    final SpecConfigElectra configA = createRandomElectraConfig(specConfigDeneb, 1);
    final SpecConfigElectra configB = createRandomElectraConfig(specConfigDeneb, 2);

    assertThat(configA).isNotEqualTo(configB);
    assertThat(configA.hashCode()).isNotEqualTo(configB.hashCode());
  }

  @Test
  @SuppressWarnings("unchecked")
  public void shouldOverrideBlobRelatedValuesValues() {
    final SpecConfigAndParent<SpecConfigElectra> specConfigAndParent =
        (SpecConfigAndParent<SpecConfigElectra>)
            SpecConfigLoader.loadConfig(
                "mainnet",
                b -> {
                  b.denebBuilder(
                      eb ->
                          eb.maxBlobsPerBlock(4)
                              // target blobs is calculated in deneb
                              .blobSidecarSubnetCount(8)
                              .maxRequestBlobSidecars(16));

                  b.electraBuilder(
                      eb ->
                          eb.maxBlobsPerBlockElectra(8)
                              .blobSidecarSubnetCountElectra(10)
                              .maxRequestBlobSidecarsElectra(13));
                });

    final SpecConfigDeneb denebConfig =
        specConfigAndParent.forMilestone(SpecMilestone.DENEB).toVersionDeneb().orElseThrow();

    final SpecConfigDeneb electraConfig =
        specConfigAndParent.forMilestone(SpecMilestone.ELECTRA).toVersionDeneb().orElseThrow();

    assertThat(denebConfig.getMaxBlobsPerBlock()).isEqualTo(4);
    assertThat(denebConfig.getBlobSidecarSubnetCount()).isEqualTo(8);
    assertThat(denebConfig.getMaxRequestBlobSidecars()).isEqualTo(16);

    assertThat(electraConfig.getMaxBlobsPerBlock()).isEqualTo(8);
    assertThat(electraConfig.getBlobSidecarSubnetCount()).isEqualTo(10);
    assertThat(electraConfig.getMaxRequestBlobSidecars()).isEqualTo(13);
  }

  @Test
  public void equals_denebConfigDiffer() {
    final SpecConfigDeneb denebA =
        SpecConfigLoader.loadConfig("mainnet").specConfig().toVersionDeneb().orElseThrow();
    final SpecConfigDeneb denebB =
        SpecConfigLoader.loadConfig(
                "mainnet",
                b -> b.denebBuilder(db -> db.maxBlobsPerBlock(denebA.getMaxBlobsPerBlock() + 4)))
            .specConfig()
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
        dataStructureUtil.randomPositiveInt(16),
        dataStructureUtil.randomPositiveInt(8),
        dataStructureUtil.randomPositiveInt(1024),
        dataStructureUtil.randomPositiveInt(8)) {};
  }
}
