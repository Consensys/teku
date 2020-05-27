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

import com.google.common.primitives.UnsignedLong;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.core.ChainBuilder;
import tech.pegasys.teku.core.StateTransitionException;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.storage.Store.Transaction;

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

    final Transaction tx = recentChainData.startStoreTransaction();
    tx.setFinalizedCheckpoint(checkpoint);
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
    final Transaction tx = recentChainData.startStoreTransaction();
    tx.putBlock(block.getRoot(), block.getBlock());
    tx.putBlockState(block.getRoot(), block.getState());
    tx.commit().reportExceptions();
  }

  public void saveBlock(final SignedBeaconBlock block) {
    final Transaction tx = recentChainData.startStoreTransaction();
    tx.putBlock(block.getRoot(), block);
    tx.commit().reportExceptions();
  }

  public void saveState(final Bytes32 blockRoot, final BeaconState state) {
    final Transaction tx = recentChainData.startStoreTransaction();
    tx.putBlockState(blockRoot, state);
    tx.commit().reportExceptions();
  }
}
