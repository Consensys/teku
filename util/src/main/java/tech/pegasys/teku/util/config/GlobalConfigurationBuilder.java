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
import java.util.OptionalInt;
import java.util.function.Supplier;
import tech.pegasys.teku.infrastructure.logging.LoggingDestination;

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
  private boolean p2pEnabled;
  private String p2pInterface;
  private int p2pPort;
  private boolean p2pDiscoveryEnabled;
  private List<String> p2pDiscoveryBootnodes;
  private Optional<String> p2pAdvertisedIp = Optional.empty();
  private OptionalInt p2pAdvertisedPort = OptionalInt.empty();
  private String p2pPrivateKeyFile;
  private int p2pPeerLowerBound;
  private int p2pPeerUpperBound;
  private int targetSubnetSubscriberCount;
  private List<String> p2pStaticPeers;
  private boolean multiPeerSyncEnabled = false;
  private Integer interopGenesisTime;
  private int interopOwnedValidatorStartIndex;
  private int interopOwnedValidatorCount;
  private String initialState;
  private int interopNumberOfValidators;
  private boolean interopEnabled;
  private Eth1Address eth1DepositContractAddress;
  private String eth1Endpoint;
  private boolean eth1DepositsFromStorageEnabled;
  private boolean logColorEnabled;
  private boolean logIncludeEventsEnabled;
  private boolean logIncludeValidatorDutiesEnabled;
  private LoggingDestination logDestination;
  private String logFile;
  private String logFileNamePattern;
  private boolean logWireCipher;
  private boolean logWirePlain;
  private boolean logWireMuxFrames;
  private boolean logWireGossip;
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
  private int restApiPort;
  private boolean restApiDocsEnabled;
  private boolean restApiEnabled;
  private String restApiInterface;
  private List<String> restApiHostAllowlist;
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

  public GlobalConfigurationBuilder setP2pEnabled(final boolean p2pEnabled) {
    this.p2pEnabled = p2pEnabled;
    return this;
  }

  public GlobalConfigurationBuilder setP2pInterface(final String p2pInterface) {
    this.p2pInterface = p2pInterface;
    return this;
  }

  public GlobalConfigurationBuilder setP2pPort(final int p2pPort) {
    this.p2pPort = p2pPort;
    return this;
  }

  public GlobalConfigurationBuilder setP2pDiscoveryEnabled(final boolean p2pDiscoveryEnabled) {
    this.p2pDiscoveryEnabled = p2pDiscoveryEnabled;
    return this;
  }

  public GlobalConfigurationBuilder setP2pDiscoveryBootnodes(
      final List<String> p2pDiscoveryBootnodes) {
    this.p2pDiscoveryBootnodes = p2pDiscoveryBootnodes;
    return this;
  }

  public GlobalConfigurationBuilder setP2pAdvertisedIp(final Optional<String> p2pAdvertisedIp) {
    this.p2pAdvertisedIp = p2pAdvertisedIp;
    return this;
  }

  public GlobalConfigurationBuilder setP2pAdvertisedPort(final OptionalInt p2pAdvertisedPort) {
    this.p2pAdvertisedPort = p2pAdvertisedPort;
    return this;
  }

  public GlobalConfigurationBuilder setP2pPrivateKeyFile(final String p2pPrivateKeyFile) {
    this.p2pPrivateKeyFile = p2pPrivateKeyFile;
    return this;
  }

  public GlobalConfigurationBuilder setP2pPeerLowerBound(final int p2pPeerLowerBound) {
    this.p2pPeerLowerBound = p2pPeerLowerBound;
    return this;
  }

  public GlobalConfigurationBuilder setP2pPeerUpperBound(final int p2pPeerUpperBound) {
    this.p2pPeerUpperBound = p2pPeerUpperBound;
    return this;
  }

  public GlobalConfigurationBuilder setTargetSubnetSubscriberCount(
      final int targetSubnetSubscriberCount) {
    this.targetSubnetSubscriberCount = targetSubnetSubscriberCount;
    return this;
  }

  public GlobalConfigurationBuilder setP2pStaticPeers(final List<String> p2pStaticPeers) {
    this.p2pStaticPeers = p2pStaticPeers;
    return this;
  }

  public GlobalConfigurationBuilder setMultiPeerSyncEnabled(final boolean multiPeerSyncEnabled) {
    this.multiPeerSyncEnabled = multiPeerSyncEnabled;
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

  public GlobalConfigurationBuilder setInitialState(final String initialState) {
    this.initialState = initialState;
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

  public GlobalConfigurationBuilder setEth1DepositsFromStorageEnabled(
      final boolean eth1DepositsFromStorageEnabled) {
    this.eth1DepositsFromStorageEnabled = eth1DepositsFromStorageEnabled;
    return this;
  }

  public GlobalConfigurationBuilder setLogColorEnabled(final boolean logColorEnabled) {
    this.logColorEnabled = logColorEnabled;
    return this;
  }

  public GlobalConfigurationBuilder setLogIncludeEventsEnabled(
      final boolean logIncludeEventsEnabled) {
    this.logIncludeEventsEnabled = logIncludeEventsEnabled;
    return this;
  }

  public GlobalConfigurationBuilder setLogIncludeValidatorDutiesEnabled(
      final boolean logIncludeValidatorDutiesEnabled) {
    this.logIncludeValidatorDutiesEnabled = logIncludeValidatorDutiesEnabled;
    return this;
  }

  public GlobalConfigurationBuilder setLogDestination(final LoggingDestination logDestination) {
    this.logDestination = logDestination;
    return this;
  }

  public GlobalConfigurationBuilder setLogFile(final String logFile) {
    this.logFile = logFile;
    return this;
  }

  public GlobalConfigurationBuilder setLogFileNamePattern(final String logFileNamePattern) {
    this.logFileNamePattern = logFileNamePattern;
    return this;
  }

  public GlobalConfigurationBuilder setLogWireCipher(boolean logWireCipher) {
    this.logWireCipher = logWireCipher;
    return this;
  }

  public GlobalConfigurationBuilder setLogWirePlain(boolean logWirePlain) {
    this.logWirePlain = logWirePlain;
    return this;
  }

  public GlobalConfigurationBuilder setLogWireMuxFrames(boolean logWireMuxFrames) {
    this.logWireMuxFrames = logWireMuxFrames;
    return this;
  }

  public GlobalConfigurationBuilder setLogWireGossip(boolean logWireGossip) {
    this.logWireGossip = logWireGossip;
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

  public GlobalConfigurationBuilder setRestApiPort(final int restApiPort) {
    this.restApiPort = restApiPort;
    return this;
  }

  public GlobalConfigurationBuilder setRestApiDocsEnabled(final boolean restApiDocsEnabled) {
    this.restApiDocsEnabled = restApiDocsEnabled;
    return this;
  }

  public GlobalConfigurationBuilder setRestApiEnabled(final boolean restApiEnabled) {
    this.restApiEnabled = restApiEnabled;
    return this;
  }

  public GlobalConfigurationBuilder setRestApiInterface(final String restApiInterface) {
    this.restApiInterface = restApiInterface;
    return this;
  }

  public GlobalConfigurationBuilder setRestApiHostAllowlist(
      final List<String> restApiHostAllowlist) {
    this.restApiHostAllowlist = restApiHostAllowlist;
    return this;
  }

  public GlobalConfigurationBuilder setNetwork(final NetworkDefinition network) {
    this.network = network;
    return this;
  }

  public GlobalConfiguration build() {
    if (network != null) {
      constants = getOrDefault(constants, network::getConstants);
      initialState = getOrOptionalDefault(initialState, network::getInitialState);
      startupTargetPeerCount =
          getOrDefault(startupTargetPeerCount, network::getStartupTargetPeerCount);
      startupTimeoutSeconds =
          getOrDefault(startupTimeoutSeconds, network::getStartupTimeoutSeconds);
      eth1DepositContractAddress =
          getOrOptionalDefault(eth1DepositContractAddress, network::getEth1DepositContractAddress);
      p2pDiscoveryBootnodes = getOrDefault(p2pDiscoveryBootnodes, network::getDiscoveryBootnodes);
      eth1Endpoint = getOrOptionalDefault(eth1Endpoint, network::getEth1Endpoint);
    }

    if (eth1DepositContractAddress == null && eth1Endpoint != null) {
      throw new InvalidConfigurationException(
          "eth1-deposit-contract-address is required if eth1-endpoint is specified.");
    }

    return new GlobalConfiguration(
        constants,
        startupTargetPeerCount,
        startupTimeoutSeconds,
        peerRateLimit,
        peerRequestLimit,
        p2pEnabled,
        p2pInterface,
        p2pPort,
        p2pDiscoveryEnabled,
        p2pDiscoveryBootnodes,
        p2pAdvertisedIp,
        p2pAdvertisedPort,
        p2pPrivateKeyFile,
        p2pPeerLowerBound,
        p2pPeerUpperBound,
        targetSubnetSubscriberCount,
        p2pStaticPeers,
        multiPeerSyncEnabled,
        interopGenesisTime,
        interopOwnedValidatorStartIndex,
        interopOwnedValidatorCount,
        initialState,
        interopNumberOfValidators,
        interopEnabled,
        eth1DepositContractAddress,
        eth1Endpoint,
        eth1DepositsFromStorageEnabled,
        logColorEnabled,
        logIncludeEventsEnabled,
        logIncludeValidatorDutiesEnabled,
        logDestination,
        logFile,
        logFileNamePattern,
        logWireCipher,
        logWirePlain,
        logWireMuxFrames,
        logWireGossip,
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
        isBlockProcessingAtStartupDisabled,
        restApiPort,
        restApiDocsEnabled,
        restApiEnabled,
        restApiInterface,
        restApiHostAllowlist);
  }

  private <T> T getOrDefault(final T explicitValue, final Supplier<T> predefinedNetworkValue) {
    return getOrOptionalDefault(explicitValue, () -> Optional.of(predefinedNetworkValue.get()));
  }

  private <T> T getOrOptionalDefault(
      final T explicitValue, final Supplier<Optional<T>> predefinedNetworkValue) {
    return explicitValue != null ? explicitValue : predefinedNetworkValue.get().orElse(null);
  }
}
