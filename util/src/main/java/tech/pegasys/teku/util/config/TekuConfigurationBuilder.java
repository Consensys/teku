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
import org.apache.tuweni.bytes.Bytes32;

public class TekuConfigurationBuilder {
  private static final boolean DEFAULT_P2P_SNAPPY_ENABLED = false;
  private String constants;
  private Integer startupTargetPeerCount;
  private Integer startupTimeoutSeconds;
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
  private List<String> p2pStaticPeers;
  private Boolean p2pSnappyEnabled;
  private Integer interopGenesisTime;
  private int interopOwnedValidatorStartIndex;
  private int interopOwnedValidatorCount;
  private String initialState;
  private int interopNumberOfValidators;
  private boolean interopEnabled;
  private String validatorsKeyFile;
  private List<String> validatorKeystoreFiles;
  private List<String> validatorKeystorePasswordFiles;
  private List<String> validatorExternalSignerPublicKeys;
  private String validatorExternalSignerUrl;
  private int validatorExternalSignerTimeout;
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
  private String dataPath;
  private StateStorageMode dataStorageMode;
  private String dataStorageCreateDbVersion;
  private long dataStorageFrequency;
  private int restApiPort;
  private boolean restApiDocsEnabled;
  private boolean restApiEnabled;
  private String restApiInterface;
  private List<String> restApiHostAllowlist;
  private NetworkDefinition network;
  private Bytes32 graffiti;

  public TekuConfigurationBuilder setConstants(final String constants) {
    this.constants = constants;
    return this;
  }

  public TekuConfigurationBuilder setStartupTargetPeerCount(final Integer startupTargetPeerCount) {
    this.startupTargetPeerCount = startupTargetPeerCount;
    return this;
  }

  public TekuConfigurationBuilder setStartupTimeoutSeconds(final Integer startupTimeoutSeconds) {
    this.startupTimeoutSeconds = startupTimeoutSeconds;
    return this;
  }

  public TekuConfigurationBuilder setP2pEnabled(final boolean p2pEnabled) {
    this.p2pEnabled = p2pEnabled;
    return this;
  }

  public TekuConfigurationBuilder setP2pInterface(final String p2pInterface) {
    this.p2pInterface = p2pInterface;
    return this;
  }

  public TekuConfigurationBuilder setP2pPort(final int p2pPort) {
    this.p2pPort = p2pPort;
    return this;
  }

  public TekuConfigurationBuilder setP2pDiscoveryEnabled(final boolean p2pDiscoveryEnabled) {
    this.p2pDiscoveryEnabled = p2pDiscoveryEnabled;
    return this;
  }

  public TekuConfigurationBuilder setP2pDiscoveryBootnodes(
      final List<String> p2pDiscoveryBootnodes) {
    this.p2pDiscoveryBootnodes = p2pDiscoveryBootnodes;
    return this;
  }

  public TekuConfigurationBuilder setP2pAdvertisedIp(final Optional<String> p2pAdvertisedIp) {
    this.p2pAdvertisedIp = p2pAdvertisedIp;
    return this;
  }

  public TekuConfigurationBuilder setP2pAdvertisedPort(final OptionalInt p2pAdvertisedPort) {
    this.p2pAdvertisedPort = p2pAdvertisedPort;
    return this;
  }

  public TekuConfigurationBuilder setP2pPrivateKeyFile(final String p2pPrivateKeyFile) {
    this.p2pPrivateKeyFile = p2pPrivateKeyFile;
    return this;
  }

  public TekuConfigurationBuilder setP2pPeerLowerBound(final int p2pPeerLowerBound) {
    this.p2pPeerLowerBound = p2pPeerLowerBound;
    return this;
  }

  public TekuConfigurationBuilder setP2pPeerUpperBound(final int p2pPeerUpperBound) {
    this.p2pPeerUpperBound = p2pPeerUpperBound;
    return this;
  }

  public TekuConfigurationBuilder setP2pStaticPeers(final List<String> p2pStaticPeers) {
    this.p2pStaticPeers = p2pStaticPeers;
    return this;
  }

  public TekuConfigurationBuilder setP2pSnappyEnabled(final Boolean p2pSnappyEnabled) {
    this.p2pSnappyEnabled = p2pSnappyEnabled;
    return this;
  }

  public TekuConfigurationBuilder setInteropGenesisTime(final Integer interopGenesisTime) {
    this.interopGenesisTime = interopGenesisTime;
    return this;
  }

  public TekuConfigurationBuilder setInteropOwnedValidatorStartIndex(
      final int interopOwnedValidatorStartIndex) {
    this.interopOwnedValidatorStartIndex = interopOwnedValidatorStartIndex;
    return this;
  }

