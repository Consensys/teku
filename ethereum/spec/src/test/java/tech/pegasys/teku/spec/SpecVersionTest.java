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

package tech.pegasys.teku.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigAltair;
import tech.pegasys.teku.spec.config.SpecConfigLoader;
import tech.pegasys.teku.spec.networks.Eth2Network;
import tech.pegasys.teku.ssz.type.Bytes4;

class SpecVersionTest {

  private final SpecConfig specConfig =
      SpecConfigLoader.loadConfig(Eth2Network.MINIMAL.configName());

  @Test
  void shouldCreatePhase0SpecFromFork() {
    final SpecVersion expectedVersion = SpecVersion.createPhase0(specConfig);
    final SpecVersion actualVersion =
        SpecVersion.createForFork(specConfig.getGenesisForkVersion(), specConfig);
    assertThat(actualVersion.getSchemaDefinitions())
        .hasSameClassAs(expectedVersion.getSchemaDefinitions());
  }

  @Test
  void shouldCreateAltairSpecFromFork() {
    final SpecConfigAltair altairSpecConfig = SpecConfigAltair.required(specConfig);
    final SpecVersion expectedVersion = SpecVersion.createAltair(altairSpecConfig);
    final SpecVersion actualVersion =
        SpecVersion.createForFork(altairSpecConfig.getAltairForkVersion(), specConfig);
    assertThat(actualVersion.getSchemaDefinitions())
        .hasSameClassAs(expectedVersion.getSchemaDefinitions());
  }

  @Test
  void shouldThrowWhenForkIsUnknown() {
    assertThatThrownBy(
            () -> SpecVersion.createForFork(Bytes4.fromHexString("0x12341234"), specConfig))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
