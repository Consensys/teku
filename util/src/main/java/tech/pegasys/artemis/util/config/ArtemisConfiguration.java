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

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.artemis.bls.BLSPublicKey;

/** Configuration of an instance of Artemis. */
public class ArtemisConfiguration {
  // Network
  private final String constants;
  private final Integer startupTargetPeerCount;
  private final Integer startupTimeoutSeconds;

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
  private final List<String> p2pStaticPeers;

  // Interop
  private final Integer interopGenesisTime;
  private final int interopOwnedValidatorStartIndex;
  private final int interopOwnedValidatorCount;
  private final String interopStartState;
  private final int interopNumberOfValidators;
  private final boolean interopEnabled;

  // Validator
  private final String validatorsKeyFile;
  // TODO: The following two options will eventually be moved to the validator subcommand
  private final List<String> validatorKeystoreFiles;
  private final List<String> validatorKeystorePasswordFiles;
  private final List<String> validatorExternalSignerPublicKeys;
  private final String validatorExternalSignerUrl;
  private final int validatorExternalSignerTimeout;

  // Deposit
  private final String eth1DepositContractAddress;
  private final String eth1Endpoint;

  // Logging
  private final boolean logColorEnabled;
  private final boolean logIncludeEventsEnabled;
  private final String logDestination;
  private final String logFile;
  private final String logFileNamePattern;

  // Output
  private final String transitionRecordDirectory;

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
      final String constants,
      final Integer startupTargetPeerCount,
      final Integer startupTimeoutSeconds,
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
      final List<String> p2pStaticPeers,
      final Integer interopGenesisTime,
      final int interopOwnedValidatorStartIndex,
      final int interopOwnedValidatorCount,
      final String interopStartState,
      final int interopNumberOfValidators,
      final boolean interopEnabled,
      final String validatorsKeyFile,
      final List<String> validatorKeystoreFiles,
      final List<String> validatorKeystorePasswordFiles,
      final List<String> validatorExternalSignerPublicKeys,
      final String validatorExternalSignerUrl,
      final int validatorExternalSignerTimeout,
      final String eth1DepositContractAddress,
      final String eth1Endpoint,
      final boolean logColorEnabled,
      final boolean logIncludeEventsEnabled,
      final String logDestination,
      final String logFile,
      final String logFileNamePattern,
      final String transitionRecordDirectory,
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
    this.constants = constants;
    this.startupTargetPeerCount = startupTargetPeerCount;
    this.startupTimeoutSeconds = startupTimeoutSeconds;
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
    this.p2pStaticPeers = p2pStaticPeers;
    this.interopGenesisTime = interopGenesisTime;
    this.interopOwnedValidatorStartIndex = interopOwnedValidatorStartIndex;
    this.interopOwnedValidatorCount = interopOwnedValidatorCount;
    this.interopStartState = interopStartState;
    this.interopNumberOfValidators = interopNumberOfValidators;
    this.interopEnabled = interopEnabled;
    this.validatorsKeyFile = validatorsKeyFile;
    this.validatorKeystoreFiles = validatorKeystoreFiles;
    this.validatorKeystorePasswordFiles = validatorKeystorePasswordFiles;
    this.validatorExternalSignerPublicKeys = validatorExternalSignerPublicKeys;
    this.validatorExternalSignerUrl = validatorExternalSignerUrl;
    this.validatorExternalSignerTimeout = validatorExternalSignerTimeout;
    this.eth1DepositContractAddress = eth1DepositContractAddress;
    this.eth1Endpoint = eth1Endpoint;
    this.logColorEnabled = logColorEnabled;
    this.logIncludeEventsEnabled = logIncludeEventsEnabled;
    this.logDestination = logDestination;
    this.logFile = logFile;
    this.logFileNamePattern = logFileNamePattern;
    this.transitionRecordDirectory = transitionRecordDirectory;
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

  public String getConstants() {
    return constants;
  }

  public int getStartupTargetPeerCount() {
    return startupTargetPeerCount;
  }

  public int getStartupTimeoutSeconds() {
    return startupTimeoutSeconds;
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

  public List<String> getP2pStaticPeers() {
    return p2pStaticPeers;
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

  public String getInteropStartState() {
    return interopStartState == null || interopStartState.isEmpty() ? null : interopStartState;
  }

  public int getInteropNumberOfValidators() {
    return interopNumberOfValidators;
  }

  public boolean isInteropEnabled() {
    return interopEnabled;
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

  public List<BLSPublicKey> getValidatorExternalSignerPublicKeys() {
    if (validatorExternalSignerPublicKeys == null) {
      return Collections.emptyList();
    }
    try {
      return validatorExternalSignerPublicKeys.stream()
          .map(key -> BLSPublicKey.fromBytes(Bytes.fromHexString(key)))
          .collect(Collectors.toList());
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Invalid configuration. Signer public key is invalid", e);
    }
  }

  public URL getValidatorExternalSignerUrl() {
    try {
      return new URL(validatorExternalSignerUrl);
    } catch (MalformedURLException e) {
      throw new IllegalArgumentException("Invalid configuration. Signer URL has invalid syntax", e);
    }
  }

  public int getValidatorExternalSignerTimeout() {
    return validatorExternalSignerTimeout;
  }

  public String getEth1DepositContractAddress() {
    return eth1DepositContractAddress;
  }

  public String getEth1Endpoint() {
    return eth1Endpoint;
  }

  public boolean isLogColorEnabled() {
    return logColorEnabled;
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

  public String getTransitionRecordDirectory() {
    return transitionRecordDirectory;
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
    final int interopNumberOfValidators = getInteropNumberOfValidators();
    if (interopNumberOfValidators < Constants.SLOTS_PER_EPOCH) {
      throw new IllegalArgumentException(
          String.format(
              "Invalid configuration. Interop number of validators [%d] must be greater than or equal to [%d]",
              interopNumberOfValidators, Constants.SLOTS_PER_EPOCH));
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
