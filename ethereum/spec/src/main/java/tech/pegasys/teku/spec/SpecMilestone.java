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

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigAltair;
import tech.pegasys.teku.ssz.type.Bytes4;

public enum SpecMilestone {
  PHASE0,
  ALTAIR;

  /**
   *
   * @param milestone The milestone being inspected
   * @return An ordered list of all milestones preceding the supplied milestone
   */
  static List<SpecMilestone> getAllPriorMilestones(SpecMilestone milestone) {
    final List<SpecMilestone> allMilestones = Arrays.asList(SpecMilestone.values());
    final int milestoneIndex = allMilestones.indexOf(milestone);
    return allMilestones.subList(0, milestoneIndex);
  }

  static Optional<Bytes4> getForkVersion(
      final SpecConfig specConfig, final SpecMilestone milestone) {
    switch (milestone) {
      case PHASE0:
        return Optional.of(specConfig.getGenesisForkVersion());
      case ALTAIR:
        return specConfig.toVersionAltair().map(SpecConfigAltair::getAltairForkVersion);
      default:
        throw new UnsupportedOperationException("Unknown milestone requested: " + milestone.name());
    }
  }

  static Optional<UInt64> getForkSlot(final SpecConfig specConfig, final SpecMilestone milestone) {
    switch (milestone) {
      case PHASE0:
        // Phase0 can only ever start at slot 0 - no non-zero slot is valid. However, another fork
        // may also be configured to start at slot 0, effectively overriding phase0
        return Optional.of(UInt64.ZERO);
      case ALTAIR:
        return specConfig.toVersionAltair().map(SpecConfigAltair::getAltairForkSlot);
      default:
        throw new UnsupportedOperationException("Unknown milestone requested: " + milestone.name());
    }
  }
}
