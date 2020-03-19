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

public class ArtemisConfigurationBuilder {
  private String network;
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
  private Integer xInteropGenesisTime;
  private int xInteropOwnedValidatorStartIndex;
  private int xInteropOwnedValidatorCount;
  private String xInteropStartState;
  private int xInteropNumberOfValidators;
  private boolean xInteropEnabled;
  private String validatorsKeyFile;
  private List<String> validatorKeystoreFiles;
  private List<String> validatorKeystorePasswordFiles;
  private String eth1DepositContractAddress;
  private String eth1Endpoint;
  private boolean logColourEnabled;
  private boolean logIncludeEventsEnabled;
  private String logDestination;
  private String logFile;
  private String logFileNamePattern;
  private String xTransactionRecordDirectory;
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

  public ArtemisConfigurationBuilder setNetwork(final String network) {
    this.network = network;
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

  public ArtemisConfigurationBuilder setxInteropGenesisTime(final Integer xInteropGenesisTime) {
    this.xInteropGenesisTime = xInteropGenesisTime;
    return this;
  }

  public ArtemisConfigurationBuilder setxInteropOwnedValidatorStartIndex(
      final int xInteropOwnedValidatorStartIndex) {
    this.xInteropOwnedValidatorStartIndex = xInteropOwnedValidatorStartIndex;
    return this;
  }

  public ArtemisConfigurationBuilder setxInteropOwnedValidatorCount(
      final int xInteropOwnedValidatorCount) {
    this.xInteropOwnedValidatorCount = xInteropOwnedValidatorCount;
    return this;
  }

  public ArtemisConfigurationBuilder setxInteropStartState(final String xInteropStartState) {
    this.xInteropStartState = xInteropStartState;
    return this;
  }

  public ArtemisConfigurationBuilder setxInteropNumberOfValidators(
      final int xInteropNumberOfValidators) {
    this.xInteropNumberOfValidators = xInteropNumberOfValidators;
    return this;
  }

  public ArtemisConfigurationBuilder setxInteropEnabled(final boolean xInteropEnabled) {
    this.xInteropEnabled = xInteropEnabled;
    return this;
  }

  public ArtemisConfigurationBuilder setValidatorsKeyFile(final String validatorsKeyFile) {
    this.validatorsKeyFile = validatorsKeyFile;
    return this;
  }

  public ArtemisConfigurationBuilder setValidatorsKeystoreFiles(
      final List<String> validatorKeystoreFiles) {
    this.validatorKeystoreFiles = validatorKeystoreFiles;
    return this;
  }

  public ArtemisConfigurationBuilder setValidatorsKeystorePasswordFiles(
      final List<String> validatorKeystorePasswordFiles) {
    this.validatorKeystorePasswordFiles = validatorKeystorePasswordFiles;
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

  public ArtemisConfigurationBuilder setLogColourEnabled(final boolean logColourEnabled) {
    this.logColourEnabled = logColourEnabled;
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

  public ArtemisConfigurationBuilder setxTransactionRecordDirectory(
      final String xTransactionRecordDirectory) {
    this.xTransactionRecordDirectory = xTransactionRecordDirectory;
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

  public ArtemisConfiguration build() {
    return new ArtemisConfiguration(
        network,
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
        xInteropGenesisTime,
        xInteropOwnedValidatorStartIndex,
        xInteropOwnedValidatorCount,
        xInteropStartState,
        xInteropNumberOfValidators,
        xInteropEnabled,
        validatorsKeyFile,
        validatorKeystoreFiles,
        validatorKeystorePasswordFiles,
        eth1DepositContractAddress,
        eth1Endpoint,
        logColourEnabled,
        logIncludeEventsEnabled,
        logDestination,
        logFile,
        logFileNamePattern,
        xTransactionRecordDirectory,
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
}