  public TekuConfigurationBuilder setInteropOwnedValidatorCount(
      final int interopOwnedValidatorCount) {
    this.interopOwnedValidatorCount = interopOwnedValidatorCount;
    return this;
  }

  public TekuConfigurationBuilder setInitialState(final String initialState) {
    this.initialState = initialState;
    return this;
  }

  public TekuConfigurationBuilder setInteropNumberOfValidators(
      final int interopNumberOfValidators) {
    this.interopNumberOfValidators = interopNumberOfValidators;
    return this;
  }

  public TekuConfigurationBuilder setInteropEnabled(final boolean interopEnabled) {
    this.interopEnabled = interopEnabled;
    return this;
  }

  public TekuConfigurationBuilder setValidatorKeyFile(final String validatorsKeyFile) {
    this.validatorsKeyFile = validatorsKeyFile;
    return this;
  }

  public TekuConfigurationBuilder setValidatorKeystoreFiles(
      final List<String> validatorKeystoreFiles) {
    this.validatorKeystoreFiles = validatorKeystoreFiles;
    return this;
  }

  public TekuConfigurationBuilder setValidatorKeystorePasswordFiles(
      final List<String> validatorKeystorePasswordFiles) {
    this.validatorKeystorePasswordFiles = validatorKeystorePasswordFiles;
    return this;
  }

  public TekuConfigurationBuilder setValidatorExternalSignerPublicKeys(
      final List<String> validatorsExternalSignerPublicKeys) {
    this.validatorExternalSignerPublicKeys = validatorsExternalSignerPublicKeys;
    return this;
  }

  public TekuConfigurationBuilder setValidatorExternalSignerUrl(
      final String validatorsExternalSignerUrl) {
    this.validatorExternalSignerUrl = validatorsExternalSignerUrl;
    return this;
  }

  public TekuConfigurationBuilder setValidatorExternalSignerTimeout(
      final int validatorsExternalSignerTimeout) {
    this.validatorExternalSignerTimeout = validatorsExternalSignerTimeout;
    return this;
  }

  public TekuConfigurationBuilder setEth1DepositContractAddress(
      final Eth1Address eth1DepositContractAddress) {
    this.eth1DepositContractAddress = eth1DepositContractAddress;
    return this;
  }

  public TekuConfigurationBuilder setEth1Endpoint(final String eth1Endpoint) {
    this.eth1Endpoint = eth1Endpoint;
    return this;
  }

  public TekuConfigurationBuilder setEth1DepositsFromStorageEnabled(
      final boolean eth1DepositsFromStorageEnabled) {
    this.eth1DepositsFromStorageEnabled = eth1DepositsFromStorageEnabled;
    return this;
  }

  public TekuConfigurationBuilder setLogColorEnabled(final boolean logColorEnabled) {
    this.logColorEnabled = logColorEnabled;
    return this;
  }

  public TekuConfigurationBuilder setLogIncludeEventsEnabled(
      final boolean logIncludeEventsEnabled) {
    this.logIncludeEventsEnabled = logIncludeEventsEnabled;
    return this;
  }

  public TekuConfigurationBuilder setLogIncludeValidatorDutiesEnabled(
      final boolean logIncludeValidatorDutiesEnabled) {
    this.logIncludeValidatorDutiesEnabled = logIncludeValidatorDutiesEnabled;
    return this;
  }

  public TekuConfigurationBuilder setLogDestination(final LoggingDestination logDestination) {
    this.logDestination = logDestination;
    return this;
  }

  public TekuConfigurationBuilder setLogFile(final String logFile) {
    this.logFile = logFile;
    return this;
  }

  public TekuConfigurationBuilder setLogFileNamePattern(final String logFileNamePattern) {
    this.logFileNamePattern = logFileNamePattern;
    return this;
  }

  public TekuConfigurationBuilder setLogWireCipher(boolean logWireCipher) {
    this.logWireCipher = logWireCipher;
    return this;
  }

  public TekuConfigurationBuilder setLogWirePlain(boolean logWirePlain) {
    this.logWirePlain = logWirePlain;
    return this;
  }

  public TekuConfigurationBuilder setLogWireMuxFrames(boolean logWireMuxFrames) {
    this.logWireMuxFrames = logWireMuxFrames;
    return this;
  }

  public TekuConfigurationBuilder setLogWireGossip(boolean logWireGossip) {
    this.logWireGossip = logWireGossip;
    return this;
  }

  public TekuConfigurationBuilder setTransitionRecordDirectory(
      final String transitionRecordDirectory) {
    this.transitionRecordDirectory = transitionRecordDirectory;
    return this;
  }

