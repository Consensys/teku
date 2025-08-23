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

package tech.pegasys.teku.spec;

import static tech.pegasys.teku.spec.SpecMilestone.ALTAIR;
import static tech.pegasys.teku.spec.SpecMilestone.BELLATRIX;
import static tech.pegasys.teku.spec.SpecMilestone.CAPELLA;
import static tech.pegasys.teku.spec.SpecMilestone.DENEB;
import static tech.pegasys.teku.spec.SpecMilestone.ELECTRA;
import static tech.pegasys.teku.spec.SpecMilestone.FULU;
import static tech.pegasys.teku.spec.SpecMilestone.GLOAS;
import static tech.pegasys.teku.spec.SpecMilestone.PHASE0;
import static tech.pegasys.teku.spec.config.SpecConfig.FAR_FUTURE_EPOCH;

import java.util.function.Consumer;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigAndParent;
import tech.pegasys.teku.spec.config.SpecConfigLoader;
import tech.pegasys.teku.spec.config.builder.SpecConfigBuilder;

public class SpecFactory {

  public static Spec create(final String configName) {
    return create(configName, false, __ -> {});
  }

  public static Spec create(
      final String configName,
      final boolean strictConfigLoadingEnabled,
      final Consumer<SpecConfigBuilder> modifier) {
    final SpecConfigAndParent<? extends SpecConfig> config =
        SpecConfigLoader.loadConfig(configName, strictConfigLoadingEnabled, modifier);
    return create(config);
  }

  public static Spec create(final SpecConfigAndParent<? extends SpecConfig> config) {
    final UInt64 altairForkEpoch = config.specConfig().getAltairForkEpoch();
    final UInt64 bellatrixForkEpoch = config.specConfig().getBellatrixForkEpoch();
    final UInt64 capellaForkEpoch = config.specConfig().getCapellaForkEpoch();
    final UInt64 denebForkEpoch = config.specConfig().getDenebForkEpoch();
    final UInt64 electraForkEpoch = config.specConfig().getElectraForkEpoch();
    final UInt64 fuluForkEpoch = config.specConfig().getFuluForkEpoch();
    final UInt64 gloasForkEpoch = config.specConfig().getGloasForkEpoch();

    final SpecMilestone highestMilestoneSupported;

    if (!gloasForkEpoch.equals(FAR_FUTURE_EPOCH)) {
      highestMilestoneSupported = GLOAS;
    } else if (!fuluForkEpoch.equals(FAR_FUTURE_EPOCH)) {
      highestMilestoneSupported = FULU;
    } else if (!electraForkEpoch.equals(FAR_FUTURE_EPOCH)) {
      highestMilestoneSupported = ELECTRA;
    } else if (!denebForkEpoch.equals(FAR_FUTURE_EPOCH)) {
      highestMilestoneSupported = DENEB;
    } else if (!capellaForkEpoch.equals(FAR_FUTURE_EPOCH)) {
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
