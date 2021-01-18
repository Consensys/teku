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

package tech.pegasys.teku.validator.api;

import java.io.File;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.status.StatusLogger;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.util.config.InvalidConfigurationException;
import tech.pegasys.teku.util.config.KeyStoreFilesLocator;
import tech.pegasys.teku.util.config.ValidatorPerformanceTrackingMode;

public class ValidatorConfig {

  private final List<String> validatorKeys;
  private final List<String> validatorKeystoreFiles;
  private final List<String> validatorKeystorePasswordFiles;
  private final List<BLSPublicKey> validatorExternalSignerPublicKeys;
  private final boolean validatorExternalSignerSlashingProtectionEnabled;
  private final URL validatorExternalSignerUrl;
  private final Duration validatorExternalSignerTimeout;
  private final Path validatorExternalSignerKeystore;
  private final Path validatorExternalSignerKeystorePasswordFile;
  private final Path validatorExternalSignerTruststore;
  private final Path validatorExternalSignerTruststorePasswordFile;
  private final GraffitiProvider graffitiProvider;
  private final ValidatorPerformanceTrackingMode validatorPerformanceTrackingMode;
  private final boolean validatorKeystoreLockingEnabled;
  private final Optional<URI> beaconNodeApiEndpoint;
  private final int validatorExternalSignerConcurrentRequestLimit;
  private final boolean useDependentRoots;

  private ValidatorConfig(
      final List<String> validatorKeys,
      final List<String> validatorKeystoreFiles,
      final List<String> validatorKeystorePasswordFiles,
      final List<BLSPublicKey> validatorExternalSignerPublicKeys,
      final URL validatorExternalSignerUrl,
      final Duration validatorExternalSignerTimeout,
      final Path validatorExternalSignerKeystore,
      final Path validatorExternalSignerKeystorePasswordFile,
      final Path validatorExternalSignerTruststore,
      final Path validatorExternalSignerTruststorePasswordFile,
      final Optional<URI> beaconNodeApiEndpoint,
      final GraffitiProvider graffitiProvider,
      final ValidatorPerformanceTrackingMode validatorPerformanceTrackingMode,
      final boolean validatorKeystoreLockingEnabled,
      final boolean validatorExternalSignerSlashingProtectionEnabled,
      final int validatorExternalSignerConcurrentRequestLimit,
      final boolean useDependentRoots) {
    this.validatorKeys = validatorKeys;
    this.validatorKeystoreFiles = validatorKeystoreFiles;
    this.validatorKeystorePasswordFiles = validatorKeystorePasswordFiles;
    this.validatorExternalSignerPublicKeys = validatorExternalSignerPublicKeys;
    this.validatorExternalSignerUrl = validatorExternalSignerUrl;
    this.validatorExternalSignerTimeout = validatorExternalSignerTimeout;
    this.validatorExternalSignerKeystore = validatorExternalSignerKeystore;
    this.validatorExternalSignerKeystorePasswordFile = validatorExternalSignerKeystorePasswordFile;
    this.validatorExternalSignerTruststore = validatorExternalSignerTruststore;
    this.validatorExternalSignerTruststorePasswordFile =
        validatorExternalSignerTruststorePasswordFile;
    this.graffitiProvider = graffitiProvider;
    this.validatorKeystoreLockingEnabled = validatorKeystoreLockingEnabled;
    this.beaconNodeApiEndpoint = beaconNodeApiEndpoint;
    this.validatorPerformanceTrackingMode = validatorPerformanceTrackingMode;
    this.validatorExternalSignerSlashingProtectionEnabled =
        validatorExternalSignerSlashingProtectionEnabled;
    this.validatorExternalSignerConcurrentRequestLimit =
        validatorExternalSignerConcurrentRequestLimit;
    this.useDependentRoots = useDependentRoots;
  }

  public static Builder builder() {
    return new Builder();
  }

