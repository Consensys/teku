/*
 * Copyright Consensys Software Inc., 2025
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.SafeFutureAssert;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.metadata.BlockAndMetaData;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.CommitteeAssignment;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.util.SlotAndBlockRootAndBlobIndex;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.api.LateBlockReorgPreparationHandler;
import tech.pegasys.teku.storage.api.StorageQueryChannel;
import tech.pegasys.teku.storage.protoarray.ForkChoiceStrategy;
import tech.pegasys.teku.storage.store.UpdatableStore;

/** Note: Most tests should be added to the integration-test directory */
class CombinedChainDataClientTest {
  private final Spec spec = TestSpecFactory.createMinimalDeneb();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final RecentChainData recentChainData = mock(RecentChainData.class);
  private final ForkChoiceStrategy forkChoiceStrategy = mock(ForkChoiceStrategy.class);
  private final StorageQueryChannel historicalChainData = mock(StorageQueryChannel.class);
  private final LateBlockReorgPreparationHandler lateBlockReorgPreparationHandler =
      mock(LateBlockReorgPreparationHandler.class);

  private final UpdatableStore store = mock(UpdatableStore.class);
  private final CombinedChainDataClient client =
      new CombinedChainDataClient(
          recentChainData, historicalChainData, spec, lateBlockReorgPreparationHandler, false);
  private final ChainHead chainHead = mock(ChainHead.class);

  final List<SignedBeaconBlock> nonCanonicalBlocks = new ArrayList<>();
  final SignedBeaconBlock firstBlock = dataStructureUtil.randomSignedBeaconBlock(1);
  final SignedBeaconBlock secondBlock = dataStructureUtil.randomSignedBeaconBlock(1);
  final BlobSidecar sidecar = dataStructureUtil.randomBlobSidecar();
  final BlobSidecar sidecar2 = dataStructureUtil.randomBlobSidecar();

  @BeforeEach
  void setUp() {
    when(recentChainData.getForkChoiceStrategy()).thenReturn(Optional.of(forkChoiceStrategy));
    when(recentChainData.getFinalizedEpoch()).thenReturn(UInt64.ZERO);
    when(forkChoiceStrategy.isOptimistic(any())).thenReturn(Optional.of(true));
    when(chainHead.isOptimistic()).thenReturn(false);
    when(chainHead.getSlot()).thenReturn(UInt64.valueOf(8428924L));
    when(lateBlockReorgPreparationHandler.onLateBlockReorgPreparation(any(), any()))
        .thenReturn(SafeFuture.COMPLETE);
  }

  @Test
  public void getCommitteesFromStateWithCache_shouldReturnCommitteeAssignments() {
    BeaconState state = dataStructureUtil.randomBeaconState();
    List<CommitteeAssignment> data =
        client.getCommitteesFromState(state, spec.getCurrentEpoch(state));
    assertThat(data.size()).isEqualTo(spec.getSlotsPerEpoch(state.getSlot()));
  }

  @Test
  public void mergeNonCanonicalAndCanonicalBlocks_shouldAddCanonicalBlockIfPresent() {
    nonCanonicalBlocks.add(firstBlock);
    assertThat(
            client
                .mergeNonCanonicalAndCanonicalBlocks(
                    nonCanonicalBlocks, chainHead, Optional.of(secondBlock))
                .stream()
                .map(BlockAndMetaData::getData))
        .containsExactlyInAnyOrder(firstBlock, secondBlock);
  }

  @Test
  public void mergeNonCanonicalAndCanonicalBlocks_shouldReturnNonCanonicalOnly() {
    nonCanonicalBlocks.add(firstBlock);
    nonCanonicalBlocks.add(secondBlock);

    assertThat(
            client
                .mergeNonCanonicalAndCanonicalBlocks(
                    nonCanonicalBlocks, chainHead, Optional.empty())
                .stream()
                .map(BlockAndMetaData::getData))
        .containsExactlyInAnyOrder(firstBlock, secondBlock);
  }

