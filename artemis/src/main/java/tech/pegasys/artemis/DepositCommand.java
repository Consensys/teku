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
import static tech.pegasys.teku.logging.StatusLogger.STDOUT;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.primitives.UnsignedLong;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.io.Closeable;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
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
import picocli.CommandLine.Command;
import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import tech.pegasys.artemis.cli.deposit.EncryptedKeystorePasswordProvider;
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
    versionProvider = VersionProvider.class,
    synopsisHeading = "%n",
    descriptionHeading = "%nDescription:%n%n",
    optionListHeading = "%nOptions:%n",
    footerHeading = "%n",
    footer = "Teku is licensed under the Apache License 2.0")
public class DepositCommand implements Runnable {
  private final Consumer<Integer> shutdownFunction;
  private @Spec CommandSpec spec;

  public DepositCommand() {
    this.shutdownFunction =
        System::exit; // required because web3j use non-daemon threads which halts the program
  }

  @VisibleForTesting
  DepositCommand(final Consumer<Integer> shutdownFunction) {
    this.shutdownFunction = shutdownFunction;
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
  public void generate(
      @Mixin CommonParams params,
      @Option(
              names = {"-n", "--number-of-validators"},
              paramLabel = "<NUMBER>",
              description = "The number of validators to create keys for and register",
              defaultValue = "1")
          int validatorCount,
      @Option(
              names = {"--output-path", "-o"},
              paramLabel = "<FILE|DIR>",
              description =
                  "Path to output file for unencrypted keys or output directory for encrypted keystore files. If not set, unencrypted keys will be written on standard out and encrypted keystores will be created in current directory")
          String outputPath,
      @Option(
              names = {"--encrypt-keys", "-e"},
              defaultValue = "true",
              paramLabel = "<true|false>",
              description = "Encrypt validator and withdrawal keys. (Default: true)",
              arity = "1")
          boolean encryptKeys,
      @Option(
              names = {"--validator-password:file"},
              paramLabel = "<FILE>",
              description = "Read password from the file to encrypt the validator keys")
          File validatorPasswordFile,
      @Option(
              names = {"--validator-password:env"},
              paramLabel = "<ENV_VAR>",
              description = "Read password from environment variable to encrypt the validator keys")
          String validatorPasswordEnv,
      @Option(
              names = {"--validator-password"},
              description = "Password to encrypt validator keys",
              interactive = true)
          String validatorPassword,
      @Option(
              names = {"--withdrawal-password:file"},
              paramLabel = "<FILE>",
              description = "Read password from the file to encrypt the withdrawal keys")
          File withdrawalPasswordFile,
      @Option(
              names = {"--withdrawal-password:env"},
              paramLabel = "<ENVIRONMENT_VAR>",
              description =
                  "Read password from environment variable to encrypt the withdrawal keys")
          String withdrawalPasswordEnv,
      @Option(
              names = {"--withdrawal-password"},
              description = "Password to encrypt withdrawal keys",
              interactive = true)
          String withdrawalPassword) {

    final KeysWriter keysWriter;
    if (encryptKeys) {
      final String validatorKeystorePassword =
          new EncryptedKeystorePasswordProvider(
                  spec, validatorPassword, validatorPasswordEnv, validatorPasswordFile, "validator")
              .retrievePassword();
      final String withdrawalKeystorePassword =
          new EncryptedKeystorePasswordProvider(
                  spec,
                  withdrawalPassword,
                  withdrawalPasswordEnv,
                  withdrawalPasswordFile,
                  "withdrawal")
              .retrievePassword();
      final Path keystoreDir =
          outputPath == null || outputPath.isBlank() ? Path.of(".") : Path.of(outputPath);
      keysWriter =
          new EncryptedKeystoreWriter(
              validatorKeystorePassword, withdrawalKeystorePassword, keystoreDir);
      STDOUT.log(Level.INFO, "Generating Encrypted Keystores ...");
    } else {
      keysWriter = new YamlKeysWriter(isBlank(outputPath) ? null : Path.of(outputPath));
      STDOUT.log(Level.INFO, "Generating unencrypted keys ...");
    }

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
      STDOUT.log(Level.INFO, "Deposit transaction(s) successful.");
    } catch (final Throwable t) {
      STDOUT.log(
          Level.FATAL,
          "Failed to send deposit transaction: " + t.getClass() + ": " + t.getMessage());
      shutdownFunction.accept(1);
    }
    shutdownFunction.accept(0);
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
  public void register(
      @Mixin CommonParams params,
      @Option(
              names = {"-s", "--signing-key"},
              paramLabel = "<PRIVATE_KEY>",
              required = true,
              description = "Private signing key for the validator")
          String validatorKey,
      @Option(
              names = {"-w", "--withdrawal-key"},
              paramLabel = "<PUBLIC_KEY>",
              required = true,
              description = "Public withdrawal key for the validator")
          String withdrawalKey) {
    try (params) {
      final DepositTransactionSender sender = params.createTransactionSender();
      sendDeposit(
              sender,
              privateKeyToKeyPair(validatorKey),
              BLSPublicKey.fromBytesCompressed(Bytes.fromHexString(withdrawalKey)),
              params.amount)
          .get();
    } catch (final Throwable t) {
      STDOUT.log(
          Level.FATAL,
          "Failed to send deposit transaction: " + t.getClass() + ": " + t.getMessage());
      System.exit(1); // Web3J creates a non-daemon thread we can't shut down. :(
    }
    System.exit(0); // Web3J creates a non-daemon thread we can't shut down. :(
  }

  @Override
  public void run() {
    CommandLine.usage(this, System.out);
  }

  private SafeFuture<TransactionReceipt> sendDeposit(
      final DepositTransactionSender sender,
      final BLSKeyPair validatorKey,
      final BLSPublicKey withdrawalPublicKey,
      final UnsignedLong amount) {
    return sender.sendDepositTransaction(validatorKey, withdrawalPublicKey, amount);
  }

  private BLSKeyPair privateKeyToKeyPair(final String validatorKey) {
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
