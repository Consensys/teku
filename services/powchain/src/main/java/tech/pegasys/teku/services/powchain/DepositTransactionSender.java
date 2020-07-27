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

package tech.pegasys.teku.services.powchain;

import com.google.common.primitives.UnsignedLong;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.util.function.Consumer;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tx.FastRawTransactionManager;
import org.web3j.tx.gas.ContractGasProvider;
import org.web3j.tx.response.PollingTransactionReceiptProcessor;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.datastructures.operations.DepositData;
import tech.pegasys.teku.datastructures.util.DepositGenerator;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.pow.contract.DepositContract;
import tech.pegasys.teku.util.config.Eth1Address;

public class DepositTransactionSender {
  // Increase the poll rate for tx receipts but keep the default 10 min timeout.
  private static final int POLL_INTERVAL_MILLIS = 2000;
  private static final int MAX_POLL_ATTEMPTS = 300;
  private final DepositGenerator depositGenerator = new DepositGenerator();
  private final DepositContract depositContract;

  public DepositTransactionSender(
      final Web3j web3j,
      final Eth1Address depositContractAddress,
      final Credentials eth1Credentials) {
    this.depositContract =
        DepositContract.load(
            depositContractAddress.toHexString(),
            web3j,
            new FastRawTransactionManager(
                web3j,
                eth1Credentials,
                new PollingTransactionReceiptProcessor(
                    web3j, POLL_INTERVAL_MILLIS, MAX_POLL_ATTEMPTS)),
            new DepositContractGasProvider(web3j));
  }

  public SafeFuture<TransactionReceipt> sendDepositTransaction(
      BLSKeyPair validatorKeyPair,
      final BLSPublicKey withdrawalPublicKey,
      final UnsignedLong amountInGwei,
      final Consumer<String> commandStdOutput,
      final Consumer<String> commandErrorOutput) {
    commandStdOutput.accept(
        String.format(
            "%nSending deposit for Validator Key [%s].%n",
            validatorKeyPair.getPublicKey().toString()));

    final DepositData depositData =
        depositGenerator.createDepositData(validatorKeyPair, amountInGwei, withdrawalPublicKey);

    final SafeFuture<TransactionReceipt> safeFuture = sendDepositTransaction(depositData);

    safeFuture.finish(
        transactionReceipt ->
            commandStdOutput.accept(
                String.format(
                    "Transaction for Validator Key [%s] Completed. Transaction Hash: [%s]%n",
                    validatorKeyPair.getPublicKey().toString(),
                    transactionReceipt.getTransactionHash())),
        exception ->
            commandErrorOutput.accept(
                String.format(
                    "Transaction for Validator Key [%s] Failed: Message: [%s]%n",
                    validatorKeyPair.getPublicKey().toString(), exception.getMessage())));

    return safeFuture;
  }

  private SafeFuture<TransactionReceipt> sendDepositTransaction(final DepositData depositData) {
    return SafeFuture.of(
        depositContract
            .deposit(
                depositData.getPubkey().toBytesCompressed().toArray(),
                depositData.getWithdrawal_credentials().toArray(),
                depositData.getSignature().getSignature().toBytesCompressed().toArray(),
                depositData.hash_tree_root().toArray(),
                new BigInteger(depositData.getAmount() + "000000000"))
            .sendAsync());
  }

  private static class DepositContractGasProvider implements ContractGasProvider {

    private final Web3j web3j;

    public DepositContractGasProvider(final Web3j web3j) {
      this.web3j = web3j;
    }

    @Override
    public BigInteger getGasPrice(final String contractFunc) {
      return getGasPrice();
    }

    @Override
    public BigInteger getGasPrice() {
      try {
        return web3j.ethGasPrice().send().getGasPrice();
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }

    @Override
    public BigInteger getGasLimit(final String contractFunc) {
      return getGasLimit();
    }

    @Override
    public BigInteger getGasLimit() {
      return BigInteger.valueOf(200_000L);
    }
  }
}
