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

package tech.pegasys.artemis.cli.deposit;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static tech.pegasys.teku.logging.StatusLogger.STATUS_LOG;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.io.Files;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import org.apache.logging.log4j.Level;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Spec;
import tech.pegasys.artemis.services.powchain.DepositTransactionSender;
import tech.pegasys.artemis.util.async.SafeFuture;
import tech.pegasys.artemis.util.bls.BLSKeyPair;
import tech.pegasys.artemis.util.cli.VersionProvider;

@Command(
    name = "generate",
    description =
        "Register validators by generating new keys and sending deposit transactions to an Ethereum 1 node",
    mixinStandardHelpOptions = true,
    abbreviateSynopsis = true,
    versionProvider = VersionProvider.class,
    synopsisHeading = "%n",
    descriptionHeading = "%nDescription:%n%n",
    optionListHeading = "%nOptions:%n",
    footerHeading = "%n",
    footer = "Teku is licensed under the Apache License 2.0")
public class DepositGenerateCommand implements Runnable {
  private final Consumer<Integer> shutdownFunction;
  private final ConsoleAdapter consoleAdapter;
  private final Function<String, String> envSupplier;

  @Spec private CommandSpec spec;
  @Mixin private CommonParams params;

  @Option(
      names = {"-n", "--number-of-validators"},
      paramLabel = "<NUMBER>",
      description = "The number of validators to create keys for and register",
      defaultValue = "1")
  private int validatorCount = 1;

  @Option(
      names = {"--output-path", "-o"},
      paramLabel = "<FILE|DIR>",
      description =
          "Path to output file for unencrypted keys or output directory for encrypted keystore files. If not set, unencrypted keys will be written on standard out and encrypted keystores will be created in current directory")
  private String outputPath;

  @Option(
      names = {"--encrypted-keystore-enabled", "-e"},
      defaultValue = "true",
      paramLabel = "<true|false>",
      description = "Create encrypted keystores for validator and withdrawal keys. (Default: true)",
      arity = "1")
  private boolean encryptKeys = true;

  @ArgGroup(heading = "Non-interactive password options for validator keystores:%n")
  private ValidatorPasswordOptions validatorPasswordOptions;

  @ArgGroup(heading = "Non-interactive password options for withdrawal keystores:%n")
  private WithdrawalPasswordOptions withdrawalPasswordOptions;

  public DepositGenerateCommand() {
    this.shutdownFunction =
        System::exit; // required because web3j use non-daemon threads which halts the program
    this.envSupplier = System::getenv;
    this.consoleAdapter = new ConsoleAdapter();
  }

  @VisibleForTesting
  DepositGenerateCommand(
      final Consumer<Integer> shutdownFunction,
      final ConsoleAdapter consoleAdapter,
      final Function<String, String> envSupplier,
      final CommandSpec spec,
      final CommonParams params,
      final int validatorCount,
      final String outputPath,
      final boolean encryptKeys,
      final ValidatorPasswordOptions validatorPasswordOptions,
      final WithdrawalPasswordOptions withdrawalPasswordOptions) {
    this.consoleAdapter = consoleAdapter;
    this.shutdownFunction = shutdownFunction;
    this.envSupplier = envSupplier;
    this.spec = spec;
    this.params = params;
    this.validatorCount = validatorCount;
    this.outputPath = outputPath;
    this.encryptKeys = encryptKeys;
    this.validatorPasswordOptions = validatorPasswordOptions;
    this.withdrawalPasswordOptions = withdrawalPasswordOptions;
  }

  @Override
  public void run() {
    final KeysWriter keysWriter = getKeysWriter();
    final CommonParams _params = params; // making it effective final as it gets injected by PicoCLI
    try (_params) {
      final DepositTransactionSender sender = params.createTransactionSender();
      final List<SafeFuture<TransactionReceipt>> futures = new ArrayList<>();
      for (int i = 0; i < validatorCount; i++) {
        final BLSKeyPair validatorKey = BLSKeyPair.random();
        final BLSKeyPair withdrawalKey = BLSKeyPair.random();

        keysWriter.writeKeys(validatorKey, withdrawalKey);

        final SafeFuture<TransactionReceipt> transactionReceiptSafeFuture =
            CommonParams.sendDeposit(
                sender, validatorKey, withdrawalKey.getPublicKey(), params.getAmount());
        futures.add(transactionReceiptSafeFuture);
      }

      SafeFuture.allOf(futures.toArray(SafeFuture[]::new)).get(2, TimeUnit.MINUTES);
    } catch (final Throwable t) {
      STATUS_LOG.log(
          Level.FATAL,
          "Failed to send deposit transaction: " + t.getClass() + ": " + t.getMessage());
      shutdownFunction.accept(1);
    }
    shutdownFunction.accept(0);
  }

