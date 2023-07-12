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

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigAltair;
import tech.pegasys.teku.spec.config.SpecConfigBellatrix;
import tech.pegasys.teku.spec.config.SpecConfigCapella;
import tech.pegasys.teku.spec.config.SpecConfigDeneb;

public enum SpecMilestone {
  PHASE0,
  ALTAIR,
  BELLATRIX,
  CAPELLA,
  DENEB;

  /**
   * Returns true if this milestone is at or after the supplied milestone ({@code other})
   *
   * @param other The milestone we're comparing against
   * @return True if this milestone is ordered at or after the given milestone
   */
  public boolean isGreaterThanOrEqualTo(final SpecMilestone other) {
    return compareTo(other) >= 0;
  }

  /** Returns the milestone prior to this milestone */
  public SpecMilestone getPreviousMilestone() {
    if (equals(PHASE0)) {
      throw new IllegalArgumentException("There is no milestone prior to Phase0");
    }
    return SpecMilestone.values()[ordinal() - 1];
  }

  /**
   * @param milestone The milestone being inspected
   * @return An ordered list of all milestones preceding the supplied milestone
   */
  public static List<SpecMilestone> getAllPriorMilestones(final SpecMilestone milestone) {
    final List<SpecMilestone> allMilestones = Arrays.asList(SpecMilestone.values());
    final int milestoneIndex = allMilestones.indexOf(milestone);
    return allMilestones.subList(0, milestoneIndex);
  }

  /**
   * @param milestone The milestone being inspected
   * @return An ordered list of the supplied milestone and all milestones succeeding it
   */
  public static List<SpecMilestone> getAllMilestonesFrom(final SpecMilestone milestone) {
    final List<SpecMilestone> allMilestones = Arrays.asList(SpecMilestone.values());
    final int milestoneIndex = allMilestones.indexOf(milestone);
    return allMilestones.subList(milestoneIndex, SpecMilestone.values().length);
  }

  /**
   * @param milestone The milestone being inspected
   * @return An ordered list of all milestones up to and included the specified milestone
   */
  public static List<SpecMilestone> getMilestonesUpTo(final SpecMilestone milestone) {
    final List<SpecMilestone> allMilestones = Arrays.asList(SpecMilestone.values());
    final int milestoneIndex = allMilestones.indexOf(milestone);
    return allMilestones.subList(0, milestoneIndex + 1);
  }

  public static SpecMilestone getHighestMilestone() {
    final int length = SpecMilestone.values().length;
    return SpecMilestone.values()[length - 1];
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
      case DENEB:
        return specConfig.toVersionDeneb().map(SpecConfigDeneb::getDenebForkVersion);
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
      case DENEB:
        return specConfig.toVersionDeneb().map(SpecConfigDeneb::getDenebForkEpoch);
      default:
        throw new UnsupportedOperationException("Unknown milestone requested: " + milestone.name());
    }
  }

  public static SpecMilestone forName(final String milestoneName) {
    checkNotNull(milestoneName, "Milestone name can't be null");
    return SpecMilestone.valueOf(milestoneName.toUpperCase(Locale.ROOT));
  }
}
