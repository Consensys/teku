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

package tech.pegasys.teku.cli.deposit;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static tech.pegasys.teku.cli.deposit.KeystorePasswordOptions.readFromEnvironmentVariable;
import static tech.pegasys.teku.cli.deposit.KeystorePasswordOptions.readFromFile;
import static tech.pegasys.teku.logging.SubCommandLogger.SUB_COMMAND_LOG;

import java.io.File;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.util.crypto.SecureRandomProvider;

public class GenerateAction {
  private static final String VALIDATOR_PASSWORD_PROMPT = "Validator Keystore";
  private static final String WITHDRAWAL_PASSWORD_PROMPT = "Withdrawal Keystore";
  private final int validatorCount;
  private final String outputPath;
  private final boolean encryptKeys;
  private final ValidatorPasswordOptions validatorPasswordOptions;
  private final WithdrawalPasswordOptions withdrawalPasswordOptions;
  private final SecureRandom srng;
  private final ConsoleAdapter consoleAdapter;
  private final boolean displayConfirmation;
  private final CommandSpec commandSpec;
  private final Function<String, String> envSupplier;

  public GenerateAction(
      final int validatorCount,
      final String outputPath,
      final boolean encryptKeys,
      final ValidatorPasswordOptions validatorPasswordOptions,
      final WithdrawalPasswordOptions withdrawalPasswordOptions,
      final boolean displayConfirmation,
      final ConsoleAdapter consoleAdapter,
      final CommandSpec commandSpec,
      final Function<String, String> envSupplier) {
    this.validatorCount = validatorCount;
    this.outputPath = outputPath;
    this.encryptKeys = encryptKeys;
    this.validatorPasswordOptions = validatorPasswordOptions;
    this.withdrawalPasswordOptions = withdrawalPasswordOptions;
    this.consoleAdapter = consoleAdapter;
    this.displayConfirmation = displayConfirmation;
    this.commandSpec = commandSpec;
    this.envSupplier = envSupplier;
    this.srng = SecureRandomProvider.createSecureRandom();
  }

  public List<ValidatorKeys> generateKeys() {
    return generateKeysStream().collect(Collectors.toList());
  }

  public Stream<ValidatorKeys> generateKeysStream() {
    final KeysWriter keysWriter = getKeysWriter(srng);
    return IntStream.range(0, validatorCount).mapToObj(ignore -> generateKey(keysWriter));
  }

  private ValidatorKeys generateKey(final KeysWriter keysWriter) {
    final BLSKeyPair validatorKey = BLSKeyPair.random(srng);
    final BLSKeyPair withdrawalKey = BLSKeyPair.random(srng);
    keysWriter.writeKeys(validatorKey, withdrawalKey);
    return new ValidatorKeys(validatorKey, withdrawalKey);
  }

  private KeysWriter getKeysWriter(final SecureRandom secureRandom) {
    final KeysWriter keysWriter;
    if (encryptKeys) {
      final String validatorKeystorePassword =
          readKeystorePassword(validatorPasswordOptions, VALIDATOR_PASSWORD_PROMPT);
      final String withdrawalKeystorePassword =
          readKeystorePassword(withdrawalPasswordOptions, WITHDRAWAL_PASSWORD_PROMPT);

      final Path keystoreDir = getKeystoreOutputDir();
      keysWriter =
          new EncryptedKeystoreWriter(
              secureRandom, validatorKeystorePassword, withdrawalKeystorePassword, keystoreDir);
    } else {
      keysWriter = new YamlKeysWriter(isBlank(outputPath) ? null : Path.of(outputPath));
      if (consoleAdapter.isConsoleAvailable() && isBlank(outputPath) && displayConfirmation) {
        SUB_COMMAND_LOG.display(
            "NOTE: This is the only time your keys will be displayed. Save these before they are gone!");
      }
    }
    return keysWriter;
  }

  private Path getKeystoreOutputDir() {
    return isBlank(outputPath) ? Path.of(".") : Path.of(outputPath);
  }

  private String readKeystorePassword(
      final KeystorePasswordOptions keystorePasswordOptions, final String passwordPrompt) {
    final String password;
    if (keystorePasswordOptions == null) {
      password = askForPassword(passwordPrompt);
    } else if (keystorePasswordOptions.getPasswordFile() != null) {
      password = readFromFile(commandSpec.commandLine(), keystorePasswordOptions.getPasswordFile());
    } else {
      password =
          readFromEnvironmentVariable(
              commandSpec.commandLine(),
              envSupplier,
              keystorePasswordOptions.getPasswordEnvironmentVariable());
    }
    return password;
  }

  private String askForPassword(final String option) {

    if (!consoleAdapter.isConsoleAvailable()) {
      throw new ParameterException(
          commandSpec.commandLine(), "Cannot read password from console: Console not available");
    }

    final char[] firstInput = consoleAdapter.readPassword("Enter password for %s:", option);
    final char[] reconfirmedInput =
        consoleAdapter.readPassword("Re-Enter password for %s:", option);
    if (firstInput == null || reconfirmedInput == null) {
      throw new ParameterException(commandSpec.commandLine(), "Error: Password is blank");
    }

    if (Arrays.equals(firstInput, reconfirmedInput)) {
      final String password = new String(firstInput);
      if (password.isBlank()) {
        throw new ParameterException(commandSpec.commandLine(), "Error: Password is blank");
      }
      return password;
    }

    throw new ParameterException(commandSpec.commandLine(), "Error: Password mismatched");
  }

  static class ValidatorPasswordOptions implements KeystorePasswordOptions {
    @Option(
        names = {"--encrypted-keystore-validator-password-file"},
        paramLabel = "<FILE>",
        required = true,
        description = "Read password from the file to encrypt the validator keys")
    File validatorPasswordFile;

    @Option(
        names = {"--encrypted-keystore-validator-password-env"},
        paramLabel = "<ENV_VAR>",
        required = true,
        description = "Read password from environment variable to encrypt the validator keys")
    String validatorPasswordEnv;

    @Override
    public File getPasswordFile() {
      return validatorPasswordFile;
    }

    @Override
    public String getPasswordEnvironmentVariable() {
      return validatorPasswordEnv;
    }
  }

  static class WithdrawalPasswordOptions implements KeystorePasswordOptions {
    @Option(
        names = {"--encrypted-keystore-withdrawal-password-file"},
        paramLabel = "<FILE>",
        description = "Read password from the file to encrypt the withdrawal keys")
    File withdrawalPasswordFile;

    @Option(
        names = {"--encrypted-keystore-withdrawal-password-env"},
        paramLabel = "<ENV_VAR>",
        description = "Read password from environment variable to encrypt the withdrawal keys")
    String withdrawalPasswordEnv;

    @Override
    public File getPasswordFile() {
      return withdrawalPasswordFile;
    }

    @Override
    public String getPasswordEnvironmentVariable() {
      return withdrawalPasswordEnv;
    }
  }

  public static class ValidatorKeys {
    final BLSKeyPair validatorKey;
    final BLSKeyPair withdrawalKey;

    public ValidatorKeys(final BLSKeyPair validatorKey, final BLSKeyPair withdrawalKey) {
      this.validatorKey = validatorKey;
      this.withdrawalKey = withdrawalKey;
    }

    public BLSKeyPair getValidatorKey() {
      return validatorKey;
    }

    public BLSKeyPair getWithdrawalKey() {
      return withdrawalKey;
    }
  }
}
