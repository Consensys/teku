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

package tech.pegasys.teku.storage.client;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.google.common.eventbus.EventBus;
import com.google.common.primitives.UnsignedLong;
import java.util.List;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSKeyGenerator;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.core.ChainBuilder;
import tech.pegasys.teku.core.ChainBuilder.BlockOptions;
import tech.pegasys.teku.core.StateTransitionException;
import tech.pegasys.teku.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.storage.Store.Transaction;
import tech.pegasys.teku.storage.api.ReorgEventChannel;
import tech.pegasys.teku.util.config.Constants;

class RecentChainDataTest {
  private static final List<BLSKeyPair> VALIDATOR_KEYS = BLSKeyGenerator.generateKeyPairs(2);

  private final ChainBuilder chainBuilder = ChainBuilder.create(VALIDATOR_KEYS);
  private final SignedBlockAndState genesis = chainBuilder.generateGenesis();

  private final BeaconState genesisState = genesis.getState();
  private final BeaconBlock genesisBlock = genesis.getBlock().getMessage();
  private final Bytes32 genesisBlockRoot = genesis.getRoot();

  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private final EventBus eventBus = mock(EventBus.class);
  private final ReorgEventChannel reorgEventChannel = mock(ReorgEventChannel.class);

  private final RecentChainData storageClient =
      MemoryOnlyRecentChainData.create(eventBus, reorgEventChannel);
  private final RecentChainData preGenesisStorageClient =
      MemoryOnlyRecentChainData.create(eventBus, reorgEventChannel);
  private RecentChainData preForkChoiceStorageClient;

  @BeforeEach
  public void setup() {
    storageClient.initializeFromGenesis(genesisState);
    preForkChoiceStorageClient =
        MemoryOnlyRecentChainData.createWithStore(
            eventBus, reorgEventChannel, storageClient.getStore());
  }

  @Test
  public void initialize_setupInitialState() {
    preGenesisStorageClient.initializeFromGenesis(genesisState);
    assertThat(preGenesisStorageClient.getGenesisTime()).isEqualTo(genesisState.getGenesis_time());
    assertThat(preGenesisStorageClient.getBestSlot())
        .isEqualTo(UnsignedLong.valueOf(Constants.GENESIS_SLOT));
    assertThat(preGenesisStorageClient.getBestState()).hasValue(genesisState);
    assertThat(preGenesisStorageClient.getStore()).isNotNull();
  }

  @Test
  public void updateBestBlock_validUpdate() throws Exception {
    final SignedBlockAndState bestBlock = chainBuilder.generateNextBlock();
    saveBlock(storageClient, bestBlock);

    storageClient.updateBestBlock(bestBlock.getRoot(), bestBlock.getSlot());
    assertThat(storageClient.getBestBlockAndState()).contains(bestBlock.toUnsigned());
  }

  @Test
  public void updateBestBlock_blockIsMissing() throws Exception {
    final SignedBlockAndState bestBlock = chainBuilder.generateNextBlock();
    saveState(storageClient, bestBlock.getRoot(), bestBlock.getState());

    storageClient.updateBestBlock(bestBlock.getRoot(), bestBlock.getSlot());
    assertThat(storageClient.getBestBlockAndState()).contains(genesis.toUnsigned());
  }

  @Test
  public void updateBestBlock_stateIsMissing() throws Exception {
    final SignedBlockAndState bestBlock = chainBuilder.generateNextBlock();
    saveBlock(storageClient, bestBlock.getBlock());

    storageClient.updateBestBlock(bestBlock.getRoot(), bestBlock.getSlot());
    assertThat(storageClient.getBestBlockAndState()).contains(genesis.toUnsigned());
  }

  @Test
  public void updateBestBlock_blockAndStateAreMissing() throws Exception {
    final SignedBlockAndState bestBlock = chainBuilder.generateNextBlock();

    storageClient.updateBestBlock(bestBlock.getRoot(), bestBlock.getSlot());
    assertThat(storageClient.getBestBlockAndState()).contains(genesis.toUnsigned());
  }

