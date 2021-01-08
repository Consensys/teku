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

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Optional;
import tech.pegasys.teku.datastructures.eth1.Eth1Address;

public class StorageConfiguration {
  private final Optional<Eth1Address> eth1DepositContract;

  private StorageConfiguration(final Optional<Eth1Address> eth1DepositContract) {
    this.eth1DepositContract = eth1DepositContract;
  }

  public static Builder builder() {
    return new Builder();
  }

  public Optional<Eth1Address> getEth1DepositContract() {
    return eth1DepositContract;
  }

  public static class Builder {
    private Optional<Eth1Address> eth1DepositContract = Optional.empty();

    private Builder() {}

    public StorageConfiguration build() {
      return new StorageConfiguration(eth1DepositContract);
    }

    public Builder eth1DepositContract(final Optional<Eth1Address> eth1DepositContract) {
      checkNotNull(eth1DepositContract);
      this.eth1DepositContract = eth1DepositContract;
      return this;
    }
  }
}
