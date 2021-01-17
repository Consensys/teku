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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import picocli.CommandLine;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.config.TekuConfiguration;
import tech.pegasys.teku.util.config.InvalidConfigurationException;

public class ValidatorKeysOptions {

  @CommandLine.Option(
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

  @CommandLine.Option(
      names = {"--validators-key-files"},
      paramLabel = "<FILENAMES>",
      description = "The list of encrypted keystore files to load the validator keys from",
      split = ",",
      hidden = true,
      arity = "0..*")
  private List<String> validatorKeystoreFiles = new ArrayList<>();

  @CommandLine.Option(
      names = {"--validators-key-password-files"},
      paramLabel = "<FILENAMES>",
      description = "The list of password files to decrypt the validator keystore files",
      split = ",",
      hidden = true,
      arity = "0..*")
  private List<String> validatorKeystorePasswordFiles = new ArrayList<>();

  @CommandLine.Option(
      names = {"--validators-external-signer-public-keys"},
      paramLabel = "<STRINGS>",
      description = "The list of external signer public keys",
      split = ",",
      arity = "0..*")
  private List<String> validatorExternalSignerPublicKeys = new ArrayList<>();

  @CommandLine.Option(
      names = {"--validators-external-signer-url"},
      paramLabel = "<NETWORK>",
      description = "URL for the external signing service",
      arity = "1")
  private String validatorExternalSignerUrl = null;

  @CommandLine.Option(
      names = {"--validators-external-signer-timeout"},
      paramLabel = "<INTEGER>",
      description = "Timeout (in milliseconds) for the external signing service",
      arity = "1")
  private long validatorExternalSignerTimeout = 5000;

  @CommandLine.Option(
      names = {"--validators-external-signer-keystore"},
      paramLabel = "<FILE>",
      description =
          "Keystore (PKCS12/JKS) to use for TLS mutual authentication with external signer",
      arity = "1")
  private String validatorExternalSignerKeystore = null;

  @CommandLine.Option(
      names = {"--validators-external-signer-keystore-password-file"},
      paramLabel = "<FILE>",
      description =
          "Password file to decrypt keystore (PKCS12/JKS) that will be used for TLS mutual authentication with external signer",
      arity = "1")
  private String validatorExternalSignerKeystorePasswordFile = null;

  @CommandLine.Option(
      names = {"--validators-external-signer-truststore"},
      paramLabel = "<FILE>",
      description = "Keystore (PKCS12/JKS) to trust external signer's self-signed certificate",
      arity = "1")
  private String validatorExternalSignerTruststore = null;

  @CommandLine.Option(
      names = {"--validators-external-signer-truststore-password-file"},
      paramLabel = "<FILE>",
      description =
          "Password file to decrypt keystore (PKCS12/JKS) that will be used to trust external signer's self-signed certificate",
      arity = "1")
  private String validatorExternalSignerTruststorePasswordFile = null;

  @CommandLine.Option(
      names = {"--Xvalidators-external-signer-concurrent-limit"},
      paramLabel = "<INTEGER>",
      description = "The maximum number of concurrent background requests to make to the signer.",
      hidden = true,
      arity = "1")
  private int validatorExternalSignerConcurrentRequestLimit = 32;

  public void configure(TekuConfiguration.Builder builder) {
    builder.validator(
        config ->
            config
                .validatorKeys(validatorKeys)
                .validatorExternalSignerPublicKeys(parseExternalSignerPublicKeys())
                .validatorExternalSignerUrl(parseValidatorExternalSignerUrl())
                .validatorExternalSignerConcurrentRequestLimit(
                    validatorExternalSignerConcurrentRequestLimit)
                .validatorExternalSignerTimeout(Duration.ofMillis(validatorExternalSignerTimeout))
                .validatorExternalSignerKeystore(convertToPath(validatorExternalSignerKeystore))
                .validatorExternalSignerKeystorePasswordFile(
                    convertToPath(validatorExternalSignerKeystorePasswordFile))
                .validatorExternalSignerTruststore(convertToPath(validatorExternalSignerTruststore))
                .validatorExternalSignerTruststorePasswordFile(
                    convertToPath(validatorExternalSignerTruststorePasswordFile))
                .validatorKeystoreFiles(validatorKeystoreFiles)
                .validatorKeystorePasswordFiles(validatorKeystorePasswordFiles));
  }

  private List<BLSPublicKey> parseExternalSignerPublicKeys() {
    PublicKeyLoader loader = new PublicKeyLoader();
    return loader.getPublicKeys(validatorExternalSignerPublicKeys);
  }

  private URL parseValidatorExternalSignerUrl() {
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

  private Path convertToPath(final String option) {
    if (Strings.isNullOrEmpty(option)) {
      return null;
    }
    return Path.of(option);
  }

  static class PublicKeyLoader {
    final ObjectMapper objectMapper;

    public PublicKeyLoader() {
      this(new ObjectMapper());
    }

    public PublicKeyLoader(final ObjectMapper objectMapper) {
      this.objectMapper = objectMapper;
    }

    public List<BLSPublicKey> getPublicKeys(final List<String> publicKeys) {
      if (publicKeys == null || publicKeys.isEmpty()) {
        return Collections.emptyList();
      }

      try {
        final Set<BLSPublicKey> blsPublicKeySet =
            publicKeys.stream()
                .filter(key -> !key.contains(":"))
                .map(key -> BLSPublicKey.fromSSZBytes(Bytes.fromHexString(key)))
                .collect(Collectors.toSet());
        blsPublicKeySet.addAll(
            publicKeys.stream()
                .filter(key -> key.contains(":"))
                .flatMap(this::readKeysFromUrl)
                .collect(Collectors.toList()));

        return List.copyOf(blsPublicKeySet);
      } catch (IllegalArgumentException e) {
        throw new InvalidConfigurationException(
            "Invalid configuration. Signer public key is invalid", e);
      }
    }

    private Stream<BLSPublicKey> readKeysFromUrl(final String url) {
      try {
        return objectMapper.readValue(url, PublicKeyRemoteList.class).keys.stream()
            .map(key -> BLSPublicKey.fromSSZBytes(Bytes.fromHexString(key)));
      } catch (IOException ex) {
        throw new IllegalArgumentException("Failed to load public keys from URL", ex);
      }
    }

    static class PublicKeyRemoteList {
      @JsonProperty("keys")
      public List<String> keys;
    }
  }
}
