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

package tech.pegasys.teku.storage.server;

import java.time.Duration;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.spec.Spec;

public class StorageConfiguration {

  public static final boolean DEFAULT_STORE_NON_CANONICAL_BLOCKS_ENABLED = false;

  public static final long DEFAULT_STORAGE_FREQUENCY = 2048L;
  public static final int DEFAULT_MAX_KNOWN_NODE_CACHE_SIZE = 100_000;
  public static final Duration DEFAULT_BLOCK_PRUNING_INTERVAL = Duration.ofHours(1);
  public static final Duration DEFAULT_BLOBS_PRUNING_INTERVAL = Duration.ofMinutes(1);
  public static final int DEFAULT_BLOBS_PRUNING_LIMIT = 32;

  private final Eth1Address eth1DepositContract;

  private final StateStorageMode dataStorageMode;
  private final long dataStorageFrequency;
  private final DatabaseVersion dataStorageCreateDbVersion;
  private final Spec spec;
  private final boolean storeNonCanonicalBlocks;
  private final int maxKnownNodeCacheSize;
  private final Duration blockPruningInterval;
  private final Duration blobsPruningInterval;
  private final int blobsPruningLimit;

  private StorageConfiguration(
      final Eth1Address eth1DepositContract,
      final StateStorageMode dataStorageMode,
      final long dataStorageFrequency,
      final DatabaseVersion dataStorageCreateDbVersion,
      final boolean storeNonCanonicalBlocks,
      final int maxKnownNodeCacheSize,
      final Duration blockPruningInterval,
      final Duration blobsPruningInterval,
      final int blobsPruningLimit,
      final Spec spec) {
    this.eth1DepositContract = eth1DepositContract;
    this.dataStorageMode = dataStorageMode;
    this.dataStorageFrequency = dataStorageFrequency;
    this.dataStorageCreateDbVersion = dataStorageCreateDbVersion;
    this.storeNonCanonicalBlocks = storeNonCanonicalBlocks;
    this.maxKnownNodeCacheSize = maxKnownNodeCacheSize;
    this.blockPruningInterval = blockPruningInterval;
    this.blobsPruningInterval = blobsPruningInterval;
    this.blobsPruningLimit = blobsPruningLimit;
    this.spec = spec;
  }

  public static Builder builder() {
    return new Builder();
  }

  public Eth1Address getEth1DepositContract() {
    return eth1DepositContract;
  }

  public StateStorageMode getDataStorageMode() {
    return dataStorageMode;
  }

  public long getDataStorageFrequency() {
    return dataStorageFrequency;
  }

  public DatabaseVersion getDataStorageCreateDbVersion() {
    return dataStorageCreateDbVersion;
  }

  public boolean isStoreNonCanonicalBlocksEnabled() {
    return storeNonCanonicalBlocks;
  }

  public int getMaxKnownNodeCacheSize() {
    return maxKnownNodeCacheSize;
  }

  public Duration getBlockPruningInterval() {
    return blockPruningInterval;
  }

  public Duration getBlobsPruningInterval() {
    return blobsPruningInterval;
  }

  public int getBlobsPruningLimit() {
    return blobsPruningLimit;
  }

  public Spec getSpec() {
    return spec;
  }

  public static final class Builder {

    private Eth1Address eth1DepositContract;
    private StateStorageMode dataStorageMode = StateStorageMode.DEFAULT_MODE;
    private long dataStorageFrequency = DEFAULT_STORAGE_FREQUENCY;
    private DatabaseVersion dataStorageCreateDbVersion = DatabaseVersion.DEFAULT_VERSION;
    private Spec spec;
    private boolean storeNonCanonicalBlocks = DEFAULT_STORE_NON_CANONICAL_BLOCKS_ENABLED;
    private int maxKnownNodeCacheSize = DEFAULT_MAX_KNOWN_NODE_CACHE_SIZE;
    private Duration blockPruningInterval = DEFAULT_BLOCK_PRUNING_INTERVAL;
    private Duration blobsPruningInterval = DEFAULT_BLOBS_PRUNING_INTERVAL;
    private int blobsPruningLimit = DEFAULT_BLOBS_PRUNING_LIMIT;

    private Builder() {}

    public Builder eth1DepositContract(Eth1Address eth1DepositContract) {
      this.eth1DepositContract = eth1DepositContract;
      return this;
    }

    public Builder eth1DepositContractDefault(Eth1Address eth1DepositContract) {
      if (this.eth1DepositContract == null) {
        this.eth1DepositContract = eth1DepositContract;
      }
      return this;
    }

    public Builder dataStorageMode(StateStorageMode dataStorageMode) {
      this.dataStorageMode = dataStorageMode;
      return this;
    }

    public Builder dataStorageFrequency(long dataStorageFrequency) {
      if (dataStorageFrequency < 0) {
        throw new InvalidConfigurationException(
            String.format("Invalid dataStorageFrequency: %d", dataStorageFrequency));
      }
      this.dataStorageFrequency = dataStorageFrequency;
      return this;
    }

    public Builder dataStorageCreateDbVersion(DatabaseVersion dataStorageCreateDbVersion) {
      this.dataStorageCreateDbVersion = dataStorageCreateDbVersion;
      return this;
    }

    public Builder specProvider(Spec spec) {
      this.spec = spec;
      return this;
    }

    public Builder storeNonCanonicalBlocks(final boolean storeNonCanonicalBlocks) {
      this.storeNonCanonicalBlocks = storeNonCanonicalBlocks;
      return this;
    }

    public Builder maxKnownNodeCacheSize(final int maxKnownNodeCacheSize) {
      if (maxKnownNodeCacheSize < 0) {
        throw new InvalidConfigurationException(
            String.format("Invalid maxKnownNodeCacheSize: %d", maxKnownNodeCacheSize));
      }
      this.maxKnownNodeCacheSize = maxKnownNodeCacheSize;
      return this;
    }

    public Builder blockPruningInterval(final Duration blockPruningInterval) {
      if (blockPruningInterval.isNegative() || blockPruningInterval.isZero()) {
        throw new InvalidConfigurationException("Block pruning interval must be positive");
      }
      this.blockPruningInterval = blockPruningInterval;
      return this;
    }

    public Builder blobsPruningInterval(final Duration blobsPruningInterval) {
      if (blobsPruningInterval.isNegative() || blobsPruningInterval.isZero()) {
        throw new InvalidConfigurationException("Blobs pruning interval must be positive");
      }
      this.blobsPruningInterval = blobsPruningInterval;
      return this;
    }

    public Builder blobsPruningLimit(final int blobsPruningLimit) {
      if (blobsPruningLimit < 0) {
        throw new InvalidConfigurationException(
            String.format("Invalid blobsPruningLimit: %d", blobsPruningLimit));
      }
      this.blobsPruningLimit = blobsPruningLimit;
      return this;
    }

    public StorageConfiguration build() {
      return new StorageConfiguration(
          eth1DepositContract,
          dataStorageMode,
          dataStorageFrequency,
          dataStorageCreateDbVersion,
          storeNonCanonicalBlocks,
          maxKnownNodeCacheSize,
          blockPruningInterval,
          blobsPruningInterval,
          blobsPruningLimit,
          spec);
    }
  }
}
