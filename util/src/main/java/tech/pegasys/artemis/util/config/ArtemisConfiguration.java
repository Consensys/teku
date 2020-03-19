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

package tech.pegasys.artemis.util.config;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;

/** Configuration of an instance of Artemis. */
public class ArtemisConfiguration {
  // Network
  private final String network;

  // P2P
  private final boolean p2pEnabled;
  private final String p2pInterface;
  private final int p2pPort;
  private final boolean p2pDiscoveryEnabled;
  private final List<String> p2pDiscoveryBootnodes;
  private final String p2pAdvertisedIp;
  private final int p2pAdvertisedPort;
  private final String p2pPrivateKeyFile;
  private final int p2pPeerLowerBound;
  private final int p2pPeerUpperBound;

  // Interop
  private final Integer xInteropGenesisTime;
  private final int xInteropOwnedValidatorStartIndex;
  private final int xInteropOwnedValidatorCount;
  private final String xInteropStartState;
  private final int xInteropNumberOfValidators;
  private final boolean xInteropEnabled;

  // Validator
  private final String validatorsKeyFile;
  // TODO: The following two options will eventually be moved to the validator subcommand
  private final List<String> validatorKeystoreFiles;
  private final List<String> validatorKeystorePasswordFiles;

  // Deposit
  private final String eth1DepositContractAddress;
  private final String eth1Endpoint;

  // Logging
  private final boolean logColourEnabled;
  private final boolean logIncludeEventsEnabled;
  private final String logDestination;
  private final String logFile;
  private final String logFileNamePattern;

  // Output
  private final String xTransactionRecordDirectory;

  // Metrics
  private final boolean metricsEnabled;
  private final int metricsPort;
  private final String metricsInterface;
  private final List<String> metricsCategories;

  // Database
  private final String dataPath;
  private final String dataStorageMode;

  // Beacon REST API
  private final int restApiPort;
  private final boolean restApiDocsEnabled;
  private final boolean restApiEnabled;
  private final String restApiInterface;

  public static ArtemisConfigurationBuilder builder() {
    return new ArtemisConfigurationBuilder();
  }

  ArtemisConfiguration(
      final String network,
      final boolean p2pEnabled,
      final String p2pInterface,
      final int p2pPort,
      final boolean p2pDiscoveryEnabled,
      final List<String> p2pDiscoveryBootnodes,
      final String p2pAdvertisedIp,
      final int p2pAdvertisedPort,
      final String p2pPrivateKeyFile,
      final int p2pPeerLowerBound,
      final int p2pPeerUpperBound,
      final Integer xInteropGenesisTime,
      final int xInteropOwnedValidatorStartIndex,
      final int xInteropOwnedValidatorCount,
      final String xInteropStartState,
      final int xInteropNumberOfValidators,
      final boolean xInteropEnabled,
      final String validatorsKeyFile,
      final List<String> validatorKeystoreFiles,
      final List<String> validatorKeystorePasswordFiles,
      final String eth1DepositContractAddress,
      final String eth1Endpoint,
      final boolean logColourEnabled,
      final boolean logIncludeEventsEnabled,
      final String logDestination,
      final String logFile,
      final String logFileNamePattern,
      final String xTransactionRecordDirectory,
      final boolean metricsEnabled,
      final int metricsPort,
      final String metricsInterface,
      final List<String> metricsCategories,
      final String dataPath,
      final String dataStorageMode,
      final int restApiPort,
      final boolean restApiDocsEnabled,
      final boolean restApiEnabled,
      final String restApiInterface) {
    this.network = network;
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
    this.xInteropGenesisTime = xInteropGenesisTime;
    this.xInteropOwnedValidatorStartIndex = xInteropOwnedValidatorStartIndex;
    this.xInteropOwnedValidatorCount = xInteropOwnedValidatorCount;
    this.xInteropStartState = xInteropStartState;
    this.xInteropNumberOfValidators = xInteropNumberOfValidators;
    this.xInteropEnabled = xInteropEnabled;
    this.validatorsKeyFile = validatorsKeyFile;
    this.validatorKeystoreFiles = validatorKeystoreFiles;
    this.validatorKeystorePasswordFiles = validatorKeystorePasswordFiles;
    this.eth1DepositContractAddress = eth1DepositContractAddress;
    this.eth1Endpoint = eth1Endpoint;
    this.logColourEnabled = logColourEnabled;
    this.logIncludeEventsEnabled = logIncludeEventsEnabled;
    this.logDestination = logDestination;
    this.logFile = logFile;
    this.logFileNamePattern = logFileNamePattern;
    this.xTransactionRecordDirectory = xTransactionRecordDirectory;
    this.metricsEnabled = metricsEnabled;
    this.metricsPort = metricsPort;
    this.metricsInterface = metricsInterface;
    this.metricsCategories = metricsCategories;
    this.dataPath = dataPath;
    this.dataStorageMode = dataStorageMode;
    this.restApiPort = restApiPort;
    this.restApiDocsEnabled = restApiDocsEnabled;
    this.restApiEnabled = restApiEnabled;
    this.restApiInterface = restApiInterface;
  }

