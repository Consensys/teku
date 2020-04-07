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
import static tech.pegasys.artemis.cli.deposit.KeystorePasswordOptions.readFromEnvironmentVariable;
import static tech.pegasys.artemis.cli.deposit.KeystorePasswordOptions.readFromFile;
import static tech.pegasys.teku.logging.SubCommandLogger.SUB_COMMAND_LOG;

import com.google.common.annotations.VisibleForTesting;
import java.io.File;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Spec;
import picocli.CommandLine.TypeConversionException;
import tech.pegasys.artemis.services.powchain.DepositTransactionSender;
import tech.pegasys.artemis.util.async.SafeFuture;
import tech.pegasys.artemis.bls.bls.BLSKeyPair;
import tech.pegasys.artemis.util.cli.VersionProvider;
import tech.pegasys.artemis.util.crypto.SecureRandomProvider;

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
  private static final String VALIDATOR_PASSWORD_PROMPT = "Validator Keystore";
  private static final String WITHDRAWAL_PASSWORD_PROMPT = "Withdrawal Keystore";

  @Spec private CommandSpec spec;
  @Mixin private CommonParams params;

  @Option(
      names = {"--number-of-validators"},
      paramLabel = "<NUMBER>",
      description = "The number of validators to create keys for and register",
      converter = PositiveIntegerTypeConverter.class,
      defaultValue = "1")
  private int validatorCount = 1;

  @Option(
      names = {"--keys-output-path"},
      paramLabel = "<FILE|DIR>",
      description =
          "Path to output file for unencrypted keys or output directory for encrypted keystore files. If not set, unencrypted keys will be written on standard out and encrypted keystores will be created in current directory")
  private String outputPath;

  @Option(
      names = {"--encrypted-keystore-enabled"},
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
    _params.displayConfirmation();

    final SecureRandom srng = SecureRandomProvider.createSecureRandom();
    try (_params) {
      final DepositTransactionSender sender = params.createTransactionSender();
      final List<SafeFuture<TransactionReceipt>> futures = new ArrayList<>();
      for (int i = 0; i < validatorCount; i++) {
        final BLSKeyPair validatorKey = BLSKeyPair.random(srng);
        final BLSKeyPair withdrawalKey = BLSKeyPair.random(srng);

        keysWriter.writeKeys(validatorKey, withdrawalKey);

        final SafeFuture<TransactionReceipt> transactionReceiptSafeFuture =
            CommonParams.sendDeposit(
                sender, validatorKey, withdrawalKey.getPublicKey(), params.getAmount());
        futures.add(transactionReceiptSafeFuture);
      }

      SafeFuture.allOf(futures.toArray(SafeFuture[]::new)).get(2, TimeUnit.MINUTES);
    } catch (final Throwable t) {
      SUB_COMMAND_LOG.sendDepositFailure(t);
      shutdownFunction.accept(1);
    }
    shutdownFunction.accept(0);
  }

  private KeysWriter getKeysWriter() {
    final KeysWriter keysWriter;
    if (encryptKeys) {
      final String validatorKeystorePassword =
          readKeystorePassword(validatorPasswordOptions, VALIDATOR_PASSWORD_PROMPT);
      final String withdrawalKeystorePassword =
          readKeystorePassword(withdrawalPasswordOptions, WITHDRAWAL_PASSWORD_PROMPT);

      final Path keystoreDir = getKeystoreOutputDir();
      keysWriter =
          new EncryptedKeystoreWriter(
              validatorKeystorePassword, withdrawalKeystorePassword, keystoreDir);
    } else {
      keysWriter = new YamlKeysWriter(isBlank(outputPath) ? null : Path.of(outputPath));
      if (consoleAdapter.isConsoleAvailable()
          && isBlank(outputPath)
          && params.isDisplayConfirmation()) {
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
      password = readFromFile(spec.commandLine(), keystorePasswordOptions.getPasswordFile());
    } else {
      password =
          readFromEnvironmentVariable(
              spec.commandLine(),
              envSupplier,
              keystorePasswordOptions.getPasswordEnvironmentVariable());
    }
    return password;
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

    throw new ParameterException(spec.commandLine(), "Error: Password mismatched");
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

  private static class PositiveIntegerTypeConverter implements ITypeConverter<Integer> {
    @Override
    public Integer convert(final String value) throws TypeConversionException {
      try {
        final int parsedValue = Integer.parseInt(value);
        if (parsedValue <= 0) {
          throw new TypeConversionException("Must be a positive number");
        }
        return parsedValue;
      } catch (final NumberFormatException e) {
        throw new TypeConversionException(
            "Invalid format: must be a numeric value but was '" + value + "'");
      }
    }
  }
}
