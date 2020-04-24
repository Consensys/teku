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

package tech.pegasys.artemis.cli.options;

import java.util.ArrayList;
import picocli.CommandLine;

public class ValidatorOptions {

  public static final String VALIDATORS_KEY_FILE_OPTION_NAME = "--validators-key-file";
  public static final String VALIDATORS_KEYSTORE_FILES_OPTION_NAME = "--validators-key-files";
  public static final String VALIDATORS_KEYSTORE_PASSWORD_FILES_OPTION_NAME =
      "--validators-key-password-files";
  public static final String VALIDATORS_EXTERNAL_SIGNER_PUBLIC_KEYS_OPTION_NAME =
      "--validators-external-signer-public-keys";
  public static final String VALIDATORS_EXTERNAL_SIGNER_URL_OPTION_NAME =
      "--validators-external-signer-url";
  public static final String VALIDATORS_EXTERNAL_SIGNER_TIMEOUT_OPTION_NAME =
      "--validators-external-signer-timeout";

  public static final String DEFAULT_VALIDATORS_KEY_FILE = null;
  public static final ArrayList<String> DEFAULT_VALIDATORS_KEYSTORE_FILES = new ArrayList<>();
  public static final ArrayList<String> DEFAULT_VALIDATORS_KEYSTORE_PASSWORD_FILES =
      new ArrayList<>();
  public static final ArrayList<String> DEFAULT_VALIDATORS_EXTERNAL_SIGNER_PUBLIC_KEYS =
      new ArrayList<>();
  public static final String DEFAULT_VALIDATORS_EXTERNAL_SIGNER_URL = null;
  public static final int DEFAULT_VALIDATORS_EXTERNAL_SIGNER_TIMEOUT = 1000;

  @CommandLine.Option(
      names = {VALIDATORS_KEY_FILE_OPTION_NAME},
      paramLabel = "<FILENAME>",
      description = "The file to load validator keys from",
      arity = "1")
  private String validatorKeyFile = DEFAULT_VALIDATORS_KEY_FILE;

  @CommandLine.Option(
      names = {VALIDATORS_KEYSTORE_FILES_OPTION_NAME},
      paramLabel = "<FILENAMES>",
      description = "The list of encrypted keystore files to load the validator keys from",
      split = ",",
      arity = "0..*")
  private ArrayList<String> validatorKeystoreFiles = DEFAULT_VALIDATORS_KEYSTORE_FILES;

  @CommandLine.Option(
      names = {VALIDATORS_KEYSTORE_PASSWORD_FILES_OPTION_NAME},
      paramLabel = "<FILENAMES>",
      description = "The list of password files to decrypt the validator keystore files",
      split = ",",
      arity = "0..*")
  private ArrayList<String> validatorKeystorePasswordFiles =
      DEFAULT_VALIDATORS_KEYSTORE_PASSWORD_FILES;

  @CommandLine.Option(
      names = {VALIDATORS_EXTERNAL_SIGNER_PUBLIC_KEYS_OPTION_NAME},
      paramLabel = "<STRINGS>",
      description = "The list of external signer public keys",
      split = ",",
      arity = "0..*")
  private ArrayList<String> validatorExternalSignerPublicKeys =
      DEFAULT_VALIDATORS_EXTERNAL_SIGNER_PUBLIC_KEYS;

  @CommandLine.Option(
      names = {VALIDATORS_EXTERNAL_SIGNER_URL_OPTION_NAME},
      paramLabel = "<NETWORK>",
      description = "URL for the external signing service",
      arity = "1")
  private String validatorExternalSignerUrl = DEFAULT_VALIDATORS_EXTERNAL_SIGNER_URL;

  @CommandLine.Option(
      names = {VALIDATORS_EXTERNAL_SIGNER_TIMEOUT_OPTION_NAME},
      paramLabel = "<INTEGER>",
      description = "Timeout for the external signing service",
      arity = "1")
  private int validatorExternalSignerTimeout = DEFAULT_VALIDATORS_EXTERNAL_SIGNER_TIMEOUT;

  public String getValidatorKeyFile() {
    return validatorKeyFile;
  }

  public ArrayList<String> getValidatorKeystoreFiles() {
    return validatorKeystoreFiles;
  }

  public ArrayList<String> getValidatorKeystorePasswordFiles() {
    return validatorKeystorePasswordFiles;
  }

  public ArrayList<String> getValidatorExternalSignerPublicKeys() {
    return validatorExternalSignerPublicKeys;
  }

  public String getValidatorExternalSignerUrl() {
    return validatorExternalSignerUrl;
  }

  public int getValidatorExternalSignerTimeout() {
    return validatorExternalSignerTimeout;
  }
}
