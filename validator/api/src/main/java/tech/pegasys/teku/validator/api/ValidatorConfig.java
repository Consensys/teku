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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.status.StatusLogger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.util.config.InvalidConfigurationException;
import tech.pegasys.teku.util.config.KeyStoreFilesLocator;
import tech.pegasys.teku.util.config.ValidatorPerformanceTrackingMode;

public class ValidatorConfig {

  private final List<String> validatorKeys;
  private final List<String> validatorKeystoreFiles;
  private final List<String> validatorKeystorePasswordFiles;
  private final List<BLSPublicKey> validatorExternalSignerPublicKeys;
  private final URL validatorExternalSignerUrl;
  private final int validatorExternalSignerTimeout;
  private final Bytes32 graffiti;
  private final ValidatorPerformanceTrackingMode validatorPerformanceTrackingMode;
  private final boolean validatorKeystoreLockingEnabled;
  private final Optional<URI> beaconNodeApiEndpoint;

  private ValidatorConfig(
      final List<String> validatorKeys,
      final List<String> validatorKeystoreFiles,
      final List<String> validatorKeystorePasswordFiles,
      final List<BLSPublicKey> validatorExternalSignerPublicKeys,
      final URL validatorExternalSignerUrl,
      final int validatorExternalSignerTimeout,
      final Optional<URI> beaconNodeApiEndpoint,
      final Bytes32 graffiti,
      final ValidatorPerformanceTrackingMode validatorPerformanceTrackingMode,
      final boolean validatorKeystoreLockingEnabled) {
    this.validatorKeys = validatorKeys;
    this.validatorKeystoreFiles = validatorKeystoreFiles;
    this.validatorKeystorePasswordFiles = validatorKeystorePasswordFiles;
    this.validatorExternalSignerPublicKeys = validatorExternalSignerPublicKeys;
    this.validatorExternalSignerUrl = validatorExternalSignerUrl;
    this.validatorExternalSignerTimeout = validatorExternalSignerTimeout;
    this.graffiti = graffiti;
    this.validatorKeystoreLockingEnabled = validatorKeystoreLockingEnabled;
    this.beaconNodeApiEndpoint = beaconNodeApiEndpoint;
    this.validatorPerformanceTrackingMode = validatorPerformanceTrackingMode;
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

  public URL getValidatorExternalSignerUrl() {
    return validatorExternalSignerUrl;
  }

  public int getValidatorExternalSignerTimeout() {
    return validatorExternalSignerTimeout;
  }

  public Optional<URI> getBeaconNodeApiEndpoint() {
    return beaconNodeApiEndpoint;
  }

  public Bytes32 getGraffiti() {
    return graffiti;
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

  public static final class Builder {

    private List<String> validatorKeys = new ArrayList<>();
    private List<String> validatorKeystoreFiles = new ArrayList<>();
    private List<String> validatorKeystorePasswordFiles = new ArrayList<>();
    private List<BLSPublicKey> validatorExternalSignerPublicKeys = new ArrayList<>();
    private URL validatorExternalSignerUrl;
    private int validatorExternalSignerTimeout;
    private Bytes32 graffiti;
    private ValidatorPerformanceTrackingMode validatorPerformanceTrackingMode;
    private boolean validatorKeystoreLockingEnabled;
    private Optional<URI> beaconNodeApiEndpoint = Optional.empty();

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

    public Builder validatorExternalSignerTimeout(int validatorExternalSignerTimeout) {
      this.validatorExternalSignerTimeout = validatorExternalSignerTimeout;
      return this;
    }

    public Builder beaconNodeApiEndpoint(final URI beaconNodeApiEndpoint) {
      this.beaconNodeApiEndpoint = Optional.of(beaconNodeApiEndpoint);
      return this;
    }

    public Builder graffiti(Bytes32 graffiti) {
      this.graffiti = graffiti;
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

    public ValidatorConfig build() {
      validateKeyStoreFilesAndPasswordFilesConfig();
      return new ValidatorConfig(
          validatorKeys,
          validatorKeystoreFiles,
          validatorKeystorePasswordFiles,
          validatorExternalSignerPublicKeys,
          validatorExternalSignerUrl,
          validatorExternalSignerTimeout,
          beaconNodeApiEndpoint,
          graffiti,
          validatorPerformanceTrackingMode,
          validatorKeystoreLockingEnabled);
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
  }
}
