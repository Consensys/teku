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

import java.util.Optional;
import tech.pegasys.teku.datastructures.eth1.Eth1Address;
import tech.pegasys.teku.storage.server.DatabaseVersion;
import tech.pegasys.teku.util.config.StateStorageMode;

public class StorageConfiguration {
  private final Optional<Eth1Address> eth1DepositContract;

  private final StateStorageMode dataStorageMode;
  private final long dataStorageFrequency;
  private final DatabaseVersion dataStorageCreateDbVersion;

  private StorageConfiguration(
      final Optional<Eth1Address> eth1DepositContract,
      final StateStorageMode dataStorageMode,
      final long dataStorageFrequency,
      final DatabaseVersion dataStorageCreateDbVersion) {
    this.eth1DepositContract = eth1DepositContract;
    this.dataStorageMode = dataStorageMode;
    this.dataStorageFrequency = dataStorageFrequency;
    this.dataStorageCreateDbVersion = dataStorageCreateDbVersion;
  }

  public static Builder builder() {
    return new Builder();
  }

  public Optional<Eth1Address> getEth1DepositContract() {
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

  public static final class Builder {

    private Optional<Eth1Address> eth1DepositContract;
    private StateStorageMode dataStorageMode;
    private long dataStorageFrequency;
    private DatabaseVersion dataStorageCreateDbVersion;

    private Builder() {}

    public Builder eth1DepositContract(Optional<Eth1Address> eth1DepositContract) {
      this.eth1DepositContract = eth1DepositContract;
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

    public StorageConfiguration build() {
      return new StorageConfiguration(
          eth1DepositContract, dataStorageMode, dataStorageFrequency, dataStorageCreateDbVersion);
    }
  }
}