  @Test
  public void getBlockBySlot_returnEmptyWhenStoreNotSet() {
    assertThat(preGenesisStorageClient.getBlockBySlot(UnsignedLong.ZERO)).isEmpty();
  }

  @Test
  public void getBlockBySlot_returnEmptyWhenBestBlockNotSet() {
    assertThat(preForkChoiceStorageClient.getBlockBySlot(UnsignedLong.ZERO)).isEmpty();
  }

  @Test
  public void getBlockBySlot_returnGenesisBlockWhenItIsTheBestState() {
    assertThat(storageClient.getBlockBySlot(UnsignedLong.ZERO)).contains(genesisBlock);
  }

  @Test
  public void getBlockBySlot_returnGenesisBlockWhenItIsNotTheBestState() throws Exception {
    final SignedBlockAndState bestBlock = addNewBestBlock(storageClient);
    // Sanity check
    assertThat(bestBlock.getSlot()).isGreaterThan(genesisBlock.getSlot());

    assertThat(storageClient.getBlockBySlot(genesisBlock.getSlot())).contains(genesisBlock);
  }

  @Test
  public void getBlockBySlot_returnEmptyWhenSlotBeforeHistoricalRootWindow() {
    // First slot where the block roots start overwriting themselves and dropping history
    final UnsignedLong bestSlot =
        UnsignedLong.valueOf(Constants.SLOTS_PER_HISTORICAL_ROOT).plus(UnsignedLong.ONE);
    final SignedBlockAndState bestBlock = dataStructureUtil.randomSignedBlockAndState(bestSlot);
    updateBestBlock(storageClient, bestBlock);

    // Slot 0 has now been overwritten by our current best slot so we can't get that block anymore.
    assertThat(storageClient.getBlockBySlot(UnsignedLong.ZERO)).isEmpty();
  }

  @Test
  public void getBlockBySlot_returnCorrectBlockFromHistoricalWindow() {
    // We've wrapped around a lot of times and are 5 slots into the next "loop"
    final int historicalIndex = 5;
    final UnsignedLong requestedSlot =
        UnsignedLong.valueOf(Constants.SLOTS_PER_HISTORICAL_ROOT)
            .times(UnsignedLong.valueOf(900000000))
            .plus(UnsignedLong.valueOf(historicalIndex));
    final SignedBlockAndState requestedBlock =
        dataStructureUtil.randomSignedBlockAndState(requestedSlot);
    final Bytes32 requestedBlockHash = requestedBlock.getRoot();
    saveBlock(storageClient, requestedBlock);

    // Avoid the simple case where the requested slot is the best slot so we have to go to the
    // historic blocks
    final UnsignedLong bestSlot = requestedSlot.plus(UnsignedLong.ONE);
    // Overwrite the genesis hash.
    final BeaconState bestState =
        dataStructureUtil
            .randomBeaconState(bestSlot)
            .updated(state -> state.getBlock_roots().set(historicalIndex, requestedBlockHash));
    final SignedBeaconBlock bestBlock =
        dataStructureUtil.randomSignedBeaconBlock(bestSlot, bestState);
    final SignedBlockAndState bestBlockAndState = new SignedBlockAndState(bestBlock, bestState);
    updateBestBlock(storageClient, bestBlockAndState);

    assertThat(storageClient.getBlockBySlot(requestedSlot))
        .contains(requestedBlock.getBlock().getMessage());
  }

  @Test
  public void getBlockBySlot_returnEmptyWhenSlotWasEmpty() throws Exception {
    // Request block for an empty slot immediately after genesis
    final UnsignedLong requestedSlot = genesisBlock.getSlot().plus(UnsignedLong.ONE);
    final UnsignedLong bestSlot = requestedSlot.plus(UnsignedLong.ONE);

    final SignedBlockAndState bestBlock = chainBuilder.generateBlockAtSlot(bestSlot);
    updateBestBlock(storageClient, bestBlock);

    assertThat(storageClient.getBlockBySlot(requestedSlot)).isEmpty();
  }

  @Test
  void getStateInEffectAtSlot_returnEmptyWhenStoreNotSet() {
    assertThat(preGenesisStorageClient.getStateInEffectAtSlot(UnsignedLong.ZERO)).isEmpty();
  }

