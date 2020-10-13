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

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import org.apache.commons.lang3.StringUtils;
import tech.pegasys.teku.infrastructure.logging.LoggingDestination;
import tech.pegasys.teku.infrastructure.metrics.MetricsConfig;

/** @deprecated - Use TekuConfiguration where possible. Global application configuration. */
@Deprecated
public class GlobalConfiguration implements MetricsConfig {
  // Network
  private final String constants;
  private final String initialState;
  private final Integer startupTargetPeerCount;
  private final Integer startupTimeoutSeconds;
  private final Integer peerRateLimit;
  private final Integer peerRequestLimit;

  // P2P
  private final boolean p2pEnabled;
  private final String p2pInterface;
  private final int p2pPort;
  private final boolean p2pDiscoveryEnabled;
  private final List<String> p2pDiscoveryBootnodes;
  private final Optional<String> p2pAdvertisedIp;
  private final OptionalInt p2pAdvertisedPort;
  private final String p2pPrivateKeyFile;
  private final int p2pPeerLowerBound;
  private final int p2pPeerUpperBound;
  private final int targetSubnetSubscriberCount;
  private final List<String> p2pStaticPeers;
  private final boolean multiPeerSyncEnabled;

  // Interop
  private final Integer interopGenesisTime;
  private final int interopOwnedValidatorStartIndex;
  private final int interopOwnedValidatorCount;
  private final int interopNumberOfValidators;
  private final boolean interopEnabled;

  // Validator
  private final Path validatorsSlashingProtectionPath;

  // Deposit
  private final Eth1Address eth1DepositContractAddress;
  private final String eth1Endpoint;
  private final boolean eth1DepositsFromStorageEnabled;

  // Logging
  private final boolean logColorEnabled;
  private final boolean logIncludeEventsEnabled;
  private final boolean logIncludeValidatorDutiesEnabled;
  private final LoggingDestination logDestination;
  private final String logFile;
  private final String logFileNamePattern;
  private final boolean logWireCipher;
  private final boolean logWirePlain;
  private final boolean logWireMuxFrames;
  private final boolean logWireGossip;

  // Output
  private final String transitionRecordDirectory;

  // Metrics
  private final boolean metricsEnabled;
  private final int metricsPort;
  private final String metricsInterface;
  private final List<String> metricsCategories;
  private final List<String> metricsHostAllowlist;

  // Database
  private final String dataPath;
  private final StateStorageMode dataStorageMode;
  private final long dataStorageFrequency;
  private final String dataStorageCreateDbVersion;

  // Store
  private final int hotStatePersistenceFrequencyInEpochs;
  private final boolean isBlockProcessingAtStartupDisabled;

  // Beacon REST API
  private final int restApiPort;
  private final boolean restApiDocsEnabled;
  private final boolean restApiEnabled;
  private final String restApiInterface;
  private final List<String> restApiHostAllowlist;

  public static GlobalConfigurationBuilder builder() {
    return new GlobalConfigurationBuilder();
  }

