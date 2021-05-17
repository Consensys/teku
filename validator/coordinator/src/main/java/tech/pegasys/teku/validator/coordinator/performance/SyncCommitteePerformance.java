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

package tech.pegasys.teku.validator.coordinator.performance;

import static tech.pegasys.teku.validator.coordinator.performance.DefaultPerformanceTracker.getPercentage;

import java.util.Objects;

public class SyncCommitteePerformance {
  private final int numberOfExpectedSignatures;
  private final int numberOfProducedSignatures;
  private final int numberOfIncludedSignatures;

  public SyncCommitteePerformance(
      final int numberOfExpectedSignatures,
      final int numberOfProducedSignatures,
      final int numberOfIncludedSignatures) {
    this.numberOfExpectedSignatures = numberOfExpectedSignatures;
    this.numberOfProducedSignatures = numberOfProducedSignatures;
    this.numberOfIncludedSignatures = numberOfIncludedSignatures;
  }

  public int getNumberOfExpectedSignatures() {
    return numberOfExpectedSignatures;
  }

  public int getNumberOfProducedSignatures() {
    return numberOfProducedSignatures;
  }

  public int getNumberOfIncludedSignatures() {
    return numberOfIncludedSignatures;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final SyncCommitteePerformance that = (SyncCommitteePerformance) o;
    return numberOfExpectedSignatures == that.numberOfExpectedSignatures
        && numberOfProducedSignatures == that.numberOfProducedSignatures
        && numberOfIncludedSignatures == that.numberOfIncludedSignatures;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        numberOfExpectedSignatures, numberOfProducedSignatures, numberOfIncludedSignatures);
  }

  @Override
  public String toString() {
    return String.format(
        "Sync committee performance: " + "expected %d, produced %d, included %d (%d%%)",
        numberOfExpectedSignatures,
        numberOfProducedSignatures,
        numberOfIncludedSignatures,
        getPercentage(numberOfIncludedSignatures, numberOfProducedSignatures));
  }
}
