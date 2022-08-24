/*
 * Copyright ConsenSys Software Inc., 2022
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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.eth1.Eth1Address;

public class ValidatorConfig {

  private static final Logger LOG = LogManager.getLogger();

  private static final int DEFAULT_REST_API_PORT = 5051;
  public static final List<String> DEFAULT_BEACON_NODE_API_ENDPOINTS =
      List.of("http://127.0.0.1:" + DEFAULT_REST_API_PORT);
  public static final boolean DEFAULT_FAILOVERS_SEND_SUBNET_SUBSCRIPTIONS_ENABLED = true;
  public static final boolean DEFAULT_VALIDATOR_CLIENT_SSZ_BLOCKS_ENABLED = false;
  public static final int DEFAULT_EXECUTOR_MAX_QUEUE_SIZE = 20_000;
  public static final Duration DEFAULT_VALIDATOR_EXTERNAL_SIGNER_TIMEOUT = Duration.ofSeconds(5);
  public static final int DEFAULT_VALIDATOR_EXTERNAL_SIGNER_CONCURRENT_REQUEST_LIMIT = 32;
  public static final boolean DEFAULT_VALIDATOR_KEYSTORE_LOCKING_ENABLED = true;
  public static final boolean DEFAULT_VALIDATOR_EXTERNAL_SIGNER_SLASHING_PROTECTION_ENABLED = true;
  public static final boolean DEFAULT_GENERATE_EARLY_ATTESTATIONS = true;
  public static final Optional<Bytes32> DEFAULT_GRAFFITI = Optional.empty();
  public static final boolean DEFAULT_VALIDATOR_PROPOSER_CONFIG_REFRESH_ENABLED = false;
  public static final boolean DEFAULT_BUILDER_REGISTRATION_DEFAULT_ENABLED = false;
  public static final boolean DEFAULT_VALIDATOR_BLINDED_BLOCKS_ENABLED = false;
  public static final int DEFAULT_VALIDATOR_REGISTRATION_SENDING_BATCH_SIZE = 100;
  public static final UInt64 DEFAULT_BUILDER_REGISTRATION_GAS_LIMIT = UInt64.valueOf(30_000_000);
  public static final Duration DEFAULT_PRIMARY_BEACON_NODE_EVENT_STREAM_RECONNECT_ATTEMPT_PERIOD =
      Duration.ofSeconds(30);

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
  private final Optional<List<URI>> beaconNodeApiEndpoints;
  private final int validatorExternalSignerConcurrentRequestLimit;
  private final boolean generateEarlyAttestations;
  private final Optional<Eth1Address> proposerDefaultFeeRecipient;
  private final Optional<String> proposerConfigSource;
  private final boolean refreshProposerConfigFromSource;
  private final boolean blindedBeaconBlocksEnabled;
  private final boolean builderRegistrationDefaultEnabled;
  private final boolean validatorClientUseSszBlocksEnabled;
  private final boolean failoversSendSubnetSubscriptionsEnabled;
  private final UInt64 builderRegistrationDefaultGasLimit;
  private final int builderRegistrationSendingBatchSize;
  private final Optional<UInt64> builderRegistrationTimestampOverride;
  private final Optional<BLSPublicKey> builderRegistrationPublicKeyOverride;
  private final int executorMaxQueueSize;
  private final Duration primaryBeaconNodeEventStreamReconnectAttemptPeriod;
  private final Optional<String> sentryNodeConfigurationFile;

  private ValidatorConfig(
      final List<String> validatorKeys,
      final List<String> validatorExternalSignerPublicKeySources,
      final URL validatorExternalSignerUrl,
      final Duration validatorExternalSignerTimeout,
      final Path validatorExternalSignerKeystore,
      final Path validatorExternalSignerKeystorePasswordFile,
      final Path validatorExternalSignerTruststore,
      final Path validatorExternalSignerTruststorePasswordFile,
      final Optional<List<URI>> beaconNodeApiEndpoints,
      final GraffitiProvider graffitiProvider,
      final ValidatorPerformanceTrackingMode validatorPerformanceTrackingMode,
      final boolean validatorKeystoreLockingEnabled,
      final boolean validatorExternalSignerSlashingProtectionEnabled,
      final int validatorExternalSignerConcurrentRequestLimit,
      final boolean generateEarlyAttestations,
      final Optional<Eth1Address> proposerDefaultFeeRecipient,
      final Optional<String> proposerConfigSource,
      final boolean refreshProposerConfigFromSource,
      final boolean builderRegistrationDefaultEnabled,
      final boolean blindedBeaconBlocksEnabled,
      final boolean validatorClientUseSszBlocksEnabled,
      final boolean failoversSendSubnetSubscriptionsEnabled,
      final UInt64 builderRegistrationDefaultGasLimit,
      final int builderRegistrationSendingBatchSize,
      final Optional<UInt64> builderRegistrationTimestampOverride,
      final Optional<BLSPublicKey> builderRegistrationPublicKeyOverride,
      final int executorMaxQueueSize,
      final Duration primaryBeaconNodeEventStreamReconnectAttemptPeriod,
      final Optional<String> sentryNodeConfigurationFile) {
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
    this.beaconNodeApiEndpoints = beaconNodeApiEndpoints;
    this.validatorPerformanceTrackingMode = validatorPerformanceTrackingMode;
    this.validatorExternalSignerSlashingProtectionEnabled =
        validatorExternalSignerSlashingProtectionEnabled;
    this.validatorExternalSignerConcurrentRequestLimit =
        validatorExternalSignerConcurrentRequestLimit;
    this.generateEarlyAttestations = generateEarlyAttestations;
    this.proposerDefaultFeeRecipient = proposerDefaultFeeRecipient;
    this.proposerConfigSource = proposerConfigSource;
    this.refreshProposerConfigFromSource = refreshProposerConfigFromSource;
    this.blindedBeaconBlocksEnabled = blindedBeaconBlocksEnabled;
    this.builderRegistrationDefaultEnabled = builderRegistrationDefaultEnabled;
    this.validatorClientUseSszBlocksEnabled = validatorClientUseSszBlocksEnabled;
    this.failoversSendSubnetSubscriptionsEnabled = failoversSendSubnetSubscriptionsEnabled;
    this.builderRegistrationDefaultGasLimit = builderRegistrationDefaultGasLimit;
    this.builderRegistrationSendingBatchSize = builderRegistrationSendingBatchSize;
    this.builderRegistrationTimestampOverride = builderRegistrationTimestampOverride;
    this.builderRegistrationPublicKeyOverride = builderRegistrationPublicKeyOverride;
    this.executorMaxQueueSize = executorMaxQueueSize;
    this.primaryBeaconNodeEventStreamReconnectAttemptPeriod =
        primaryBeaconNodeEventStreamReconnectAttemptPeriod;
    this.sentryNodeConfigurationFile = sentryNodeConfigurationFile;
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

  public Optional<URI> getPrimaryBeaconNodeApiEndpoint() {
    return beaconNodeApiEndpoints.map(endpoints -> endpoints.get(0));
  }

  public Optional<List<URI>> getBeaconNodeApiEndpoints() {
    return beaconNodeApiEndpoints;
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

  public Optional<Eth1Address> getProposerDefaultFeeRecipient() {
    validateProposerDefaultFeeRecipientOrProposerConfigSource();
    return proposerDefaultFeeRecipient;
  }

  public Optional<String> getProposerConfigSource() {
    validateProposerDefaultFeeRecipientOrProposerConfigSource();
    return proposerConfigSource;
  }

  public UInt64 getBuilderRegistrationDefaultGasLimit() {
    return builderRegistrationDefaultGasLimit;
  }

  public int getBuilderRegistrationSendingBatchSize() {
    return builderRegistrationSendingBatchSize;
  }

  public Optional<UInt64> getBuilderRegistrationTimestampOverride() {
    return builderRegistrationTimestampOverride;
  }

  public Optional<BLSPublicKey> getBuilderRegistrationPublicKeyOverride() {
    return builderRegistrationPublicKeyOverride;
  }

  public boolean getRefreshProposerConfigFromSource() {
    return refreshProposerConfigFromSource;
  }

  public boolean isBlindedBeaconBlocksEnabled() {
    return blindedBeaconBlocksEnabled;
  }

  public boolean isValidatorClientUseSszBlocksEnabled() {
    return validatorClientUseSszBlocksEnabled;
  }

  public boolean isFailoversSendSubnetSubscriptionsEnabled() {
    return failoversSendSubnetSubscriptionsEnabled;
  }

  public boolean isBuilderRegistrationDefaultEnabled() {
    return builderRegistrationDefaultEnabled;
  }

  public int getExecutorMaxQueueSize() {
    return executorMaxQueueSize;
  }

  public Duration getPrimaryBeaconNodeEventStreamReconnectAttemptPeriod() {
    return primaryBeaconNodeEventStreamReconnectAttemptPeriod;
  }

  public Optional<String> getSentryNodeConfigurationFile() {
    return sentryNodeConfigurationFile;
  }

  private void validateProposerDefaultFeeRecipientOrProposerConfigSource() {
    if (proposerDefaultFeeRecipient.isEmpty()
        && proposerConfigSource.isEmpty()
        && !(validatorKeys.isEmpty() && validatorExternalSignerPublicKeySources.isEmpty())) {
      throw new InvalidConfigurationException(
          "Invalid configuration. --validators-proposer-default-fee-recipient or --validators-proposer-config must be specified when Bellatrix milestone is active");
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
    private Optional<List<URI>> beaconNodeApiEndpoints = Optional.empty();
    private boolean validatorExternalSignerSlashingProtectionEnabled =
        DEFAULT_VALIDATOR_EXTERNAL_SIGNER_SLASHING_PROTECTION_ENABLED;
    private boolean generateEarlyAttestations = DEFAULT_GENERATE_EARLY_ATTESTATIONS;
    private Optional<Eth1Address> proposerDefaultFeeRecipient = Optional.empty();
    private Optional<String> proposerConfigSource = Optional.empty();
    private boolean refreshProposerConfigFromSource =
        DEFAULT_VALIDATOR_PROPOSER_CONFIG_REFRESH_ENABLED;
    private boolean validatorsRegistrationDefaultEnabled =
        DEFAULT_BUILDER_REGISTRATION_DEFAULT_ENABLED;
    private boolean blindedBlocksEnabled = DEFAULT_VALIDATOR_BLINDED_BLOCKS_ENABLED;
    private boolean validatorClientSszBlocksEnabled = DEFAULT_VALIDATOR_CLIENT_SSZ_BLOCKS_ENABLED;
    private boolean failoversSendSubnetSubscriptionsEnabled =
        DEFAULT_FAILOVERS_SEND_SUBNET_SUBSCRIPTIONS_ENABLED;
    private UInt64 builderRegistrationDefaultGasLimit = DEFAULT_BUILDER_REGISTRATION_GAS_LIMIT;
    private int builderRegistrationSendingBatchSize =
        DEFAULT_VALIDATOR_REGISTRATION_SENDING_BATCH_SIZE;
    private Optional<UInt64> builderRegistrationTimestampOverride = Optional.empty();
    private Optional<BLSPublicKey> builderRegistrationPublicKeyOverride = Optional.empty();
    private int executorMaxQueueSize = DEFAULT_EXECUTOR_MAX_QUEUE_SIZE;
    private Duration primaryBeaconNodeEventStreamReconnectAttemptPeriod =
        DEFAULT_PRIMARY_BEACON_NODE_EVENT_STREAM_RECONNECT_ATTEMPT_PERIOD;
    private Optional<String> sentryNodeConfigurationFile = Optional.empty();

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

    public Builder beaconNodeApiEndpoints(final List<URI> beaconNodeApiEndpoints) {
      this.beaconNodeApiEndpoints = Optional.of(beaconNodeApiEndpoints);
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

    public Builder generateEarlyAttestations(final boolean generateEarlyAttestations) {
      this.generateEarlyAttestations = generateEarlyAttestations;
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

    public Builder builderRegistrationDefaultEnabled(
        final boolean validatorsRegistrationDefaultEnabled) {
      this.validatorsRegistrationDefaultEnabled = validatorsRegistrationDefaultEnabled;
      return this;
    }

    public Builder blindedBeaconBlocksEnabled(final boolean blindedBeaconBlockEnabled) {
      this.blindedBlocksEnabled = blindedBeaconBlockEnabled;
      return this;
    }

    public Builder validatorClientUseSszBlocksEnabled(
        final boolean validatorClientUseSszBlocksEnabled) {
      this.validatorClientSszBlocksEnabled = validatorClientUseSszBlocksEnabled;
      return this;
    }

    public Builder failoversSendSubnetSubscriptionsEnabled(
        final boolean failoversSendSubnetSubscriptionsEnabled) {
      this.failoversSendSubnetSubscriptionsEnabled = failoversSendSubnetSubscriptionsEnabled;
      return this;
    }

    public Builder builderRegistrationDefaultGasLimit(
        final UInt64 builderRegistrationDefaultGasLimit) {
      this.builderRegistrationDefaultGasLimit = builderRegistrationDefaultGasLimit;
      return this;
    }

    public Builder builderRegistrationSendingBatchSize(
        final int builderRegistrationSendingBatchSize) {
      this.builderRegistrationSendingBatchSize = builderRegistrationSendingBatchSize;
      return this;
    }

    public Builder builderRegistrationTimestampOverride(
        final UInt64 builderRegistrationTimestampOverride) {
      this.builderRegistrationTimestampOverride =
          Optional.ofNullable(builderRegistrationTimestampOverride);
      return this;
    }

    public Builder builderRegistrationPublicKeyOverride(
        final String builderRegistrationPublicKeyOverride) {
      this.builderRegistrationPublicKeyOverride =
          Optional.ofNullable(builderRegistrationPublicKeyOverride)
              .map(BLSPublicKey::fromHexString);
      return this;
    }

    public Builder executorMaxQueueSize(final int executorMaxQueueSize) {
      this.executorMaxQueueSize = executorMaxQueueSize;
      return this;
    }

    public Builder primaryBeaconNodeEventStreamReconnectAttemptPeriod(
        final Duration primaryBeaconNodeEventStreamReconnectAttemptPeriod) {
      this.primaryBeaconNodeEventStreamReconnectAttemptPeriod =
          primaryBeaconNodeEventStreamReconnectAttemptPeriod;
      return this;
    }

    public Builder sentryNodeConfigurationFile(final String configFile) {
      this.sentryNodeConfigurationFile = Optional.ofNullable(configFile);
      return this;
    }

    public ValidatorConfig build() {
      validateExternalSignerUrlAndPublicKeys();
      validateExternalSignerKeystoreAndPasswordFileConfig();
      validateExternalSignerTruststoreAndPasswordFileConfig();
      validateExternalSignerURLScheme();
      validateValidatorsRegistrationAndBlindedBlocks();
      return new ValidatorConfig(
          validatorKeys,
          validatorExternalSignerPublicKeySources,
          validatorExternalSignerUrl,
          validatorExternalSignerTimeout,
          validatorExternalSignerKeystore,
          validatorExternalSignerKeystorePasswordFile,
          validatorExternalSignerTruststore,
          validatorExternalSignerTruststorePasswordFile,
          beaconNodeApiEndpoints,
          graffitiProvider,
          validatorPerformanceTrackingMode,
          validatorKeystoreLockingEnabled,
          validatorExternalSignerSlashingProtectionEnabled,
          validatorExternalSignerConcurrentRequestLimit,
          generateEarlyAttestations,
          proposerDefaultFeeRecipient,
          proposerConfigSource,
          refreshProposerConfigFromSource,
          validatorsRegistrationDefaultEnabled,
          blindedBlocksEnabled,
          validatorClientSszBlocksEnabled,
          failoversSendSubnetSubscriptionsEnabled,
          builderRegistrationDefaultGasLimit,
          builderRegistrationSendingBatchSize,
          builderRegistrationTimestampOverride,
          builderRegistrationPublicKeyOverride,
          executorMaxQueueSize,
          primaryBeaconNodeEventStreamReconnectAttemptPeriod,
          sentryNodeConfigurationFile);
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

    private void validateValidatorsRegistrationAndBlindedBlocks() {
      if (validatorsRegistrationDefaultEnabled && !blindedBlocksEnabled) {
        LOG.info(
            "'--validators-builder-registration-default-enabled' requires '--validators-proposer-blinded-blocks-enabled', enabling it");
        blindedBlocksEnabled = true;
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