  @Test
  public void getStateInEffectAtSlot_returnGenesisStateWhenItIsTheBestState() {
    assertThat(storageClient.getStateInEffectAtSlot(genesis.getSlot())).contains(genesisState);
  }

  @Test
  public void getStateInEffectAtSlot_returnStateFromLastBlockWhenSlotsAreEmpty() throws Exception {
    // Request block for an empty slot immediately after genesis
    final UnsignedLong requestedSlot = genesisBlock.getSlot().plus(UnsignedLong.ONE);
    final UnsignedLong bestSlot = requestedSlot.plus(UnsignedLong.ONE);

    final SignedBlockAndState bestBlock = chainBuilder.generateBlockAtSlot(bestSlot);
    updateBestBlock(storageClient, bestBlock);

    assertThat(storageClient.getStateInEffectAtSlot(requestedSlot)).contains(genesisState);
  }

  @Test
  public void getStateInEffectAtSlot_returnStateFromLastBlockWhenHeadSlotIsEmpty() {
    assertThat(storageClient.getStateInEffectAtSlot(UnsignedLong.ONE)).contains(genesisState);
  }

  @Test
  public void isIncludedInBestState_falseWhenNoStoreSet() {
    assertThat(preGenesisStorageClient.isIncludedInBestState(genesisBlockRoot)).isFalse();
  }

  @Test
  public void isIncludedInBestState_falseWhenBestBlockNotSet() {
    assertThat(preForkChoiceStorageClient.isIncludedInBestState(genesisBlockRoot)).isFalse();
  }

  @Test
  public void isIncludedInBestState_falseWhenNoBlockAtSlot() throws Exception {
    final ChainBuilder fork = chainBuilder.fork();
    final SignedBlockAndState forkBlock = fork.generateNextBlock();
    saveBlock(storageClient, forkBlock);

    final UnsignedLong bestSlot = forkBlock.getSlot().plus(UnsignedLong.ONE);
    final SignedBlockAndState bestBlock = chainBuilder.generateBlockAtSlot(bestSlot);
    updateBestBlock(storageClient, bestBlock);

    assertThat(storageClient.isIncludedInBestState(forkBlock.getRoot())).isFalse();
  }

  @Test
  public void isIncludedInBestState_falseWhenBlockAtSlotDoesNotMatch() throws Exception {
    // Build a small chain, so we can later generate attestations
    for (int i = 0; i < 10; i++) {
      advanceChain(storageClient);
    }

    // Create a fork chain
    final ChainBuilder fork = chainBuilder.fork();
    final SignedBlockAndState forkBlock = fork.generateNextBlock();
    saveBlock(storageClient, forkBlock);

    // Generate attestation at canonical block to differentiate from fork block
    final UnsignedLong bestSlot = forkBlock.getSlot();
    final Attestation attestation =
        chainBuilder.streamValidAttestationsForBlockAtSlot(bestSlot).findFirst().orElseThrow();
    final BlockOptions blockOptions = BlockOptions.create().addAttestation(attestation);
    final SignedBlockAndState bestBlock = chainBuilder.generateBlockAtSlot(bestSlot, blockOptions);
    updateBestBlock(storageClient, bestBlock);

    // Fork block should not be accessible from best state
    assertThat(storageClient.isIncludedInBestState(forkBlock.getRoot())).isFalse();
  }

  @Test
  public void isIncludedInBestState_trueWhenBlockAtSlotDoesMatch() throws Exception {
    final SignedBlockAndState targetBlock = chainBuilder.generateNextBlock();
    saveBlock(storageClient, targetBlock);
    addNewBestBlock(storageClient);

    assertThat(storageClient.isIncludedInBestState(targetBlock.getRoot())).isTrue();
  }

