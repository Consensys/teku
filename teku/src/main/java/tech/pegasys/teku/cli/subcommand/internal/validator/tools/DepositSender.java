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

package tech.pegasys.teku.cli.subcommand.internal.validator.tools;

import static tech.pegasys.teku.infrastructure.logging.SubCommandLogger.SUB_COMMAND_LOG;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.datastructures.eth1.Eth1Address;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class DepositSender implements AutoCloseable {

  private final IntConsumer shutdownFunction;
  private final ConsoleAdapter consoleAdapter;
  private final Credentials eth1Credentials;
  private final String eth1NodeUrl;
  private final Eth1Address contractAddress;
  private final boolean verboseOutputEnabled;
  private final UInt64 amount;
  private final DepositTransactionSender sender;
  private OkHttpClient httpClient;
  private ScheduledExecutorService executorService;
  private Web3j web3j;
  private final Consumer<String> commandStdOutput;
  private final Consumer<String> commandErrorOutput;

  public DepositSender(
      final String eth1NodeUrl,
      final Credentials eth1Credentials,
      final Eth1Address contractAddress,
      final boolean verboseOutputEnabled,
      final UInt64 amount,
      final IntConsumer shutdownFunction,
      final ConsoleAdapter consoleAdapter) {
    this.eth1NodeUrl = eth1NodeUrl;
    this.eth1Credentials = eth1Credentials;
    this.contractAddress = contractAddress;
    this.verboseOutputEnabled = verboseOutputEnabled;
    this.commandStdOutput = verboseOutputEnabled ? SUB_COMMAND_LOG::display : s -> {};
    this.commandErrorOutput = SUB_COMMAND_LOG::error;
    this.amount = amount;
    this.shutdownFunction = shutdownFunction;
    this.consoleAdapter = consoleAdapter;
    this.sender = createTransactionSender();
  }

  private DepositTransactionSender createTransactionSender() {
    httpClient = new OkHttpClient.Builder().connectionPool(new ConnectionPool()).build();
    executorService =
        Executors.newScheduledThreadPool(
            1, new ThreadFactoryBuilder().setDaemon(true).setNameFormat("web3j-%d").build());
    web3j = Web3j.build(new HttpService(eth1NodeUrl, httpClient), 1000, executorService);
    return new DepositTransactionSender(web3j, contractAddress, eth1Credentials);
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

  public void displayConfirmation(final int totalNumberOfDeposits) {
    if (!verboseOutputEnabled || !consoleAdapter.isConsoleAvailable()) {
      return;
    }

    // gwei to eth
    final String eth =
        new BigDecimal(amount.bigIntegerValue())
            .divide(BigDecimal.TEN.pow(9), 9, RoundingMode.HALF_UP)
            .toString();

    final String transactionPart =
        totalNumberOfDeposits == 1 ? "a transaction" : totalNumberOfDeposits + " transactions";
    final String reply =
        consoleAdapter.readLine(
            "You are about to submit "
                + transactionPart
                + " of %s Eth to contract address [%s].\nThis is irreversible, please make sure you understand the consequences. Are you sure you want to continue? [y/n]",
            eth,
            contractAddress);
    if ("y".equalsIgnoreCase(reply)) {
      return;
    }
    System.out.println("Transaction cancelled.");
    shutdownFunction.accept(1);
  }

  public SafeFuture<TransactionReceipt> sendDeposit(
      final BLSKeyPair validatorKey, final BLSPublicKey withdrawalPublicKey) {
    return sender.sendDepositTransaction(
        validatorKey, withdrawalPublicKey, amount, commandStdOutput, commandErrorOutput);
  }
}