  @Test
  public void mergeNonCanonicalAndCanonicalBlocks_shouldReturnCanonicalOnly() {
    assertThat(
            client
                .mergeNonCanonicalAndCanonicalBlocks(
                    nonCanonicalBlocks, chainHead, Optional.of(secondBlock))
                .stream()
                .map(BlockAndMetaData::getData))
        .containsExactlyInAnyOrder(secondBlock);
  }

  @Test
  void isOptimistic_shouldReturnOptimisticStatusOfKnownBlocks() {
    when(forkChoiceStrategy.isOptimistic(firstBlock.getRoot())).thenReturn(Optional.of(true));
    when(forkChoiceStrategy.isOptimistic(secondBlock.getRoot())).thenReturn(Optional.of(false));

    assertThat(client.isOptimisticBlock(firstBlock.getRoot())).isTrue();
    assertThat(client.isOptimisticBlock(secondBlock.getRoot())).isFalse();
  }

  @Test
  void isOptimistic_shouldReturnOptimisticStatusOfFinalizedBlockForUnknownBlocks() {
    final Checkpoint finalized = dataStructureUtil.randomCheckpoint();
    when(recentChainData.getFinalizedCheckpoint()).thenReturn(Optional.of(finalized));
    when(forkChoiceStrategy.isOptimistic(firstBlock.getRoot())).thenReturn(Optional.empty());
    when(forkChoiceStrategy.isOptimistic(finalized.getRoot())).thenReturn(Optional.of(true));

    assertThat(client.isOptimisticBlock(firstBlock.getRoot())).isTrue();
  }

  @Test
  void getsEarliestAvailableBlobSidecarSlot() {
    when(historicalChainData.getEarliestAvailableBlobSidecarSlot())
        .thenReturn(SafeFuture.completedFuture(Optional.of(UInt64.ONE)));

    final Optional<UInt64> result =
        SafeFutureAssert.safeJoin(client.getEarliestAvailableBlobSidecarSlot());

    assertThat(result).hasValue(UInt64.ONE);
  }

  @Test
  void getStateForBlockProduction_directsToStateAtSlotExact()
      throws ExecutionException, InterruptedException {
    final BeaconState state = dataStructureUtil.randomBeaconState(UInt64.valueOf(2));
    final Optional<Bytes32> recentBlockRoot =
        Optional.of(spec.getBlockRootAtSlot(state, UInt64.ONE));
    final SlotAndBlockRoot slotAndBlockRoot =
        new SlotAndBlockRoot(UInt64.ONE, recentBlockRoot.get());
    final Runnable onLateBlockReorgPreparationCompleted = mock(Runnable.class);
    when(recentChainData.getBlockRootInEffectBySlot(UInt64.ONE)).thenReturn(recentBlockRoot);
    when(recentChainData.getStore()).thenReturn(store);
    when(store.retrieveStateAtSlot(slotAndBlockRoot))
        .thenReturn(SafeFuture.completedFuture(Optional.of(state)));
    final SafeFuture<Optional<BeaconState>> future =
        client.getStateForBlockProduction(UInt64.ONE, false, onLateBlockReorgPreparationCompleted);
    assertThat(future.get()).contains(state);
    verify(onLateBlockReorgPreparationCompleted, never()).run();
    // getStateAtSlotExact
    verify(recentChainData).getBlockRootInEffectBySlot(UInt64.ONE);
    verify(store).retrieveStateAtSlot(slotAndBlockRoot);
  }

