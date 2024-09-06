/*
 * Copyright Consensys Software Inc., 2024
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

package tech.pegasys.teku.networks;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigLoader;
import tech.pegasys.teku.spec.config.builder.SpecConfigBuilder;
import tech.pegasys.teku.spec.networks.Eth2Network;

public class EphemeryNetwork {
  private static final long GENESIS_CHAINID = 39438135;
  private static final long GENESIS_TIMESTAMP = 1720119600;
  private static final int PERIOD = 28;
  private static final long PERIOD_IN_SECONDS = (PERIOD * 24 * 60 * 60);

  public static void updateConfig(final SpecConfigBuilder builder) {
    final SpecConfig config = SpecConfigLoader.loadConfig("ephemery");

    if (Eth2Network.EPHEMERY.configName().equals("ephemery") && config != null) {
      long currentTimestamp = Instant.now().getEpochSecond();
      long periodsSinceGenesis =
          ChronoUnit.DAYS.between(Instant.ofEpochSecond(GENESIS_TIMESTAMP), Instant.now()) / PERIOD;
      long updatedTimestamp = GENESIS_TIMESTAMP + (periodsSinceGenesis * PERIOD_IN_SECONDS);
      Long updatedChainId = GENESIS_CHAINID + periodsSinceGenesis;

      try {
        if (currentTimestamp > (GENESIS_TIMESTAMP + PERIOD_IN_SECONDS)) {
          builder.rawConfig(config.getRawConfig()).depositNetworkId(updatedChainId);
          builder.rawConfig(config.getRawConfig()).depositChainId(updatedChainId);
          builder.rawConfig(config.getRawConfig()).minGenesisTime(UInt64.valueOf(updatedTimestamp));
        }

      } catch (RuntimeException e) {
        throw new RuntimeException("Error updating genesis file:  + e.getMessage(), e");
      }
    }
  }
}
