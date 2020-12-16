/*
 * Copyright 2020 ConsenSys AG.
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
import java.util.function.Supplier;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

/**
 * @deprecated - Use TekuConfigurationBuilder where possible. Global application configuration
 *     builder.
 */
@Deprecated
public class GlobalConfigurationBuilder {

  private String constants;
  private Integer startupTargetPeerCount;
  private Integer startupTimeoutSeconds;
  private Integer peerRateLimit;
  private Integer peerRequestLimit;
  private Integer interopGenesisTime;
  private int interopOwnedValidatorStartIndex;
  private int interopOwnedValidatorCount;
  private int interopNumberOfValidators;
  private boolean interopEnabled;
  private Eth1Address eth1DepositContractAddress;
  private String eth1Endpoint;
  private Optional<UInt64> eth1DepositContractDeployBlock = Optional.empty();
  private int eth1LogsMaxBlockRange;
  private boolean eth1DepositsFromStorageEnabled;
  private String transitionRecordDirectory;
  private boolean metricsEnabled;
  private int metricsPort;
  private String metricsInterface;
  private List<String> metricsCategories;
  private List<String> metricsHostAllowlist;
  private StateStorageMode dataStorageMode;
  private String dataStorageCreateDbVersion;
  private int hotStatePersistenceFrequencyInEpochs;
  private boolean isBlockProcessingAtStartupDisabled;
  private long dataStorageFrequency;
  private NetworkDefinition network;

  public GlobalConfigurationBuilder setConstants(final String constants) {
    this.constants = constants;
    return this;
  }

  public GlobalConfigurationBuilder setStartupTargetPeerCount(
      final Integer startupTargetPeerCount) {
    this.startupTargetPeerCount = startupTargetPeerCount;
    return this;
  }

  public GlobalConfigurationBuilder setStartupTimeoutSeconds(final Integer startupTimeoutSeconds) {
    this.startupTimeoutSeconds = startupTimeoutSeconds;
    return this;
  }

  public GlobalConfigurationBuilder setPeerRateLimit(final Integer peerRateLimit) {
    this.peerRateLimit = peerRateLimit;
    return this;
  }

  public GlobalConfigurationBuilder setPeerRequestLimit(final Integer peerRequestLimit) {
    this.peerRequestLimit = peerRequestLimit;
    return this;
  }

  public GlobalConfigurationBuilder setInteropGenesisTime(final Integer interopGenesisTime) {
    this.interopGenesisTime = interopGenesisTime;
    return this;
  }

  public GlobalConfigurationBuilder setInteropOwnedValidatorStartIndex(
      final int interopOwnedValidatorStartIndex) {
    this.interopOwnedValidatorStartIndex = interopOwnedValidatorStartIndex;
    return this;
  }

  public GlobalConfigurationBuilder setInteropOwnedValidatorCount(
      final int interopOwnedValidatorCount) {
    this.interopOwnedValidatorCount = interopOwnedValidatorCount;
    return this;
  }

  public GlobalConfigurationBuilder setInteropNumberOfValidators(
      final int interopNumberOfValidators) {
    this.interopNumberOfValidators = interopNumberOfValidators;
    return this;
  }

  public GlobalConfigurationBuilder setInteropEnabled(final boolean interopEnabled) {
    this.interopEnabled = interopEnabled;
    return this;
  }

  public GlobalConfigurationBuilder setEth1DepositContractAddress(
      final Eth1Address eth1DepositContractAddress) {
    this.eth1DepositContractAddress = eth1DepositContractAddress;
    return this;
  }

  public GlobalConfigurationBuilder setEth1Endpoint(final String eth1Endpoint) {
    this.eth1Endpoint = eth1Endpoint;
    return this;
  }

  public GlobalConfigurationBuilder setEth1DepositContractDeployBlock(
      final Optional<UInt64> eth1DepositContractDeployBlock) {
    this.eth1DepositContractDeployBlock = eth1DepositContractDeployBlock;
    return this;
  }

  public GlobalConfigurationBuilder setEth1LogsMaxBlockRange(final int eth1LogsMaxBlockRange) {
    this.eth1LogsMaxBlockRange = eth1LogsMaxBlockRange;
    return this;
  }

  public GlobalConfigurationBuilder setEth1DepositsFromStorageEnabled(
      final boolean eth1DepositsFromStorageEnabled) {
    this.eth1DepositsFromStorageEnabled = eth1DepositsFromStorageEnabled;
    return this;
  }

