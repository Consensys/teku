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

package tech.pegasys.teku.storage.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.primitives.UnsignedLong;
import tech.pegasys.teku.core.ChainBuilder;
import tech.pegasys.teku.core.StateTransitionException;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.storage.store.UpdatableStore.StoreTransaction;

public class ChainUpdater {

  public final RecentChainData recentChainData;
  public final ChainBuilder chainBuilder;

  public ChainUpdater(final RecentChainData recentChainData, final ChainBuilder chainBuilder) {
    this.recentChainData = recentChainData;
    this.chainBuilder = chainBuilder;
  }

  public SignedBlockAndState addNewBestBlock() {
    try {
      final SignedBlockAndState nextBlock;
      nextBlock = chainBuilder.generateNextBlock();
      updateBestBlock(nextBlock);
      return nextBlock;
    } catch (StateTransitionException e) {
      throw new IllegalStateException(e);
    }
  }

  public SignedBlockAndState initializeGenesis() {
    final SignedBlockAndState genesis = chainBuilder.generateGenesis();
    recentChainData.initializeFromGenesis(genesis.getState());
    return genesis;
  }

  public SignedBlockAndState finalizeEpoch(final long epoch) {
    return finalizeEpoch(UnsignedLong.valueOf(epoch));
  }

  public SignedBlockAndState finalizeEpoch(final UnsignedLong epoch) {

    final SignedBlockAndState blockAndState =
        chainBuilder.getLatestBlockAndStateAtEpochBoundary(epoch);
    final Checkpoint checkpoint = new Checkpoint(epoch, blockAndState.getRoot());

    final StoreTransaction tx = recentChainData.startStoreTransaction();
    tx.setFinalizedCheckpoint(checkpoint);
    tx.putCheckpointState(checkpoint, blockAndState.getState());
    if (recentChainData
            .getStore()
            .getJustifiedCheckpoint()
            .getEpoch()
            .compareTo(checkpoint.getEpoch())
        < 0) {
      // Justified checkpoint must be at or beyond finalized checkpoint
      tx.setJustifiedCheckpoint(checkpoint);
    }
    tx.commit().reportExceptions();

    return blockAndState;
  }

  public void updateBestBlock(final SignedBlockAndState bestBlock) {
    saveBlock(bestBlock);

    recentChainData.updateBestBlock(bestBlock.getRoot(), bestBlock.getSlot());
  }

  public SignedBlockAndState advanceChain() {
    try {
      final SignedBlockAndState block = chainBuilder.generateNextBlock();
      saveBlock(block);
      return block;
    } catch (StateTransitionException e) {
      throw new IllegalStateException(e);
    }
  }

  public SignedBlockAndState advanceChain(final long slot) {
    return advanceChain(UnsignedLong.valueOf(slot));
  }

  public SignedBlockAndState advanceChain(final UnsignedLong slot) {
    try {
      final SignedBlockAndState block = chainBuilder.generateBlockAtSlot(slot);
      saveBlock(block);
      return block;
    } catch (StateTransitionException e) {
      throw new IllegalStateException(e);
    }
  }

  public void saveBlock(final SignedBlockAndState block) {
    final StoreTransaction tx = recentChainData.startStoreTransaction();
    tx.putBlockAndState(block.getBlock(), block.getState());
    assertThat(tx.commit()).isCompleted();
  }
}
