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

import static com.google.common.base.Preconditions.checkNotNull;

import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.spec.datastructures.eth1.Eth1Address;

public class ValidatorConfig {

  private static final int DEFAULT_REST_API_PORT = 5051;
  public static final String DEFAULT_BEACON_NODE_API_ENDPOINT =
      "http://127.0.0.1:" + DEFAULT_REST_API_PORT;
  public static final Duration DEFAULT_VALIDATOR_EXTERNAL_SIGNER_TIMEOUT = Duration.ofSeconds(5);
  public static final int DEFAULT_VALIDATOR_EXTERNAL_SIGNER_CONCURRENT_REQUEST_LIMIT = 32;
  public static final boolean DEFAULT_VALIDATOR_KEYSTORE_LOCKING_ENABLED = true;
  public static final boolean DEFAULT_VALIDATOR_EXTERNAL_SIGNER_SLASHING_PROTECTION_ENABLED = true;
  public static final boolean DEFAULT_USE_DEPENDENT_ROOTS = true;
  public static final boolean DEFAULT_GENERATE_EARLY_ATTESTATIONS = true;
  public static final boolean DEFAULT_SEND_ATTESTATIONS_AS_BATCH = true;
  public static final Optional<Bytes32> DEFAULT_GRAFFITI = Optional.empty();
  public static final boolean DEFAULT_VALIDATOR_PROPOSER_CONFIG_REFRESH_ENABLED = false;

  private final List<String> validatorKeys;
  private final List<String> validatorExternalSignerPublicKeySources;
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
  private final boolean generateEarlyAttestations;
  private final Optional<Eth1Address> proposerDefaultFeeRecipient;
  private final Optional<String> proposerConfigSource;
  private final boolean refreshProposerConfigFromSource;

