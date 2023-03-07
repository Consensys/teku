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

package tech.pegasys.teku.storage.store;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.Objects;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;

public class StoreConfig {
  public static final int MAX_CACHE_SIZE = 10_000;

  public static final int DEFAULT_STATE_CACHE_SIZE = 32 * 5;
  public static final int DEFAULT_BLOCK_CACHE_SIZE = 32;
  public static final int DEFAULT_CHECKPOINT_STATE_CACHE_SIZE = 20;
  public static final int DEFAULT_HOT_STATE_PERSISTENCE_FREQUENCY_IN_EPOCHS = 2;

  public static final int DEFAULT_EARLIEST_AVAILABLE_BLOCK_SLOT_QUERY_FREQUENCY = 0;

  private final int stateCacheSize;
  private final int blockCacheSize;
  private final int checkpointStateCacheSize;
  private final int hotStatePersistenceFrequencyInEpochs;
  private final int earliestAvailableBlockSlotFrequency;

  private StoreConfig(
      final int stateCacheSize,
      final int blockCacheSize,
      final int checkpointStateCacheSize,
      final int hotStatePersistenceFrequencyInEpochs,
      int earliestAvailableBlockSlotFrequency) {
    this.stateCacheSize = stateCacheSize;
    this.blockCacheSize = blockCacheSize;
    this.checkpointStateCacheSize = checkpointStateCacheSize;
    this.hotStatePersistenceFrequencyInEpochs = hotStatePersistenceFrequencyInEpochs;
    this.earliestAvailableBlockSlotFrequency = earliestAvailableBlockSlotFrequency;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static StoreConfig createDefault() {
    return builder().build();
  }

  public int getStateCacheSize() {
    return stateCacheSize;
  }

  public int getBlockCacheSize() {
    return blockCacheSize;
  }

  public int getCheckpointStateCacheSize() {
    return checkpointStateCacheSize;
  }

  public int getEarliestAvailableBlockSlotFrequency() {
    return earliestAvailableBlockSlotFrequency;
  }

  public int getHotStatePersistenceFrequencyInEpochs() {
    return hotStatePersistenceFrequencyInEpochs;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final StoreConfig that = (StoreConfig) o;
    return stateCacheSize == that.stateCacheSize
        && blockCacheSize == that.blockCacheSize
        && checkpointStateCacheSize == that.checkpointStateCacheSize
        && hotStatePersistenceFrequencyInEpochs == that.hotStatePersistenceFrequencyInEpochs;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        stateCacheSize,
        blockCacheSize,
        checkpointStateCacheSize,
        hotStatePersistenceFrequencyInEpochs);
  }

  public static class Builder {
    private int stateCacheSize = DEFAULT_STATE_CACHE_SIZE;
    private int blockCacheSize = DEFAULT_BLOCK_CACHE_SIZE;
    private int checkpointStateCacheSize = DEFAULT_CHECKPOINT_STATE_CACHE_SIZE;
    private int hotStatePersistenceFrequencyInEpochs =
        DEFAULT_HOT_STATE_PERSISTENCE_FREQUENCY_IN_EPOCHS;
    private int earliestAvailableBlockSlotFrequency = 0;

    private Builder() {}

    public StoreConfig build() {
      return new StoreConfig(
          stateCacheSize,
          blockCacheSize,
          checkpointStateCacheSize,
          hotStatePersistenceFrequencyInEpochs,
          earliestAvailableBlockSlotFrequency);
    }

    public Builder stateCacheSize(final int stateCacheSize) {
      validateCacheSize(stateCacheSize);
      this.stateCacheSize = stateCacheSize;
      return this;
    }

    public Builder blockCacheSize(final int blockCacheSize) {
      validateCacheSize(blockCacheSize);
      this.blockCacheSize = blockCacheSize;
      return this;
    }

    public Builder checkpointStateCacheSize(final int checkpointStateCacheSize) {
      validateCacheSize(checkpointStateCacheSize);
      this.checkpointStateCacheSize = checkpointStateCacheSize;
      return this;
    }

    public Builder earliestAvailableBlockSlotFrequency(
        int earliestAvailableBlockSlotQueryFrequency) {
      this.earliestAvailableBlockSlotFrequency = earliestAvailableBlockSlotQueryFrequency;
      return this;
    }

    public Builder hotStatePersistenceFrequencyInEpochs(
        final int hotStatePersistenceFrequencyInEpochs) {
      if (hotStatePersistenceFrequencyInEpochs < 0) {
        throw new InvalidConfigurationException(
            String.format(
                "Invalid hotStatePersistenceFrequencyInEpochs: %d",
                hotStatePersistenceFrequencyInEpochs));
      }
      this.hotStatePersistenceFrequencyInEpochs = hotStatePersistenceFrequencyInEpochs;
      return this;
    }

    private void validateCacheSize(final int cacheSize) {
      checkArgument(cacheSize >= 0, "Cache size cannot be negative");
      checkArgument(
          cacheSize <= MAX_CACHE_SIZE, "Cache size %s exceeds max: %s", cacheSize, MAX_CACHE_SIZE);
    }
  }
}
