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

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.ethereum.pow.api.DepositTreeSnapshot;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.epbs.SignedExecutionPayloadAndState;
import tech.pegasys.teku.spec.datastructures.state.AnchorPoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;
import tech.pegasys.teku.storage.api.StorageUpdate;
import tech.pegasys.teku.storage.api.StorageUpdateChannel;
import tech.pegasys.teku.storage.api.UpdateResult;
import tech.pegasys.teku.storage.api.WeakSubjectivityUpdate;

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
  public void commit_shouldAddHotBlindedExecutionPayloadEnvelopesToStorageUpdate() {
    final UpdatableStore store = createGenesisStore();
    final SignedBlockAndState blockAndState = chainBuilder.generateNextBlock();
    final SignedExecutionPayloadAndState executionPayloadAndState =
        chainBuilder.getExecutionPayloadAndStateAtSlot(blockAndState.getSlot()).orElseThrow();
    final CapturingStorageUpdateChannel capturingStorageUpdateChannel =
        new CapturingStorageUpdateChannel();

    final UpdatableStore.StoreTransaction tx =
        store.startTransaction(capturingStorageUpdateChannel);
    tx.putBlockAndState(blockAndState, spec.calculateBlockCheckpoints(blockAndState.getState()));
    tx.putExecutionPayloadAndState(
        executionPayloadAndState.executionPayload(), executionPayloadAndState.state());

    assertThat(tx.commit()).isCompleted();
    assertThat(capturingStorageUpdateChannel.getLastStorageUpdate()).isNotNull();
    assertThat(
            capturingStorageUpdateChannel
                .getLastStorageUpdate()
                .getBlindedExecutionPayloadEnvelopesByBlockRoot())
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
  public void commit_shouldAddAllHotBlindedExecutionPayloadEnvelopesToStorageUpdate() {
    final UpdatableStore store = createGenesisStore();
    final SignedBlockAndState firstBlockAndState = chainBuilder.generateNextBlock();
    final SignedBlockAndState secondBlockAndState = chainBuilder.generateNextBlock();
    final SignedExecutionPayloadAndState firstExecutionPayloadAndState =
        chainBuilder.getExecutionPayloadAndStateAtSlot(firstBlockAndState.getSlot()).orElseThrow();
    final SignedExecutionPayloadAndState secondExecutionPayloadAndState =
        chainBuilder.getExecutionPayloadAndStateAtSlot(secondBlockAndState.getSlot()).orElseThrow();
    final CapturingStorageUpdateChannel capturingStorageUpdateChannel =
        new CapturingStorageUpdateChannel();

    final UpdatableStore.StoreTransaction tx =
        store.startTransaction(capturingStorageUpdateChannel);
    tx.putBlockAndState(
        firstBlockAndState, spec.calculateBlockCheckpoints(firstBlockAndState.getState()));
    tx.putExecutionPayloadAndState(
        firstExecutionPayloadAndState.executionPayload(), firstExecutionPayloadAndState.state());
    tx.putBlockAndState(
        secondBlockAndState, spec.calculateBlockCheckpoints(secondBlockAndState.getState()));
    tx.putExecutionPayloadAndState(
        secondExecutionPayloadAndState.executionPayload(), secondExecutionPayloadAndState.state());

    assertThat(tx.commit()).isCompleted();
    assertThat(capturingStorageUpdateChannel.getLastStorageUpdate()).isNotNull();
    assertThat(
            capturingStorageUpdateChannel
                .getLastStorageUpdate()
                .getBlindedExecutionPayloadEnvelopesByBlockRoot())
        .containsOnlyKeys(firstBlockAndState.getRoot(), secondBlockAndState.getRoot());
  }

  private static class CapturingStorageUpdateChannel implements StorageUpdateChannel {
    private StorageUpdate lastStorageUpdate;

    @Override
    public SafeFuture<UpdateResult> onStorageUpdate(final StorageUpdate event) {
      lastStorageUpdate = event;
      return SafeFuture.completedFuture(UpdateResult.EMPTY);
    }

    @Override
    public SafeFuture<Void> onFinalizedBlocks(
        final Collection<SignedBeaconBlock> finalizedBlocks,
        final Map<SlotAndBlockRoot, List<BlobSidecar>> blobSidecarsBySlot,
        final Optional<UInt64> maybeEarliestBlobSidecarSlot) {
      return SafeFuture.COMPLETE;
    }

    @Override
    public SafeFuture<Void> onReconstructedFinalizedState(
        final BeaconState finalizedState, final Bytes32 blockRoot) {
      return SafeFuture.COMPLETE;
    }

    @Override
    public SafeFuture<Void> onWeakSubjectivityUpdate(
        final WeakSubjectivityUpdate weakSubjectivityUpdate) {
      return SafeFuture.COMPLETE;
    }

    @Override
    public SafeFuture<Void> onFinalizedDepositSnapshot(
        final DepositTreeSnapshot depositTreeSnapshot) {
      return SafeFuture.COMPLETE;
    }

    @Override
    public void onChainInitialized(final AnchorPoint initialAnchor) {}

    public StorageUpdate getLastStorageUpdate() {
      return lastStorageUpdate;
    }
  }
}
