/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.storage.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SyncAsyncRunner.SYNC_RUNNER;
import static tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoicePayloadStatus.PAYLOAD_STATUS_PENDING;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReadWriteLock;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.dataproviders.lookup.EarliestBlobSidecarSlotProvider;
import tech.pegasys.teku.dataproviders.lookup.StateAndBlockSummaryProvider;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.BlockAndCheckpoints;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.forkchoice.ProtoNodeData;
import tech.pegasys.teku.spec.datastructures.state.AnchorPoint;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.generator.ChainBuilder;
import tech.pegasys.teku.storage.api.StorageUpdate;
import tech.pegasys.teku.storage.api.StorageUpdateChannel;
import tech.pegasys.teku.storage.api.StoredBlockMetadata;
import tech.pegasys.teku.storage.api.UpdateResult;
import tech.pegasys.teku.storage.protoarray.ForkChoiceStrategy;

class StoreTransactionUpdatesFactoryTest {

  private final Spec spec = TestSpecFactory.createMinimalDeneb();
  private final ChainBuilder chainBuilder = ChainBuilder.create(spec);

  @Test
  void finalizedGloasBoundaryShouldUsePendingExecutionContext() {
    final Spec gloasSpec = TestSpecFactory.createMinimalGloas();
    final ChainBuilder gloasChainBuilder = ChainBuilder.create(gloasSpec);
    final SignedBlockAndState genesis = gloasChainBuilder.generateGenesis();
    final Checkpoint genesisCheckpoint = gloasChainBuilder.getCurrentCheckpointForEpoch(0);
    final UInt64 finalizedSlot = gloasSpec.computeStartSlotAtEpoch(UInt64.ONE);
    gloasChainBuilder.generateBlocksUpToSlot(finalizedSlot);
    final SignedBlockAndState finalizedBlock = gloasChainBuilder.getLatestBlockAndState();
    final Checkpoint finalizedCheckpoint = new Checkpoint(UInt64.ONE, finalizedBlock.getRoot());
    final AnchorPoint latestFinalized =
        AnchorPoint.create(gloasSpec, finalizedCheckpoint, finalizedBlock);

    final Store store = mock(Store.class);
    final ForkChoiceStrategy forkChoiceStrategy = mock(ForkChoiceStrategy.class);
    final ProtoNodeData pendingNode = mock(ProtoNodeData.class);
    final UInt64 executionBlockNumber = UInt64.valueOf(123);
    final UInt64 executionGasLimit = UInt64.valueOf(60_000_000);
    when(pendingNode.getExecutionBlockNumber()).thenReturn(executionBlockNumber);
    when(pendingNode.getExecutionGasLimit()).thenReturn(executionGasLimit);
    when(store.getFinalizedCheckpoint()).thenReturn(genesisCheckpoint);
    when(store.getLatestFinalized())
        .thenReturn(AnchorPoint.create(gloasSpec, genesisCheckpoint, genesis));
    when(store.containsBlock(finalizedBlock.getRoot())).thenReturn(false);
    when(store.containsBlock(finalizedBlock.getParentRoot())).thenReturn(true);
    when(store.getForkChoiceStrategy()).thenReturn(forkChoiceStrategy);
    when(forkChoiceStrategy.getBlockData(finalizedBlock.getRoot(), PAYLOAD_STATUS_PENDING))
        .thenReturn(Optional.of(pendingNode));

    final StoreTransaction tx =
        new StoreTransaction(
            gloasSpec,
            store,
            mock(ReadWriteLock.class),
            mock(StorageUpdateChannel.class),
            UpdatableStore.StoreUpdateHandler.NOOP);
    tx.putBlockAndState(
        finalizedBlock, gloasSpec.calculateBlockCheckpoints(finalizedBlock.getState()));
    tx.setFinalizedCheckpoint(finalizedCheckpoint, false);

    final StoreTransactionUpdates updates =
        StoreTransactionUpdatesFactory.create(gloasSpec, store, tx, latestFinalized);
    updates.applyToStore(store, UpdateResult.EMPTY);

    @SuppressWarnings("unchecked")
    final ArgumentCaptor<Optional<BlockAndCheckpoints>> boundaryCaptor =
        ArgumentCaptor.forClass(Optional.class);
    verify(forkChoiceStrategy)
        .applyUpdate(any(), any(), any(), any(), any(), boundaryCaptor.capture());
    final BlockAndCheckpoints boundaryBlock = boundaryCaptor.getValue().orElseThrow();
    assertThat(boundaryBlock.getExecutionBlockNumber()).contains(executionBlockNumber);
    assertThat(boundaryBlock.getExecutionGasLimit()).contains(executionGasLimit);
  }

