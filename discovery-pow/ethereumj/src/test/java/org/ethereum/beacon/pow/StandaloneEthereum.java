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

import java.lang.reflect.Field;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import org.ethereum.config.CommonConfig;
import org.ethereum.config.SystemProperties;
import org.ethereum.core.Block;
import org.ethereum.core.CallTransaction;
import org.ethereum.core.Repository;
import org.ethereum.core.Transaction;
import org.ethereum.crypto.ECKey;
import org.ethereum.facade.Blockchain;
import org.ethereum.facade.EthereumImpl;
import org.ethereum.facade.SyncStatus;
import org.ethereum.facade.SyncStatus.SyncStage;
import org.ethereum.listener.CompositeEthereumListener;
import org.ethereum.listener.EthereumListener;
import org.ethereum.listener.EthereumListenerAdapter;
import org.ethereum.util.blockchain.StandaloneBlockchain;
import org.ethereum.vm.program.ProgramResult;

public class StandaloneEthereum extends EthereumImpl {
  StandaloneBlockchain standaloneBlockchain;
  CompositeEthereumListener listener;

  public StandaloneEthereum(SystemProperties config, StandaloneBlockchain standaloneBlockchain) {
    super(config, getListenerHack(standaloneBlockchain));
    this.standaloneBlockchain = standaloneBlockchain;
  }

  private static CompositeEthereumListener getListenerHack(StandaloneBlockchain sb) {
    try {
      Field field = sb.getClass().getDeclaredField("listener");
      field.setAccessible(true);
      return (CompositeEthereumListener) field.get(sb);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public org.ethereum.facade.Repository getPendingState() {
    return standaloneBlockchain.getPendingState().getRepository();
  }

  @Override
  public Future<Transaction> submitTransaction(Transaction transaction) {
    standaloneBlockchain.submitTransaction(transaction);
    CompletableFuture<Transaction> ret = new CompletableFuture<>();
    ret.complete(transaction);
    return ret;
  }

  @Override
  public ProgramResult callConstantFunction(
      String receiveAddress,
      ECKey senderPrivateKey,
      CallTransaction.Function function,
      Object... funcArgs) {
    Transaction tx =
        CallTransaction.createCallTransaction(
            0, 0, 100000000000000L, receiveAddress, 0, function, funcArgs);
    tx.sign(senderPrivateKey);
    Block block = standaloneBlockchain.getBlockchain().getBestBlock();

    Repository repository =
        standaloneBlockchain
            .getBlockchain()
            .getRepository()
            .getSnapshotTo(block.getStateRoot())
            .startTracking();

    try {
      org.ethereum.core.TransactionExecutor executor =
          new org.ethereum.core.TransactionExecutor(
                  tx,
                  block.getCoinbase(),
                  repository,
                  standaloneBlockchain.getBlockchain().getBlockStore(),
                  standaloneBlockchain.getBlockchain().getProgramInvokeFactory(),
                  block,
                  new EthereumListenerAdapter(),
                  0)
              .withCommonConfig(CommonConfig.getDefault())
              .setLocalCall(true);

      executor.init();
      executor.execute();
      executor.go();
      executor.finalization();

      return executor.getResult();
    } finally {
      repository.rollback();
    }
  }

  @Override
  public org.ethereum.facade.Repository getRepository() {
    return standaloneBlockchain.getBlockchain().getRepository();
  }

  @Override
  public void addListener(EthereumListener listener) {
    standaloneBlockchain.addEthereumListener(listener);
  }

  @Override
  public Blockchain getBlockchain() {
    return standaloneBlockchain.getBlockchain();
  }

  @Override
  public SyncStatus getSyncStatus() {
    long best = getBlockchain().getBestBlock().getNumber();
    return new SyncStatus(SyncStage.Complete, best, best);
  }
}
