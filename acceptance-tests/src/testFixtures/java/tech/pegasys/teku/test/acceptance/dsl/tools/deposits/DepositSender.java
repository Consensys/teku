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

package tech.pegasys.teku.test.acceptance.dsl.tools.deposits;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.util.config.Eth1Address;

class DepositSender implements AutoCloseable {

  private final Credentials eth1Credentials;
  private final String eth1NodeUrl;
  private final Eth1Address contractAddress;
  private final UInt64 amount;
  private final DepositTransactionSender sender;
  private OkHttpClient httpClient;
  private ScheduledExecutorService executorService;
  private Web3j web3j;

  public DepositSender(
      final String eth1NodeUrl,
      final Credentials eth1Credentials,
      final Eth1Address contractAddress,
      final UInt64 amount) {
    this.eth1NodeUrl = eth1NodeUrl;
    this.eth1Credentials = eth1Credentials;
    this.contractAddress = contractAddress;
    this.amount = amount;
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

  public SafeFuture<TransactionReceipt> sendDeposit(ValidatorKeys validatorKeys) {
    final BLSKeyPair validatorKey = validatorKeys.validatorKey;
    final BLSPublicKey withdrawalPublicKey = validatorKeys.withdrawalKey.getPublicKey();
    return sender.sendDepositTransaction(validatorKey, withdrawalPublicKey, amount);
  }
}
