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

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.blocks.StateAndBlockSummary;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.generator.ChainBuilder;
import tech.pegasys.teku.storage.api.StorageQueryChannel;
import tech.pegasys.teku.storage.storageSystem.DelayedStorageUpdateChannel;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;
import tech.pegasys.teku.storage.store.StoreConfig;

class BufferingStorageChannelTest {

  private final Spec spec = TestSpecFactory.createDefault();
  private final StorageSystem storage =
      InMemoryStorageSystemBuilder.create()
          .specProvider(spec)
          .delayedStorage(true)
          .storeNonCanonicalBlocks(true)
          .storeConfig(
              StoreConfig.builder()
                  .asyncStorageEnabled(true)
                  .hotStatePersistenceFrequencyInEpochs(1)
                  .build())
          .build();
  private final ChainUpdater chainUpdater = storage.chainUpdater();
  private final DelayedStorageUpdateChannel delayChannel = storage.getDelayedStorageUpdateChannel();
  private final StorageQueryChannel storageQueryChannel = storage.getStorageQueryChannel();

  @BeforeEach
  void setUp() {
    chainUpdater.initializeGenesis();
    delayChannel.completeAllActions();
  }

  @Test
  void shouldBufferNewBlocks() {
    final SignedBlockAndState newBlock = chainUpdater.addNewBestBlock();
    assertBeforeAndAfter(
        () -> {
          assertThat(storageQueryChannel.getBlockByBlockRoot(newBlock.getRoot()))
              .isCompletedWithValue(Optional.of(newBlock.getBlock()));
          assertThat(storageQueryChannel.getHotBlocksByRoot(Set.of(newBlock.getRoot())))
              .isCompletedWithValue(Map.of(newBlock.getRoot(), newBlock.getBlock()));
          assertThat(storageQueryChannel.getSlotAndBlockRootByStateRoot(newBlock.getStateRoot()))
              .isCompletedWithValue(
                  Optional.of(new SlotAndBlockRoot(newBlock.getSlot(), newBlock.getRoot())));
        });
  }

  @Test
  void shouldBufferNewHotStates() {
    final SignedBlockAndState newBlock =
        chainUpdater.advanceChain(spec.computeStartSlotAtEpoch(UInt64.valueOf(2)));
    assertBeforeAndAfter(
        () -> {
          assertThat(storageQueryChannel.getBlockByBlockRoot(newBlock.getRoot()))
              .isCompletedWithValue(Optional.of(newBlock.getBlock()));
          assertThat(storageQueryChannel.getHotBlockAndStateByBlockRoot(newBlock.getRoot()))
              .isCompletedWithValue(Optional.of(newBlock));
          assertThat(storageQueryChannel.getHotStateAndBlockSummaryByBlockRoot(newBlock.getRoot()))
              .isCompletedWithValue(Optional.of(StateAndBlockSummary.create(newBlock)));
        });
  }

  @Test
  void shouldGetUnbufferedHotBlocksByRoot() {
    final SignedBlockAndState newBlock = chainUpdater.addNewBestBlock();
    delayChannel.completeAllActions();
    assertThat(storageQueryChannel.getHotBlocksByRoot(Set.of(newBlock.getRoot())))
        .isCompletedWithValue(Map.of(newBlock.getRoot(), newBlock.getBlock()));
  }

  @Test
  void shouldGetMixOfBufferedAndUnbufferedHotBlocksByRoot() {
    final SignedBlockAndState storedBlock = chainUpdater.addNewBestBlock();
    delayChannel.completeAllActions();
    final SignedBlockAndState bufferedBlock = chainUpdater.addNewBestBlock();
    assertThat(
            storageQueryChannel.getHotBlocksByRoot(
                Set.of(storedBlock.getRoot(), bufferedBlock.getRoot())))
        .isCompletedWithValue(
            Map.of(
                storedBlock.getRoot(),
                storedBlock.getBlock(),
                bufferedBlock.getRoot(),
                bufferedBlock.getBlock()));
  }

