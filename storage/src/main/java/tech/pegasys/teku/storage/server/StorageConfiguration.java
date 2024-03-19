/*
 * Copyright Consensys Software Inc., 2022
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

import static tech.pegasys.teku.storage.server.StateStorageMode.MINIMAL;
import static tech.pegasys.teku.storage.server.StateStorageMode.NOT_SET;
import static tech.pegasys.teku.storage.server.StateStorageMode.PRUNE;
import static tech.pegasys.teku.storage.server.VersionedDatabaseFactory.STORAGE_MODE_PATH;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.service.serviceutils.layout.DataConfig;
import tech.pegasys.teku.service.serviceutils.layout.DataDirLayout;
import tech.pegasys.teku.spec.Spec;

public class StorageConfiguration {

  public static final boolean DEFAULT_STORE_NON_CANONICAL_BLOCKS_ENABLED = false;
  public static final int DEFAULT_STATE_REBUILD_TIMEOUT_SECONDS = 120;
  public static final long DEFAULT_STORAGE_FREQUENCY = 2048L;
  public static final int DEFAULT_MAX_KNOWN_NODE_CACHE_SIZE = 100_000;
  public static final Duration DEFAULT_BLOCK_PRUNING_INTERVAL = Duration.ofMinutes(15);
  public static final int DEFAULT_BLOCK_PRUNING_LIMIT = 5000;
  public static final Duration DEFAULT_BLOBS_PRUNING_INTERVAL = Duration.ofMinutes(1);

  // 60/12 = 5 blocks per minute * 6 max blobs per block = 30 blobs per minute at maximum, 15 as
  // target. Let's configure 48 pruning per minute, so we have some room for catching up.
  public static final int DEFAULT_BLOBS_PRUNING_LIMIT = 48;

  private final Eth1Address eth1DepositContract;

  private final StateStorageMode dataStorageMode;
  private final long dataStorageFrequency;
  private final DatabaseVersion dataStorageCreateDbVersion;
  private final Spec spec;
  private final boolean storeNonCanonicalBlocks;
  private final int maxKnownNodeCacheSize;
  private final Duration blockPruningInterval;
  private final int blockPruningLimit;
  private final Duration blobsPruningInterval;
  private final int blobsPruningLimit;

  private final int stateRebuildTimeoutSeconds;

  private StorageConfiguration(
      final Eth1Address eth1DepositContract,
      final StateStorageMode dataStorageMode,
      final long dataStorageFrequency,
      final DatabaseVersion dataStorageCreateDbVersion,
      final boolean storeNonCanonicalBlocks,
      final int maxKnownNodeCacheSize,
      final Duration blockPruningInterval,
      final int blockPruningLimit,
      final Duration blobsPruningInterval,
      final int blobsPruningLimit,
      int stateRebuildTimeoutSeconds,
      final Spec spec) {
    this.eth1DepositContract = eth1DepositContract;
    this.dataStorageMode = dataStorageMode;
    this.dataStorageFrequency = dataStorageFrequency;
    this.dataStorageCreateDbVersion = dataStorageCreateDbVersion;
    this.storeNonCanonicalBlocks = storeNonCanonicalBlocks;
    this.maxKnownNodeCacheSize = maxKnownNodeCacheSize;
    this.blockPruningInterval = blockPruningInterval;
    this.blockPruningLimit = blockPruningLimit;
    this.blobsPruningInterval = blobsPruningInterval;
    this.blobsPruningLimit = blobsPruningLimit;
    this.stateRebuildTimeoutSeconds = stateRebuildTimeoutSeconds;
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

  public int getStateRebuildTimeoutSeconds() {
    return stateRebuildTimeoutSeconds;
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

  public int getBlockPruningLimit() {
    return blockPruningLimit;
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
    private static final Logger LOG = LogManager.getLogger();
    private Eth1Address eth1DepositContract;
    private StateStorageMode dataStorageMode = StateStorageMode.DEFAULT_MODE;
    private long dataStorageFrequency = DEFAULT_STORAGE_FREQUENCY;
    private DatabaseVersion dataStorageCreateDbVersion = DatabaseVersion.DEFAULT_VERSION;
    private Spec spec;
    private DataConfig dataConfig;
    private boolean storeNonCanonicalBlocks = DEFAULT_STORE_NON_CANONICAL_BLOCKS_ENABLED;
    private int maxKnownNodeCacheSize = DEFAULT_MAX_KNOWN_NODE_CACHE_SIZE;
    private Duration blockPruningInterval = DEFAULT_BLOCK_PRUNING_INTERVAL;
    private int blockPruningLimit = DEFAULT_BLOCK_PRUNING_LIMIT;
    private Duration blobsPruningInterval = DEFAULT_BLOBS_PRUNING_INTERVAL;
    private int blobsPruningLimit = DEFAULT_BLOBS_PRUNING_LIMIT;
    private int stateRebuildTimeoutSeconds = DEFAULT_STATE_REBUILD_TIMEOUT_SECONDS;

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

    public Builder dataConfig(final DataConfig dataConfig) {
      this.dataConfig = dataConfig;
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

    public Builder blockPruningLimit(final int blockPruningLimit) {
      if (blockPruningLimit < 0) {
        throw new InvalidConfigurationException(
            String.format("Invalid blockPruningLimit: %d", blockPruningLimit));
      }
      this.blockPruningLimit = blockPruningLimit;
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
      determineDataStorageMode();
      return new StorageConfiguration(
          eth1DepositContract,
          dataStorageMode,
          dataStorageFrequency,
          dataStorageCreateDbVersion,
          storeNonCanonicalBlocks,
          maxKnownNodeCacheSize,
          blockPruningInterval,
          blockPruningLimit,
          blobsPruningInterval,
          blobsPruningLimit,
          stateRebuildTimeoutSeconds,
          spec);
    }

    private void determineDataStorageMode() {
      if (dataConfig != null) {
        final DataDirLayout dataDirLayout = DataDirLayout.createFrom(dataConfig);
        this.dataStorageMode =
            determineStorageDefault(
                dataDirLayout.getBeaconDataDirectory().toFile().exists(),
                getStorageModeFromPersistedDatabase(dataDirLayout),
                dataStorageMode);
      } else {
        if (dataStorageMode.equals(NOT_SET)) {
          dataStorageMode = PRUNE;
        }
      }
    }

    private Optional<StateStorageMode> getStorageModeFromPersistedDatabase(
        final DataDirLayout dataDirLayout) {
      final Path dbStorageModeFile =
          dataDirLayout.getBeaconDataDirectory().resolve(STORAGE_MODE_PATH);
      if (!Files.exists(dbStorageModeFile)) {
        return Optional.empty();
      }
      try {
        final StateStorageMode dbStorageMode =
            StateStorageMode.valueOf(Files.readString(dbStorageModeFile).trim());
        LOG.debug("Read previous storage mode as {}", dbStorageMode);
        return Optional.of(dbStorageMode);
      } catch (final IOException ex) {
        throw new UncheckedIOException("Failed to read storage mode from file", ex);
      }
    }

    public Builder stateRebuildTimeoutSeconds(int stateRebuildTimeoutSeconds) {
      if (stateRebuildTimeoutSeconds < 10 || stateRebuildTimeoutSeconds > 300) {
        LOG.warn("State rebuild timeout is set outside of sensible defaults of 10 -> 300, {} was defined. Cannot be below 1, will allow the value to exceed 300.", stateRebuildTimeoutSeconds);
      }
      this.stateRebuildTimeoutSeconds = Math.max(stateRebuildTimeoutSeconds, 1);
      LOG.debug("stateRebuildTimeoutSeconds = {}", stateRebuildTimeoutSeconds);
      return this;
    }
  }

  static StateStorageMode determineStorageDefault(
      final boolean isExistingStore,
      final Optional<StateStorageMode> maybeHistoricStorageMode,
      final StateStorageMode modeRequested) {
    if (modeRequested != NOT_SET) {
      return modeRequested;
    }
    return maybeHistoricStorageMode.orElse(isExistingStore ? PRUNE : MINIMAL);
  }
}
