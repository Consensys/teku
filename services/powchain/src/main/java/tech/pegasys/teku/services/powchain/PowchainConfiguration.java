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

package tech.pegasys.teku.services.powchain;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Optional;
import tech.pegasys.teku.datastructures.eth1.Eth1Address;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class PowchainConfiguration {
  private final String eth1Endpoint;
  private final Eth1Address depositContract;
  private final Optional<UInt64> depositContractDeployBlock;
  private final int eth1LogsMaxBlockRange;

  private PowchainConfiguration(
      final String eth1Endpoint,
      final Eth1Address depositContract,
      final Optional<UInt64> depositContractDeployBlock,
      final int eth1LogsMaxBlockRange) {
    this.eth1Endpoint = eth1Endpoint;
    this.depositContract = depositContract;
    this.depositContractDeployBlock = depositContractDeployBlock;
    this.eth1LogsMaxBlockRange = eth1LogsMaxBlockRange;
  }

  public static Builder builder() {
    return new Builder();
  }

  public boolean isEnabled() {
    return eth1Endpoint != null;
  }

  public String getEth1Endpoint() {
    return eth1Endpoint;
  }

  public Eth1Address getDepositContract() {
    return depositContract;
  }

  public Optional<UInt64> getDepositContractDeployBlock() {
    return depositContractDeployBlock;
  }

  public int getEth1LogsMaxBlockRange() {
    return eth1LogsMaxBlockRange;
  }

  public static class Builder {
    private Optional<String> eth1Endpoint = Optional.empty();
    private Eth1Address depositContract;
    private Optional<UInt64> depositContractDeployBlock = Optional.empty();
    private int eth1LogsMaxBlockRange;

    private Builder() {}

    public PowchainConfiguration build() {
      validate();
      return new PowchainConfiguration(
          eth1Endpoint.orElse(null),
          depositContract,
          depositContractDeployBlock,
          eth1LogsMaxBlockRange);
    }

    private void validate() {
      if (eth1Endpoint.isPresent()) {
        checkNotNull(
            depositContract,
            "Eth1 deposit contract address is required if an eth1 endpoint is specified.");
      }
    }

    public Builder eth1Endpoint(final String eth1Endpoint) {
      checkNotNull(eth1Endpoint);
      return eth1Endpoint(Optional.of(eth1Endpoint));
    }

    public Builder eth1Endpoint(final Optional<String> eth1Endpoint) {
      checkNotNull(eth1Endpoint);
      this.eth1Endpoint = eth1Endpoint.filter(s -> !s.isBlank());
      return this;
    }

    public Builder depositContract(final Eth1Address depositContract) {
      checkNotNull(depositContract);
      this.depositContract = depositContract;
      return this;
    }

    public Builder depositContractDeployBlock(final UInt64 depositContractDeployBlock) {
      checkNotNull(depositContractDeployBlock);
      this.depositContractDeployBlock = Optional.of(depositContractDeployBlock);
      return this;
    }

    public Builder depositContractDeployBlock(final Optional<UInt64> depositContractDeployBlock) {
      checkNotNull(depositContractDeployBlock);
      this.depositContractDeployBlock = depositContractDeployBlock;
      return this;
    }

    public Builder eth1LogsMaxBlockRange(final int eth1LogsMaxBlockRange) {
      this.eth1LogsMaxBlockRange = eth1LogsMaxBlockRange;
      return this;
    }
  }
}
