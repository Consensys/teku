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

import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigAltair;
import tech.pegasys.teku.spec.config.SpecConfigLoader;
import tech.pegasys.teku.spec.networks.Eth2Network;

public class TestSpecFactory {

  public static Spec createMinimalAltair() {
    final SpecConfigAltair specConfig =
        SpecConfigAltair.required(SpecConfigLoader.loadConfig(Eth2Network.MINIMAL.configName()));
    return SpecFactory.create(specConfig, specConfig.getAltairForkVersion());
  }

  public static Spec createMinimalPhase0() {
    final SpecConfig specConfig = SpecConfigLoader.loadConfig(Eth2Network.MINIMAL.configName());
    return SpecFactory.create(specConfig, specConfig.getGenesisForkVersion());
  }

  public static Spec createMainnetAltair() {
    final SpecConfigAltair specConfig =
        SpecConfigAltair.required(SpecConfigLoader.loadConfig(Eth2Network.MAINNET.configName()));
    return SpecFactory.create(specConfig, specConfig.getAltairForkVersion());
  }

  public static Spec createMainnetPhase0() {
    final SpecConfig specConfig = SpecConfigLoader.loadConfig(Eth2Network.MAINNET.configName());
    return SpecFactory.create(specConfig, specConfig.getGenesisForkVersion());
  }
}
