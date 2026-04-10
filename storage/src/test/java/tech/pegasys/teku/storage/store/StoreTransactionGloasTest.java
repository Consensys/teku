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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.bls.BLSKeyGenerator;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.dataproviders.lookup.EarliestBlobSidecarSlotProvider;
import tech.pegasys.teku.dataproviders.lookup.StateAndBlockSummaryProvider;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.SyncAsyncRunner;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.epbs.SignedExecutionPayloadAndState;
import tech.pegasys.teku.spec.datastructures.state.AnchorPoint;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.generator.ChainBuilder;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;
import tech.pegasys.teku.storage.api.StorageUpdate;
import tech.pegasys.teku.storage.api.StorageUpdateChannel;
import tech.pegasys.teku.storage.api.StoredBlockMetadata;
import tech.pegasys.teku.storage.api.UpdateResult;

public class StoreTransactionGloasTest extends AbstractStoreTest {

  @Override
  protected Spec getSpec() {
    return TestSpecFactory.createMinimalGloas();
  }

  @Test
  public void getExecutionPayloadStateIfAvailable_fromTx() {
    final UpdatableStore store = createGenesisStore();
    final SignedBlockAndState blockAndState = chainBuilder.generateNextBlock();
    final Optional<SignedExecutionPayloadAndState> maybeExecutionPayloadAndState =
        chainBuilder.getExecutionPayloadAndStateAtSlot(blockAndState.getSlot());
    assertThat(maybeExecutionPayloadAndState).isPresent();
    final SignedExecutionPayloadAndState executionPayloadAndState =
        maybeExecutionPayloadAndState.get();
    final UpdatableStore.StoreTransaction tx = store.startTransaction(storageUpdateChannel);
    tx.putExecutionPayloadAndState(
        executionPayloadAndState.executionPayload(), executionPayloadAndState.state());

    final Optional<BeaconState> result =
        tx.getExecutionPayloadStateIfAvailable(blockAndState.getRoot());
    assertThat(result).hasValue(executionPayloadAndState.state());
  }

  @Test
  public void commit_shouldAddBlindedExecutionPayloadEnvelopesToStorageUpdate() {
    final UpdatableStore store = createGenesisStore();
    final SignedBlockAndState blockAndState = chainBuilder.generateNextBlock();
    final SignedExecutionPayloadAndState executionPayloadAndState =
        chainBuilder.getExecutionPayloadAndStateAtSlot(blockAndState.getSlot()).orElseThrow();
    final StorageUpdateChannel channel = mock(StorageUpdateChannel.class);
    when(channel.onStorageUpdate(any())).thenReturn(SafeFuture.completedFuture(UpdateResult.EMPTY));

    final UpdatableStore.StoreTransaction tx = store.startTransaction(channel);
    tx.putBlockAndState(blockAndState, spec.calculateBlockCheckpoints(blockAndState.getState()));
    tx.putExecutionPayloadAndState(
        executionPayloadAndState.executionPayload(), executionPayloadAndState.state());

    assertThat(tx.commit()).isCompleted();
    final ArgumentCaptor<StorageUpdate> captor = ArgumentCaptor.forClass(StorageUpdate.class);
    verify(channel).onStorageUpdate(captor.capture());
    assertThat(captor.getValue().getBlindedExecutionPayloadEnvelopesByBlockRoot())
        .containsEntry(
            blockAndState.getRoot(),
            executionPayloadAndState
                .executionPayload()
                .toSignedBlindedExecutionPayloadEnvelope(
                    SchemaDefinitionsGloas.required(
                        spec.atSlot(executionPayloadAndState.executionPayload().getSlot())
                            .getSchemaDefinitions())));
  }

