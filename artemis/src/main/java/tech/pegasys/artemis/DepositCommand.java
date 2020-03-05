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

import static tech.pegasys.artemis.util.alogger.ALogger.STDOUT;

import com.google.common.primitives.UnsignedLong;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.Level;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import tech.pegasys.artemis.bls.keystore.KeyStore;
import tech.pegasys.artemis.bls.keystore.KeyStoreLoader;
import tech.pegasys.artemis.bls.keystore.model.Cipher;
import tech.pegasys.artemis.bls.keystore.model.CipherFunction;
import tech.pegasys.artemis.bls.keystore.model.KdfParam;
import tech.pegasys.artemis.bls.keystore.model.KeyStoreData;
import tech.pegasys.artemis.bls.keystore.model.SCryptParam;
import tech.pegasys.artemis.services.powchain.DepositTransactionSender;
import tech.pegasys.artemis.util.async.SafeFuture;
import tech.pegasys.artemis.util.bls.BLSKeyPair;
import tech.pegasys.artemis.util.bls.BLSPublicKey;
import tech.pegasys.artemis.util.cli.VersionProvider;
import tech.pegasys.artemis.util.crypto.SecureRandomProvider;
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
              names = {"--output-file", "-o"},
              description =
                  "File to write validator keys to. Keys are printed to std out if not specified")
          String outputFile,
      @Option(
              names = {"--encrypt-keys", "-e"},
              defaultValue = "false",
              description = "Encrypt validator keys. Defaults to <false>",
              arity = "1")
          boolean encryptKeys,
      @Option(
              names = {"--keystore-output-dir", "-k"},
              paramLabel = "<PATH>",
              description =
                  "Encrypted keystore output directory. Use current directory if not specified")
          String keystorePath) {

    try (params) {
      // generate BLS validator key and withdrawl key
      final List<Pair<BLSKeyPair, BLSKeyPair>> validatorKeyPairs =
          IntStream.range(0, validatorCount)
              .mapToObj(value -> Pair.of(BLSKeyPair.random(), BLSKeyPair.random()))
              .collect(Collectors.toList());

      final DepositTransactionSender sender = params.createTransactionSender();

      final StringBuilder yamlStringBuilder = new StringBuilder();
      final boolean printOnStandardOut = outputFile == null || outputFile.isBlank();
      final Path keyStoreOutputDirectory =
          keystorePath == null || keystorePath.isBlank() ? Path.of(".") : Path.of(keystorePath);
      final AtomicInteger counterSuffix = new AtomicInteger(0);
      final List<SafeFuture<TransactionReceipt>> futures;
      try {
        futures =
            validatorKeyPairs.stream()
                .map(
                    keyPair -> {
                      final String yamlFormattedString = getYamlFormattedString(keyPair);
                      if (encryptKeys) {
                        generateKeystores(
                            keyPair, keyStoreOutputDirectory, counterSuffix.incrementAndGet());
                      } else if (printOnStandardOut) {
                        System.out.println(yamlFormattedString);
                      } else {
                        yamlStringBuilder.append(yamlFormattedString);
                      }

                      return sendDeposit(
                          sender,
                          keyPair.getLeft(),
                          keyPair.getRight().getPublicKey(),
                          params.amount);
                    })
                .collect(Collectors.toList());

      } finally {
        if (!encryptKeys && !printOnStandardOut) {
          // write keys yaml to file
          Files.writeString(Path.of(outputFile), yamlStringBuilder.toString());
        }
      }

      SafeFuture.allOf(futures.toArray(SafeFuture[]::new)).get(2, TimeUnit.MINUTES);
    } catch (final Throwable t) {
      STDOUT.log(
          Level.FATAL,
          "Failed to send deposit transaction: " + t.getClass() + ": " + t.getMessage());
      System.exit(1); // Web3J creates a non-daemon thread we can't shut down. :(
    }
    System.exit(0); // Web3J creates a non-daemon thread we can't shut down. :(
  }

  private String getYamlFormattedString(final Pair<BLSKeyPair, BLSKeyPair> keyPair) {
    return String.format(
        "- {privkey: '%s', pubkey: '%s', withdrawalPrivkey: '%s', withdrawalPubkey: '%s'}",
        keyPair.getLeft().getSecretKey().getSecretKey().toBytes(),
        keyPair.getLeft().getPublicKey().toBytesCompressed(),
        keyPair.getRight().getSecretKey().getSecretKey().toBytes(),
        keyPair.getRight().getPublicKey().toBytesCompressed());
  }

  private void generateKeystores(
      final Pair<BLSKeyPair, BLSKeyPair> keyPair,
      final Path keyStoreOutputDirectory,
      final int counter) {
    generateKeystore(keyPair.getLeft(), keyStoreOutputDirectory, "validator", counter);
    generateKeystore(keyPair.getRight(), keyStoreOutputDirectory, "withdrawal", counter);
  }

  private void generateKeystore(
      final BLSKeyPair key, final Path outputDir, final String prefix, final int counter) {
    try {
      final KdfParam kdfParam = new SCryptParam(32, Bytes32.random());
      final Cipher cipher = new Cipher(CipherFunction.AES_128_CTR, Bytes.random(16));
      final String randomPassword = generateRandomHexToken();
      final KeyStoreData keyStoreData =
          KeyStore.encrypt(
              key.getSecretKey().getSecretKey().toBytes(),
              generateRandomHexToken(),
              "",
              kdfParam,
              cipher);

      // create sub directory
      final Path keystoreDirectory =
          Files.createDirectories(outputDir.resolve("validator_" + counter));

      // save keystore
      KeyStoreLoader.saveToFile(
          keystoreDirectory.resolve(prefix + "_keystore_" + counter + ".json"), keyStoreData);

      // save password
      Files.writeString(
          keystoreDirectory.resolve(prefix + "_password_" + counter + ".txt"), randomPassword);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public String generateRandomHexToken() {
    final SecureRandom secureRandom = SecureRandomProvider.createSecureRandom();
    byte[] token = new byte[32];
    secureRandom.nextBytes(token);
    return new BigInteger(1, token).toString(16); // hex encoding
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