  public ValidatorPerformanceTrackingMode getValidatorPerformanceTrackingMode() {
    return validatorPerformanceTrackingMode;
  }

  public boolean isValidatorKeystoreLockingEnabled() {
    return validatorKeystoreLockingEnabled;
  }

  public List<String> getValidatorKeystoreFiles() {
    return validatorKeystoreFiles;
  }

  public List<String> getValidatorKeystorePasswordFiles() {
    return validatorKeystorePasswordFiles;
  }

  public List<BLSPublicKey> getValidatorExternalSignerPublicKeys() {
    return validatorExternalSignerPublicKeys;
  }

  public boolean isValidatorExternalSignerSlashingProtectionEnabled() {
    return validatorExternalSignerSlashingProtectionEnabled;
  }

  public URL getValidatorExternalSignerUrl() {
    return validatorExternalSignerUrl;
  }

  public Duration getValidatorExternalSignerTimeout() {
    return validatorExternalSignerTimeout;
  }

  public int getValidatorExternalSignerConcurrentRequestLimit() {
    return validatorExternalSignerConcurrentRequestLimit;
  }

  public Pair<Path, Path> getValidatorExternalSignerKeystorePasswordFilePair() {
    return Pair.of(validatorExternalSignerKeystore, validatorExternalSignerKeystorePasswordFile);
  }

  public Pair<Path, Path> getValidatorExternalSignerTruststorePasswordFilePair() {
    return Pair.of(
        validatorExternalSignerTruststore, validatorExternalSignerTruststorePasswordFile);
  }

  public Optional<URI> getBeaconNodeApiEndpoint() {
    return beaconNodeApiEndpoint;
  }

  public GraffitiProvider getGraffitiProvider() {
    return graffitiProvider;
  }

  public List<String> getValidatorKeys() {
    return validatorKeys;
  }

  public List<Pair<Path, Path>> getValidatorKeystorePasswordFilePairs() {
    final KeyStoreFilesLocator processor =
        new KeyStoreFilesLocator(validatorKeys, File.pathSeparator);
    processor.parse();
    if (validatorKeystoreFiles != null) {
      processor.parseKeyAndPasswordList(
          getValidatorKeystoreFiles(), getValidatorKeystorePasswordFiles());
    }

    return processor.getFilePairs();
  }

  public boolean useDependentRoots() {
    return useDependentRoots;
  }

  public static final class Builder {

    private List<String> validatorKeys = new ArrayList<>();
    private List<String> validatorKeystoreFiles = new ArrayList<>();
    private List<String> validatorKeystorePasswordFiles = new ArrayList<>();
    private List<BLSPublicKey> validatorExternalSignerPublicKeys = new ArrayList<>();
    private URL validatorExternalSignerUrl;
    private int validatorExternalSignerConcurrentRequestLimit;
    private Duration validatorExternalSignerTimeout = Duration.ofSeconds(5);
    private Path validatorExternalSignerKeystore;
    private Path validatorExternalSignerKeystorePasswordFile;
    private Path validatorExternalSignerTruststore;
    private Path validatorExternalSignerTruststorePasswordFile;
    private GraffitiProvider graffitiProvider;
    private ValidatorPerformanceTrackingMode validatorPerformanceTrackingMode;
    private boolean validatorKeystoreLockingEnabled;
    private Optional<URI> beaconNodeApiEndpoint = Optional.empty();
    private boolean validatorExternalSignerSlashingProtectionEnabled = true;
    private boolean useDependentRoots = false;

    private Builder() {}

    public Builder validatorKeys(List<String> validatorKeys) {
      this.validatorKeys = validatorKeys;
      return this;
    }

    public Builder validatorKeystoreFiles(List<String> validatorKeystoreFiles) {
      this.validatorKeystoreFiles = validatorKeystoreFiles;
      return this;
    }

