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

package tech.pegasys.artemis.services.powchain;

import com.google.common.primitives.UnsignedLong;
import java.math.BigInteger;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tx.FastRawTransactionManager;
import org.web3j.tx.gas.DefaultGasProvider;
import org.web3j.tx.response.PollingTransactionReceiptProcessor;
import tech.pegasys.artemis.datastructures.operations.DepositData;
import tech.pegasys.artemis.datastructures.util.DepositGenerator;
import tech.pegasys.artemis.pow.contract.DepositContract;
import tech.pegasys.artemis.util.async.SafeFuture;
import tech.pegasys.artemis.util.bls.BLSKeyPair;
import tech.pegasys.artemis.util.bls.BLSPublicKey;

public class DepositTransactionSender {
  // Increase the poll rate for tx receipts but keep the default 10 min timeout.
  private static final int POLL_INTERVAL_MILLIS = 2000;
  private static final int MAX_POLL_ATTEMPTS = 300;
  private final DepositGenerator depositGenerator = new DepositGenerator();
  private final DepositContract depositContract;

  public DepositTransactionSender(
      final Web3j web3j, final String depositContractAddress, final Credentials eth1Credentials) {
    this.depositContract =
        DepositContract.load(
            depositContractAddress,
            web3j,
            new FastRawTransactionManager(
                web3j,
                eth1Credentials,
                new PollingTransactionReceiptProcessor(
                    web3j, POLL_INTERVAL_MILLIS, MAX_POLL_ATTEMPTS)),
            new DefaultGasProvider());
  }

  public SafeFuture<TransactionReceipt> sendDepositTransaction(
      BLSKeyPair validatorKeyPair,
      final BLSPublicKey withdrawalPublicKey,
      final UnsignedLong amountInGwei) {
    final DepositData depositData =
        depositGenerator.createDepositData(validatorKeyPair, amountInGwei, withdrawalPublicKey);

    return sendDepositTransaction(depositData);
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
}