  @Test
  void hotBlocksShouldNotContainPrunedRoots() {
    final SignedBlockAndState genesis = chainBuilder.generateGenesis();
    final Checkpoint genesisCheckpoint = chainBuilder.getCurrentCheckpointForEpoch(0);

    final UpdatableStore store =
        StoreBuilder.create()
            .asyncRunner(SYNC_RUNNER)
            .metricsSystem(new StubMetricsSystem())
            .specProvider(spec)
            .blockProvider(roots -> SafeFuture.completedFuture(Collections.emptyMap()))
            .earliestBlobSidecarSlotProvider(EarliestBlobSidecarSlotProvider.NOOP)
            .stateProvider(StateAndBlockSummaryProvider.NOOP)
            .anchor(Optional.empty())
            .genesisTime(genesis.getState().getGenesisTime())
            .time(genesis.getState().getGenesisTime())
            .latestFinalized(AnchorPoint.create(spec, genesisCheckpoint, genesis))
            .justifiedCheckpoint(genesisCheckpoint)
            .bestJustifiedCheckpoint(genesisCheckpoint)
            .blockInformation(
                Map.of(
                    genesis.getRoot(),
                    new StoredBlockMetadata(
                        genesis.getSlot(),
                        genesis.getRoot(),
                        genesis.getParentRoot(),
                        genesis.getStateRoot(),
                        genesis.getExecutionBlockNumber(),
                        genesis.getExecutionBlockHash(),
                        Optional.of(spec.calculateBlockCheckpoints(genesis.getState())))))
            .storeConfig(StoreConfig.createDefault())
            .votes(Collections.emptyMap())
            .latestCanonicalBlockRoot(Optional.empty())
            .build();

    // Generate blocks for multiple epochs to trigger finalization
    final UInt64 epoch3Slot = spec.computeStartSlotAtEpoch(UInt64.valueOf(3));
    chainBuilder.generateBlocksUpToSlot(epoch3Slot);

    final StorageUpdateChannel channel = mock(StorageUpdateChannel.class);
    when(channel.onStorageUpdate(any())).thenReturn(SafeFuture.completedFuture(UpdateResult.EMPTY));

    // Start a transaction and add all blocks
    final UpdatableStore.StoreTransaction tx = store.startTransaction(channel);
    chainBuilder
        .streamBlocksAndStates(1, chainBuilder.getLatestSlot().longValue())
        .forEach(
            blockAndState ->
                tx.putBlockAndState(
                    blockAndState, spec.calculateBlockCheckpoints(blockAndState.getState())));

    // Set finalized checkpoint to epoch 2
    final Checkpoint finalizedCheckpoint =
        chainBuilder.getCurrentCheckpointForEpoch(UInt64.valueOf(2));
    tx.setFinalizedCheckpoint(finalizedCheckpoint, false);

    tx.commit().join();

    // Verify we captured a storage update
    final ArgumentCaptor<StorageUpdate> captor = ArgumentCaptor.forClass(StorageUpdate.class);
    verify(channel).onStorageUpdate(captor.capture());
    final StorageUpdate storageUpdate = captor.getValue();

    // Get the finalized block
    final SignedBlockAndState finalizedBlockAndState =
        chainBuilder.getBlockAndState(finalizedCheckpoint.getRoot()).orElseThrow();

    // Collect all block roots that should have been pruned (blocks at or before finalized slot,
    // except the finalized block itself)
    final UInt64 finalizedSlot = finalizedBlockAndState.getSlot();
    final List<Bytes32> rootsThatShouldBePruned =
        chainBuilder
            .streamBlocksAndStates(1, finalizedSlot.longValue())
            .map(SignedBlockAndState::getRoot)
            .filter(root -> !root.equals(finalizedBlockAndState.getRoot()))
            .toList();

    // Verify that the pruned roots are really pruned
    assertThat(rootsThatShouldBePruned).isNotEmpty();
    for (final Bytes32 prunedRoot : rootsThatShouldBePruned) {
      assertThat(storageUpdate.getHotBlocks())
          .describedAs("Hot blocks should not contain pruned root %s", prunedRoot)
          .doesNotContainKey(prunedRoot);
    }

    // Also verify the pruned roots are actually recorded in the deleted hot blocks
    assertThat(storageUpdate.getDeletedHotBlocks().keySet()).containsAll(rootsThatShouldBePruned);
  }
}
