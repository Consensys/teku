/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.validator.coordinator.performance;

import static tech.pegasys.teku.validator.coordinator.performance.DefaultPerformanceTracker.getPercentage;

import com.google.common.base.Objects;

public class AttestationPerformance {
  final int numberOfProducedAttestations;
  final int numberOfIncludedAttestations;
  final int inclusionDistanceMax;
  final int inclusionDistanceMin;
  final double inclusionDistanceAverage;
  final int correctTargetCount;
  final int correctHeadBlockCount;

  public AttestationPerformance(
      int numberOfProducedAttestations,
      int numberOfIncludedAttestations,
      int inclusionDistanceMax,
      int inclusionDistanceMin,
      double inclusionDistanceAverage,
      int correctTargetCount,
      int correctHeadBlockCount) {
    this.numberOfProducedAttestations = numberOfProducedAttestations;
    this.numberOfIncludedAttestations = numberOfIncludedAttestations;
    this.inclusionDistanceMax = inclusionDistanceMax;
    this.inclusionDistanceMin = inclusionDistanceMin;
    this.inclusionDistanceAverage = inclusionDistanceAverage;
    this.correctTargetCount = correctTargetCount;
    this.correctHeadBlockCount = correctHeadBlockCount;
  }

  public static AttestationPerformance empty() {
    return new AttestationPerformance(0, 0, 0, 0, 0, 0, 0);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof AttestationPerformance)) return false;
    AttestationPerformance that = (AttestationPerformance) o;
    return numberOfProducedAttestations == that.numberOfProducedAttestations
        && numberOfIncludedAttestations == that.numberOfIncludedAttestations
        && inclusionDistanceMax == that.inclusionDistanceMax
        && inclusionDistanceMin == that.inclusionDistanceMin
        && Double.compare(that.inclusionDistanceAverage, inclusionDistanceAverage) == 0
        && Double.compare(that.correctTargetCount, correctTargetCount) == 0
        && Double.compare(that.correctHeadBlockCount, correctHeadBlockCount) == 0;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(
        numberOfProducedAttestations,
        numberOfIncludedAttestations,
        inclusionDistanceMax,
        inclusionDistanceMin,
        inclusionDistanceAverage,
        correctTargetCount,
        correctHeadBlockCount);
  }

  @Override
  public String toString() {
    return String.format(
        "Attestation performance: "
            + "produced %d, included %d (%d%%), "
            + "distance %d / %.2f / %d, "
            + "correct target %d (%d%%), correct head %d (%d%%)",
        numberOfProducedAttestations,
        numberOfIncludedAttestations,
        getPercentage(numberOfIncludedAttestations, numberOfProducedAttestations),
        inclusionDistanceMin,
        inclusionDistanceAverage,
        inclusionDistanceMax,
        correctTargetCount,
        getPercentage(correctTargetCount, numberOfProducedAttestations),
        correctHeadBlockCount,
        getPercentage(correctHeadBlockCount, numberOfProducedAttestations));
  }
}
