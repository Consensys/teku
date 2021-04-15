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

import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigAltair;
import tech.pegasys.teku.spec.config.SpecConfigLoader;
import tech.pegasys.teku.spec.config.TestConfigLoader;
import tech.pegasys.teku.spec.networks.Eth2Network;

public class TestSpecFactory {

  public static Spec createMinimalAltair() {
    final SpecConfigAltair specConfig = getAltairSpecConfig(Eth2Network.MINIMAL);
    return create(specConfig, SpecMilestone.ALTAIR);
  }

  public static Spec createMinimal(final SpecMilestone specMilestone) {
    if (specMilestone == SpecMilestone.PHASE0) {
      return createMinimalPhase0();
    }
    return createMinimalAltair();
  }

  public static Spec createMinimalPhase0() {
    final SpecConfig specConfig = SpecConfigLoader.loadConfig(Eth2Network.MINIMAL.configName());
    return create(specConfig, SpecMilestone.PHASE0);
  }

  public static Spec createMainnetAltair() {
    final SpecConfigAltair specConfig = getAltairSpecConfig(Eth2Network.MAINNET);
    return create(specConfig, SpecMilestone.ALTAIR);
  }

  public static Spec createMainnetPhase0() {
    final SpecConfig specConfig = SpecConfigLoader.loadConfig(Eth2Network.MAINNET.configName());
    return create(specConfig, SpecMilestone.PHASE0);
  }

  public static Spec createPhase0(final String configName) {
    final SpecConfig specConfig = SpecConfigLoader.loadConfig(configName);
    return createPhase0(specConfig);
  }

  public static Spec createPhase0(final SpecConfig config) {
    return create(config, SpecMilestone.PHASE0);
  }

  private static Spec create(
      final SpecConfig config, final SpecMilestone highestSupportedMilestone) {
    return Spec.create(config, highestSupportedMilestone);
  }

  private static SpecConfigAltair getAltairSpecConfig(final Eth2Network network) {
    return SpecConfigAltair.required(
        TestConfigLoader.loadConfig(
            network.configName(), c -> c.altairBuilder(a -> a.altairForkSlot(UInt64.ZERO))));
  }
}
