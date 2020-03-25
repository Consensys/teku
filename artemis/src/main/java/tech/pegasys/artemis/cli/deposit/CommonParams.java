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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.primitives.UnsignedLong;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import org.web3j.crypto.CipherException;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.WalletUtils;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;
import picocli.CommandLine;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Spec;
import picocli.CommandLine.TypeConversionException;
import tech.pegasys.artemis.services.powchain.DepositTransactionSender;
import tech.pegasys.artemis.util.async.SafeFuture;
import tech.pegasys.artemis.util.bls.BLSKeyPair;
import tech.pegasys.artemis.util.bls.BLSPublicKey;

public class CommonParams implements Closeable {
  private static final UnsignedLong MINIMUM_REQUIRED_GWEI = UnsignedLong.valueOf(32_000_000_000L);
  private final Consumer<Integer> shutdownFunction;
  private final ConsoleAdapter consoleAdapter;
  @Spec private CommandSpec spec;

  @Option(
      names = {"--eth1-endpoint"},
      required = true,
      paramLabel = "<URL>",
      description = "JSON-RPC endpoint URL for the Ethereum 1 node to send transactions via")
  private String eth1NodeUrl;

  @Option(
      names = {"--eth1-deposit-contract-address"},
      required = true,
      paramLabel = "<ADDRESS>",
      description = "Address of the deposit contract")
  private String contractAddress;

  @ArgGroup(exclusive = true, multiplicity = "1")
  private Eth1PrivateKeyOptions eth1PrivateKeyOptions;

  @Option(
      names = {"--deposit-amount-gwei"},
      required = true,
      paramLabel = "<GWEI>",
      converter = UnsignedLongConverter.class,
      description = "Deposit amount in Gwei")
  private UnsignedLong amount;

  @Option(
      names = {"--X-confirm-enabled"},
      arity = "1",
      defaultValue = "true",
      hidden = true)
  private boolean displayConfirmation = true;

  private OkHttpClient httpClient;
  private ScheduledExecutorService executorService;
  private Web3j web3j;

  CommonParams() {
    this.shutdownFunction = System::exit;
    this.consoleAdapter = new ConsoleAdapter();
  }

  @VisibleForTesting
  public CommonParams(
      final CommandSpec commandSpec,
      final Eth1PrivateKeyOptions eth1PrivateKeyOptions,
      final Consumer<Integer> shutdownFunction,
      final ConsoleAdapter consoleAdapter) {
    this.spec = commandSpec;
    this.eth1PrivateKeyOptions = eth1PrivateKeyOptions;
    this.amount = MINIMUM_REQUIRED_GWEI;
    this.shutdownFunction = shutdownFunction;
    this.consoleAdapter = consoleAdapter;
  }

  public void displayConfirmation() {
    if (!displayConfirmation) {
      return;
    }

    if (!consoleAdapter.isConsoleAvailable()) {
      throw new ParameterException(spec.commandLine(), "Console not available");
    }

    // gwei to eth
    final String eth =
        new BigDecimal(amount.bigIntegerValue())
            .divide(BigDecimal.TEN.pow(9), 9, RoundingMode.HALF_UP)
            .toString();

    final String reply =
        consoleAdapter.readLine(
            "You are about to submit a transaction of %s Eth. This is irreversible, please make sure you understand the consequences. Are you sure you want to continue? [y/n]",
            eth);
    if ("y".equalsIgnoreCase(reply)) {
      return;
    }
    System.out.println("Transaction cancelled.");
    shutdownFunction.accept(0);
  }

  public DepositTransactionSender createTransactionSender() {
    httpClient = new OkHttpClient.Builder().connectionPool(new ConnectionPool()).build();
    executorService =
        Executors.newScheduledThreadPool(
            1, new ThreadFactoryBuilder().setDaemon(true).setNameFormat("web3j-%d").build());
    web3j = Web3j.build(new HttpService(eth1NodeUrl, httpClient), 1000, executorService);
    return new DepositTransactionSender(web3j, contractAddress, getEth1Credentials());
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

  public UnsignedLong getAmount() {
    return amount;
  }

  public boolean isDisplayConfirmation() {
    return displayConfirmation;
  }

  Credentials getEth1Credentials() {
    if (eth1PrivateKeyOptions.eth1PrivateKey != null) {
      return Credentials.create(eth1PrivateKeyOptions.eth1PrivateKey);
    } else if (eth1PrivateKeyOptions.keystoreOptions != null) {
      return eth1CredentialsFromKeystore();
    } else {
      // not meant to happen
      throw new IllegalStateException("Private Key Options are not initialized");
    }
  }

  private Credentials eth1CredentialsFromKeystore() {
    final String keystorePassword =
        KeystorePasswordOptions.readFromFile(
            spec.commandLine(), eth1PrivateKeyOptions.keystoreOptions.eth1KeystorePasswordFile);
    final File eth1KeystoreFile = eth1PrivateKeyOptions.keystoreOptions.eth1KeystoreFile;
    try {
      return WalletUtils.loadCredentials(keystorePassword, eth1KeystoreFile);
    } catch (final FileNotFoundException e) {
      throw new ParameterException(
          spec.commandLine(), "Error: File not found: " + eth1KeystoreFile, e);
    } catch (final IOException e) {
      throw new ParameterException(
          spec.commandLine(),
          "Error: Unexpected IO Error while reading Eth1 keystore ["
              + eth1KeystoreFile
              + "] : "
              + e.getMessage(),
          e);
    } catch (final CipherException e) {
      throw new ParameterException(
          spec.commandLine(),
          "Error: Unable to decrypt Eth1 keystore [" + eth1KeystoreFile + "] : " + e.getMessage(),
          e);
    }
  }

  static SafeFuture<TransactionReceipt> sendDeposit(
      final DepositTransactionSender sender,
      final BLSKeyPair validatorKey,
      final BLSPublicKey withdrawalPublicKey,
      final UnsignedLong amount) {
    return sender.sendDepositTransaction(validatorKey, withdrawalPublicKey, amount);
  }

  private static class UnsignedLongConverter implements CommandLine.ITypeConverter<UnsignedLong> {
    @Override
    public UnsignedLong convert(final String value) {
      final UnsignedLong depositAmountGwei;

      try {
        depositAmountGwei = UnsignedLong.valueOf(value);
      } catch (final NumberFormatException e) {
        throw new TypeConversionException(
            "Invalid format: must be a numeric value but was " + value);
      }

      if (depositAmountGwei.compareTo(MINIMUM_REQUIRED_GWEI) < 0) {
        throw new TypeConversionException(
            String.format(
                "The specified deposit amount [%s] does not match minimum deposit amount requirements [%s]",
                depositAmountGwei.toString(), MINIMUM_REQUIRED_GWEI.toString()));
      }
      return depositAmountGwei;
    }
  }
}