    public Builder validatorKeystorePasswordFiles(List<String> validatorKeystorePasswordFiles) {
      this.validatorKeystorePasswordFiles = validatorKeystorePasswordFiles;
      return this;
    }

    public Builder validatorExternalSignerPublicKeys(
        List<BLSPublicKey> validatorExternalSignerPublicKeys) {
      this.validatorExternalSignerPublicKeys = validatorExternalSignerPublicKeys;
      return this;
    }

    public Builder validatorExternalSignerUrl(URL validatorExternalSignerUrl) {
      this.validatorExternalSignerUrl = validatorExternalSignerUrl;
      return this;
    }

    public Builder validatorExternalSignerSlashingProtectionEnabled(
        final boolean validatorExternalSignerSlashingProtectionEnabled) {
      this.validatorExternalSignerSlashingProtectionEnabled =
          validatorExternalSignerSlashingProtectionEnabled;
      return this;
    }

    public Builder validatorExternalSignerTimeout(final Duration validatorExternalSignerTimeout) {
      this.validatorExternalSignerTimeout = validatorExternalSignerTimeout;
      return this;
    }

    public Builder validatorExternalSignerConcurrentRequestLimit(
        int validatorExternalSignerConcurrentRequestLimit) {
      this.validatorExternalSignerConcurrentRequestLimit =
          validatorExternalSignerConcurrentRequestLimit;
      return this;
    }

    public Builder validatorExternalSignerKeystore(final Path validatorExternalSignerKeystore) {
      this.validatorExternalSignerKeystore = validatorExternalSignerKeystore;
      return this;
    }

    public Builder validatorExternalSignerKeystorePasswordFile(
        final Path validatorExternalSignerKeystorePasswordFile) {
      this.validatorExternalSignerKeystorePasswordFile =
          validatorExternalSignerKeystorePasswordFile;
      return this;
    }

    public Builder validatorExternalSignerTruststore(final Path validatorExternalSignerTruststore) {
      this.validatorExternalSignerTruststore = validatorExternalSignerTruststore;
      return this;
    }

    public Builder validatorExternalSignerTruststorePasswordFile(
        final Path validatorExternalSignerTruststorePasswordFile) {
      this.validatorExternalSignerTruststorePasswordFile =
          validatorExternalSignerTruststorePasswordFile;
      return this;
    }

    public Builder beaconNodeApiEndpoint(final URI beaconNodeApiEndpoint) {
      this.beaconNodeApiEndpoint = Optional.of(beaconNodeApiEndpoint);
      return this;
    }

    public Builder graffitiProvider(GraffitiProvider graffitiProvider) {
      this.graffitiProvider = graffitiProvider;
      return this;
    }

    public Builder validatorPerformanceTrackingMode(
        ValidatorPerformanceTrackingMode validatorPerformanceTrackingMode) {
      this.validatorPerformanceTrackingMode = validatorPerformanceTrackingMode;
      return this;
    }

    public Builder validatorKeystoreLockingEnabled(boolean validatorKeystoreLockingEnabled) {
      this.validatorKeystoreLockingEnabled = validatorKeystoreLockingEnabled;
      return this;
    }

    public Builder useDependentRoots(final boolean useDependentRoots) {
      this.useDependentRoots = useDependentRoots;
      return this;
    }

    public ValidatorConfig build() {
      validateKeyStoreFilesAndPasswordFilesConfig();
      validateExternalSignerUrlAndPublicKeys();
      validateExternalSignerKeystoreAndPasswordFileConfig();
      validateExternalSignerTruststoreAndPasswordFileConfig();
      validateExternalSignerURLScheme();
      return new ValidatorConfig(
          validatorKeys,
          validatorKeystoreFiles,
          validatorKeystorePasswordFiles,
          validatorExternalSignerPublicKeys,
          validatorExternalSignerUrl,
          validatorExternalSignerTimeout,
          validatorExternalSignerKeystore,
          validatorExternalSignerKeystorePasswordFile,
          validatorExternalSignerTruststore,
          validatorExternalSignerTruststorePasswordFile,
          beaconNodeApiEndpoint,
          graffitiProvider,
          validatorPerformanceTrackingMode,
          validatorKeystoreLockingEnabled,
          validatorExternalSignerSlashingProtectionEnabled,
          validatorExternalSignerConcurrentRequestLimit,
          useDependentRoots);
    }

