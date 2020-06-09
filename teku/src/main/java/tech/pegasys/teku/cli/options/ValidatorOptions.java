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

import java.util.ArrayList;
import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import picocli.CommandLine.Option;
import tech.pegasys.teku.util.cli.GraffitiConverter;

public class ValidatorOptions {

  @Option(
      names = {"--validators-unencrypted-key-file"},
      paramLabel = "<FILENAME>",
      description = "The file to load unencrypted validator keys from",
      arity = "1")
  private String validatorKeyFile = null;

  @Option(
      names = {"--validators-key-files"},
      paramLabel = "<FILENAMES>",
      description = "The list of encrypted keystore files to load the validator keys from",
      split = ",",
      arity = "0..*")
  private List<String> validatorKeystoreFiles = new ArrayList<>();

  @Option(
      names = {"--validators-key-password-files"},
      paramLabel = "<FILENAMES>",
      description = "The list of password files to decrypt the validator keystore files",
      split = ",",
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

  public String getValidatorKeyFile() {
    return validatorKeyFile;
  }

  public List<String> getValidatorKeystoreFiles() {
    return validatorKeystoreFiles;
  }

  public List<String> getValidatorKeystorePasswordFiles() {
    return validatorKeystorePasswordFiles;
  }

  public List<String> getValidatorExternalSignerPublicKeys() {
    return validatorExternalSignerPublicKeys;
  }

  public String getValidatorExternalSignerUrl() {
    return validatorExternalSignerUrl;
  }

  public int getValidatorExternalSignerTimeout() {
    return validatorExternalSignerTimeout;
  }

  public Bytes32 getGraffiti() {
    return graffiti;
  }
}
