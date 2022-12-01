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

package tech.pegasys.teku.services.powchain;

import static com.google.common.base.Preconditions.checkNotNull;
import static tech.pegasys.teku.beacon.pow.DepositSnapshotFileLoader.DEFAULT_SNAPSHOT_RESOURCE_PATHS;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.networks.Eth2Network;

public class PowchainConfiguration {
  public static final int DEFAULT_ETH1_LOGS_MAX_BLOCK_RANGE = 10_000;
  public static final boolean DEFAULT_USE_MISSING_DEPOSIT_EVENT_LOGGING = false;
  public static final boolean DEFAULT_DEPOSIT_SNAPSHOT_ENABLED = false;

  private final Spec spec;
  private final List<String> eth1Endpoints;
  private final Eth1Address depositContract;
  private final Optional<UInt64> depositContractDeployBlock;
  private final Optional<String> depositSnapshotPath;
  private final int eth1LogsMaxBlockRange;
  private final boolean useMissingDepositEventLogging;
  private final boolean depositSnapshotEnabled;

  private PowchainConfiguration(
      final Spec spec,
      final List<String> eth1Endpoints,
      final Eth1Address depositContract,
      final Optional<UInt64> depositContractDeployBlock,
      final Optional<String> depositSnapshotPath,
      final int eth1LogsMaxBlockRange,
      final boolean useMissingDepositEventLogging,
      final boolean depositSnapshotEnabled) {
    this.spec = spec;
    this.eth1Endpoints = eth1Endpoints;
    this.depositContract = depositContract;
    this.depositContractDeployBlock = depositContractDeployBlock;
    this.depositSnapshotPath = depositSnapshotPath;
    this.depositSnapshotEnabled = depositSnapshotEnabled;
    this.eth1LogsMaxBlockRange = eth1LogsMaxBlockRange;
    this.useMissingDepositEventLogging = useMissingDepositEventLogging;
  }

  public static Builder builder() {
    return new Builder();
  }

  public boolean isEnabled() {
    return !eth1Endpoints.isEmpty();
  }

  public Spec getSpec() {
    return spec;
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

  public Optional<String> getDepositSnapshotPath() {
    return depositSnapshotPath;
  }

  public boolean isDepositSnapshotEnabled() {
    return depositSnapshotEnabled;
  }

  public int getEth1LogsMaxBlockRange() {
    return eth1LogsMaxBlockRange;
  }

  public boolean useMissingDepositEventLogging() {
    return useMissingDepositEventLogging;
  }

  public static class Builder {
    private Spec spec;
    private List<String> eth1Endpoints = new ArrayList<>();
    private Eth1Address depositContract;
    private Optional<UInt64> depositContractDeployBlock = Optional.empty();
    private Optional<String> depositSnapshotPath = Optional.empty();
    private int eth1LogsMaxBlockRange = DEFAULT_ETH1_LOGS_MAX_BLOCK_RANGE;
    private boolean useMissingDepositEventLogging = DEFAULT_USE_MISSING_DEPOSIT_EVENT_LOGGING;
    private boolean depositSnapshotEnabled = DEFAULT_DEPOSIT_SNAPSHOT_ENABLED;

    private Builder() {}

    public PowchainConfiguration build() {
      validate();
      return new PowchainConfiguration(
          spec,
          eth1Endpoints,
          depositContract,
          depositContractDeployBlock,
          depositSnapshotPath,
          eth1LogsMaxBlockRange,
          useMissingDepositEventLogging,
          depositSnapshotEnabled);
    }

    private void validate() {
      checkNotNull(spec, "Must specify a spec");
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

    public Builder depositContractDefault(final Eth1Address depositContract) {
      if (this.depositContract == null) {
        this.depositContract = depositContract;
      }
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

    public Builder depositContractDeployBlockDefault(
        final Optional<UInt64> depositContractDeployBlock) {
      checkNotNull(depositContractDeployBlock);
      if (this.depositContractDeployBlock.isEmpty()) {
        this.depositContractDeployBlock = depositContractDeployBlock;
      }
      return this;
    }

    public Builder depositSnapshotPath(final String depositSnapshotPath) {
      if (StringUtils.isNotBlank(depositSnapshotPath)) {
        this.depositSnapshotPath = Optional.of(depositSnapshotPath);
      }
      return this;
    }

    public Builder setDepositSnapshotPathForNetwork(final Optional<Eth2Network> eth2Network) {
      checkNotNull(eth2Network);
      if (eth2Network.isPresent()
          && this.depositSnapshotEnabled
          && DEFAULT_SNAPSHOT_RESOURCE_PATHS.containsKey(eth2Network.get())) {
        if (depositSnapshotPath.isPresent()) {
          throw new InvalidConfigurationException(
              "Use either custom deposit tree snapshot path or snapshot bundle");
        }
        this.depositSnapshotPath =
            Optional.of(
                PowchainConfiguration.class
                    .getResource(DEFAULT_SNAPSHOT_RESOURCE_PATHS.get(eth2Network.get()))
                    .toExternalForm());
      }
      return this;
    }

    public Builder depositSnapshotEnabled(final boolean depositSnapshotEnabled) {
      this.depositSnapshotEnabled = depositSnapshotEnabled;
      return this;
    }

    public Builder eth1LogsMaxBlockRange(final int eth1LogsMaxBlockRange) {
      if (eth1LogsMaxBlockRange < 0) {
        throw new InvalidConfigurationException(
            String.format("Invalid eth1LogsMaxBlockRange: %d", eth1LogsMaxBlockRange));
      }
      this.eth1LogsMaxBlockRange = eth1LogsMaxBlockRange;
      return this;
    }

    public Builder specProvider(final Spec spec) {
      this.spec = spec;
      return this;
    }

    public Builder useMissingDepositEventLogging(final boolean useMissingDepositEventLogging) {
      this.useMissingDepositEventLogging = useMissingDepositEventLogging;
      return this;
    }
  }
}
