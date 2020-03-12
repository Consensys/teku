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

import com.google.common.primitives.UnsignedLong;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.io.Closeable;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;
import picocli.CommandLine;
import tech.pegasys.artemis.services.powchain.DepositTransactionSender;
import tech.pegasys.artemis.util.async.SafeFuture;
import tech.pegasys.artemis.util.bls.BLSKeyPair;
import tech.pegasys.artemis.util.bls.BLSPublicKey;

public class CommonParams implements Closeable {
  @CommandLine.Option(
      names = {"-u", "--node-url"},
      required = true,
      paramLabel = "<URL>",
      description = "JSON-RPC endpoint URL for the Ethereum 1 node to send transactions via")
  private String eth1NodeUrl;

  @CommandLine.Option(
      names = {"-c", "--contract-address"},
      required = true,
      paramLabel = "<ADDRESS>",
      description = "Address of the deposit contract")
  private String contractAddress;

  @CommandLine.Option(
      names = {"-p", "--private-key"},
      required = true,
      paramLabel = "<KEY>",
      description = "Ethereum 1 private key to use to send transactions")
  private String eth1PrivateKey;

  @CommandLine.Option(
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

  public UnsignedLong getAmount() {
    return amount;
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
      return UnsignedLong.valueOf(value);
    }
  }
}
