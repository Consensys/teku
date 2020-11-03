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

package tech.pegasys.teku.cli.options;

import com.google.common.base.Strings;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import picocli.CommandLine.Option;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.cli.converter.GraffitiConverter;
import tech.pegasys.teku.config.TekuConfiguration;
import tech.pegasys.teku.util.config.InvalidConfigurationException;
import tech.pegasys.teku.util.config.ValidatorPerformanceTrackingMode;

public class ValidatorOptions {
  @Option(
      names = {"--validator-keys"},
      paramLabel = "<KEY_DIR>:<PASS_DIR> | <KEY_FILE>:<PASS_FILE>",
      description =
          "<KEY_DIR>:<PASS_DIR> will find <KEY_DIR>/**.json, and expect to find <PASS_DIR>/**.txt.\n"
              + "<KEY_FILE>:<PASS_FILE> will expect that the file <KEY_FILE> exists, "
              + "and the file containing the password for it is <PASS_FILE>.\n"
              + "The path separator is operating system dependent, and should be ';' in windows rather than ':'.",
      split = ",",
      arity = "1..*")
  private List<String> validatorKeys = new ArrayList<>();

  @Option(
      names = {"--validators-key-files"},
      paramLabel = "<FILENAMES>",
      description = "The list of encrypted keystore files to load the validator keys from",
      split = ",",
      hidden = true,
      arity = "0..*")
  private List<String> validatorKeystoreFiles = new ArrayList<>();

  @Option(
      names = {"--validators-key-password-files"},
      paramLabel = "<FILENAMES>",
      description = "The list of password files to decrypt the validator keystore files",
      split = ",",
      hidden = true,
      arity = "0..*")
  private List<String> validatorKeystorePasswordFiles = new ArrayList<>();

  @Option(
      names = {"--validators-external-signer-public-keys"},
      paramLabel = "<STRINGS>",
      description = "The list of external signer public keys",
      split = ",",
      arity = "0..*")
  private List<String> validatorExternalSignerPublicKeys = new ArrayList<>();

  @Option(
      names = {"--validators-external-signer-url"},
      paramLabel = "<NETWORK>",
      description = "URL for the external signing service",
      arity = "1")
  private String validatorExternalSignerUrl = null;

  @Option(
      names = {"--validators-external-signer-timeout"},
      paramLabel = "<INTEGER>",
      description = "Timeout (in milliseconds) for the external signing service",
      arity = "1")
  private int validatorExternalSignerTimeout = 1000;

  @Option(
      names = {"--validators-graffiti"},
      converter = GraffitiConverter.class,
      paramLabel = "<GRAFFITI STRING>",
      description =
          "Graffiti to include during block creation (gets converted to bytes and padded to Bytes32).",
      arity = "1")
  private Bytes32 graffiti;

  @Option(
      names = {"--validators-performance-tracking-enabled"},
      paramLabel = "<BOOLEAN>",
      description = "Enable validator performance tracking",
      fallbackValue = "true",
      arity = "0..1")
  private boolean validatorPerformanceTrackingEnabled = false;

  @Option(
      names = {"--validators-performance-tracking-mode"},
      paramLabel = "<TRACKING_MODE>",
      description = "Set strategy for handling performance tracking",
      arity = "1")
  private ValidatorPerformanceTrackingMode validatorPerformanceTrackingMode =
      ValidatorPerformanceTrackingMode.ALL;

  @Option(
      names = {"--validators-keystore-locking-enabled"},
      paramLabel = "<BOOLEAN>",
      description = "Enable locking validator keystore files",
      arity = "1")
  private boolean validatorKeystoreLockingEnabled = true;

  public void configure(TekuConfiguration.Builder builder) {
    builder.validator(
        config ->
            config
                .validatorKeystoreLockingEnabled(validatorKeystoreLockingEnabled)
                .validatorKeystoreFiles(validatorKeystoreFiles)
                .validatorKeystorePasswordFiles(validatorKeystorePasswordFiles)
                .validatorExternalSignerPublicKeys(parseExternalSignerPublicKeys())
                .validatorExternalSignerUrl(parseValidatorExternalSignerUrl())
                .validatorExternalSignerTimeout(validatorExternalSignerTimeout)
                .validatorPerformanceTrackingEnabled(validatorPerformanceTrackingEnabled)
                .validatorPerformanceTrackingMode(validatorPerformanceTrackingMode)
                .graffiti(graffiti)
                .validatorKeys(validatorKeys));
  }

  private List<BLSPublicKey> parseExternalSignerPublicKeys() {
    if (validatorExternalSignerPublicKeys == null) {
      return Collections.emptyList();
    }
    try {
      return validatorExternalSignerPublicKeys.stream()
          .map(key -> BLSPublicKey.fromSSZBytes(Bytes.fromHexString(key)))
          .collect(Collectors.toList());
    } catch (IllegalArgumentException e) {
      throw new InvalidConfigurationException(
          "Invalid configuration. Signer public key is invalid", e);
    }
  }

  public URL parseValidatorExternalSignerUrl() {
    if (Strings.isNullOrEmpty(validatorExternalSignerUrl)) {
      return null;
    }
    try {
      return new URL(validatorExternalSignerUrl);
    } catch (MalformedURLException e) {
      throw new InvalidConfigurationException(
          "Invalid configuration. Signer URL has invalid syntax", e);
    }
  }
}
