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

public class BlockPerformance {
  final int numberOfExpectedBlocks;
  final int numberOfIncludedBlocks;
  final int numberOfProducedBlocks;

  public BlockPerformance(
      int numberOfExpectedBlocks, int numberOfIncludedBlocks, int numberOfProducedBlocks) {
    this.numberOfExpectedBlocks = numberOfExpectedBlocks;
    this.numberOfIncludedBlocks = numberOfIncludedBlocks;
    this.numberOfProducedBlocks = numberOfProducedBlocks;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof BlockPerformance)) return false;
    BlockPerformance that = (BlockPerformance) o;
    return numberOfExpectedBlocks == that.numberOfExpectedBlocks
        && numberOfIncludedBlocks == that.numberOfIncludedBlocks
        && numberOfProducedBlocks == that.numberOfProducedBlocks;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(numberOfExpectedBlocks, numberOfIncludedBlocks, numberOfProducedBlocks);
  }

  @Override
  public String toString() {
    return String.format(
        "Block performance: expected %d, produced %d, included %d (%d%%)",
        numberOfExpectedBlocks,
        numberOfProducedBlocks,
        numberOfIncludedBlocks,
        getPercentage(numberOfIncludedBlocks, numberOfProducedBlocks));
  }
}
