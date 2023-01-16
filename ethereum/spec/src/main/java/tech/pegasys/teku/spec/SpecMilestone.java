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

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigAltair;
import tech.pegasys.teku.spec.config.SpecConfigBellatrix;
import tech.pegasys.teku.spec.config.SpecConfigCapella;
import tech.pegasys.teku.spec.config.SpecConfigEip4844;

public enum SpecMilestone {
  PHASE0,
  ALTAIR,
  BELLATRIX,
  CAPELLA,
  EIP4844;

  /**
   * Returns true if this milestone is at or after the supplied milestone ({@code other})
   *
   * @param other The milestone we're comparing against
   * @return True if this milestone is ordered at or after the given milestone
   */
  public boolean isGreaterThanOrEqualTo(final SpecMilestone other) {
    return compareTo(other) >= 0;
  }

  /**
   * @param milestone The milestone being inspected
   * @return An ordered list of all milestones preceding the supplied milestone
   */
  public static List<SpecMilestone> getAllPriorMilestones(SpecMilestone milestone) {
    final List<SpecMilestone> allMilestones = Arrays.asList(SpecMilestone.values());
    final int milestoneIndex = allMilestones.indexOf(milestone);
    return allMilestones.subList(0, milestoneIndex);
  }

  /**
   * @param milestone The milestone being inspected
   * @return An ordered list of all milestones up to and included the specified milestone
   */
  static List<SpecMilestone> getMilestonesUpTo(SpecMilestone milestone) {
    final List<SpecMilestone> allMilestones = Arrays.asList(SpecMilestone.values());
    final int milestoneIndex = allMilestones.indexOf(milestone);
    return allMilestones.subList(0, milestoneIndex + 1);
  }

  static boolean areMilestonesInOrder(final SpecMilestone... milestones) {
    final List<SpecMilestone> allMilestones = Arrays.asList(SpecMilestone.values());
    int lastMilestoneIndex = -1;
    for (SpecMilestone milestone : milestones) {
      final int curMilestoneIndex = allMilestones.indexOf(milestone);
      if (curMilestoneIndex < lastMilestoneIndex) {
        return false;
      }
      lastMilestoneIndex = curMilestoneIndex;
    }
    return true;
  }

  static Optional<Bytes4> getForkVersion(
      final SpecConfig specConfig, final SpecMilestone milestone) {
    switch (milestone) {
      case PHASE0:
        return Optional.of(specConfig.getGenesisForkVersion());
      case ALTAIR:
        return specConfig.toVersionAltair().map(SpecConfigAltair::getAltairForkVersion);
      case BELLATRIX:
        return specConfig.toVersionBellatrix().map(SpecConfigBellatrix::getBellatrixForkVersion);
      case CAPELLA:
        return specConfig.toVersionCapella().map(SpecConfigCapella::getCapellaForkVersion);
      case EIP4844:
        return specConfig.toVersionEip4844().map(SpecConfigEip4844::getEip4844ForkVersion);
      default:
        throw new UnsupportedOperationException("Unknown milestone requested: " + milestone.name());
    }
  }

  static Optional<UInt64> getForkEpoch(final SpecConfig specConfig, final SpecMilestone milestone) {
    switch (milestone) {
      case PHASE0:
        // Phase0 can only ever start at epoch 0 - no non-zero slot is valid. However, another fork
        // may also be configured to start at epoch 0, effectively overriding phase0
        return Optional.of(UInt64.ZERO);
      case ALTAIR:
        return specConfig.toVersionAltair().map(SpecConfigAltair::getAltairForkEpoch);
      case BELLATRIX:
        return specConfig.toVersionBellatrix().map(SpecConfigBellatrix::getBellatrixForkEpoch);
      case CAPELLA:
        return specConfig.toVersionCapella().map(SpecConfigCapella::getCapellaForkEpoch);
      case EIP4844:
        return specConfig.toVersionEip4844().map(SpecConfigEip4844::getEip4844ForkEpoch);
      default:
        throw new UnsupportedOperationException("Unknown milestone requested: " + milestone.name());
    }
  }
}
