/*
 * Copyright 2019 ConsenSys AG.
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

package tech.pegasys.teku.util.config;

import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import tech.pegasys.teku.infrastructure.metrics.MetricsConfig;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

/** @deprecated - Use TekuConfiguration where possible. Global application configuration. */
@Deprecated
public class GlobalConfiguration implements MetricsConfig {
  // Network
  private final NetworkDefinition networkDefinition;
  private final String constants;
  private final Integer startupTargetPeerCount;
  private final Integer startupTimeoutSeconds;
  private final Integer peerRateLimit;
  private final Integer peerRequestLimit;

  // Interop
  private final Integer interopGenesisTime;
  private final int interopOwnedValidatorStartIndex;
  private final int interopOwnedValidatorCount;
  private final int interopNumberOfValidators;
  private final boolean interopEnabled;

  // Deposit
  private final Eth1Address eth1DepositContractAddress;
  private final String eth1Endpoint;
  private final boolean eth1DepositsFromStorageEnabled;
  private final Optional<UInt64> eth1DepositContractDeployBlock;
  private final int eth1LogsMaxBlockRange;

  // Output
  private final String transitionRecordDirectory;

  // Metrics
  private final boolean metricsEnabled;
  private final int metricsPort;
  private final String metricsInterface;
  private final List<String> metricsCategories;
  private final List<String> metricsHostAllowlist;

  // Database
  private final StateStorageMode dataStorageMode;
  private final long dataStorageFrequency;
  private final String dataStorageCreateDbVersion;

  // Store
  private final int hotStatePersistenceFrequencyInEpochs;
  private final boolean isBlockProcessingAtStartupDisabled;

  public static GlobalConfigurationBuilder builder() {
    return new GlobalConfigurationBuilder();
  }

  GlobalConfiguration(
      final NetworkDefinition networkDefinition,
      final String constants,
      final Integer startupTargetPeerCount,
      final Integer startupTimeoutSeconds,
      final Integer peerRateLimit,
      final Integer peerRequestLimit,
      final Integer interopGenesisTime,
      final int interopOwnedValidatorStartIndex,
      final int interopOwnedValidatorCount,
      final int interopNumberOfValidators,
      final boolean interopEnabled,
      final Eth1Address eth1DepositContractAddress,
      final String eth1Endpoint,
      final Optional<UInt64> eth1DepositContractDeployBlock,
      final int eth1LogsMaxBlockRange,
      final boolean eth1DepositsFromStorageEnabled,
      final String transitionRecordDirectory,
      final boolean metricsEnabled,
      final int metricsPort,
      final String metricsInterface,
      final List<String> metricsCategories,
      final List<String> metricsHostAllowlist,
      final StateStorageMode dataStorageMode,
      final long dataStorageFrequency,
      final String dataStorageCreateDbVersion,
      final int hotStatePersistenceFrequencyInEpochs,
      final boolean isBlockProcessingAtStartupDisabled) {
    this.networkDefinition = networkDefinition;
    this.constants = constants;
    this.startupTargetPeerCount = startupTargetPeerCount;
    this.startupTimeoutSeconds = startupTimeoutSeconds;
    this.peerRateLimit = peerRateLimit;
    this.peerRequestLimit = peerRequestLimit;
    this.interopGenesisTime = interopGenesisTime;
    this.interopOwnedValidatorStartIndex = interopOwnedValidatorStartIndex;
    this.interopOwnedValidatorCount = interopOwnedValidatorCount;
    this.interopNumberOfValidators = interopNumberOfValidators;
    this.interopEnabled = interopEnabled;
    this.eth1DepositContractAddress = eth1DepositContractAddress;
    this.eth1Endpoint = eth1Endpoint;
    this.eth1DepositContractDeployBlock = eth1DepositContractDeployBlock;
    this.eth1LogsMaxBlockRange = eth1LogsMaxBlockRange;
    this.eth1DepositsFromStorageEnabled = eth1DepositsFromStorageEnabled;
    this.transitionRecordDirectory = transitionRecordDirectory;
    this.metricsEnabled = metricsEnabled;
    this.metricsPort = metricsPort;
    this.metricsInterface = metricsInterface;
    this.metricsCategories = metricsCategories;
    this.metricsHostAllowlist = metricsHostAllowlist;
    this.dataStorageMode = dataStorageMode;
    this.dataStorageFrequency = dataStorageFrequency;
    this.dataStorageCreateDbVersion = dataStorageCreateDbVersion;
    this.hotStatePersistenceFrequencyInEpochs = hotStatePersistenceFrequencyInEpochs;
    this.isBlockProcessingAtStartupDisabled = isBlockProcessingAtStartupDisabled;
  }

