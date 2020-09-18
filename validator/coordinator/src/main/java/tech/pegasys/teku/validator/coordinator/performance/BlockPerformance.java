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

import com.google.common.base.Objects;

import static tech.pegasys.teku.validator.coordinator.performance.RecentChainDataPerformanceTracker.getPercentage;

public class BlockPerformance {
  private final int numberOfIncludedBlocks;
  private final int numberOfSentBlocks;

  public BlockPerformance(int numberOfIncludedBlocks, int numberOfSentBlocks) {
    this.numberOfIncludedBlocks = numberOfIncludedBlocks;
    this.numberOfSentBlocks = numberOfSentBlocks;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof BlockPerformance)) return false;
    BlockPerformance that = (BlockPerformance) o;
    return numberOfIncludedBlocks == that.numberOfIncludedBlocks
        && numberOfSentBlocks == that.numberOfSentBlocks;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(numberOfIncludedBlocks, numberOfSentBlocks);
  }

  @Override
  public String toString() {
    return String.format(
        "\n ===== Block Performance Information ===== \n"
            + " - Number of sent blocks: %d\n"
            + " - Number of blocks included on chain: %d\n"
            + " - %%age of inclusion at: %d%%",
        numberOfSentBlocks,
        numberOfIncludedBlocks,
        getPercentage(numberOfIncludedBlocks, numberOfSentBlocks));
  }
}