  @Test
  public void startStoreTransaction_mutateFinalizedCheckpoint() {
    preGenesisStorageClient.initializeFromGenesis(dataStructureUtil.randomBeaconState());

    final Checkpoint originalCheckpoint =
        preGenesisStorageClient.getStore().getFinalizedCheckpoint();
    final Checkpoint newCheckpoint = dataStructureUtil.randomCheckpoint();
    assertThat(originalCheckpoint).isNotEqualTo(newCheckpoint); // Sanity check

    final Transaction tx = preGenesisStorageClient.startStoreTransaction();
    tx.setFinalizedCheckpoint(newCheckpoint);

    tx.commit().reportExceptions();

    // Check that store was updated
    final Checkpoint currentCheckpoint =
        preGenesisStorageClient.getStore().getFinalizedCheckpoint();
    assertThat(currentCheckpoint).isEqualTo(newCheckpoint);
  }

  @Test
  public void startStoreTransaction_doNotMutateFinalizedCheckpoint() {
    preGenesisStorageClient.initializeFromGenesis(dataStructureUtil.randomBeaconState());
    final Checkpoint originalCheckpoint =
        preGenesisStorageClient.getStore().getFinalizedCheckpoint();

    final Transaction tx = preGenesisStorageClient.startStoreTransaction();
    tx.setTime(UnsignedLong.valueOf(11L));
    tx.commit().reportExceptions();
    verify(eventBus, never()).post(argThat((obj) -> obj instanceof Checkpoint));

    final Checkpoint currentCheckpoint =
        preGenesisStorageClient.getStore().getFinalizedCheckpoint();
    assertThat(currentCheckpoint).isEqualTo(originalCheckpoint);
  }

  @Test
  public void updateBestBlock_noReorgEventWhenBestBlockFirstSet() {
    preGenesisStorageClient.initializeFromGenesis(genesisState);
    verifyNoInteractions(reorgEventChannel);
  }

  @Test
  public void updateBestBlock_noReorgEventWhenChainAdvances() throws Exception {
    final ChainBuilder chainBuilder = ChainBuilder.create(BLSKeyGenerator.generateKeyPairs(1));
    chainBuilder.generateGenesis();
    preGenesisStorageClient.initializeFromGenesis(chainBuilder.getStateAtSlot(0));
    verifyNoInteractions(reorgEventChannel);

    chainBuilder.generateBlocksUpToSlot(2);
    importBlocksAndStates(chainBuilder);

    final SignedBlockAndState latestBlockAndState = chainBuilder.getLatestBlockAndState();
    preGenesisStorageClient.updateBestBlock(
        latestBlockAndState.getRoot(), latestBlockAndState.getSlot());
    verifyNoInteractions(reorgEventChannel);
  }

  @Test
  public void updateBestBlock_reorgEventWhenChainSwitchesToNewBlockAtSameSlot() throws Exception {
    final ChainBuilder chainBuilder = ChainBuilder.create(BLSKeyGenerator.generateKeyPairs(16));
    chainBuilder.generateGenesis();
    preGenesisStorageClient.initializeFromGenesis(chainBuilder.getStateAtSlot(0));
    verifyNoInteractions(reorgEventChannel);

    chainBuilder.generateBlockAtSlot(1);

    // Set target slot at which to create duplicate blocks
    // and generate block options to make each block unique
    final List<BlockOptions> blockOptions =
        chainBuilder
            .streamValidAttestationsForBlockAtSlot(UnsignedLong.ONE)
            .map(attestation -> BlockOptions.create().addAttestation(attestation))
            .limit(2)
            .collect(toList());
    final ChainBuilder forkBuilder = chainBuilder.fork();
    final SignedBlockAndState latestBlockAndState =
        chainBuilder.generateBlockAtSlot(UnsignedLong.valueOf(2), blockOptions.get(0));
    final SignedBlockAndState latestForkBlockAndState =
        forkBuilder.generateBlockAtSlot(UnsignedLong.valueOf(2), blockOptions.get(1));
    importBlocksAndStates(chainBuilder, forkBuilder);

    // Update to head block of original chain.
    preGenesisStorageClient.updateBestBlock(
        latestBlockAndState.getRoot(), latestBlockAndState.getSlot());
    verifyNoInteractions(reorgEventChannel);

    // Switch to fork.
    preGenesisStorageClient.updateBestBlock(
        latestForkBlockAndState.getRoot(), latestForkBlockAndState.getSlot());
    verify(reorgEventChannel)
        .reorgOccurred(latestForkBlockAndState.getRoot(), latestForkBlockAndState.getSlot());
  }