  public NetworkDefinition getNetworkDefinition() {
    return networkDefinition;
  }

  public String getConstants() {
    return constants;
  }

  public int getStartupTargetPeerCount() {
    return startupTargetPeerCount;
  }

  public int getStartupTimeoutSeconds() {
    return startupTimeoutSeconds;
  }

  public int getPeerRateLimit() {
    return peerRateLimit;
  }

  public int getPeerRequestLimit() {
    return peerRequestLimit;
  }

  public Integer getInteropGenesisTime() {
    if (interopGenesisTime == 0) {
      return Math.toIntExact((System.currentTimeMillis() / 1000) + 5);
    } else {
      return interopGenesisTime;
    }
  }

  public int getInteropOwnedValidatorStartIndex() {
    return interopOwnedValidatorStartIndex;
  }

  public int getInteropOwnedValidatorCount() {
    return interopOwnedValidatorCount;
  }

  public int getInteropNumberOfValidators() {
    return interopNumberOfValidators;
  }

  public boolean isInteropEnabled() {
    return interopEnabled;
  }

  public boolean isEth1Enabled() {
    return !StringUtils.isEmpty(eth1Endpoint);
  }

  public Eth1Address getEth1DepositContractAddress() {
    return eth1DepositContractAddress;
  }

  public Optional<UInt64> getEth1DepositContractDeployBlock() {
    return eth1DepositContractDeployBlock;
  }

  public int getEth1LogsMaxBlockRange() {
    return eth1LogsMaxBlockRange;
  }

  public String getEth1Endpoint() {
    return eth1Endpoint;
  }

  public boolean isEth1DepositsFromStorageEnabled() {
    return eth1DepositsFromStorageEnabled;
  }

  public String getTransitionRecordDirectory() {
    return transitionRecordDirectory;
  }

  @Override
  public boolean isMetricsEnabled() {
    return metricsEnabled;
  }

  @Override
  public int getMetricsPort() {
    return metricsPort;
  }

  @Override
  public String getMetricsInterface() {
    return metricsInterface;
  }

  @Override
  public List<String> getMetricsCategories() {
    return metricsCategories;
  }

  @Override
  public List<String> getMetricsHostAllowlist() {
    return metricsHostAllowlist;
  }

  public StateStorageMode getDataStorageMode() {
    return dataStorageMode;
  }

  public long getDataStorageFrequency() {
    return dataStorageFrequency;
  }

  public String getDataStorageCreateDbVersion() {
    return dataStorageCreateDbVersion;
  }

  public int getHotStatePersistenceFrequencyInEpochs() {
    return hotStatePersistenceFrequencyInEpochs;
  }

  public boolean isBlockProcessingAtStartupDisabled() {
    return isBlockProcessingAtStartupDisabled;
  }

  public void validate() throws IllegalArgumentException {
    final int interopNumberOfValidators = getInteropNumberOfValidators();
    if (interopNumberOfValidators < Constants.SLOTS_PER_EPOCH) {
      throw new InvalidConfigurationException(
          String.format(
              "Invalid configuration. Interop number of validators [%d] must be greater than or equal to [%d]",
              interopNumberOfValidators, Constants.SLOTS_PER_EPOCH));
    }
  }
}
