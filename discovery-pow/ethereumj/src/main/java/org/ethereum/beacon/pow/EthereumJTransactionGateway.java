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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.ethereum.beacon.pow.validator.TransactionGateway;
import org.ethereum.core.Transaction;
import org.ethereum.facade.Ethereum;
import org.ethereum.facade.SyncStatus;
import org.ethereum.listener.EthereumListenerAdapter;
import tech.pegasys.artemis.util.bytes.BytesValue;

public class EthereumJTransactionGateway implements TransactionGateway {
  private Ethereum ethereum;

  public EthereumJTransactionGateway(Ethereum ethereum) {
    this.ethereum = ethereum;
  }

  private boolean isReady() {
    return ethereum.getSyncStatus().getStage() != SyncStatus.SyncStage.Complete;
  }

  @Override
  public CompletableFuture<TxStatus> send(BytesValue signedTransaction) {
    CompletableFuture<TxStatus> result = new CompletableFuture<>();
    executeOnSyncDone(
        () -> {
          Future txFuture =
              ethereum.submitTransaction(new Transaction(signedTransaction.getArrayUnsafe()));
          try {
            if (txFuture.get() == null) {
              result.complete(TxStatus.ERROR);
            } else {
              result.complete(TxStatus.SUCCESS);
            }
          } catch (InterruptedException | ExecutionException e) {
            result.complete(TxStatus.ERROR);
          }
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
