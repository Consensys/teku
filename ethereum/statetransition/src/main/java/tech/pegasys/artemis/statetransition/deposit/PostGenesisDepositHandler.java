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

package tech.pegasys.artemis.statetransition.deposit;

import com.google.common.eventbus.EventBus;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.util.DepositUtil;
import tech.pegasys.artemis.datastructures.util.MerkleTree;
import tech.pegasys.artemis.pow.api.DepositEventChannel;
import tech.pegasys.artemis.pow.event.DepositsFromBlockEvent;
import tech.pegasys.artemis.storage.ChainStorageClient;

public class PostGenesisDepositHandler implements DepositEventChannel {

  private final MerkleTree<Bytes32> merkleTree;
  private final ChainStorageClient chainStorageClient;
  private final EventBus eventBus;

  public PostGenesisDepositHandler(
      MerkleTree<Bytes32> genesisMerkleTree,
      final ChainStorageClient chainStorageClient,
      final EventBus eventBus) {
    this.merkleTree = genesisMerkleTree;
    this.chainStorageClient = chainStorageClient;
    this.eventBus = eventBus;
  }

  @Override
  public void notifyDepositsFromBlock(final DepositsFromBlockEvent event) {
    if (chainStorageClient.isPreGenesis()) {
      return;
    }

    event.getDeposits().stream()
        .map(DepositUtil::convertDepositEventToOperationDeposit)
        .forEachOrdered(
            deposit -> {
              Bytes32 depositHash = deposit.getData().hash_tree_root();
              merkleTree.add(depositHash);
              deposit.setProof(merkleTree.getProofTreeByValue(depositHash));
              eventBus.post(deposit);
            });
  }
}
