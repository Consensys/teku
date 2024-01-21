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

import java.util.Optional;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class SpecConfigDenebTest {
  private final Spec spec = TestSpecFactory.createMinimalCapella();

  @Test
  public void equals_mainnet() {
    SpecConfigDeneb configA = SpecConfigLoader.loadConfig("mainnet").toVersionDeneb().orElseThrow();
    SpecConfigDeneb configB = SpecConfigLoader.loadConfig("mainnet").toVersionDeneb().orElseThrow();

    assertThat(configA).isEqualTo(configB);
    assertThat(configA.hashCode()).isEqualTo(configB.hashCode());
  }

  @Test
  public void equals_sameRandomValues() {
    SpecConfigElectra specConfigElectra =
        SpecConfigLoader.loadConfig("mainnet").toVersionElectra().orElseThrow();
    SpecConfigDeneb configA = createRandomDenebConfig(specConfigElectra, 1);
    SpecConfigDeneb configB = createRandomDenebConfig(specConfigElectra, 1);

    assertThat(configA).isEqualTo(configB);
    assertThat(configA.hashCode()).isEqualTo(configB.hashCode());
  }

  @Test
  public void equals_differentRandomValues() {
    SpecConfigElectra specConfigElectra =
        SpecConfigLoader.loadConfig("mainnet").toVersionElectra().orElseThrow();
    SpecConfigDeneb configA = createRandomDenebConfig(specConfigElectra, 1);
    SpecConfigDeneb configB = createRandomDenebConfig(specConfigElectra, 2);

    assertThat(configA).isNotEqualTo(configB);
    assertThat(configA.hashCode()).isNotEqualTo(configB.hashCode());
  }

  @Test
  public void equals_capellaConfigDiffer() {
    SpecConfigElectra electraA =
        SpecConfigLoader.loadConfig("mainnet").toVersionElectra().orElseThrow();
    SpecConfigElectra electraB =
        SpecConfigLoader.loadConfig(
                "mainnet",
                b ->
                    b.capellaBuilder(
                        cb ->
                            cb.maxWithdrawalsPerPayload(
                                electraA.getMaxWithdrawalsPerPayload() + 4)))
            .toVersionElectra()
            .orElseThrow();

    SpecConfigDeneb configA = createRandomDenebConfig(electraA, 1);
    SpecConfigDeneb configB = createRandomDenebConfig(electraB, 1);

    assertThat(configA).isNotEqualTo(configB);
    assertThat(configA.hashCode()).isNotEqualTo(configB.hashCode());
  }

  private SpecConfigDeneb createRandomDenebConfig(
      final SpecConfigElectra electraConfig, final int seed) {
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(seed, spec);

    return new SpecConfigDenebImpl(
        electraConfig,
        dataStructureUtil.randomBytes4(),
        dataStructureUtil.randomUInt64(),
        dataStructureUtil.randomPositiveInt(),
        dataStructureUtil.randomPositiveInt(),
        dataStructureUtil.randomPositiveInt(),
        dataStructureUtil.randomPositiveInt(),
        dataStructureUtil.randomPositiveInt(),
        dataStructureUtil.randomPositiveInt(),
        dataStructureUtil.randomPositiveInt(),
        dataStructureUtil.randomPositiveInt(),
        dataStructureUtil.randomPositiveInt(),
        Optional.empty());
  }
}
