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

import java.util.List;
import java.util.stream.Collectors;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.util.config.Eth1Address;

public class DepositGenerator implements AutoCloseable {
  private final ValidatorKeyGenerator validatorKeyGenerator;
  private final DepositSenderService depositSenderService;

  public DepositGenerator(
      final String eth1Endpoint,
      final Eth1Address depositContractAddress,
      final Credentials eth1Credentials,
      final int validatorCount,
      final UInt64 amount) {
    this.validatorKeyGenerator = new ValidatorKeyGenerator(validatorCount);
    this.depositSenderService =
        new DepositSenderService(eth1Endpoint, eth1Credentials, depositContractAddress, amount);
  }

  public SafeFuture<Void> generate() {
    return SafeFuture.of(
        () -> {
          final List<SafeFuture<TransactionReceipt>> transactionReceipts =
              validatorKeyGenerator
                  .generateKeysStream()
                  .map(depositSenderService::sendDeposit)
                  .collect(Collectors.toList());
          return SafeFuture.allOf(transactionReceipts.toArray(SafeFuture[]::new));
        });
  }

  public SafeFuture<Void> generate2transactions() {
    return SafeFuture.of(
        () -> {
          final List<SafeFuture<Void>> transactionReceipts =
              validatorKeyGenerator
                  .generateKeysStream()
                  .map(
                      keys -> {
                        SafeFuture<TransactionReceipt> future1 =
                            depositSenderService.sendDeposit(keys);
                        SafeFuture<TransactionReceipt> future2 =
                            depositSenderService.sendDeposit(keys);
                        return SafeFuture.allOf(future1, future2);
                      })
                  .collect(Collectors.toList());
          return SafeFuture.allOf(transactionReceipts.toArray(SafeFuture[]::new));
        });
  }

  @Override
  public void close() {
    depositSenderService.close();
  }
}