  @Test
  void shouldBufferFinalizedBlocks() {
    final UInt64 lastFinalizedSlot = spec.computeStartSlotAtEpoch(UInt64.valueOf(3));
    final SignedBlockAndState newHead = chainUpdater.advanceChainUntil(lastFinalizedSlot);
    chainUpdater.updateBestBlock(newHead);

    chainUpdater.finalizeCurrentChain();

    final UInt64 latestFinalizedSlot =
        storage.recentChainData().getFinalizedCheckpoint().orElseThrow().getEpochStartSlot(spec);

    final List<SignedBlockAndState> finalizedChain =
        storage
            .chainBuilder()
            .streamBlocksAndStates(UInt64.ZERO, latestFinalizedSlot)
            .collect(Collectors.toList());

    // Before the update applies, the latest finalized checkpoint blocks will still be hot
    // TODO: Simplify things so it's just getBlocksByRoot not getHotBlocksByRoot
    final SignedBeaconBlock genesisBlock = storage.chainBuilder().getBlockAtSlot(0);
    final SignedBeaconBlock newFinalizedBlock =
        storage.chainBuilder().getBlockAtSlot(latestFinalizedSlot);
    assertThatSafeFuture(
            storageQueryChannel.getHotBlocksByRoot(
                Set.of(genesisBlock.getRoot(), newFinalizedBlock.getRoot())))
        .isCompletedWithValue(
            Map.of(
                genesisBlock.getRoot(),
                genesisBlock,
                newFinalizedBlock.getRoot(),
                newFinalizedBlock));

    assertBeforeAndAfter(
        () -> {
          for (SignedBlockAndState blockAndState : finalizedChain) {
            assertThatSafeFuture(
                    storageQueryChannel.getHotBlocksByRoot(
                        finalizedChain.stream()
                            .filter(
                                block ->
                                    !block.getRoot().equals(genesisBlock.getRoot())
                                        && !block.getRoot().equals(newFinalizedBlock.getRoot()))
                            .map(SignedBlockAndState::getRoot)
                            .collect(Collectors.toSet())))
                .describedAs("Finalized blocks are not still buffered as hot")
                .isCompletedWithValue(Collections.emptyMap());

            assertThatSafeFuture(storageQueryChannel.getBlockByBlockRoot(blockAndState.getRoot()))
                .describedAs("Block by root at slot %s", blockAndState.getSlot())
                .isCompletedWithOptionalContaining(blockAndState.getBlock());

            assertThatSafeFuture(
                    storageQueryChannel.getFinalizedBlockAtSlot(blockAndState.getSlot()))
                .describedAs("Finalized block at slot %s", blockAndState.getSlot())
                .isCompletedWithOptionalContaining(blockAndState.getBlock());

            assertThatSafeFuture(
                    storageQueryChannel.getLatestFinalizedBlockAtSlot(blockAndState.getSlot()))
                .describedAs("Latest finalized block at slot %s", blockAndState.getSlot())
                .isCompletedWithOptionalContaining(blockAndState.getBlock());

            assertThatSafeFuture(
                    storageQueryChannel.getSlotAndBlockRootByStateRoot(
                        blockAndState.getStateRoot()))
                .describedAs("Slot and block by state root at slot %s", blockAndState.getSlot())
                .isCompletedWithOptionalContaining(
                    new SlotAndBlockRoot(blockAndState.getSlot(), blockAndState.getRoot()));

            assertThatSafeFuture(
                    storageQueryChannel.getLatestFinalizedStateAtSlot(blockAndState.getSlot()))
                .describedAs("Latest state at slot %s", blockAndState.getSlot())
                .isCompletedWithOptionalContaining(blockAndState.getState());

            assertThatSafeFuture(
                    storageQueryChannel.getFinalizedStateByBlockRoot(blockAndState.getRoot()))
                .describedAs("State by block root at slot %s", blockAndState.getSlot())
                .isCompletedWithOptionalContaining(blockAndState.getState());

            assertThatSafeFuture(
                    storageQueryChannel.getFinalizedSlotByStateRoot(blockAndState.getStateRoot()))
                .describedAs("Finalized slot by state root at slot %s", blockAndState.getSlot())
                .isCompletedWithOptionalContaining(blockAndState.getSlot());
          }
        });

    // After the update is applied only the latest finalized checkpoint is available as hot
    assertThatSafeFuture(
            storageQueryChannel.getHotBlocksByRoot(
                Set.of(genesisBlock.getRoot(), newFinalizedBlock.getRoot())))
        .isCompletedWithValue(Map.of(newFinalizedBlock.getRoot(), newFinalizedBlock));
  }

