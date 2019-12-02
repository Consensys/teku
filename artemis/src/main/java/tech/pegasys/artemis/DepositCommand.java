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
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import org.apache.logging.log4j.Level;
import org.apache.tuweni.bytes.Bytes;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;
import picocli.CommandLine.Command;
import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import tech.pegasys.artemis.services.powchain.DepositTransactionSender;
import tech.pegasys.artemis.util.bls.BLSKeyPair;
import tech.pegasys.artemis.util.cli.VersionProvider;
import tech.pegasys.artemis.util.mikuli.KeyPair;
import tech.pegasys.artemis.util.mikuli.SecretKey;

@Command(
    name = "deposit",
    description = "Register validators by sending deposit transactions to an Ethereum 1 node",
    abbreviateSynopsis = true,
    mixinStandardHelpOptions = true,
    versionProvider = VersionProvider.class,
    synopsisHeading = "%n",
    descriptionHeading = "%nDescription:%n%n",
    optionListHeading = "%nOptions:%n",
    footerHeading = "%n",
    footer = "Artemis is licensed under the Apache License 2.0")
public class DepositCommand implements Callable<Integer> {

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

  @Option(
      names = {"-g", "--generated-validator-count"},
      paramLabel = "<NUMBER>",
      description = "The number of validators to create new random keys for.")
  private int validatorCount = 0;

  @Option(
      names = {"--output-file", "-o"},
      description = "File to write validator keys to. Keys are printed to std out if not specified")
  private String outputFile;

  @Parameters(
      arity = "0..",
      paramLabel = "<KEY>",
      description =
          "Validator private keys to register. A different withdrawal key can be specified using a colon separator (<signing-key>:<withdrawal-key>)")
  private List<String> validatorKeys = new ArrayList<>();

  private final List<CompletableFuture<TransactionReceipt>> futures = new ArrayList<>();

  @Override
  public Integer call() {
    try {
      final OkHttpClient httpClient =
          new OkHttpClient.Builder().connectionPool(new ConnectionPool()).build();
      final ScheduledExecutorService executorService =
          Executors.newScheduledThreadPool(
              1, new ThreadFactoryBuilder().setDaemon(true).setNameFormat("web3j-%d").build());
      final Web3j web3j =
          Web3j.build(new HttpService(eth1NodeUrl, httpClient), 1000, executorService);
      final DepositTransactionSender sender =
          new DepositTransactionSender(web3j, contractAddress, eth1PrivateKey);

      final PrintStream keyWriter;
      if (outputFile == null || outputFile.isBlank()) {
        keyWriter = System.out;
      } else {
        keyWriter = new PrintStream(new FileOutputStream(outputFile), true, StandardCharsets.UTF_8);
      }
      try (keyWriter) {

        for (int i = 0; i < validatorCount; i++) {
          final BLSKeyPair validatorKey = BLSKeyPair.random();
          final BLSKeyPair withdrawalKey = BLSKeyPair.random();
          sendDeposit(keyWriter, sender, validatorKey, withdrawalKey);
        }

        for (String keyArg : validatorKeys) {
          final String validatorKey;
          final String withdrawalKey;
          if (keyArg.contains(":")) {
            validatorKey = keyArg.substring(0, keyArg.indexOf(":"));
            withdrawalKey = keyArg.substring(keyArg.indexOf(":") + 1);
          } else {
            validatorKey = keyArg;
            withdrawalKey = keyArg;
          }

          sendDeposit(
              keyWriter,
              sender,
              privateKeyToKeyPair(validatorKey),
              privateKeyToKeyPair(withdrawalKey));
        }
      }

      CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).get(2, TimeUnit.MINUTES);
      web3j.shutdown();
      httpClient.dispatcher().executorService().shutdownNow();
      httpClient.connectionPool().evictAll();
      executorService.shutdownNow();

      System.exit(0); // Web3J creates a non-daemon thread we can't shut down. :(
      return 0;
    } catch (final Throwable t) {
      STDOUT.log(
          Level.FATAL,
          "Failed to send deposit transaction: " + t.getClass() + ": " + t.getMessage());
      System.exit(1); // Web3J creates a non-daemon thread we can't shut down. :(
      return 1;
    }
  }

  private void sendDeposit(
      final PrintStream keyWriter,
      final DepositTransactionSender sender,
      final BLSKeyPair validatorKey,
      final BLSKeyPair withdrawalKey) {
    keyWriter.println(
        String.format(
            "- {privkey: '%s', pubkey: '%s', withdrawalPrivkey: '%s', withdrawalPubkey: '%s'}",
            validatorKey.getSecretKey().getSecretKey().toBytes(),
            validatorKey.getPublicKey().toBytesCompressed(),
            withdrawalKey.getSecretKey().getSecretKey().toBytes(),
            withdrawalKey.getPublicKey().toBytesCompressed()));
    futures.add(sender.sendDepositTransaction(validatorKey, withdrawalKey, amount));
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
}
