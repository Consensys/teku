/*
 * Copyright ConsenSys Software Inc., 2022
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

public class SpecConfigEip4844Test {
  private final Spec spec = TestSpecFactory.createMinimalCapella();

  @Test
  public void equals_mainnet() {
    SpecConfigEip4844 configA =
        SpecConfigLoader.loadConfig("mainnet").toVersionEip4844().orElseThrow();
    SpecConfigEip4844 configB =
        SpecConfigLoader.loadConfig("mainnet").toVersionEip4844().orElseThrow();

    assertThat(configA).isEqualTo(configB);
    assertThat(configA.hashCode()).isEqualTo(configB.hashCode());
  }

  @Test
  public void equals_sameRandomValues() {
    SpecConfigCapella specConfigCapella =
        SpecConfigLoader.loadConfig("mainnet").toVersionCapella().orElseThrow();
    SpecConfigEip4844 configA = createRandomEip4844Config(specConfigCapella, 1);
    SpecConfigEip4844 configB = createRandomEip4844Config(specConfigCapella, 1);

    assertThat(configA).isEqualTo(configB);
    assertThat(configA.hashCode()).isEqualTo(configB.hashCode());
  }

  @Test
  public void equals_differentRandomValues() {
    SpecConfigCapella specConfigCapella =
        SpecConfigLoader.loadConfig("mainnet").toVersionCapella().orElseThrow();
    SpecConfigEip4844 configA = createRandomEip4844Config(specConfigCapella, 1);
    SpecConfigEip4844 configB = createRandomEip4844Config(specConfigCapella, 2);

    assertThat(configA).isNotEqualTo(configB);
    assertThat(configA.hashCode()).isNotEqualTo(configB.hashCode());
  }

  @Test
  public void equals_capellaConfigDiffer() {
    SpecConfigCapella capellaA =
        SpecConfigLoader.loadConfig("mainnet").toVersionCapella().orElseThrow();
    SpecConfigCapella capellaB =
        SpecConfigLoader.loadConfig(
                "mainnet",
                b ->
                    b.capellaBuilder(
                        cb ->
                            cb.maxWithdrawalsPerPayload(
                                capellaA.getMaxWithdrawalsPerPayload() + 4)))
            .toVersionCapella()
            .orElseThrow();

    SpecConfigEip4844 configA = createRandomEip4844Config(capellaA, 1);
    SpecConfigEip4844 configB = createRandomEip4844Config(capellaB, 1);

    assertThat(configA).isNotEqualTo(configB);
    assertThat(configA.hashCode()).isNotEqualTo(configB.hashCode());
  }

  private SpecConfigEip4844 createRandomEip4844Config(
      final SpecConfigCapella capellaConfig, final int seed) {
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(seed, spec);

    return new SpecConfigEip4844Impl(
        capellaConfig,
        dataStructureUtil.randomBytes4(),
        dataStructureUtil.randomUInt64(),
        dataStructureUtil.randomPositiveInt(),
        dataStructureUtil.randomPositiveInt(),
        Optional.empty(),
        true);
  }
}
