/*
 * Copyright 2019 ConsenSys AG.
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

package tech.pegasys.artemis;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static tech.pegasys.artemis.DepositCommand.Generate;
import static tech.pegasys.artemis.DepositCommand.Register;
import static tech.pegasys.teku.logging.StatusLogger.STATUS_LOG;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.io.Files;
import com.google.common.primitives.UnsignedLong;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.io.Closeable;
import java.io.Console;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import org.apache.logging.log4j.Level;
import org.apache.tuweni.bytes.Bytes;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;
import picocli.CommandLine;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Spec;
import tech.pegasys.artemis.cli.deposit.EncryptedKeystoreWriter;
import tech.pegasys.artemis.cli.deposit.KeysWriter;
import tech.pegasys.artemis.cli.deposit.YamlKeysWriter;
import tech.pegasys.artemis.services.powchain.DepositTransactionSender;
import tech.pegasys.artemis.util.async.SafeFuture;
import tech.pegasys.artemis.util.bls.BLSKeyPair;
import tech.pegasys.artemis.util.bls.BLSPublicKey;
import tech.pegasys.artemis.util.cli.VersionProvider;
import tech.pegasys.artemis.util.mikuli.KeyPair;
import tech.pegasys.artemis.util.mikuli.SecretKey;

@Command(
    name = "validator",
    description = "Register validators by sending deposit transactions to an Ethereum 1 node",
    abbreviateSynopsis = true,
    mixinStandardHelpOptions = true,
    subcommands = {Generate.class, Register.class},
    versionProvider = VersionProvider.class,
    synopsisHeading = "%n",
    descriptionHeading = "%nDescription:%n%n",
    optionListHeading = "%nOptions:%n",
    footerHeading = "%n",
    footer = "Teku is licensed under the Apache License 2.0")
public class DepositCommand implements Runnable {

  static class ValidatorPasswordOptions {
    @SuppressWarnings("UnusedVariable")
    @Option(
        names = {"--validator-password-file"},
        paramLabel = "<FILE>",
        required = true,
        description = "Read password from the file to encrypt the validator keys")
    private File validatorPasswordFile;

    @SuppressWarnings("UnusedVariable")
    @Option(
        names = {"--validator-password-env"},
        paramLabel = "<ENV_VAR>",
        required = true,
        description = "Read password from environment variable to encrypt the validator keys")
    private String validatorPasswordEnv;
  }

  static class WithdrawalPasswordOptions {
    @SuppressWarnings("UnusedVariable")
    @Option(
        names = {"--withdrawal-password-file"},
        paramLabel = "<FILE>",
        description = "Read password from the file to encrypt the withdrawal keys")
    private File withdrawalPasswordFile;

    @SuppressWarnings("UnusedVariable")
    @Option(
        names = {"--withdrawal-password-env"},
        paramLabel = "<ENV_VAR>",
        description = "Read password from environment variable to encrypt the withdrawal keys")
    private String withdrawalPasswordEnv;
  }

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
  static class Generate implements Runnable {
    private final Consumer<Integer> shutdownFunction;
    private @Spec CommandSpec spec;
    @Mixin private final CommonParams params = null;

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
        description =
            "Create encrypted keystores for validator and withdrawal keys. (Default: true)",
        arity = "1")
    private boolean encryptKeys = true;

    @ArgGroup(heading = "Non-interactive password options for validator keystores:%n")
    private ValidatorPasswordOptions validatorPasswordOptions;

    @ArgGroup(heading = "Non-interactive password options for withdrawal keystores:%n")
    private WithdrawalPasswordOptions withdrawalPasswordOptions;

    public Generate() {
      this.shutdownFunction =
          System::exit; // required because web3j use non-daemon threads which halts the program
    }

    @VisibleForTesting
    Generate(final Consumer<Integer> shutdownFunction) {
      this.shutdownFunction = shutdownFunction;
    }

    @Override
    public void run() {
      final KeysWriter keysWriter = getKeysWriter();

      try (params) {
        final DepositTransactionSender sender = params.createTransactionSender();
        final List<SafeFuture<TransactionReceipt>> futures = new ArrayList<>();
        for (int i = 0; i < validatorCount; i++) {
          final BLSKeyPair validatorKey = BLSKeyPair.random();
          final BLSKeyPair withdrawalKey = BLSKeyPair.random();

          keysWriter.writeKeys(validatorKey, withdrawalKey);

          final SafeFuture<TransactionReceipt> transactionReceiptSafeFuture =
              sendDeposit(sender, validatorKey, withdrawalKey.getPublicKey(), params.amount);
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
      Console console = System.console();
      if (console == null) {
        throw new ParameterException(
            spec.commandLine(), "Cannot read password from console: Console not available");
      }

      final char[] firstInput = console.readPassword("Enter password for %s:", option);
      final char[] reconfirmedInput = console.readPassword("Re-Enter password for %s:", option);
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

    public String readFromEnvironmentVariable(final String environmentVariable) {
      final String password = System.getenv(environmentVariable);
      if (password == null) {
        throw new ParameterException(
            spec.commandLine(),
            "Error: Password cannot be read from environment variable: " + environmentVariable);
      }
      return password;
    }

    public String readFromFile(final File passwordFile) {
      try {
        return Files.asCharSource(passwordFile, StandardCharsets.UTF_8).readFirstLine();

      } catch (final FileNotFoundException e) {
        throw new ParameterException(spec.commandLine(), "Error: File not found: " + passwordFile);
      } catch (final IOException e) {
        throw new ParameterException(
            spec.commandLine(),
            "Error: Unexpected IO error reading file [" + passwordFile + "] : " + e.getMessage());
      }
    }

    /*
    private boolean isPasswordOptionsValid(final File validatorPasswordFile, final String validatorPasswordEnv, final File withdrawalPasswordFile, final String withdrawalPasswordEnv) {
      boolean valid = true;
      if (validatorPasswordFile != null && validatorPasswordEnv != null) {
        System.err.println("Invalid Input. Expecting only one of [--validator-password-file | --validator-password-env] option.");
        valid = false;
      }

      if (withdrawalPasswordFile != null && withdrawalPasswordEnv != null) {
        System.err.println("Invalid Input. Expecting only one of [--withdrawal-password-file | --withdrawal-password-env] option.");
        valid = false;
      }
      return valid;
    } */
  }

  @Command(
      name = "register",
      description =
          "Register a validator from existing keys but sending a deposit transaction to an Ethereum 1 node",
      mixinStandardHelpOptions = true,
      abbreviateSynopsis = true,
      versionProvider = VersionProvider.class,
      synopsisHeading = "%n",
      descriptionHeading = "%nDescription:%n%n",
      optionListHeading = "%nOptions:%n",
      footerHeading = "%n",
      footer = "Teku is licensed under the Apache License 2.0")
  static class Register implements Runnable {
    @Mixin private final CommonParams params = null;

    @Option(
        names = {"-s", "--signing-key"},
        paramLabel = "<PRIVATE_KEY>",
        required = true,
        description = "Private signing key for the validator")
    private String validatorKey;

    @Option(
        names = {"-w", "--withdrawal-key"},
        paramLabel = "<PUBLIC_KEY>",
        required = true,
        description = "Public withdrawal key for the validator")
    private String withdrawalKey;

    @Override
    public void run() {
      try (params) {
        final DepositTransactionSender sender = params.createTransactionSender();
        sendDeposit(
                sender,
                privateKeyToKeyPair(validatorKey),
                BLSPublicKey.fromBytesCompressed(Bytes.fromHexString(withdrawalKey)),
                params.amount)
            .get();
      } catch (final Throwable t) {
        STATUS_LOG.log(
            Level.FATAL,
            "Failed to send deposit transaction: " + t.getClass() + ": " + t.getMessage());
        System.exit(1); // Web3J creates a non-daemon thread we can't shut down. :(
      }
      System.exit(0); // Web3J creates a non-daemon thread we can't shut down. :(
    }
  }

  @Override
  public void run() {
    CommandLine.usage(this, System.out);
  }

  static SafeFuture<TransactionReceipt> sendDeposit(
      final DepositTransactionSender sender,
      final BLSKeyPair validatorKey,
      final BLSPublicKey withdrawalPublicKey,
      final UnsignedLong amount) {
    return sender.sendDepositTransaction(validatorKey, withdrawalPublicKey, amount);
  }

  static BLSKeyPair privateKeyToKeyPair(final String validatorKey) {
    return new BLSKeyPair(new KeyPair(SecretKey.fromBytes(Bytes.fromHexString(validatorKey))));
  }

  private static class UnsignedLongConverter implements ITypeConverter<UnsignedLong> {

    @Override
    public UnsignedLong convert(final String value) {
      return UnsignedLong.valueOf(value);
    }
  }

  public static class CommonParams implements Closeable {
    @Option(
        names = {"-u", "--node-url"},
        required = true,
        paramLabel = "<URL>",
        description = "JSON-RPC endpoint URL for the Ethereum 1 node to send transactions via")
    private String eth1NodeUrl;

    @Option(
        names = {"-c", "--contract-address"},
        required = true,
        paramLabel = "<ADDRESS>",
        description = "Address of the deposit contract")
    private String contractAddress;

    @Option(
        names = {"-p", "--private-key"},
        required = true,
        paramLabel = "<KEY>",
        description = "Ethereum 1 private key to use to send transactions")
    private String eth1PrivateKey;

    @Option(
        names = {"-a", "--amount"},
        paramLabel = "<GWEI>",
        converter = UnsignedLongConverter.class,
        description = "Deposit amount in Gwei (default: ${DEFAULT-VALUE})")
    private UnsignedLong amount = UnsignedLong.valueOf(32000000000L);

    private OkHttpClient httpClient;
    private ScheduledExecutorService executorService;
    private Web3j web3j;

    public DepositTransactionSender createTransactionSender() {
      httpClient = new OkHttpClient.Builder().connectionPool(new ConnectionPool()).build();
      executorService =
          Executors.newScheduledThreadPool(
              1, new ThreadFactoryBuilder().setDaemon(true).setNameFormat("web3j-%d").build());
      web3j = Web3j.build(new HttpService(eth1NodeUrl, httpClient), 1000, executorService);
      return new DepositTransactionSender(web3j, contractAddress, eth1PrivateKey);
    }

    @Override
    public void close() {
      if (web3j != null) {
        web3j.shutdown();
        httpClient.dispatcher().executorService().shutdownNow();
        httpClient.connectionPool().evictAll();
        executorService.shutdownNow();
      }
    }
  }
}
