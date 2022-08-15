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

package tech.pegasys.teku.storage.client;

import static com.google.common.base.Preconditions.checkState;
import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.time.TimeUtilities.secondsToMillis;

import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.executionlayer.PayloadStatus;
import tech.pegasys.teku.spec.generator.ChainBuilder;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsBellatrix;
import tech.pegasys.teku.storage.store.UpdatableStore.StoreTransaction;

public class ChainUpdater {

  public final RecentChainData recentChainData;
  public final ChainBuilder chainBuilder;
  public final Spec spec;

  public ChainUpdater(final RecentChainData recentChainData, final ChainBuilder chainBuilder) {
    this.recentChainData = recentChainData;
    this.chainBuilder = chainBuilder;
    spec = TestSpecFactory.createMinimalPhase0();
  }

  public ChainUpdater(
      final RecentChainData recentChainData, final ChainBuilder chainBuilder, final Spec spec) {
    this.recentChainData = recentChainData;
    this.chainBuilder = chainBuilder;
    this.spec = spec;
  }

  public UInt64 getHeadSlot() {
    return recentChainData.getHeadSlot();
  }

  public void advanceCurrentSlotToAtLeast(final UInt64 currentSlot) {
    checkState(!recentChainData.isPreGenesis(), "Cannot set current slot before genesis");
    final UInt64 slotTime = getSlotTime(currentSlot);
    if (recentChainData.getStore().getTimeSeconds().isLessThan(slotTime)) {
      setTime(slotTime);
    }
  }

  public void setCurrentSlot(final UInt64 currentSlot) {
    checkState(!recentChainData.isPreGenesis(), "Cannot set current slot before genesis");
    setTime(getSlotTime(currentSlot));
  }

  public void setTime(final UInt64 time) {
    checkState(!recentChainData.isPreGenesis(), "Cannot set time before genesis");
    final StoreTransaction tx = recentChainData.startStoreTransaction();
    tx.setTimeMillis(secondsToMillis(time));
    tx.commit().join();
  }

  public SignedBlockAndState addNewBestBlock() {
    final SignedBlockAndState nextBlock = chainBuilder.generateNextBlock();
    updateBestBlock(nextBlock);
    return nextBlock;
  }

  public SignedBlockAndState initializeGenesis() {
    return initializeGenesis(true);
  }

  public SignedBlockAndState initializeGenesis(final boolean signDeposits) {
    return initializeGenesis(
        signDeposits, spec.getGenesisSpecConfig().getMaxEffectiveBalance(), Optional.empty());
  }

  public SignedBlockAndState initializeGenesisWithPayload(final boolean signDeposits) {
    return initializeGenesis(
        signDeposits,
        spec.getGenesisSpecConfig().getMaxEffectiveBalance(),
        Optional.of(
            SchemaDefinitionsBellatrix.required(spec.getGenesisSchemaDefinitions())
                .getExecutionPayloadHeaderSchema()
                .create(
                    Bytes32.random(),
                    Bytes20.ZERO,
                    Bytes32.ZERO,
                    Bytes32.ZERO,
                    Bytes.random(256),
                    Bytes32.ZERO,
                    UInt64.ZERO,
                    UInt64.ZERO,
                    UInt64.ZERO,
                    UInt64.ZERO,
                    Bytes32.ZERO,
                    UInt256.ONE,
                    Bytes32.random(),
                    Bytes32.ZERO)));
  }

  public SignedBlockAndState initializeGenesis(
      final boolean signDeposits,
      final UInt64 depositAmount,
      final Optional<ExecutionPayloadHeader> payloadHeader) {
    final SignedBlockAndState genesis =
        chainBuilder.generateGenesis(UInt64.ZERO, signDeposits, depositAmount, payloadHeader);
    recentChainData.initializeFromGenesis(genesis.getState(), UInt64.ZERO);
    return genesis;
  }

  public SignedBlockAndState finalizeEpoch(final long epoch) {
    return finalizeEpoch(UInt64.valueOf(epoch));
  }

  public SignedBlockAndState finalizeEpoch(final UInt64 epoch) {
    final SignedBlockAndState blockAndState =
        chainBuilder.getLatestBlockAndStateAtEpochBoundary(epoch);
    final Checkpoint checkpoint = new Checkpoint(epoch, blockAndState.getRoot());

    final StoreTransaction tx = recentChainData.startStoreTransaction();
    tx.setFinalizedCheckpoint(checkpoint, false);
    assertThat(tx.commit()).isDone();

    return blockAndState;
  }

  public SignedBlockAndState finalizeCurrentChain() {
    final List<SignedBlockAndState> newBlocks = chainBuilder.finalizeCurrentChain();
    newBlocks.forEach(this::saveBlock);
    final SignedBlockAndState newHead = newBlocks.get(newBlocks.size() - 1);
    updateBestBlock(newHead);
    return newHead;
  }

