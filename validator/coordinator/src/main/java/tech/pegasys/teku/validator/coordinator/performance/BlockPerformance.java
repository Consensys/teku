package tech.pegasys.teku.validator.coordinator.performance;

import com.google.common.base.Objects;

import static tech.pegasys.teku.validator.coordinator.performance.PerformanceTracker.getPercentage;

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
    return numberOfIncludedBlocks == that.numberOfIncludedBlocks &&
            numberOfSentBlocks == that.numberOfSentBlocks;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(numberOfIncludedBlocks, numberOfSentBlocks);
  }

  @Override
  public String toString() {
    return String.format(
            " ===== Block Performance Information ===== \n"
                    + " - Number of sent blocks: %d\n"
                    + " - Number of sent blocks included on chain: %d\n"
                    + " - %age of inclusion at: %d%%\n",
            numberOfSentBlocks,
            numberOfIncludedBlocks,
            getPercentage(numberOfIncludedBlocks, numberOfSentBlocks));
  }
}
