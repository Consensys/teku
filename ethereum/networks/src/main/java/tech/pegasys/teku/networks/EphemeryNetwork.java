/*
 * Copyright Consensys Software Inc., 2025
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
import tech.pegasys.teku.infrastructure.time.SystemTimeProvider;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigLoader;
import tech.pegasys.teku.spec.config.builder.SpecConfigBuilder;

public class EphemeryNetwork {
  private static final long GENESIS_CHAINID = 39438135;
  private static final long INITIAL_GENESIS_TIMESTAMP = 1720119600;
  private static final int PERIOD = 28;
  private static final long PERIOD_IN_SECONDS = (PERIOD * 24 * 60 * 60);
  public static final long MAX_EPHEMERY_SLOT = (PERIOD_IN_SECONDS / 12) - 1;

  static long getPeriodsSinceGenesis(final TimeProvider timeProvider) {
    return ChronoUnit.DAYS.between(
            Instant.ofEpochSecond(INITIAL_GENESIS_TIMESTAMP),
            Instant.ofEpochMilli(timeProvider.getTimeInMillis().longValue()))
        / PERIOD;
  }

  public static void updateConfig(final SpecConfigBuilder builder) {
    updateConfig(builder, new SystemTimeProvider());
  }

  static void updateConfig(final SpecConfigBuilder builder, final TimeProvider timeProvider) {
    final SpecConfig config = SpecConfigLoader.loadConfig("ephemery").specConfig();
    final SpecConfigBuilder rawConfigBuilder = builder.rawConfig(config.getRawConfig());
    final long periodsSinceInitialGenesis = getPeriodsSinceGenesis(timeProvider);

    try {
      if (periodsSinceInitialGenesis > 0L) {
        final long updatedChainId = GENESIS_CHAINID + periodsSinceInitialGenesis;
        final long currentPeriodGenesis =
            INITIAL_GENESIS_TIMESTAMP + (periodsSinceInitialGenesis * PERIOD_IN_SECONDS);
        rawConfigBuilder.depositNetworkId(updatedChainId);
        rawConfigBuilder.depositChainId(updatedChainId);
        rawConfigBuilder.minGenesisTime(UInt64.valueOf(currentPeriodGenesis));
      }
    } catch (RuntimeException e) {
      throw new RuntimeException("Error updating genesis file: " + e.getMessage(), e);
    }
  }
}
