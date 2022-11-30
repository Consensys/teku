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

import java.util.List;
import java.util.stream.Collectors;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;

public class DepositGenerator implements AutoCloseable {
  private final DepositSenderService depositSenderService;

  public DepositGenerator(
      final Spec spec,
      final String eth1Endpoint,
      final Eth1Address depositContractAddress,
      final Credentials eth1Credentials,
      final UInt64 amount) {
    this.depositSenderService =
        new DepositSenderService(
            spec, eth1Endpoint, eth1Credentials, depositContractAddress, amount);
  }

  public SafeFuture<Void> sendDeposits(final ValidatorKeystores validators) {
    return SafeFuture.of(
        () -> {
          final List<SafeFuture<TransactionReceipt>> transactionReceipts =
              validators.getValidatorKeys().stream()
                  .map(this::sendDeposit)
                  .collect(Collectors.toList());
          return SafeFuture.allOf(transactionReceipts.toArray(SafeFuture[]::new));
        });
  }

  private SafeFuture<TransactionReceipt> sendDeposit(ValidatorKeys validatorKeys) {
    return depositSenderService.sendDeposit(validatorKeys);
  }

  @Override
  public void close() {
    depositSenderService.close();
  }
}
