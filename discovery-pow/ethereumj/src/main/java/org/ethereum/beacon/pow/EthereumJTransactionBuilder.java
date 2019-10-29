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

package org.ethereum.beacon.pow;

import java.math.BigInteger;
import java.util.concurrent.CompletableFuture;
import org.ethereum.beacon.core.operations.deposit.DepositData;
import org.ethereum.beacon.core.types.Gwei;
import org.ethereum.beacon.pow.validator.TransactionBuilder;
import org.ethereum.core.CallTransaction;
import org.ethereum.core.Transaction;
import org.ethereum.crypto.ECKey;
import org.ethereum.facade.Ethereum;
import org.ethereum.facade.SyncStatus;
import org.ethereum.listener.EthereumListenerAdapter;
import org.ethereum.listener.RecommendedGasPriceTracker;
import org.ethereum.util.blockchain.EtherUtil;
import tech.pegasys.artemis.ethereum.core.Address;
import tech.pegasys.artemis.util.bytes.BytesValue;

public class EthereumJTransactionBuilder implements TransactionBuilder {

  private final Ethereum ethereum;
  private final Address contractDeployAddress;
  private final CallTransaction.Contract contract;
  private final RecommendedGasPriceTracker gasPriceTracker;

  public EthereumJTransactionBuilder(Ethereum ethereum, String contractDeployAddress) {
    this.ethereum = ethereum;
    this.contractDeployAddress = Address.fromHexString(contractDeployAddress);
    this.contract = new CallTransaction.Contract(ContractAbi.getContractAbi());
    this.gasPriceTracker = new RecommendedGasPriceTracker();
    ethereum.addListener(gasPriceTracker);
  }

  private boolean isReady() {
    return ethereum.getSyncStatus().getStage() != SyncStatus.SyncStage.Complete;
  }

  @Override
  public CompletableFuture<BytesValue> createTransaction(
      String fromAddress, DepositData depositData, Gwei amount) {
    CompletableFuture<BytesValue> result = new CompletableFuture<>();
    executeOnSyncDone(
        () -> {
          BigInteger nonce =
              ethereum
                  .getRepository()
                  .getNonce(Address.fromHexString(fromAddress).getArrayUnsafe());
          byte[] data =
              contract
                  .getByName("deposit")
                  .encode(
                      depositData.getPubKey().getArrayUnsafe(),
                      depositData.getWithdrawalCredentials().getArrayUnsafe(),
                      depositData.getSignature().getArrayUnsafe());
          Transaction tx =
              ethereum.createTransaction(
                  nonce,
                  BigInteger.valueOf(gasPriceTracker.getRecommendedGasPrice()),
                  BigInteger.valueOf(2_000_000), // FIXME: Why this number??
                  contractDeployAddress.getArrayUnsafe(),
                  EtherUtil.convert(amount.longValue(), EtherUtil.Unit.GWEI),
                  data);
          result.complete(BytesValue.wrap(tx.getEncoded()));
        });

    return result;
  }

  @Override
  public CompletableFuture<BytesValue> signTransaction(
      BytesValue unsignedTransaction, BytesValue eth1PrivKey) {
    CompletableFuture<BytesValue> result = new CompletableFuture<>();
    executeOnSyncDone(
        () -> {
          Transaction tx = new Transaction(unsignedTransaction.getArrayUnsafe());
          tx.sign(ECKey.fromPrivate(eth1PrivKey.getArrayUnsafe()));

          result.complete(BytesValue.wrap(tx.getEncoded()));
        });

    return result;
  }

  private void executeOnSyncDone(Runnable runnable) {
    if (isReady()) {
      runnable.run();
    } else {
      ethereum.addListener(
          new EthereumListenerAdapter() {
            @Override
            public void onSyncDone(SyncState state) {
              if (state == SyncState.COMPLETE) {
                runnable.run();
              }
            }
          });
    }
  }
}