  @Test
  public void updateBestBlock_reorgEventWhenChainSwitchesToNewBlockAtLaterSlot() throws Exception {
    final ChainBuilder chainBuilder = ChainBuilder.create(BLSKeyGenerator.generateKeyPairs(16));
    chainBuilder.generateGenesis();
    preGenesisStorageClient.initializeFromGenesis(chainBuilder.getStateAtSlot(0));
    verifyNoInteractions(reorgEventChannel);

    chainBuilder.generateBlockAtSlot(1);

    // Set target slot at which to create duplicate blocks
    // and generate block options to make each block unique
    final List<BlockOptions> blockOptions =
        chainBuilder
            .streamValidAttestationsForBlockAtSlot(UnsignedLong.ONE)
            .map(attestation -> BlockOptions.create().addAttestation(attestation))
            .limit(2)
            .collect(toList());
    final ChainBuilder forkBuilder = chainBuilder.fork();
    final SignedBlockAndState latestBlockAndState =
        chainBuilder.generateBlockAtSlot(UnsignedLong.valueOf(2), blockOptions.get(0));

    forkBuilder.generateBlockAtSlot(UnsignedLong.valueOf(2), blockOptions.get(1));

    // Fork extends a slot further
    final SignedBlockAndState latestForkBlockAndState = forkBuilder.generateBlockAtSlot(3);
    importBlocksAndStates(chainBuilder, forkBuilder);

    // Update to head block of original chain.
    preGenesisStorageClient.updateBestBlock(
        latestBlockAndState.getRoot(), latestBlockAndState.getSlot());
    verifyNoInteractions(reorgEventChannel);

    // Switch to fork.
    preGenesisStorageClient.updateBestBlock(
        latestForkBlockAndState.getRoot(), latestForkBlockAndState.getSlot());
    verify(reorgEventChannel)
        .reorgOccurred(latestForkBlockAndState.getRoot(), latestForkBlockAndState.getSlot());
  }

  private void importBlocksAndStates(final ChainBuilder... chainBuilders) {
    final Transaction transaction = preGenesisStorageClient.startStoreTransaction();
    Stream.of(chainBuilders)
        .flatMap(ChainBuilder::streamBlocksAndStates)
        .forEach(
            blockAndState -> {
              transaction.putBlock(blockAndState.getRoot(), blockAndState.getBlock());
              transaction.putBlockState(blockAndState.getRoot(), blockAndState.getState());
            });
    transaction.commit().join();
  }

  private SignedBlockAndState addNewBestBlock(RecentChainData recentChainData)
      throws StateTransitionException {
    final SignedBlockAndState nextBlock = chainBuilder.generateNextBlock();
    updateBestBlock(recentChainData, nextBlock);

    return nextBlock;
  }

  private void updateBestBlock(
      RecentChainData recentChainData, final SignedBlockAndState bestBlock) {
    saveBlock(recentChainData, bestBlock);

    storageClient.updateBestBlock(bestBlock.getRoot(), bestBlock.getSlot());
  }

  private void advanceChain(final RecentChainData recentChainData) throws StateTransitionException {
    saveBlock(recentChainData, chainBuilder.generateNextBlock());
  }

  private void saveBlock(final RecentChainData recentChainData, final SignedBlockAndState block) {
    final Transaction tx = recentChainData.startStoreTransaction();
    tx.putBlock(block.getRoot(), block.getBlock());
    tx.putBlockState(block.getRoot(), block.getState());
    tx.commit().reportExceptions();
  }

  private void saveBlock(final RecentChainData recentChainData, final SignedBeaconBlock block) {
    final Transaction tx = recentChainData.startStoreTransaction();
    tx.putBlock(block.getRoot(), block);
    tx.commit().reportExceptions();
  }

  private void saveState(
      final RecentChainData recentChainData, final Bytes32 blockRoot, final BeaconState state) {
    final Transaction tx = recentChainData.startStoreTransaction();
    tx.putBlockState(blockRoot, state);
    tx.commit().reportExceptions();
  }
}