  @Test
  void getStateForBlockProduction_whenEnabledAndHaveNoChainHead()
      throws ExecutionException, InterruptedException {
    final BeaconState state = dataStructureUtil.randomBeaconState(UInt64.valueOf(2));
    final Optional<Bytes32> recentBlockRoot =
        Optional.of(spec.getBlockRootAtSlot(state, UInt64.ONE));
    final SlotAndBlockRoot slotAndBlockRoot =
        new SlotAndBlockRoot(UInt64.ONE, recentBlockRoot.get());
    final Runnable onLateBlockReorgPreparationCompleted = mock(Runnable.class);
    when(recentChainData.getStore()).thenReturn(store);

    when(recentChainData.getBestBlockRoot()).thenReturn(Optional.empty());
    when(recentChainData.getBlockRootInEffectBySlot(UInt64.ONE)).thenReturn(recentBlockRoot);
    when(store.retrieveStateAtSlot(slotAndBlockRoot))
        .thenReturn(SafeFuture.completedFuture(Optional.of(state)));

    final SafeFuture<Optional<BeaconState>> future =
        client.getStateForBlockProduction(UInt64.ONE, true, onLateBlockReorgPreparationCompleted);
    assertThat(future.get()).contains(state);
    verify(onLateBlockReorgPreparationCompleted, never()).run();
    // getStateAtSlotExact
    verify(recentChainData).getBlockRootInEffectBySlot(UInt64.ONE);
    verify(store).retrieveStateAtSlot(slotAndBlockRoot);
  }

  @Test
  void getStateForBlockProduction_whenEnabledAndHeadChainMatches()
      throws ExecutionException, InterruptedException {
    final BeaconState state = dataStructureUtil.randomBeaconState(UInt64.valueOf(2));

    final ChainHead chainHead = mock(ChainHead.class);
    final Bytes32 recentBlockRoot = spec.getBlockRootAtSlot(state, UInt64.ONE);
    final Runnable onLateBlockReorgPreparationCompleted = mock(Runnable.class);

    when(recentChainData.getChainHead()).thenReturn(Optional.of(chainHead));
    when(chainHead.getRoot()).thenReturn(recentBlockRoot);

    final SlotAndBlockRoot slotAndBlockRoot = new SlotAndBlockRoot(UInt64.ONE, recentBlockRoot);
    when(recentChainData.getStore()).thenReturn(store);

    when(recentChainData.getProposerHead(any(), any())).thenReturn(recentBlockRoot);
    when(recentChainData.getBlockRootInEffectBySlot(UInt64.ONE))
        .thenReturn(Optional.of(recentBlockRoot));
    when(store.retrieveStateAtSlot(slotAndBlockRoot))
        .thenReturn(SafeFuture.completedFuture(Optional.of(state)));

    final SafeFuture<Optional<BeaconState>> future =
        client.getStateForBlockProduction(UInt64.ONE, true, onLateBlockReorgPreparationCompleted);
    assertThat(future.get()).contains(state);
    verify(onLateBlockReorgPreparationCompleted, never()).run();
    // getStateAtSlotExact
    verify(recentChainData).getBlockRootInEffectBySlot(UInt64.ONE);
    verify(store).retrieveStateAtSlot(slotAndBlockRoot);
  }

  @Test
  void getStateForBlockProduction_whenEnabledAndChainHeadDifferent()
      throws ExecutionException, InterruptedException {
    final BeaconState state = dataStructureUtil.randomBeaconState(UInt64.valueOf(2));
    final Bytes32 proposerHead = dataStructureUtil.randomBytes32();
    final BeaconState proposerState = dataStructureUtil.randomBeaconState(UInt64.ONE);
    final ChainHead chainHead = mock(ChainHead.class);
    final Bytes32 recentBlockRoot = spec.getBlockRootAtSlot(state, UInt64.ONE);
    final SlotAndBlockRoot slotAndBlockRoot = new SlotAndBlockRoot(UInt64.ONE, recentBlockRoot);
    final Runnable onLateBlockReorgPreparationCompleted = mock(Runnable.class);
    when(recentChainData.getStore()).thenReturn(store);

    when(recentChainData.getChainHead()).thenReturn(Optional.of(chainHead));
    when(chainHead.getRoot()).thenReturn(recentBlockRoot);
    when(recentChainData.getProposerHead(any(), any())).thenReturn(proposerHead);
    when(recentChainData.getSlotForBlockRoot(proposerHead)).thenReturn(Optional.of(UInt64.ZERO));
    when(recentChainData.getBlockRootInEffectBySlot(UInt64.ONE))
        .thenReturn(Optional.of(recentBlockRoot));
    when(store.retrieveBlockState(proposerHead))
        .thenReturn(SafeFuture.completedFuture(Optional.of(proposerState)));

    final SafeFuture<Void> lateBlockReorgPreparationFuture = new SafeFuture<>();

    when(lateBlockReorgPreparationHandler.onLateBlockReorgPreparation(UInt64.ZERO, recentBlockRoot))
        .thenReturn(lateBlockReorgPreparationFuture);

    when(store.retrieveStateAtSlot(slotAndBlockRoot))
        .thenReturn(SafeFuture.completedFuture(Optional.of(state)));

    final SafeFuture<Optional<BeaconState>> future =
        client.getStateForBlockProduction(UInt64.ONE, true, onLateBlockReorgPreparationCompleted);

    // should be pending until late block reorg preparation completes
    assertThat(future).isNotDone();
    verify(onLateBlockReorgPreparationCompleted, never()).run();
    // should retrieve state while waiting
    verify(store).retrieveBlockState(proposerHead);

    lateBlockReorgPreparationFuture.complete(null);
    verify(onLateBlockReorgPreparationCompleted).run();

    assertThat(future.get()).contains(proposerState);

    verify(store, never()).retrieveStateAtSlot(slotAndBlockRoot);
  }

