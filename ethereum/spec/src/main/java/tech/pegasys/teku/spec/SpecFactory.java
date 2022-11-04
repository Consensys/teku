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

package tech.pegasys.teku.spec;

import static tech.pegasys.teku.spec.SpecMilestone.ALTAIR;
import static tech.pegasys.teku.spec.SpecMilestone.BELLATRIX;
import static tech.pegasys.teku.spec.SpecMilestone.CAPELLA;
import static tech.pegasys.teku.spec.SpecMilestone.PHASE0;
import static tech.pegasys.teku.spec.config.SpecConfig.FAR_FUTURE_EPOCH;

import java.util.function.Consumer;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigAltair;
import tech.pegasys.teku.spec.config.SpecConfigBellatrix;
import tech.pegasys.teku.spec.config.SpecConfigBuilder;
import tech.pegasys.teku.spec.config.SpecConfigCapella;
import tech.pegasys.teku.spec.config.SpecConfigLoader;

public class SpecFactory {

  public static Spec create(String configName) {
    return create(configName, __ -> {});
  }

  public static Spec create(final String configName, final Consumer<SpecConfigBuilder> modifier) {
    final SpecConfig config = SpecConfigLoader.loadConfig(configName, modifier);
    return create(config);
  }

  public static Spec create(final SpecConfig config) {
    final UInt64 altairForkEpoch =
        config.toVersionAltair().map(SpecConfigAltair::getAltairForkEpoch).orElse(FAR_FUTURE_EPOCH);
    final UInt64 bellatrixForkEpoch =
        config
            .toVersionBellatrix()
            .map(SpecConfigBellatrix::getBellatrixForkEpoch)
            .orElse(FAR_FUTURE_EPOCH);
    final UInt64 capellaForkEpoch =
        config
            .toVersionCapella()
            .map(SpecConfigCapella::getCapellaForkEpoch)
            .orElse(FAR_FUTURE_EPOCH);
    final SpecMilestone highestMilestoneSupported;

    if (!capellaForkEpoch.equals(FAR_FUTURE_EPOCH)) {
      highestMilestoneSupported = CAPELLA;
    } else if (!bellatrixForkEpoch.equals(FAR_FUTURE_EPOCH)) {
      highestMilestoneSupported = BELLATRIX;
    } else if (!altairForkEpoch.equals(FAR_FUTURE_EPOCH)) {
      highestMilestoneSupported = ALTAIR;
    } else {
      highestMilestoneSupported = PHASE0;
    }

    return Spec.create(config, highestMilestoneSupported);
  }
}
