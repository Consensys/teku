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

import static tech.pegasys.teku.validator.coordinator.performance.PerformanceTracker.getPercentage;

import com.google.common.base.Objects;

public class AttestationPerformance {
  private final int numberOfSentAttestations;
  private final int numberOfIncludedAttestations;
  private final int inclusionDistanceMax;
  private final int inclusionDistanceMin;
  private final double inclusionDistanceAverage;
  private final double correctTargetCount;
  private final double correctHeadBlockCount;

  public AttestationPerformance(
      int numberOfSentAttestations,
      int numberOfIncludedAttestations,
      int inclusionDistanceMax,
      int inclusionDistanceMin,
      double inclusionDistanceAverage,
      double correctTargetCount,
      double correctHeadBlockCount) {
    this.numberOfSentAttestations = numberOfSentAttestations;
    this.numberOfIncludedAttestations = numberOfIncludedAttestations;
    this.inclusionDistanceMax = inclusionDistanceMax;
    this.inclusionDistanceMin = inclusionDistanceMin;
    this.inclusionDistanceAverage = inclusionDistanceAverage;
    this.correctTargetCount = correctTargetCount;
    this.correctHeadBlockCount = correctHeadBlockCount;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof AttestationPerformance)) return false;
    AttestationPerformance that = (AttestationPerformance) o;
    return numberOfSentAttestations == that.numberOfSentAttestations
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
        numberOfSentAttestations,
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
        "\n ===== Attestation Performance Information ===== \n"
            + " - Number of sent attestations: %d\n"
            + " - Number of sent attestations included on chain: %d\n"
            + " - %%age of inclusion at: %d%%\n"
            + " - Inclusion distances: average: %f, min: %d, max: %d\n"
            + " - %%age with correct target at: %d%%\n"
            + " - %%age with correct head block root at: %d%%",
        numberOfSentAttestations,
        numberOfIncludedAttestations,
        getPercentage(numberOfIncludedAttestations, numberOfSentAttestations),
        inclusionDistanceAverage,
        inclusionDistanceMin,
        inclusionDistanceMax,
        getPercentage((long) correctTargetCount, numberOfSentAttestations),
        getPercentage((long) correctHeadBlockCount, numberOfSentAttestations));
  }
}
