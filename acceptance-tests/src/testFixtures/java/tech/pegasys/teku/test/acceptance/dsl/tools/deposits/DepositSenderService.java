/*
 * Copyright ConsenSys Software Inc., 2022
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
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;

public class DepositSenderService implements AutoCloseable {
  private final UInt64 amount;
  private final DepositTransactionSender sender;
  private final OkHttpClient httpClient;
  private final ScheduledExecutorService executorService;
  private final Web3j web3j;

  public DepositSenderService(
      final Spec spec,
      final String eth1NodeUrl,
      final Credentials eth1Credentials,
      final Eth1Address contractAddress,
      final UInt64 amount) {
    this.amount = amount;
    this.httpClient = new OkHttpClient.Builder().connectionPool(new ConnectionPool()).build();
    this.executorService =
        Executors.newScheduledThreadPool(
            1, new ThreadFactoryBuilder().setDaemon(true).setNameFormat("web3j-%d").build());
    this.web3j = Web3j.build(new HttpService(eth1NodeUrl, httpClient), 1000, executorService);
    this.sender = new DepositTransactionSender(spec, web3j, contractAddress, eth1Credentials);
  }

  @Override
  public void close() {
    web3j.shutdown();
    httpClient.dispatcher().executorService().shutdownNow();
    httpClient.connectionPool().evictAll();
    executorService.shutdownNow();
  }

  public SafeFuture<TransactionReceipt> sendDeposit(ValidatorKeys validatorKeys) {
    final BLSKeyPair validatorKey = validatorKeys.getValidatorKey();
    return sender.sendDepositTransaction(
        validatorKey, validatorKeys.getWithdrawalCredentials(), amount);
  }
}