  public SignedBlockAndState finalizeCurrentChainOptimistically() {
    final List<SignedBlockAndState> newBlocks = chainBuilder.finalizeCurrentChain();
    newBlocks.forEach(this::saveOptimisticBlock);
    final SignedBlockAndState newHead = newBlocks.get(newBlocks.size() - 1);
    updateBestBlock(newHead);
    return newHead;
  }

  public SignedBlockAndState justifyEpoch(final long epoch) {
    return justifyEpoch(UInt64.valueOf(epoch));
  }

  public SignedBlockAndState justifyEpoch(final UInt64 epoch) {
    final SignedBlockAndState blockAndState =
        chainBuilder.getLatestBlockAndStateAtEpochBoundary(epoch);
    final Checkpoint checkpoint = new Checkpoint(epoch, blockAndState.getRoot());

    final StoreTransaction tx = recentChainData.startStoreTransaction();
    tx.setJustifiedCheckpoint(checkpoint);
    tx.setBestJustifiedCheckpoint(checkpoint);
    assertThat(tx.commit()).isDone();

    return blockAndState;
  }

  public void syncWith(final ChainBuilder otherChain) {
    otherChain.streamBlocksAndStates().forEach(this::saveBlock);
    updateBestBlock(otherChain.getLatestBlockAndState());
  }

  public void updateBestBlock(final SignedBlockAndState bestBlock) {
    if (!recentChainData.containsBlock(bestBlock.getRoot())) {
      saveBlock(bestBlock);
    }

    recentChainData.updateHead(bestBlock.getRoot(), bestBlock.getSlot());
    final StoreTransaction tx = recentChainData.startStoreTransaction();
    final Checkpoint justifiedCheckpoint = bestBlock.getState().getCurrentJustifiedCheckpoint();
    if (justifiedCheckpoint
        .getEpoch()
        .isGreaterThan(recentChainData.getJustifiedCheckpoint().orElseThrow().getEpoch())) {
      tx.setJustifiedCheckpoint(justifiedCheckpoint);
      tx.setBestJustifiedCheckpoint(justifiedCheckpoint);
    }
    final Checkpoint finalizedCheckpoint = bestBlock.getState().getFinalizedCheckpoint();
    if (finalizedCheckpoint.getEpoch().isGreaterThan(recentChainData.getFinalizedEpoch())) {
      tx.setFinalizedCheckpoint(finalizedCheckpoint, false);
    }
    assertThat(tx.commit()).isCompleted();
  }

  public SignedBlockAndState advanceChain() {
    final SignedBlockAndState block = chainBuilder.generateNextBlock();
    saveBlock(block);
    return block;
  }

  public SignedBlockAndState advanceChain(final long slot) {
    return advanceChain(UInt64.valueOf(slot));
  }

  public SignedBlockAndState advanceChainUntil(final long slot) {
    return advanceChainUntil(UInt64.valueOf(slot));
  }

  public SignedBlockAndState advanceChainUntil(final UInt64 slot) {
    UInt64 currentSlot = chainBuilder.getLatestSlot();
    SignedBlockAndState latestSigneBlockAndState = chainBuilder.getLatestBlockAndState();
    while (currentSlot.isLessThan(slot)) {
      currentSlot = currentSlot.increment();
      latestSigneBlockAndState = advanceChain(currentSlot);
    }
    return latestSigneBlockAndState;
  }

  public SignedBlockAndState advanceChain(final UInt64 slot) {
    final SignedBlockAndState block = chainBuilder.generateBlockAtSlot(slot);
    saveBlock(block);
    return block;
  }

  public void saveBlock(final SignedBlockAndState block) {
    final StoreTransaction tx = recentChainData.startStoreTransaction();
    tx.putBlockAndState(
        block.getBlock(), block.getState(), spec.calculateBlockCheckpoints(block.getState()));
    assertThat(tx.commit()).isCompleted();
    recentChainData
        .getUpdatableForkChoiceStrategy()
        .orElseThrow()
        .onExecutionPayloadResult(block.getRoot(), PayloadStatus.VALID, true);
    saveBlockTime(block);
  }

  public void saveOptimisticBlock(final SignedBlockAndState block) {
    assertThat(spec.isBlockProcessorOptimistic(block.getSlot()))
        .withFailMessage("can't save optimistic block if block processor is not optimistic")
        .isTrue();
    final StoreTransaction tx = recentChainData.startStoreTransaction();
    tx.putBlockAndState(
        block.getBlock(), block.getState(), spec.calculateBlockCheckpoints(block.getState()));
    assertThat(tx.commit()).isCompleted();
    saveBlockTime(block);
  }

  public void saveBlockTime(final SignedBlockAndState block) {
    // Make sure time is consistent with block
    final UInt64 blockTime = getSlotTime(block.getSlot());
    if (blockTime.compareTo(recentChainData.getStore().getTimeSeconds()) > 0) {
      setTime(blockTime);
    }
  }

  protected UInt64 getSlotTime(final UInt64 slot) {
    final UInt64 secPerSlot = UInt64.valueOf(spec.atSlot(slot).getConfig().getSecondsPerSlot());
    return recentChainData.getGenesisTime().plus(slot.times(secPerSlot));
  }
}