  @Test
  void getsBlobSidecarBySlotAndBlockRootAndBlobIndex() {
    final SlotAndBlockRootAndBlobIndex correctKey =
        new SlotAndBlockRootAndBlobIndex(
            sidecar.getSlot(), sidecar.getBlockRoot(), sidecar.getIndex());
    final Bytes32 blockRoot = dataStructureUtil.randomBytes32();
    final SlotAndBlockRootAndBlobIndex incorrectKey =
        new SlotAndBlockRootAndBlobIndex(sidecar.getSlot(), blockRoot, sidecar.getIndex());
    setupGetBlobSidecar(correctKey, sidecar);

    final Optional<BlobSidecar> correctResult =
        SafeFutureAssert.safeJoin(client.getBlobSidecarByKey(correctKey));
    assertThat(correctResult).hasValue(sidecar);

    final Optional<BlobSidecar> incorrectResult =
        SafeFutureAssert.safeJoin(client.getBlobSidecarByKey(incorrectKey));
    assertThat(incorrectResult).isEmpty();
  }

  @Test
  void getsBlobSidecarBySlotAndBlobIndex() {
    final SlotAndBlockRootAndBlobIndex key =
        new SlotAndBlockRootAndBlobIndex(
            sidecar.getSlot(), sidecar.getBlockRoot(), sidecar.getIndex());
    setupGetBlobSidecar(key, sidecar);
    setupGetSlotForBlockRoot(sidecar.getBlockRoot(), sidecar.getSlot());
    setupGetRecentlyValidatedBlobSidecar(sidecar2.getBlockRoot(), sidecar2.getIndex(), sidecar2);

    final Optional<BlobSidecar> result =
        SafeFutureAssert.safeJoin(
            client.getBlobSidecarByBlockRootAndIndex(sidecar.getBlockRoot(), sidecar.getIndex()));
    assertThat(result).hasValue(sidecar);

    final Optional<BlobSidecar> result2 =
        SafeFutureAssert.safeJoin(
            client.getBlobSidecarByBlockRootAndIndex(sidecar2.getBlockRoot(), sidecar2.getIndex()));
    assertThat(result2).hasValue(sidecar2);

    final Bytes32 blockRoot = setupGetBlockByBlockRoot(sidecar.getSlot().plus(1));
    final Optional<BlobSidecar> incorrectResult =
        SafeFutureAssert.safeJoin(
            client.getBlobSidecarByBlockRootAndIndex(blockRoot, sidecar.getIndex()));
    assertThat(incorrectResult).isEmpty();
  }

