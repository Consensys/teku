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

package tech.pegasys.artemis.util.config;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

public class ArtemisConfigurationBuilder {
  private String constants;
  private boolean p2pEnabled;
  private String p2pInterface;
  private int p2pPort;
  private boolean p2pDiscoveryEnabled;
  private List<String> p2pDiscoveryBootnodes;
  private String p2pAdvertisedIp;
  private int p2pAdvertisedPort;
  private String p2pPrivateKeyFile;
  private int p2pPeerLowerBound;
  private int p2pPeerUpperBound;
  private List<String> p2pStaticPeers;
  private Integer interopGenesisTime;
  private int interopOwnedValidatorStartIndex;
  private int interopOwnedValidatorCount;
  private String interopStartState;
  private int interopNumberOfValidators;
  private boolean interopEnabled;
  private String validatorsKeyFile;
  private List<String> validatorKeystoreFiles;
  private List<String> validatorKeystorePasswordFiles;
  private List<String> validatorExternalSignerPublicKeys;
  private String validatorExternalSignerUrl;
  private int validatorExternalSignerTimeout;
  private String eth1DepositContractAddress;
  private String eth1Endpoint;
  private boolean logColorEnabled;
  private boolean logIncludeEventsEnabled;
  private String logDestination;
  private String logFile;
  private String logFileNamePattern;
  private String transitionRecordDirectory;
  private boolean metricsEnabled;
  private int metricsPort;
  private String metricsInterface;
  private List<String> metricsCategories;
  private String dataPath;
  private String dataStorageMode;
  private int restApiPort;
  private boolean restApiDocsEnabled;
  private boolean restApiEnabled;
  private String restApiInterface;
  private PredefinedNetwork network;

  public ArtemisConfigurationBuilder setConstants(final String constants) {
    this.constants = constants;
    return this;
  }

  public ArtemisConfigurationBuilder setP2pEnabled(final boolean p2pEnabled) {
    this.p2pEnabled = p2pEnabled;
    return this;
  }

  public ArtemisConfigurationBuilder setP2pInterface(final String p2pInterface) {
    this.p2pInterface = p2pInterface;
    return this;
  }

  public ArtemisConfigurationBuilder setP2pPort(final int p2pPort) {
    this.p2pPort = p2pPort;
    return this;
  }

  public ArtemisConfigurationBuilder setP2pDiscoveryEnabled(final boolean p2pDiscoveryEnabled) {
    this.p2pDiscoveryEnabled = p2pDiscoveryEnabled;
    return this;
  }

  public ArtemisConfigurationBuilder setP2pDiscoveryBootnodes(
      final List<String> p2pDiscoveryBootnodes) {
    this.p2pDiscoveryBootnodes = p2pDiscoveryBootnodes;
    return this;
  }

  public ArtemisConfigurationBuilder setP2pAdvertisedIp(final String p2pAdvertisedIp) {
    this.p2pAdvertisedIp = p2pAdvertisedIp;
    return this;
  }

  public ArtemisConfigurationBuilder setP2pAdvertisedPort(final int p2pAdvertisedPort) {
    this.p2pAdvertisedPort = p2pAdvertisedPort;
    return this;
  }

  public ArtemisConfigurationBuilder setP2pPrivateKeyFile(final String p2pPrivateKeyFile) {
    this.p2pPrivateKeyFile = p2pPrivateKeyFile;
    return this;
  }

  public ArtemisConfigurationBuilder setP2pPeerLowerBound(final int p2pPeerLowerBound) {
    this.p2pPeerLowerBound = p2pPeerLowerBound;
    return this;
  }

  public ArtemisConfigurationBuilder setP2pPeerUpperBound(final int p2pPeerUpperBound) {
    this.p2pPeerUpperBound = p2pPeerUpperBound;
    return this;
  }

  public ArtemisConfigurationBuilder setP2pStaticPeers(final List<String> p2pStaticPeers) {
    this.p2pStaticPeers = p2pStaticPeers;
    return this;
  }

  public ArtemisConfigurationBuilder setInteropGenesisTime(final Integer interopGenesisTime) {
    this.interopGenesisTime = interopGenesisTime;
    return this;
  }

  public ArtemisConfigurationBuilder setInteropOwnedValidatorStartIndex(
      final int interopOwnedValidatorStartIndex) {
    this.interopOwnedValidatorStartIndex = interopOwnedValidatorStartIndex;
    return this;
  }

  public ArtemisConfigurationBuilder setInteropOwnedValidatorCount(
      final int interopOwnedValidatorCount) {
    this.interopOwnedValidatorCount = interopOwnedValidatorCount;
    return this;
  }

  public ArtemisConfigurationBuilder setInteropStartState(final String interopStartState) {
    this.interopStartState = interopStartState;
    return this;
  }

  public ArtemisConfigurationBuilder setInteropNumberOfValidators(
      final int interopNumberOfValidators) {
    this.interopNumberOfValidators = interopNumberOfValidators;
    return this;
  }

  public ArtemisConfigurationBuilder setInteropEnabled(final boolean interopEnabled) {
    this.interopEnabled = interopEnabled;
    return this;
  }

  public ArtemisConfigurationBuilder setValidatorKeyFile(final String validatorsKeyFile) {
    this.validatorsKeyFile = validatorsKeyFile;
    return this;
  }

  public ArtemisConfigurationBuilder setValidatorKeystoreFiles(
      final List<String> validatorKeystoreFiles) {
    this.validatorKeystoreFiles = validatorKeystoreFiles;
    return this;
  }