  private ValidatorConfig(
      final List<String> validatorKeys,
      final List<String> validatorExternalSignerPublicKeySources,
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
      final boolean useDependentRoots,
      final boolean generateEarlyAttestations,
      final Optional<Eth1Address> proposerDefaultFeeRecipient,
      final Optional<String> proposerConfigSource,
      final boolean refreshProposerConfigFromSource) {
    this.validatorKeys = validatorKeys;
    this.validatorExternalSignerPublicKeySources = validatorExternalSignerPublicKeySources;
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
    this.generateEarlyAttestations = generateEarlyAttestations;
    this.proposerDefaultFeeRecipient = proposerDefaultFeeRecipient;
    this.proposerConfigSource = proposerConfigSource;
    this.refreshProposerConfigFromSource = refreshProposerConfigFromSource;
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

  public List<String> getValidatorExternalSignerPublicKeySources() {
    return validatorExternalSignerPublicKeySources;
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

  public boolean generateEarlyAttestations() {
    return generateEarlyAttestations;
  }

  public boolean useDependentRoots() {
    return useDependentRoots;
  }

  public Optional<Eth1Address> getProposerDefaultFeeRecipient() {
    validateProposerDefaultFeeRecipientOrProposerConfigSource();
    return proposerDefaultFeeRecipient;
  }

  public Optional<String> getProposerConfigSource() {
    validateProposerDefaultFeeRecipientOrProposerConfigSource();
    return proposerConfigSource;
  }

  public boolean getRefreshProposerConfigFromSource() {
    return refreshProposerConfigFromSource;
  }

  private void validateProposerDefaultFeeRecipientOrProposerConfigSource() {
    if (proposerDefaultFeeRecipient.isEmpty()
        && proposerConfigSource.isEmpty()
        && !(validatorKeys.isEmpty() && validatorExternalSignerPublicKeySources.isEmpty())) {
      throw new InvalidConfigurationException(
          "Invalid configuration. --Xvalidators-proposer-default-fee-recipient or --Xvalidators-proposer-config must be specified when Bellatrix milestone is active");
    }
  }

  public static final class Builder {
    private List<String> validatorKeys = new ArrayList<>();
    private List<String> validatorExternalSignerPublicKeySources = new ArrayList<>();
    private URL validatorExternalSignerUrl;
    private int validatorExternalSignerConcurrentRequestLimit =
        DEFAULT_VALIDATOR_EXTERNAL_SIGNER_CONCURRENT_REQUEST_LIMIT;
    private Duration validatorExternalSignerTimeout = DEFAULT_VALIDATOR_EXTERNAL_SIGNER_TIMEOUT;
    private Path validatorExternalSignerKeystore;
    private Path validatorExternalSignerKeystorePasswordFile;
    private Path validatorExternalSignerTruststore;
    private Path validatorExternalSignerTruststorePasswordFile;
    private GraffitiProvider graffitiProvider =
        new FileBackedGraffitiProvider(DEFAULT_GRAFFITI, Optional.empty());
    private ValidatorPerformanceTrackingMode validatorPerformanceTrackingMode =
        ValidatorPerformanceTrackingMode.DEFAULT_MODE;
    private boolean validatorKeystoreLockingEnabled = DEFAULT_VALIDATOR_KEYSTORE_LOCKING_ENABLED;
    private Optional<URI> beaconNodeApiEndpoint = Optional.empty();
    private boolean validatorExternalSignerSlashingProtectionEnabled =
        DEFAULT_VALIDATOR_EXTERNAL_SIGNER_SLASHING_PROTECTION_ENABLED;
    private boolean useDependentRoots = DEFAULT_USE_DEPENDENT_ROOTS;
    private boolean generateEarlyAttestations = DEFAULT_GENERATE_EARLY_ATTESTATIONS;
    private Optional<Eth1Address> proposerDefaultFeeRecipient = Optional.empty();
    private Optional<String> proposerConfigSource = Optional.empty();
    private boolean refreshProposerConfigFromSource =
        DEFAULT_VALIDATOR_PROPOSER_CONFIG_REFRESH_ENABLED;

    private Builder() {}

    public Builder validatorKeys(List<String> validatorKeys) {
      this.validatorKeys = validatorKeys;
      return this;
    }

    public Builder validatorExternalSignerPublicKeySources(
        List<String> validatorExternalSignerPublicKeySources) {
      checkNotNull(validatorExternalSignerPublicKeySources);
      this.validatorExternalSignerPublicKeySources = validatorExternalSignerPublicKeySources;
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
      if (validatorExternalSignerTimeout.isNegative()) {
        throw new InvalidConfigurationException(
            String.format(
                "Invalid validatorExternalSignerTimeout: %s", validatorExternalSignerTimeout));
      }
      this.validatorExternalSignerTimeout = validatorExternalSignerTimeout;
      return this;
    }

    public Builder validatorExternalSignerConcurrentRequestLimit(
        int validatorExternalSignerConcurrentRequestLimit) {
      if (validatorExternalSignerConcurrentRequestLimit < 0) {
        throw new InvalidConfigurationException(
            String.format(
                "Invalid validatorExternalSignerConcurrentRequestLimit: %s",
                validatorExternalSignerConcurrentRequestLimit));
      }
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

    public Builder generateEarlyAttestations(final boolean generateEarlyAttestations) {
      this.generateEarlyAttestations = generateEarlyAttestations;
      return this;
    }

    public Builder proposerDefaultFeeRecipient(final Eth1Address proposerDefaultFeeRecipient) {
      this.proposerDefaultFeeRecipient = Optional.ofNullable(proposerDefaultFeeRecipient);
      return this;
    }

    public Builder proposerDefaultFeeRecipient(final String proposerDefaultFeeRecipient) {
      if (proposerDefaultFeeRecipient == null) {
        this.proposerDefaultFeeRecipient = Optional.empty();
      } else {
        this.proposerDefaultFeeRecipient =
            Optional.of(Eth1Address.fromHexString(proposerDefaultFeeRecipient));
      }
      return this;
    }

    public Builder proposerConfigSource(final String proposerConfigSource) {
      this.proposerConfigSource = Optional.ofNullable(proposerConfigSource);
      return this;
    }

    public Builder refreshProposerConfigFromSource(final boolean refreshProposerConfigFromSource) {
      this.refreshProposerConfigFromSource = refreshProposerConfigFromSource;
      return this;
    }

    public ValidatorConfig build() {
      validateExternalSignerUrlAndPublicKeys();
      validateExternalSignerKeystoreAndPasswordFileConfig();
      validateExternalSignerTruststoreAndPasswordFileConfig();
      validateExternalSignerURLScheme();
      return new ValidatorConfig(
          validatorKeys,
          validatorExternalSignerPublicKeySources,
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
          useDependentRoots,
          generateEarlyAttestations,
          proposerDefaultFeeRecipient,
          proposerConfigSource,
          refreshProposerConfigFromSource);
    }

    private void validateExternalSignerUrlAndPublicKeys() {
      if (validatorExternalSignerPublicKeySources.isEmpty()) {
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
      if (validatorExternalSignerPublicKeySources.isEmpty() || validatorExternalSignerUrl == null) {
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

    private static boolean isURLSchemeHttps(final URL url) {
      final String protocol = url.getProtocol();
      return protocol != null && protocol.equalsIgnoreCase("https");
    }

    private boolean onlyOneInitialized(final Object o1, final Object o2) {
      return (o1 == null) != (o2 == null);
    }
  }
}