  @Test
  void getBestFinalizedState_fetchesFinalizedState()
      throws ExecutionException, InterruptedException {
    final Checkpoint finalized = dataStructureUtil.randomCheckpoint(1024);
    when(recentChainData.getStore()).thenReturn(store);
    final UInt64 slot = spec.computeStartSlotAtEpoch(UInt64.valueOf(1024));
    final BeaconState state = dataStructureUtil.randomBeaconState(slot);
    when(store.getFinalizedCheckpoint()).thenReturn(finalized);
    when(recentChainData.getBlockRootInEffectBySlot(any()))
        .thenReturn(Optional.of(state.getLatestBlockHeader().getBodyRoot()));
    when(store.retrieveStateAtSlot(any()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(state)));

    final SafeFuture<Optional<BeaconState>> maybeFinalizedState = client.getBestFinalizedState();
    // don't care about result, checking we called the right function with the right slot

    assertThat(maybeFinalizedState.get().orElseThrow().getSlot()).isEqualTo(slot);
    verify(recentChainData, times(1)).getBlockRootInEffectBySlot(slot);
  }

  @Test
  void getBestFinalizedState_gracefulIfNotReady() throws ExecutionException, InterruptedException {
    when(recentChainData.getStore()).thenReturn(null);
    final SafeFuture<Optional<BeaconState>> maybeFinalizedState = client.getBestFinalizedState();
    assertThat(maybeFinalizedState.get()).isEmpty();
  }

  @Test
  @SuppressWarnings("FutureReturnValueIgnored")
  void getEarliestAvailableDataColumnSlot_WithFallback_shouldRespectConfig() {
    client.getEarliestAvailableDataColumnSlotWithFallback();

    verify(historicalChainData).getEarliestDataColumnSidecarSlot();

    final CombinedChainDataClient clientWithEarliestAvailableDataColumnSlotSupport =
        new CombinedChainDataClient(
            recentChainData, historicalChainData, spec, lateBlockReorgPreparationHandler, true);

    clientWithEarliestAvailableDataColumnSlotSupport
        .getEarliestAvailableDataColumnSlotWithFallback();
    verify(historicalChainData).getEarliestAvailableDataColumnSlot();

    verifyNoMoreInteractions(historicalChainData);
  }

  private void setupGetBlobSidecar(
      final SlotAndBlockRootAndBlobIndex key, final BlobSidecar result) {
    when(historicalChainData.getBlobSidecar(any()))
        .thenAnswer(
            args -> {
              final SlotAndBlockRootAndBlobIndex argKey = args.getArgument(0);
              if (argKey.equals(key)) {
                return SafeFuture.completedFuture(Optional.of(result));
              } else {
                return SafeFuture.completedFuture(Optional.empty());
              }
            });
  }

  private void setupGetSlotForBlockRoot(final Bytes32 blockRoot, final UInt64 slot) {
    when(recentChainData.getSlotForBlockRoot(any()))
        .thenAnswer(
            args -> {
              final Bytes32 argRoot = args.getArgument(0);
              if (argRoot.equals(blockRoot)) {
                return Optional.of(slot);
              } else {
                return Optional.empty();
              }
            });
  }

  private void setupGetRecentlyValidatedBlobSidecar(
      final Bytes32 blockRoot, final UInt64 index, final BlobSidecar result) {
    when(recentChainData.getRecentlyValidatedBlobSidecar(any(), any()))
        .thenAnswer(
            args -> {
              final Bytes32 blockRootArg = args.getArgument(0);
              final UInt64 indexArg = args.getArgument(1);
              if (blockRootArg.equals(blockRoot) && indexArg.equals(index)) {
                return Optional.of(result);
              } else {
                return Optional.empty();
              }
            });
  }

  private Bytes32 setupGetBlockByBlockRoot(final UInt64 slot) {
    final SignedBeaconBlock signedBeaconBlock = dataStructureUtil.randomSignedBeaconBlock(slot);
    when(historicalChainData.getBlockByBlockRoot(any()))
        .thenAnswer(
            args -> {
              final Bytes32 argRoot = args.getArgument(0);
              if (argRoot.equals(signedBeaconBlock.getRoot())) {
                return SafeFuture.completedFuture(Optional.of(signedBeaconBlock));
              } else {
                return SafeFuture.completedFuture(Optional.empty());
              }
            });
    return signedBeaconBlock.getRoot();
  }
}