    private void validateKeyStoreFilesAndPasswordFilesConfig() {
      if (validatorKeystoreFiles.isEmpty() && validatorKeystorePasswordFiles.isEmpty()) {
        return;
      }
      if (validatorKeystoreFiles.isEmpty() != validatorKeystorePasswordFiles.isEmpty()) {
        final String errorMessage =
            "Invalid configuration. '--validators-key-files' and '--validators-key-password-files' must be specified together";
        throw new InvalidConfigurationException(errorMessage);
      }

      if (validatorKeystoreFiles.size() != validatorKeystorePasswordFiles.size()) {
        StatusLogger.getLogger()
            .debug(
                "Invalid configuration. The size of validator.validatorsKeystoreFiles {} and validator.validatorsKeystorePasswordFiles {} must match",
                validatorKeystoreFiles.size(),
                validatorKeystorePasswordFiles.size());

        final String errorMessage =
            String.format(
                "Invalid configuration. The number of --validators-key-files (%d) must equal the number of --validators-key-password-files (%d)",
                validatorKeystoreFiles.size(), validatorKeystorePasswordFiles.size());
        throw new InvalidConfigurationException(errorMessage);
      }
    }

    private void validateExternalSignerUrlAndPublicKeys() {
      if (externalPublicKeysNotDefined()) {
        return;
      }

      if (validatorExternalSignerUrl == null) {
        final String errorMessage =
            "Invalid configuration. '--validators-external-signer-url' and '--validators-external-signer-public-keys' must be specified together";
        throw new InvalidConfigurationException(errorMessage);
      }
    }

    private void validateExternalSignerKeystoreAndPasswordFileConfig() {
      if (onlyOneInitialized(
          validatorExternalSignerKeystore, validatorExternalSignerKeystorePasswordFile)) {
        final String errorMessage =
            "Invalid configuration. '--validators-external-signer-keystore' and '--validators-external-signer-keystore-password-file' must be specified together";
        throw new InvalidConfigurationException(errorMessage);
      }
    }

    private void validateExternalSignerTruststoreAndPasswordFileConfig() {
      if (onlyOneInitialized(
          validatorExternalSignerTruststore, validatorExternalSignerTruststorePasswordFile)) {
        final String errorMessage =
            "Invalid configuration. '--validators-external-signer-truststore' and '--validators-external-signer-truststore-password-file' must be specified together";
        throw new InvalidConfigurationException(errorMessage);
      }
    }

    private void validateExternalSignerURLScheme() {
      if (externalPublicKeysNotDefined() || validatorExternalSignerUrl == null) {
        return;
      }

      if (validatorExternalSignerKeystore != null || validatorExternalSignerTruststore != null) {
        if (!isURLSchemeHttps(validatorExternalSignerUrl)) {
          final String errorMessage =
              String.format(
                  "Invalid configuration. --validators-external-signer-url (%s) must start with https because external signer keystore/truststore are defined",
                  validatorExternalSignerUrl);
          throw new InvalidConfigurationException(errorMessage);
        }
      }
    }

    private boolean externalPublicKeysNotDefined() {
      return validatorExternalSignerPublicKeys == null
          || validatorExternalSignerPublicKeys.isEmpty();
    }

    private static boolean isURLSchemeHttps(final URL url) {
      final String protocol = url.getProtocol();
      return protocol != null && protocol.equalsIgnoreCase("https");
    }

    private boolean onlyOneInitialized(final Object o1, final Object o2) {
      return (o1 == null) != (o2 == null);
    }
  }
}
