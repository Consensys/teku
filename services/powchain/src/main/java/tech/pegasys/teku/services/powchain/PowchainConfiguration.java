/*
 * Copyright Consensys Software Inc., 2026
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
import org.apache.commons.lang3.StringUtils;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.http.UrlSanitizer;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.networks.Eth2Network;

public class PowchainConfiguration {
  public static final boolean DEFAULT_DEPOSIT_SNAPSHOT_ENABLED = true;
  public static final String DEPOSIT_SNAPSHOT_URL_PATH = "/eth/v1/beacon/deposit_snapshot";

  private final Spec spec;
  private final List<String> eth1Endpoints;
  private final Eth1Address depositContract;
  private final Optional<UInt64> depositContractDeployBlock;
  private final DepositTreeSnapshotConfiguration depositTreeSnapshotConfiguration;

  private PowchainConfiguration(
      final Spec spec,
      final List<String> eth1Endpoints,
      final Eth1Address depositContract,
      final Optional<UInt64> depositContractDeployBlock,
      final DepositTreeSnapshotConfiguration depositTreeSnapshotConfiguration) {
    this.spec = spec;
    this.eth1Endpoints = eth1Endpoints;
    this.depositContract = depositContract;
    this.depositContractDeployBlock = depositContractDeployBlock;
    this.depositTreeSnapshotConfiguration = depositTreeSnapshotConfiguration;
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

  public DepositTreeSnapshotConfiguration getDepositTreeSnapshotConfiguration() {
    return depositTreeSnapshotConfiguration;
  }

  public static class Builder {
    private Spec spec;
    private List<String> eth1Endpoints = new ArrayList<>();
    private Eth1Address depositContract;
    private Optional<UInt64> depositContractDeployBlock = Optional.empty();
    private Optional<String> customDepositSnapshotPath = Optional.empty();
    private Optional<String> checkpointSyncDepositSnapshotUrl = Optional.empty();
    private Optional<String> bundledDepositSnapshotPath = Optional.empty();
    private boolean depositSnapshotEnabled = DEFAULT_DEPOSIT_SNAPSHOT_ENABLED;

    private Builder() {}

    public PowchainConfiguration build() {
      validate();

      final boolean isBundledSnapshotEnabled =
          this.depositSnapshotEnabled && this.customDepositSnapshotPath.isEmpty();

      return new PowchainConfiguration(
          spec,
          eth1Endpoints,
          depositContract,
          depositContractDeployBlock,
          new DepositTreeSnapshotConfiguration(
              customDepositSnapshotPath,
              checkpointSyncDepositSnapshotUrl,
              bundledDepositSnapshotPath,
              isBundledSnapshotEnabled));
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
      this.eth1Endpoints = eth1Endpoints.stream().filter(s -> !s.isBlank()).toList();
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

    public Builder customDepositSnapshotPath(final String depositSnapshotPath) {
      if (StringUtils.isNotBlank(depositSnapshotPath)) {
        this.customDepositSnapshotPath = Optional.of(depositSnapshotPath);
      }
      return this;
    }

    public Builder setDepositSnapshotPathForNetwork(final Optional<Eth2Network> eth2Network) {
      checkNotNull(eth2Network);
      if (eth2Network.isPresent()
          && this.depositSnapshotEnabled
          && DEFAULT_SNAPSHOT_RESOURCE_PATHS.containsKey(eth2Network.get())) {
        this.bundledDepositSnapshotPath =
            Optional.of(
                PowchainConfiguration.class
                    .getResource(DEFAULT_SNAPSHOT_RESOURCE_PATHS.get(eth2Network.get()))
                    .toExternalForm());
      }
      return this;
    }

    public Builder checkpointSyncDepositSnapshotUrl(final String checkpointSyncUrl) {
      if (StringUtils.isNotBlank(checkpointSyncUrl)) {
        this.checkpointSyncDepositSnapshotUrl =
            Optional.of(UrlSanitizer.appendPath(checkpointSyncUrl, DEPOSIT_SNAPSHOT_URL_PATH));
      }
      return this;
    }

    public Builder depositSnapshotEnabled(final boolean depositSnapshotEnabled) {
      this.depositSnapshotEnabled = depositSnapshotEnabled;
      return this;
    }

    public Builder specProvider(final Spec spec) {
      this.spec = spec;
      return this;
    }
  }
}