  private KeysWriter getKeysWriter() {
    final KeysWriter keysWriter;
    if (encryptKeys) {
      final String validatorKeystorePassword = readValidatorKeystorePassword();
      final String withdrawalKeystorePassword = readWithdrawalKeystorePassword();

      final Path keystoreDir = getKeystoreOutputDir();
      keysWriter =
          new EncryptedKeystoreWriter(
              validatorKeystorePassword, withdrawalKeystorePassword, keystoreDir);
      STATUS_LOG.log(Level.INFO, "Generating Encrypted Keystores in " + keystoreDir);
    } else {
      keysWriter = new YamlKeysWriter(isBlank(outputPath) ? null : Path.of(outputPath));
    }
    return keysWriter;
  }

  private Path getKeystoreOutputDir() {
    return isBlank(outputPath) ? Path.of(".") : Path.of(outputPath);
  }

  private String readWithdrawalKeystorePassword() {
    final String withdrawalKeystorePassword;
    if (withdrawalPasswordOptions == null) {
      withdrawalKeystorePassword = askForPassword("Withdrawal Keystore");
    } else if (withdrawalPasswordOptions.withdrawalPasswordFile != null) {
      withdrawalKeystorePassword = readFromFile(withdrawalPasswordOptions.withdrawalPasswordFile);
    } else {
      withdrawalKeystorePassword =
          readFromEnvironmentVariable(withdrawalPasswordOptions.withdrawalPasswordEnv);
    }
    return withdrawalKeystorePassword;
  }

  private String readValidatorKeystorePassword() {
    final String validatorKeystorePassword;
    if (validatorPasswordOptions == null) {
      validatorKeystorePassword = askForPassword("Validator Keystore");
    } else if (validatorPasswordOptions.validatorPasswordFile != null) {
      validatorKeystorePassword = readFromFile(validatorPasswordOptions.validatorPasswordFile);
    } else {
      validatorKeystorePassword =
          readFromEnvironmentVariable(validatorPasswordOptions.validatorPasswordEnv);
    }
    return validatorKeystorePassword;
  }

  private String askForPassword(final String option) {

    if (!consoleAdapter.isConsoleAvailable()) {
      throw new ParameterException(
          spec.commandLine(), "Cannot read password from console: Console not available");
    }

    final char[] firstInput = consoleAdapter.readPassword("Enter password for %s:", option);
    final char[] reconfirmedInput =
        consoleAdapter.readPassword("Re-Enter password for %s:", option);
    if (firstInput == null || reconfirmedInput == null) {
      throw new ParameterException(spec.commandLine(), "Error: Password is blank");
    }

    if (Arrays.equals(firstInput, reconfirmedInput)) {
      final String password = new String(firstInput);
      if (password.isBlank()) {
        throw new ParameterException(spec.commandLine(), "Error: Password is blank");
      }
      return password;
    }

    throw new ParameterException(spec.commandLine(), "Error: Password mismatched.");
  }

  private String readFromEnvironmentVariable(final String environmentVariable) {
    final String password = envSupplier.apply(environmentVariable);
    if (password == null) {
      throw new ParameterException(
          spec.commandLine(),
          "Error: Password cannot be read from environment variable: " + environmentVariable);
    }
    return password;
  }

  private String readFromFile(final File passwordFile) {
    try {
      final String password =
          Files.asCharSource(passwordFile, StandardCharsets.UTF_8).readFirstLine();
      if (isBlank(password)) {
        throw new ParameterException(
            spec.commandLine(), "Error: Empty password from file: " + passwordFile);
      }
      return password;
    } catch (final FileNotFoundException e) {
      throw new ParameterException(spec.commandLine(), "Error: File not found: " + passwordFile);
    } catch (final IOException e) {
      throw new ParameterException(
          spec.commandLine(),
          "Error: Unexpected IO error reading file [" + passwordFile + "] : " + e.getMessage());
    }
  }

  static class ValidatorPasswordOptions {
    @Option(
        names = {"--validator-password-file"},
        paramLabel = "<FILE>",
        required = true,
        description = "Read password from the file to encrypt the validator keys")
    File validatorPasswordFile;

    @Option(
        names = {"--validator-password-env"},
        paramLabel = "<ENV_VAR>",
        required = true,
        description = "Read password from environment variable to encrypt the validator keys")
    String validatorPasswordEnv;
  }

  static class WithdrawalPasswordOptions {
    @Option(
        names = {"--withdrawal-password-file"},
        paramLabel = "<FILE>",
        description = "Read password from the file to encrypt the withdrawal keys")
    File withdrawalPasswordFile;

    @Option(
        names = {"--withdrawal-password-env"},
        paramLabel = "<ENV_VAR>",
        description = "Read password from environment variable to encrypt the withdrawal keys")
    String withdrawalPasswordEnv;
  }
}