  @Test
  void getSlotAndBlockRootByStateRoot_shouldIncludePreviousFinalizedCheckpoint() {
    final UInt64 lastFinalizedSlot = spec.computeStartSlotAtEpoch(UInt64.valueOf(2));
    chainUpdater.updateBestBlock(chainUpdater.advanceChainUntil(lastFinalizedSlot));

    chainUpdater.finalizeCurrentChain();

    delayChannel.completeAllActions();

    final Checkpoint priorFinalizedCheckpoint =
        storage.recentChainData().getFinalizedCheckpoint().orElseThrow();
    final SignedBeaconBlock priorFinalizedCheckpointBlock =
        safeJoin(storageQueryChannel.getBlockByBlockRoot(priorFinalizedCheckpoint.getRoot()))
            .orElseThrow();

    chainUpdater.finalizeCurrentChain();

    assertBeforeAndAfter(
        () ->
            assertThatSafeFuture(
                    storageQueryChannel.getSlotAndBlockRootByStateRoot(
                        priorFinalizedCheckpointBlock.getStateRoot()))
                .isCompletedWithOptionalContaining(
                    new SlotAndBlockRoot(
                        priorFinalizedCheckpointBlock.getSlot(),
                        priorFinalizedCheckpointBlock.getRoot())));
  }

  @Test
  void getLatestFinalizedBlockAtSlot_shouldHandleSkipSlotsCorrectly() {
    final UInt64 lastFinalizedSlot = spec.computeStartSlotAtEpoch(UInt64.valueOf(3));
    chainUpdater.advanceChainUntil(5);
    // Skip slots 6, 7 and 8
    chainUpdater.advanceChain(9);
    final SignedBlockAndState newHead = chainUpdater.advanceChainUntil(lastFinalizedSlot);
    chainUpdater.updateBestBlock(newHead);

    chainUpdater.finalizeCurrentChain();

    assertBeforeAndAfter(
        () -> {
          final SignedBeaconBlock block4 = storage.chainBuilder().getBlockAtSlot(4);
          final SignedBeaconBlock block5 = storage.chainBuilder().getBlockAtSlot(5);
          final SignedBeaconBlock block9 = storage.chainBuilder().getBlockAtSlot(9);
          assertThatSafeFuture(storageQueryChannel.getLatestFinalizedBlockAtSlot(UInt64.valueOf(4)))
              .isCompletedWithOptionalContaining(block4);
          assertThatSafeFuture(storageQueryChannel.getLatestFinalizedBlockAtSlot(UInt64.valueOf(5)))
              .isCompletedWithOptionalContaining(block5);
          assertThatSafeFuture(storageQueryChannel.getLatestFinalizedBlockAtSlot(UInt64.valueOf(6)))
              .isCompletedWithOptionalContaining(block5);
          assertThatSafeFuture(storageQueryChannel.getLatestFinalizedBlockAtSlot(UInt64.valueOf(7)))
              .isCompletedWithOptionalContaining(block5);
          assertThatSafeFuture(storageQueryChannel.getLatestFinalizedBlockAtSlot(UInt64.valueOf(8)))
              .isCompletedWithOptionalContaining(block5);
          assertThatSafeFuture(storageQueryChannel.getLatestFinalizedBlockAtSlot(UInt64.valueOf(9)))
              .isCompletedWithOptionalContaining(block9);
        });
  }