  public TekuConfigurationBuilder setMetricsEnabled(final boolean metricsEnabled) {
    this.metricsEnabled = metricsEnabled;
    return this;
  }

  public TekuConfigurationBuilder setMetricsPort(final int metricsPort) {
    this.metricsPort = metricsPort;
    return this;
  }

  public TekuConfigurationBuilder setMetricsInterface(final String metricsInterface) {
    this.metricsInterface = metricsInterface;
    return this;
  }

  public TekuConfigurationBuilder setMetricsCategories(final List<String> metricsCategories) {
    this.metricsCategories = metricsCategories;
    return this;
  }

  public TekuConfigurationBuilder setMetricsHostAllowlist(final List<String> metricsHostAllowlist) {
    this.metricsHostAllowlist = metricsHostAllowlist;
    return this;
  }

  public TekuConfigurationBuilder setDataPath(final String dataPath) {
    this.dataPath = dataPath;
    return this;
  }

  public TekuConfigurationBuilder setDataStorageMode(final StateStorageMode dataStorageMode) {
    this.dataStorageMode = dataStorageMode;
    return this;
  }

  public TekuConfigurationBuilder setDataStorageFrequency(final long dataStorageFrequency) {
    this.dataStorageFrequency = dataStorageFrequency;
    return this;
  }

  public TekuConfigurationBuilder setDataStorageCreateDbVersion(
      final String dataStorageCreateDbVersion) {
    this.dataStorageCreateDbVersion = dataStorageCreateDbVersion;
    return this;
  }

  public TekuConfigurationBuilder setRestApiPort(final int restApiPort) {
    this.restApiPort = restApiPort;
    return this;
  }

  public TekuConfigurationBuilder setRestApiDocsEnabled(final boolean restApiDocsEnabled) {
    this.restApiDocsEnabled = restApiDocsEnabled;
    return this;
  }

  public TekuConfigurationBuilder setRestApiEnabled(final boolean restApiEnabled) {
    this.restApiEnabled = restApiEnabled;
    return this;
  }

  public TekuConfigurationBuilder setRestApiInterface(final String restApiInterface) {
    this.restApiInterface = restApiInterface;
    return this;
  }

  public TekuConfigurationBuilder setRestApiHostAllowlist(final List<String> restApiHostAllowlist) {
    this.restApiHostAllowlist = restApiHostAllowlist;
    return this;
  }

  public TekuConfigurationBuilder setGraffiti(final Bytes32 graffiti) {
    this.graffiti = graffiti;
    return this;
  }

  public TekuConfigurationBuilder setNetwork(final NetworkDefinition network) {
    this.network = network;
    return this;
  }

  public TekuConfiguration build() {
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
      p2pSnappyEnabled =
          getOrOptionalDefault(p2pSnappyEnabled, network::getSnappyCompressionEnabled);
    }

    if (eth1DepositContractAddress == null && eth1Endpoint != null) {
      throw new InvalidConfigurationException(
          "eth1-deposit-contract-address is required if eth1-endpoint is specified.");
    }

    p2pSnappyEnabled = Optional.ofNullable(p2pSnappyEnabled).orElse(DEFAULT_P2P_SNAPPY_ENABLED);
    return new TekuConfiguration(
        constants,
        startupTargetPeerCount,
        startupTimeoutSeconds,
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
        p2pStaticPeers,
        p2pSnappyEnabled,
        interopGenesisTime,
        interopOwnedValidatorStartIndex,
        interopOwnedValidatorCount,
        initialState,
        interopNumberOfValidators,
        interopEnabled,
        validatorsKeyFile,
        validatorKeystoreFiles,
        validatorKeystorePasswordFiles,
        validatorExternalSignerPublicKeys,
        validatorExternalSignerUrl,
        validatorExternalSignerTimeout,
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
        dataPath,
        dataStorageMode,
        dataStorageFrequency,
        dataStorageCreateDbVersion,
        restApiPort,
        restApiDocsEnabled,
        restApiEnabled,
        restApiInterface,
        restApiHostAllowlist,
        graffiti);
  }

  private <T> T getOrDefault(final T explicitValue, final Supplier<T> predefinedNetworkValue) {
    return getOrOptionalDefault(explicitValue, () -> Optional.of(predefinedNetworkValue.get()));
  }

  private <T> T getOrOptionalDefault(
      final T explicitValue, final Supplier<Optional<T>> predefinedNetworkValue) {
    return explicitValue != null ? explicitValue : predefinedNetworkValue.get().orElse(null);
  }
}