  public ArtemisConfigurationBuilder setValidatorKeystorePasswordFiles(
      final List<String> validatorKeystorePasswordFiles) {
    this.validatorKeystorePasswordFiles = validatorKeystorePasswordFiles;
    return this;
  }

  public ArtemisConfigurationBuilder setValidatorExternalSignerPublicKeys(
      final List<String> validatorsExternalSignerPublicKeys) {
    this.validatorExternalSignerPublicKeys = validatorsExternalSignerPublicKeys;
    return this;
  }

  public ArtemisConfigurationBuilder setValidatorExternalSignerUrl(
      final String validatorsExternalSignerUrl) {
    this.validatorExternalSignerUrl = validatorsExternalSignerUrl;
    return this;
  }

  public ArtemisConfigurationBuilder setValidatorExternalSignerTimeout(
      final int validatorsExternalSignerTimeout) {
    this.validatorExternalSignerTimeout = validatorsExternalSignerTimeout;
    return this;
  }

  public ArtemisConfigurationBuilder setEth1DepositContractAddress(
      final String eth1DepositContractAddress) {
    this.eth1DepositContractAddress = eth1DepositContractAddress;
    return this;
  }

  public ArtemisConfigurationBuilder setEth1Endpoint(final String eth1Endpoint) {
    this.eth1Endpoint = eth1Endpoint;
    return this;
  }

  public ArtemisConfigurationBuilder setLogColorEnabled(final boolean logColorEnabled) {
    this.logColorEnabled = logColorEnabled;
    return this;
  }

  public ArtemisConfigurationBuilder setLogIncludeEventsEnabled(
      final boolean logIncludeEventsEnabled) {
    this.logIncludeEventsEnabled = logIncludeEventsEnabled;
    return this;
  }

  public ArtemisConfigurationBuilder setLogDestination(final String logDestination) {
    this.logDestination = logDestination;
    return this;
  }

  public ArtemisConfigurationBuilder setLogFile(final String logFile) {
    this.logFile = logFile;
    return this;
  }

  public ArtemisConfigurationBuilder setLogFileNamePattern(final String logFileNamePattern) {
    this.logFileNamePattern = logFileNamePattern;
    return this;
  }

  public ArtemisConfigurationBuilder setTransitionRecordDirectory(
      final String transitionRecordDirectory) {
    this.transitionRecordDirectory = transitionRecordDirectory;
    return this;
  }

  public ArtemisConfigurationBuilder setMetricsEnabled(final boolean metricsEnabled) {
    this.metricsEnabled = metricsEnabled;
    return this;
  }

  public ArtemisConfigurationBuilder setMetricsPort(final int metricsPort) {
    this.metricsPort = metricsPort;
    return this;
  }

  public ArtemisConfigurationBuilder setMetricsInterface(final String metricsInterface) {
    this.metricsInterface = metricsInterface;
    return this;
  }

  public ArtemisConfigurationBuilder setMetricsCategories(final List<String> metricsCategories) {
    this.metricsCategories = metricsCategories;
    return this;
  }

  public ArtemisConfigurationBuilder setDataPath(final String dataPath) {
    this.dataPath = dataPath;
    return this;
  }

  public ArtemisConfigurationBuilder setDataStorageMode(final String dataStorageMode) {
    this.dataStorageMode = dataStorageMode;
    return this;
  }

  public ArtemisConfigurationBuilder setRestApiPort(final int restApiPort) {
    this.restApiPort = restApiPort;
    return this;
  }

  public ArtemisConfigurationBuilder setRestApiDocsEnabled(final boolean restApiDocsEnabled) {
    this.restApiDocsEnabled = restApiDocsEnabled;
    return this;
  }

  public ArtemisConfigurationBuilder setRestApiEnabled(final boolean restApiEnabled) {
    this.restApiEnabled = restApiEnabled;
    return this;
  }

  public ArtemisConfigurationBuilder setRestApiInterface(final String restApiInterface) {
    this.restApiInterface = restApiInterface;
    return this;
  }

  public ArtemisConfigurationBuilder setNetwork(final PredefinedNetwork network) {
    this.network = network;
    return this;
  }

  public ArtemisConfiguration build() {
    if (network != null) {
      System.out.println("Applying network " + network);
      constants = getEffectiveValue(constants, () -> Optional.of(network.getConstants()));
      eth1DepositContractAddress =
          getEffectiveValue(eth1DepositContractAddress, network::getEth1DepositContractAddress);
      p2pDiscoveryBootnodes =
          getEffectiveValue(p2pDiscoveryBootnodes, network::getDiscoveryBootnodes);
      eth1Endpoint = getEffectiveValue(eth1Endpoint, network::getEth1Endpoint);
    }
    return new ArtemisConfiguration(
        constants,
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
        interopGenesisTime,
        interopOwnedValidatorStartIndex,
        interopOwnedValidatorCount,
        interopStartState,
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
        logColorEnabled,
        logIncludeEventsEnabled,
        logDestination,
        logFile,
        logFileNamePattern,
        transitionRecordDirectory,
        metricsEnabled,
        metricsPort,
        metricsInterface,
        metricsCategories,
        dataPath,
        dataStorageMode,
        restApiPort,
        restApiDocsEnabled,
        restApiEnabled,
        restApiInterface);
  }

  private <T> T getEffectiveValue(
      final T explicitValue, final Supplier<Optional<T>> predefinedNetworkValue) {
    return explicitValue != null ? explicitValue : predefinedNetworkValue.get().orElse(null);
  }
}