  GlobalConfiguration(
      final String constants,
      final Integer startupTargetPeerCount,
      final Integer startupTimeoutSeconds,
      final Integer peerRateLimit,
      final Integer peerRequestLimit,
      final boolean p2pEnabled,
      final String p2pInterface,
      final int p2pPort,
      final boolean p2pDiscoveryEnabled,
      final List<String> p2pDiscoveryBootnodes,
      final Optional<String> p2pAdvertisedIp,
      final OptionalInt p2pAdvertisedPort,
      final String p2pPrivateKeyFile,
      final int p2pPeerLowerBound,
      final int p2pPeerUpperBound,
      final int targetSubnetSubscriberCount,
      final List<String> p2pStaticPeers,
      final boolean multiPeerSyncEnabled,
      final Integer interopGenesisTime,
      final int interopOwnedValidatorStartIndex,
      final int interopOwnedValidatorCount,
      final String initialState,
      final int interopNumberOfValidators,
      final boolean interopEnabled,
      final Eth1Address eth1DepositContractAddress,
      final String eth1Endpoint,
      final boolean eth1DepositsFromStorageEnabled,
      final boolean logColorEnabled,
      final boolean logIncludeEventsEnabled,
      final boolean logIncludeValidatorDutiesEnabled,
      final LoggingDestination logDestination,
      final String logFile,
      final String logFileNamePattern,
      final boolean logWireCipher,
      final boolean logWirePlain,
      final boolean logWireMuxFrames,
      final boolean logWireGossip,
      final String transitionRecordDirectory,
      final boolean metricsEnabled,
      final int metricsPort,
      final String metricsInterface,
      final List<String> metricsCategories,
      final List<String> metricsHostAllowlist,
      final String dataPath,
      final StateStorageMode dataStorageMode,
      final long dataStorageFrequency,
      final String dataStorageCreateDbVersion,
      final int hotStatePersistenceFrequencyInEpochs,
      final boolean isBlockProcessingAtStartupDisabled,
      final int restApiPort,
      final boolean restApiDocsEnabled,
      final boolean restApiEnabled,
      final String restApiInterface,
      final List<String> restApiHostAllowlist,
      final Path validatorsSlashingProtectionPath) {
    this.constants = constants;
    this.startupTargetPeerCount = startupTargetPeerCount;
    this.startupTimeoutSeconds = startupTimeoutSeconds;
    this.peerRateLimit = peerRateLimit;
    this.peerRequestLimit = peerRequestLimit;
    this.p2pEnabled = p2pEnabled;
    this.p2pInterface = p2pInterface;
    this.p2pPort = p2pPort;
    this.p2pDiscoveryEnabled = p2pDiscoveryEnabled;
    this.p2pDiscoveryBootnodes = p2pDiscoveryBootnodes;
    this.p2pAdvertisedIp = p2pAdvertisedIp;
    this.p2pAdvertisedPort = p2pAdvertisedPort;
    this.p2pPrivateKeyFile = p2pPrivateKeyFile;
    this.p2pPeerLowerBound = p2pPeerLowerBound;
    this.p2pPeerUpperBound = p2pPeerUpperBound;
    this.targetSubnetSubscriberCount = targetSubnetSubscriberCount;
    this.p2pStaticPeers = p2pStaticPeers;
    this.multiPeerSyncEnabled = multiPeerSyncEnabled;
    this.interopGenesisTime = interopGenesisTime;
    this.interopOwnedValidatorStartIndex = interopOwnedValidatorStartIndex;
    this.interopOwnedValidatorCount = interopOwnedValidatorCount;
    this.initialState = initialState;
    this.interopNumberOfValidators = interopNumberOfValidators;
    this.interopEnabled = interopEnabled;
    this.eth1DepositContractAddress = eth1DepositContractAddress;
    this.eth1Endpoint = eth1Endpoint;
    this.eth1DepositsFromStorageEnabled = eth1DepositsFromStorageEnabled;
    this.logColorEnabled = logColorEnabled;
    this.logIncludeEventsEnabled = logIncludeEventsEnabled;
    this.logIncludeValidatorDutiesEnabled = logIncludeValidatorDutiesEnabled;
    this.logDestination = logDestination;
    this.logFile = logFile;
    this.logFileNamePattern = logFileNamePattern;
    this.logWireCipher = logWireCipher;
    this.logWirePlain = logWirePlain;
    this.logWireMuxFrames = logWireMuxFrames;
    this.logWireGossip = logWireGossip;
    this.transitionRecordDirectory = transitionRecordDirectory;
    this.metricsEnabled = metricsEnabled;
    this.metricsPort = metricsPort;
    this.metricsInterface = metricsInterface;
    this.metricsCategories = metricsCategories;
    this.metricsHostAllowlist = metricsHostAllowlist;
    this.dataPath = dataPath;
    this.dataStorageMode = dataStorageMode;
    this.dataStorageFrequency = dataStorageFrequency;
    this.dataStorageCreateDbVersion = dataStorageCreateDbVersion;
    this.hotStatePersistenceFrequencyInEpochs = hotStatePersistenceFrequencyInEpochs;
    this.isBlockProcessingAtStartupDisabled = isBlockProcessingAtStartupDisabled;
    this.restApiPort = restApiPort;
    this.restApiDocsEnabled = restApiDocsEnabled;
    this.restApiEnabled = restApiEnabled;
    this.restApiInterface = restApiInterface;
    this.restApiHostAllowlist = restApiHostAllowlist;
    this.validatorsSlashingProtectionPath = validatorsSlashingProtectionPath;
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

  public boolean isP2pEnabled() {
    return p2pEnabled;
  }

  public String getP2pInterface() {
    return p2pInterface;
  }

  public int getP2pPort() {
    return p2pPort;
  }

  public boolean isP2pDiscoveryEnabled() {
    return p2pDiscoveryEnabled;
  }

  public List<String> getP2pDiscoveryBootnodes() {
    return p2pDiscoveryBootnodes;
  }

  public Optional<String> getP2pAdvertisedIp() {
    return p2pAdvertisedIp;
  }

  public OptionalInt getP2pAdvertisedPort() {
    return p2pAdvertisedPort;
  }

  public String getP2pPrivateKeyFile() {
    return p2pPrivateKeyFile;
  }

  public int getP2pPeerLowerBound() {
    return p2pPeerLowerBound;
  }

  public int getP2pPeerUpperBound() {
    return p2pPeerUpperBound;
  }

  public int getMinimumRandomlySelectedPeerCount() {
    return Math.min(1, p2pPeerLowerBound * 2 / 10);
  }

  public int getTargetSubnetSubscriberCount() {
    return targetSubnetSubscriberCount;
  }

  public List<String> getP2pStaticPeers() {
    return p2pStaticPeers;
  }

  public boolean isMultiPeerSyncEnabled() {
    return multiPeerSyncEnabled;
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

  public String getInitialState() {
    return initialState == null || initialState.isEmpty() ? null : initialState;
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

  public String getEth1Endpoint() {
    return eth1Endpoint;
  }

  public boolean isEth1DepositsFromStorageEnabled() {
    return eth1DepositsFromStorageEnabled;
  }

  public boolean isLogColorEnabled() {
    return logColorEnabled;
  }

  public boolean isLogIncludeEventsEnabled() {
    return logIncludeEventsEnabled;
  }

  public boolean isLogIncludeValidatorDutiesEnabled() {
    return logIncludeValidatorDutiesEnabled;
  }

  public LoggingDestination getLogDestination() {
    return logDestination;
  }

  public String getLogFile() {
    return logFile;
  }

  public String getLogFileNamePattern() {
    return logFileNamePattern;
  }

  public boolean isLogWireCipher() {
    return logWireCipher;
  }

  public boolean isLogWirePlain() {
    return logWirePlain;
  }

  public boolean isLogWireMuxFrames() {
    return logWireMuxFrames;
  }

  public boolean isLogWireGossip() {
    return logWireGossip;
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

  public String getDataPath() {
    return dataPath;
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

  public int getRestApiPort() {
    return restApiPort;
  }

  public boolean isRestApiDocsEnabled() {
    return restApiDocsEnabled;
  }

  public boolean isRestApiEnabled() {
    return restApiEnabled;
  }

  public String getRestApiInterface() {
    return restApiInterface;
  }

  public List<String> getRestApiHostAllowlist() {
    return restApiHostAllowlist;
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

  public Path getValidatorsSlashingProtectionPath() {
    return validatorsSlashingProtectionPath;
  }
}
