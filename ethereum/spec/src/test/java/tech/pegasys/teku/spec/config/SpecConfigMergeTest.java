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
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class SpecConfigMergeTest {
  private final Spec spec = TestSpecFactory.createMinimalAltair();

  @Test
  public void equals_mainnet() {
    SpecConfigMerge configA = SpecConfigLoader.loadConfig("mainnet").toVersionMerge().orElseThrow();
    SpecConfigMerge configB = SpecConfigLoader.loadConfig("mainnet").toVersionMerge().orElseThrow();

    assertThat(configA).isEqualTo(configB);
    assertThat(configA.hashCode()).isEqualTo(configB.hashCode());
  }

  @Test
  public void equals_sameRandomValues() {
    SpecConfigAltair altair =
        SpecConfigLoader.loadConfig("mainnet").toVersionAltair().orElseThrow();
    ;
    SpecConfigMerge configA = createRandomMergeConfig(altair, 1);
    SpecConfigMerge configB = createRandomMergeConfig(altair, 1);

    assertThat(configA).isEqualTo(configB);
    assertThat(configA.hashCode()).isEqualTo(configB.hashCode());
  }

  @Test
  public void equals_differentRandomValues() {
    SpecConfigAltair altair =
        SpecConfigLoader.loadConfig("mainnet").toVersionAltair().orElseThrow();
    SpecConfigMerge configA = createRandomMergeConfig(altair, 1);
    SpecConfigMerge configB = createRandomMergeConfig(altair, 2);

    assertThat(configA).isNotEqualTo(configB);
    assertThat(configA.hashCode()).isNotEqualTo(configB.hashCode());
  }

  @Test
  public void equals_altairConfigDiffer() {
    SpecConfigAltair altairA =
        SpecConfigLoader.loadConfig("mainnet", b -> {}).toVersionAltair().orElseThrow();
    SpecConfigAltair altairB =
        SpecConfigLoader.loadConfig(
                "mainnet",
                b ->
                    b.altairBuilder(ab -> ab.syncCommitteeSize(altairA.getSyncCommitteeSize() + 4)))
            .toVersionAltair()
            .orElseThrow();

    SpecConfigMerge configA = createRandomMergeConfig(altairA, 1);
    SpecConfigMerge configB = createRandomMergeConfig(altairB, 1);

    assertThat(configA).isNotEqualTo(configB);
    assertThat(configA.hashCode()).isNotEqualTo(configB.hashCode());
  }

  private SpecConfigMerge createRandomMergeConfig(
      final SpecConfigAltair altairConfig, final int seed) {
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(seed, spec);

    return new SpecConfigMerge(
        altairConfig,
        dataStructureUtil.randomBytes4(),
        dataStructureUtil.randomUInt64(),
        dataStructureUtil.randomUInt64(),
        dataStructureUtil.randomPositiveInt(),
        dataStructureUtil.randomPositiveInt(),
        dataStructureUtil.randomPositiveInt(),
        dataStructureUtil.randomPositiveInt(),
        dataStructureUtil.randomPositiveInt(),
        dataStructureUtil.randomPositiveInt(),
        dataStructureUtil.randomUInt256(),
        dataStructureUtil.randomBytes32(),
        dataStructureUtil.randomUInt64());
  }
}
