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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.eth1.Eth1Address;

public class PowchainConfiguration {
  private final List<String> eth1Endpoints;
  private final Eth1Address depositContract;
  private final Optional<UInt64> depositContractDeployBlock;
  private final int eth1LogsMaxBlockRange;

  private PowchainConfiguration(
      final List<String> eth1Endpoints,
      final Eth1Address depositContract,
      final Optional<UInt64> depositContractDeployBlock,
      final int eth1LogsMaxBlockRange) {
    this.eth1Endpoints = eth1Endpoints;
    this.depositContract = depositContract;
    this.depositContractDeployBlock = depositContractDeployBlock;
    this.eth1LogsMaxBlockRange = eth1LogsMaxBlockRange;
  }

  public static Builder builder() {
    return new Builder();
  }

  public boolean isEnabled() {
    return !eth1Endpoints.isEmpty();
  }

  public List<String> getEth1Endpoints() {
    return eth1Endpoints;
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
    private List<String> eth1Endpoints = new ArrayList<>();
    private Eth1Address depositContract;
    private Optional<UInt64> depositContractDeployBlock = Optional.empty();
    private int eth1LogsMaxBlockRange;

    private Builder() {}

    public PowchainConfiguration build() {
      validate();
      return new PowchainConfiguration(
          eth1Endpoints, depositContract, depositContractDeployBlock, eth1LogsMaxBlockRange);
    }

    private void validate() {
      if (!eth1Endpoints.isEmpty()) {
        checkNotNull(
            depositContract,
            "Eth1 deposit contract address is required if an eth1 endpoint is specified.");
      }
    }

    public Builder eth1Endpoints(final List<String> eth1Endpoints) {
      checkNotNull(eth1Endpoints);
      this.eth1Endpoints =
          eth1Endpoints.stream().filter(s -> !s.isBlank()).collect(Collectors.toList());
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