  @Test
  void getLatestFinalizedStateAtSlot_shouldHandleSkipSlotsCorrectly() {
    final UInt64 lastFinalizedSlot = spec.computeStartSlotAtEpoch(UInt64.valueOf(3));
    chainUpdater.advanceChainUntil(5);
    // Skip slots 6, 7 and 8
    chainUpdater.advanceChain(9);
    final SignedBlockAndState newHead = chainUpdater.advanceChainUntil(lastFinalizedSlot);
    chainUpdater.updateBestBlock(newHead);

    chainUpdater.finalizeCurrentChain();

    assertBeforeAndAfter(
        () -> {
          final BeaconState state4 = storage.chainBuilder().getStateAtSlot(4);
          final BeaconState state5 = storage.chainBuilder().getStateAtSlot(5);
          final BeaconState state9 = storage.chainBuilder().getStateAtSlot(9);
          assertThatSafeFuture(storageQueryChannel.getLatestFinalizedStateAtSlot(UInt64.valueOf(4)))
              .isCompletedWithOptionalContaining(state4);
          assertThatSafeFuture(storageQueryChannel.getLatestFinalizedStateAtSlot(UInt64.valueOf(5)))
              .isCompletedWithOptionalContaining(state5);
          assertThatSafeFuture(storageQueryChannel.getLatestFinalizedStateAtSlot(UInt64.valueOf(6)))
              .isCompletedWithOptionalContaining(state5);
          assertThatSafeFuture(storageQueryChannel.getLatestFinalizedStateAtSlot(UInt64.valueOf(7)))
              .isCompletedWithOptionalContaining(state5);
          assertThatSafeFuture(storageQueryChannel.getLatestFinalizedStateAtSlot(UInt64.valueOf(8)))
              .isCompletedWithOptionalContaining(state5);
          assertThatSafeFuture(storageQueryChannel.getLatestFinalizedStateAtSlot(UInt64.valueOf(9)))
              .isCompletedWithOptionalContaining(state9);
        });
  }

  @Test
  void shouldRemoveNonCanonicalBlocks() {}

  @Test
  void shouldBufferFinalizedNonCanonicalBlocks() {
    final UInt64 lastFinalizedSlot = spec.computeStartSlotAtEpoch(UInt64.valueOf(2));
    chainUpdater.advanceChainUntil(4);
    final ChainBuilder forkBuilder = storage.chainBuilder().fork();
    // Fork skips block 5 which makes it different to the canonical fork.
    final SignedBlockAndState forkBlock = forkBuilder.generateBlockAtSlot(6);
    chainUpdater.saveBlock(forkBlock);
    chainUpdater.updateBestBlock(chainUpdater.advanceChainUntil(lastFinalizedSlot));

    chainUpdater.finalizeCurrentChain();

    // Non-canonical blocks won't be available by slot while buffered
    // It would be nice if they are but we don't necessarily have the block data required to buffer
    // them. Given there's no guarantee they were seen that's reasonable.
    // They will still be available by block root
    assertThatSafeFuture(storageQueryChannel.getNonCanonicalBlocksBySlot(UInt64.valueOf(6)))
        .isCompletedWithValue(emptyList());
    assertBeforeAndAfter(
        () ->
            assertThatSafeFuture(storageQueryChannel.getBlockByBlockRoot(forkBlock.getRoot()))
                .isCompletedWithOptionalContaining(forkBlock.getBlock()));
    // Non-canonical blocks should be available when the database write completes
    assertThatSafeFuture(storageQueryChannel.getNonCanonicalBlocksBySlot(UInt64.valueOf(6)))
        .isCompletedWithValue(List.of(forkBlock.getBlock()));
  }

  private void assertBeforeAndAfter(final Runnable assertions) {
    assertThatNoException().describedAs("Before database complete").isThrownBy(assertions::run);
    assertions.run();
    delayChannel.completeAllActions();
    assertThat(((BufferingStorageChannel) storageQueryChannel).getBufferedItemCount())
        .overridingErrorMessage("Buffered items should have been cleaned up, but weren't")
        .isZero();
    assertThatNoException().describedAs("After database complete").isThrownBy(assertions::run);
  }
}
