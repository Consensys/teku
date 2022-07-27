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

package tech.pegasys.teku.services.chainstorage;

import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.networks.Eth2NetworkConfiguration;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.eth1.Eth1Address;
import tech.pegasys.teku.storage.server.DatabaseVersion;
import tech.pegasys.teku.storage.server.StateStorageMode;
import tech.pegasys.teku.storage.server.VersionedDatabaseFactory;

public class StorageConfiguration {

  public static final boolean DEFAULT_STORE_NON_CANONICAL_BLOCKS_ENABLED = false;

  public static final boolean DEFAULT_STORE_BLOCK_PAYLOAD_SEPARATELY = false;
  public static final int DEFAULT_MAX_KNOWN_NODE_CACHE_SIZE = 100_000;

  private final Eth1Address eth1DepositContract;

  private final StateStorageMode dataStorageMode;
  private final long dataStorageFrequency;
  private final DatabaseVersion dataStorageCreateDbVersion;
  private boolean storeBlockExecutionPayloadSeparately;
  private final Spec spec;
  private final boolean storeNonCanonicalBlocks;
  private final int maxKnownNodeCacheSize;
  private final boolean storeVotesEquivocation;

  private StorageConfiguration(
      final Eth1Address eth1DepositContract,
      final StateStorageMode dataStorageMode,
      final long dataStorageFrequency,
      final DatabaseVersion dataStorageCreateDbVersion,
      final boolean storeNonCanonicalBlocks,
      final int maxKnownNodeCacheSize,
      final boolean storeVotesEquivocation,
      final boolean storeBlockExecutionPayloadSeparately,
      final Spec spec) {
    this.eth1DepositContract = eth1DepositContract;
    this.dataStorageMode = dataStorageMode;
    this.dataStorageFrequency = dataStorageFrequency;
    this.dataStorageCreateDbVersion = dataStorageCreateDbVersion;
    this.storeNonCanonicalBlocks = storeNonCanonicalBlocks;
    this.maxKnownNodeCacheSize = maxKnownNodeCacheSize;
    this.storeVotesEquivocation = storeVotesEquivocation;
    this.storeBlockExecutionPayloadSeparately = storeBlockExecutionPayloadSeparately;
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

  public boolean isStoreVotesEquivocation() {
    return storeVotesEquivocation;
  }

  public boolean isStoreBlockExecutionPayloadSeparately() {
    return storeBlockExecutionPayloadSeparately;
  }

  public Spec getSpec() {
    return spec;
  }

  public static final class Builder {

    private Eth1Address eth1DepositContract;
    private StateStorageMode dataStorageMode = StateStorageMode.DEFAULT_MODE;
    private long dataStorageFrequency = VersionedDatabaseFactory.DEFAULT_STORAGE_FREQUENCY;
    private DatabaseVersion dataStorageCreateDbVersion = DatabaseVersion.DEFAULT_VERSION;
    private boolean storeVotesEquivocation =
        Eth2NetworkConfiguration.DEFAULT_EQUIVOCATING_INDICES_ENABLED;
    private Spec spec;
    private boolean storeNonCanonicalBlocks = DEFAULT_STORE_NON_CANONICAL_BLOCKS_ENABLED;
    private int maxKnownNodeCacheSize = DEFAULT_MAX_KNOWN_NODE_CACHE_SIZE;
    private boolean storeBlockExecutionPayloadSeparately = DEFAULT_STORE_BLOCK_PAYLOAD_SEPARATELY;

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

    public Builder storeVotesEquivocation(boolean storeVotesEquivocation) {
      this.storeVotesEquivocation = storeVotesEquivocation;
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

    public StorageConfiguration build() {
      return new StorageConfiguration(
          eth1DepositContract,
          dataStorageMode,
          dataStorageFrequency,
          dataStorageCreateDbVersion,
          storeNonCanonicalBlocks,
          maxKnownNodeCacheSize,
          storeVotesEquivocation,
          storeBlockExecutionPayloadSeparately,
          spec);
    }

    public Builder storeBlockExecutionPayloadSeparately(
        final boolean storeBlockExecutionPayloadSeparately) {
      this.storeBlockExecutionPayloadSeparately = storeBlockExecutionPayloadSeparately;
      return this;
    }
  }
}