  public String getNetwork() {
    return network;
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

  public String getP2pAdvertisedIp() {
    return p2pAdvertisedIp;
  }

  public int getP2pAdvertisedPort() {
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

  public Integer getxInteropGenesisTime() {
    return xInteropGenesisTime;
  }

  public int getxInteropOwnedValidatorStartIndex() {
    return xInteropOwnedValidatorStartIndex;
  }

  public int getxInteropOwnedValidatorCount() {
    return xInteropOwnedValidatorCount;
  }

  public String getxInteropStartState() {
    return xInteropStartState == null || xInteropStartState.isEmpty() ? null : xInteropStartState;
  }

  public int getxInteropNumberOfValidators() {
    return xInteropNumberOfValidators;
  }

  public boolean isxInteropEnabled() {
    return xInteropEnabled;
  }

  public String getValidatorsKeyFile() {
    return validatorsKeyFile;
  }

  public List<String> getValidatorKeystoreFiles() {
    return validatorKeystoreFiles;
  }

  public List<String> getValidatorKeystorePasswordFiles() {
    return validatorKeystorePasswordFiles;
  }

  public String getEth1DepositContractAddress() {
    return eth1DepositContractAddress;
  }

  public String getEth1Endpoint() {
    return eth1Endpoint;
  }

  public boolean isLogColourEnabled() {
    return logColourEnabled;
  }

  public boolean isLogIncludeEventsEnabled() {
    return logIncludeEventsEnabled;
  }

  public String getLogDestination() {
    return logDestination;
  }

  public String getLogFile() {
    return logFile;
  }

  public String getLogFileNamePattern() {
    return logFileNamePattern;
  }

  public String getxTransactionRecordDirectory() {
    return xTransactionRecordDirectory;
  }

  public boolean isMetricsEnabled() {
    return metricsEnabled;
  }

  public int getMetricsPort() {
    return metricsPort;
  }

  public String getMetricsInterface() {
    return metricsInterface;
  }

  public List<String> getMetricsCategories() {
    return metricsCategories;
  }

  public String getDataPath() {
    return dataPath;
  }

  public String getDataStorageMode() {
    return dataStorageMode;
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

  public List<Pair<Path, Path>> getValidatorKeystorePasswordFilePairs() {
    final List<String> keystoreFiles = getValidatorKeystoreFiles();
    final List<String> keystorePasswordFiles = getValidatorKeystorePasswordFiles();

    if (keystoreFiles.isEmpty() || keystorePasswordFiles.isEmpty()) {
      return null;
    }

    validateKeyStoreFilesAndPasswordFilesSize();

    final List<Pair<Path, Path>> keystoreFilePasswordFilePairs = new ArrayList<>();
    for (int i = 0; i < keystoreFiles.size(); i++) {
      keystoreFilePasswordFilePairs.add(
          Pair.of(Path.of(keystoreFiles.get(i)), Path.of(keystorePasswordFiles.get(i))));
    }
    return keystoreFilePasswordFilePairs;
  }

  public void validateConfig() throws IllegalArgumentException {
    if (getxInteropNumberOfValidators() < Constants.SLOTS_PER_EPOCH) {
      throw new IllegalArgumentException("Invalid config.toml");
    }
    validateKeyStoreFilesAndPasswordFilesSize();
  }

  private void validateKeyStoreFilesAndPasswordFilesSize() {
    final List<String> validatorKeystoreFiles = getValidatorKeystoreFiles();
    final List<String> validatorKeystorePasswordFiles = getValidatorKeystorePasswordFiles();

    if (validatorKeystoreFiles.size() != validatorKeystorePasswordFiles.size()) {
      final String errorMessage =
          String.format(
              "Invalid configuration. The size of validator.validatorsKeystoreFiles [%d] and validator.validatorsKeystorePasswordFiles [%d] must match",
              validatorKeystoreFiles.size(), validatorKeystorePasswordFiles.size());
      throw new IllegalArgumentException(errorMessage);
    }
  }
}
