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

import static tech.pegasys.teku.spec.SpecMilestone.ALTAIR;
import static tech.pegasys.teku.spec.SpecMilestone.PHASE0;
import static tech.pegasys.teku.spec.config.SpecConfig.FAR_FUTURE_EPOCH;

import java.util.Optional;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigAltair;
import tech.pegasys.teku.spec.config.SpecConfigBuilder;
import tech.pegasys.teku.spec.config.SpecConfigLoader;

public class SpecFactory {

  public static Spec create(String configName) {
    return create(configName, Optional.empty());
  }

  public static Spec create(String configName, final Optional<UInt64> altairForkEpoch) {
    final SpecConfig config =
        SpecConfigLoader.loadConfig(
            configName,
            builder ->
                altairForkEpoch.ifPresent(
                    forkEpoch -> overrideAltairForkEpoch(builder, forkEpoch)));
    return create(config);
  }

  private static void overrideAltairForkEpoch(
      final SpecConfigBuilder builder, final UInt64 forkEpoch) {
    builder.altairBuilder(altairBuilder -> altairBuilder.altairForkEpoch(forkEpoch));
  }

  public static Spec create(final SpecConfig config) {
    final UInt64 altairForkEpoch =
        config.toVersionAltair().map(SpecConfigAltair::getAltairForkEpoch).orElse(FAR_FUTURE_EPOCH);

    final SpecMilestone highestMilestoneSupported =
        altairForkEpoch.equals(FAR_FUTURE_EPOCH) ? PHASE0 : ALTAIR;
    return Spec.create(config, highestMilestoneSupported);
  }
}