  @Test
  public void commit_shouldAddAllBlindedExecutionPayloadEnvelopesToStorageUpdate() {
    final UpdatableStore store = createGenesisStore();
    final SignedBlockAndState firstBlockAndState = chainBuilder.generateNextBlock();
    final SignedBlockAndState secondBlockAndState = chainBuilder.generateNextBlock();
    final SignedExecutionPayloadAndState firstExecutionPayloadAndState =
        chainBuilder.getExecutionPayloadAndStateAtSlot(firstBlockAndState.getSlot()).orElseThrow();
    final SignedExecutionPayloadAndState secondExecutionPayloadAndState =
        chainBuilder.getExecutionPayloadAndStateAtSlot(secondBlockAndState.getSlot()).orElseThrow();
    final StorageUpdateChannel channel = mock(StorageUpdateChannel.class);
    when(channel.onStorageUpdate(any())).thenReturn(SafeFuture.completedFuture(UpdateResult.EMPTY));

    final UpdatableStore.StoreTransaction tx = store.startTransaction(channel);
    tx.putBlockAndState(
        firstBlockAndState, spec.calculateBlockCheckpoints(firstBlockAndState.getState()));
    tx.putExecutionPayloadAndState(
        firstExecutionPayloadAndState.executionPayload(), firstExecutionPayloadAndState.state());
    tx.putBlockAndState(
        secondBlockAndState, spec.calculateBlockCheckpoints(secondBlockAndState.getState()));
    tx.putExecutionPayloadAndState(
        secondExecutionPayloadAndState.executionPayload(), secondExecutionPayloadAndState.state());

    assertThat(tx.commit()).isCompleted();
    final ArgumentCaptor<StorageUpdate> captor = ArgumentCaptor.forClass(StorageUpdate.class);
    verify(channel).onStorageUpdate(captor.capture());
    assertThat(captor.getValue().getBlindedExecutionPayloadEnvelopesByBlockRoot())
        .containsOnlyKeys(firstBlockAndState.getRoot(), secondBlockAndState.getRoot());
  }

  @Test
  public void commit_shouldRetainBlindedEnvelopesForBlocksFinalizedInSameTransaction() {
    final List<BLSKeyPair> keys = BLSKeyGenerator.generateKeyPairs(16);
    final ChainBuilder glaosCchainBuilder = ChainBuilder.create(spec, keys);
    final SignedBlockAndState genesis = glaosCchainBuilder.generateGenesis();
    final Checkpoint genesisCheckpoint = glaosCchainBuilder.getCurrentCheckpointForEpoch(0);

    final UpdatableStore store =
        StoreBuilder.create()
            .asyncRunner(SyncAsyncRunner.SYNC_RUNNER)
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

    // Generate blocks spanning 2 epochs so we can finalize epoch 1
    final UInt64 epoch2Slot = spec.computeStartSlotAtEpoch(UInt64.valueOf(2));
    glaosCchainBuilder.generateBlocksUpToSlot(epoch2Slot);

    final StorageUpdateChannel channel = mock(StorageUpdateChannel.class);
    when(channel.onStorageUpdate(any())).thenReturn(SafeFuture.completedFuture(UpdateResult.EMPTY));

    final UpdatableStore.StoreTransaction tx = store.startTransaction(channel);

    // Add all blocks and their execution payloads in the same transaction
    glaosCchainBuilder
        .streamBlocksAndStates(1, glaosCchainBuilder.getLatestSlot().longValue())
        .forEach(
            blockAndState -> {
              tx.putBlockAndState(
                  blockAndState, spec.calculateBlockCheckpoints(blockAndState.getState()));
              glaosCchainBuilder
                  .getExecutionPayloadAndStateAtSlot(blockAndState.getSlot())
                  .ifPresent(
                      epAndState ->
                          tx.putExecutionPayloadAndState(
                              epAndState.executionPayload(), epAndState.state()));
            });

    // Finalize at epoch 1 — blocks before this checkpoint will be pruned from hot
    final Checkpoint finalizedCheckpoint =
        glaosCchainBuilder.getCurrentCheckpointForEpoch(UInt64.valueOf(1));
    tx.setFinalizedCheckpoint(finalizedCheckpoint, false);

    assertThat(tx.commit()).isCompleted();
    final ArgumentCaptor<StorageUpdate> captor = ArgumentCaptor.forClass(StorageUpdate.class);
    verify(channel).onStorageUpdate(captor.capture());
    final StorageUpdate storageUpdate = captor.getValue();

    // Finalized blocks were pruned from hot, but their envelopes must still be present
    final SignedBlockAndState finalizedBlockAndState =
        glaosCchainBuilder.getBlockAndState(finalizedCheckpoint.getRoot()).orElseThrow();
    glaosCchainBuilder
        .streamBlocksAndStates(1, finalizedBlockAndState.getSlot().longValue())
        .forEach(
            blockAndState ->
                assertThat(storageUpdate.getBlindedExecutionPayloadEnvelopesByBlockRoot())
                    .describedAs(
                        "Envelope for finalized block at slot %s should be present",
                        blockAndState.getSlot())
                    .containsKey(blockAndState.getRoot()));
  }
}
