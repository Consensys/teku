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

import java.util.Optional;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigLoader;

public class SpecFactory {

  public static Spec create(String configName) {
    return create(configName, Optional.empty());
  }

  public static Spec create(String configName, final Optional<UInt64> altairForkSlot) {
    final SpecConfig config =
        SpecConfigLoader.loadConfig(
            configName,
            builder ->
                altairForkSlot.ifPresent(
                    florkSlot ->
                        builder.altairBuilder(
                            altairBuilder -> altairBuilder.altairForkSlot(florkSlot))));
    return create(config, altairForkSlot);
  }

  public static Spec create(final SpecConfig config, final Optional<UInt64> altairForkSlot) {
    final SpecMilestone highestMilestoneSupported =
        altairForkSlot.map(__ -> SpecMilestone.ALTAIR).orElse(SpecMilestone.PHASE0);
    return Spec.create(config, highestMilestoneSupported);
  }
}
