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
import java.util.concurrent.CompletableFuture;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tx.FastRawTransactionManager;
import org.web3j.tx.gas.DefaultGasProvider;
import tech.pegasys.artemis.datastructures.operations.DepositData;
import tech.pegasys.artemis.datastructures.util.DepositGenerator;
import tech.pegasys.artemis.pow.contract.DepositContract;
import tech.pegasys.artemis.util.bls.BLSKeyPair;

public class DepositTransactionSender {

  private final DepositGenerator depositGenerator = new DepositGenerator();
  private final DepositContract depositContract;

  public DepositTransactionSender(
      final Web3j web3j, final String depositContractAddress, final String eth1PrivateKey) {
    this.depositContract =
        DepositContract.load(
            depositContractAddress,
            web3j,
            new FastRawTransactionManager(web3j, Credentials.create(eth1PrivateKey)),
            new DefaultGasProvider());
  }

  public CompletableFuture<TransactionReceipt> sendDepositTransaction(
      BLSKeyPair validatorKeyPair, BLSKeyPair withdrawalKeyPair, final UnsignedLong amountInGwei) {
    final DepositData depositData =
        depositGenerator.createDepositData(validatorKeyPair, withdrawalKeyPair, amountInGwei);

    return sendDepositTransaction(depositData);
  }

  private CompletableFuture<TransactionReceipt> sendDepositTransaction(
      final DepositData depositData) {
    return depositContract
        .deposit(
            depositData.getPubkey().toBytesCompressed().toArray(),
            depositData.getWithdrawal_credentials().toArray(),
            depositData.getSignature().getSignature().toBytesCompressed().toArray(),
            depositData.hash_tree_root().toArray(),
            new BigInteger(depositData.getAmount() + "000000000"))
        .sendAsync();
  }
}