  public GlobalConfigurationBuilder setTransitionRecordDirectory(
      final String transitionRecordDirectory) {
    this.transitionRecordDirectory = transitionRecordDirectory;
    return this;
  }

  public GlobalConfigurationBuilder setMetricsEnabled(final boolean metricsEnabled) {
    this.metricsEnabled = metricsEnabled;
    return this;
  }

  public GlobalConfigurationBuilder setMetricsPort(final int metricsPort) {
    this.metricsPort = metricsPort;
    return this;
  }

  public GlobalConfigurationBuilder setMetricsInterface(final String metricsInterface) {
    this.metricsInterface = metricsInterface;
    return this;
  }

  public GlobalConfigurationBuilder setMetricsCategories(final List<String> metricsCategories) {
    this.metricsCategories = metricsCategories;
    return this;
  }

  public GlobalConfigurationBuilder setMetricsHostAllowlist(
      final List<String> metricsHostAllowlist) {
    this.metricsHostAllowlist = metricsHostAllowlist;
    return this;
  }

  public GlobalConfigurationBuilder setDataStorageMode(final StateStorageMode dataStorageMode) {
    this.dataStorageMode = dataStorageMode;
    return this;
  }

  public GlobalConfigurationBuilder setDataStorageFrequency(final long dataStorageFrequency) {
    this.dataStorageFrequency = dataStorageFrequency;
    return this;
  }

  public GlobalConfigurationBuilder setDataStorageCreateDbVersion(
      final String dataStorageCreateDbVersion) {
    this.dataStorageCreateDbVersion = dataStorageCreateDbVersion;
    return this;
  }

  public GlobalConfigurationBuilder setHotStatePersistenceFrequencyInEpochs(
      final int hotStatePersistenceFrequencyInEpochs) {
    this.hotStatePersistenceFrequencyInEpochs = hotStatePersistenceFrequencyInEpochs;
    return this;
  }

  public GlobalConfigurationBuilder setIsBlockProcessingAtStartupDisabled(
      final boolean isDisabled) {
    this.isBlockProcessingAtStartupDisabled = isDisabled;
    return this;
  }

  public GlobalConfigurationBuilder setNetwork(final NetworkDefinition network) {
    this.network = network;
    return this;
  }

  public GlobalConfiguration build() {
    if (network != null) {
      constants = getOrDefault(constants, network::getConstants);
      startupTargetPeerCount =
          getOrDefault(startupTargetPeerCount, network::getStartupTargetPeerCount);
      startupTimeoutSeconds =
          getOrDefault(startupTimeoutSeconds, network::getStartupTimeoutSeconds);
      eth1DepositContractAddress =
          getOrOptionalDefault(eth1DepositContractAddress, network::getEth1DepositContractAddress);
      eth1Endpoint = getOrOptionalDefault(eth1Endpoint, network::getEth1Endpoint);
      eth1DepositContractDeployBlock = network.getEth1DepositContractDeployBlock();
    }

    if (eth1DepositContractAddress == null && eth1Endpoint != null) {
      throw new InvalidConfigurationException(
          "eth1-deposit-contract-address is required if eth1-endpoint is specified.");
    }

    return new GlobalConfiguration(
        network,
        constants,
        startupTargetPeerCount,
        startupTimeoutSeconds,
        peerRateLimit,
        peerRequestLimit,
        interopGenesisTime,
        interopOwnedValidatorStartIndex,
        interopOwnedValidatorCount,
        interopNumberOfValidators,
        interopEnabled,
        eth1DepositContractAddress,
        eth1Endpoint,
        eth1DepositContractDeployBlock,
        eth1LogsMaxBlockRange,
        eth1DepositsFromStorageEnabled,
        transitionRecordDirectory,
        metricsEnabled,
        metricsPort,
        metricsInterface,
        metricsCategories,
        metricsHostAllowlist,
        dataStorageMode,
        dataStorageFrequency,
        dataStorageCreateDbVersion,
        hotStatePersistenceFrequencyInEpochs,
        isBlockProcessingAtStartupDisabled);
  }

  private <T> T getOrDefault(final T explicitValue, final Supplier<T> predefinedNetworkValue) {
    return getOrOptionalDefault(explicitValue, () -> Optional.of(predefinedNetworkValue.get()));
  }

  private <T> T getOrOptionalDefault(
      final T explicitValue, final Supplier<Optional<T>> predefinedNetworkValue) {
    return explicitValue != null ? explicitValue : predefinedNetworkValue.get().orElse(null);
  }
}
