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

package tech.pegasys.teku.services.chainstorage;

import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.eth1.Eth1Address;
import tech.pegasys.teku.storage.server.DatabaseVersion;
import tech.pegasys.teku.storage.server.StateStorageMode;
import tech.pegasys.teku.storage.server.VersionedDatabaseFactory;

public class StorageConfiguration {

  public static final boolean DEFAULT_STORE_NON_CANONICAL_BLOCKS_ENABLED = false;
  public static final int DEFAULT_MAX_KNOWN_NODE_CACHE_SIZE = 100_000;

  private final Eth1Address eth1DepositContract;

  private final StateStorageMode dataStorageMode;
  private final long dataStorageFrequency;
  private final DatabaseVersion dataStorageCreateDbVersion;
  private final Spec spec;
  private final boolean storeNonCanonicalBlocks;
  private final int maxKnownNodeCacheSize;

  private StorageConfiguration(
      final Eth1Address eth1DepositContract,
      final StateStorageMode dataStorageMode,
      final long dataStorageFrequency,
      final DatabaseVersion dataStorageCreateDbVersion,
      final boolean storeNonCanonicalBlocks,
      final int maxKnownNodeCacheSize,
      final Spec spec) {
    this.eth1DepositContract = eth1DepositContract;
    this.dataStorageMode = dataStorageMode;
    this.dataStorageFrequency = dataStorageFrequency;
    this.dataStorageCreateDbVersion = dataStorageCreateDbVersion;
    this.storeNonCanonicalBlocks = storeNonCanonicalBlocks;
    this.maxKnownNodeCacheSize = maxKnownNodeCacheSize;
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

  public Spec getSpec() {
    return spec;
  }

  public static final class Builder {

    private Eth1Address eth1DepositContract;
    private StateStorageMode dataStorageMode = StateStorageMode.DEFAULT_MODE;
    private long dataStorageFrequency = VersionedDatabaseFactory.DEFAULT_STORAGE_FREQUENCY;
    private DatabaseVersion dataStorageCreateDbVersion = DatabaseVersion.DEFAULT_VERSION;
    private Spec spec;
    private boolean storeNonCanonicalBlocks = DEFAULT_STORE_NON_CANONICAL_BLOCKS_ENABLED;
    private int maxKnownNodeCacheSize = DEFAULT_MAX_KNOWN_NODE_CACHE_SIZE;

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
          spec);
    }
  }
}
