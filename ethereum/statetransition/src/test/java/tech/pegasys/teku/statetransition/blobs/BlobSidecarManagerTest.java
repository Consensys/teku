/*
 * Copyright Consensys Software Inc., 2022
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

package tech.pegasys.teku.statetransition.blobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.logic.versions.deneb.blobs.BlobSidecarsAndValidationResult;
import tech.pegasys.teku.spec.logic.versions.deneb.blobs.BlobSidecarsAvailabilityChecker;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.blobs.BlobSidecarManager.ReceivedBlobSidecarListener;
import tech.pegasys.teku.statetransition.blobs.BlobSidecarManager.RemoteOrigin;
import tech.pegasys.teku.statetransition.blobs.BlobSidecarManagerImpl.ForkChoiceBlobSidecarsAvailabilityCheckerProvider;
import tech.pegasys.teku.statetransition.blobs.BlobSidecarManagerImpl.UnpooledBlockBlobSidecarsTrackerProvider;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoiceBlobSidecarsAvailabilityChecker;
import tech.pegasys.teku.statetransition.util.BlockBlobSidecarsTrackersPoolImpl;
import tech.pegasys.teku.statetransition.util.FutureItems;
import tech.pegasys.teku.statetransition.validation.BlobSidecarGossipValidator;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.storage.client.RecentChainData;

public class BlobSidecarManagerTest {
  private final Spec spec = TestSpecFactory.createMinimalDeneb();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final RecentChainData recentChainData = mock(RecentChainData.class);
  private final BlobSidecarGossipValidator blobSidecarValidator =
      mock(BlobSidecarGossipValidator.class);
  private final BlockBlobSidecarsTrackersPoolImpl blockBlobSidecarsTrackersPool =
      mock(BlockBlobSidecarsTrackersPoolImpl.class);
  private final Map<Bytes32, InternalValidationResult> invalidBlobSidecarRoots = new HashMap<>();

  @SuppressWarnings("unchecked")
  private final FutureItems<BlobSidecar> futureBlobSidecars = mock(FutureItems.class);

  private final ForkChoiceBlobSidecarsAvailabilityCheckerProvider
      forkChoiceBlobSidecarsAvailabilityCheckerProvider =
          mock(ForkChoiceBlobSidecarsAvailabilityCheckerProvider.class);
  private final UnpooledBlockBlobSidecarsTrackerProvider unpooledBlockBlobSidecarsTrackerProvider =
      mock(UnpooledBlockBlobSidecarsTrackerProvider.class);

  private final BlobSidecarManagerImpl blobSidecarManager =
      new BlobSidecarManagerImpl(
          spec,
          recentChainData,
          blockBlobSidecarsTrackersPool,
          blobSidecarValidator,
          futureBlobSidecars,
          invalidBlobSidecarRoots,
          forkChoiceBlobSidecarsAvailabilityCheckerProvider,
          unpooledBlockBlobSidecarsTrackerProvider);

  private final ReceivedBlobSidecarListener receivedBlobSidecarListener =
      mock(ReceivedBlobSidecarListener.class);

  private final BlobSidecar blobSidecar = dataStructureUtil.randomBlobSidecar();
  private final List<BlobSidecar> blobSidecars = List.of(blobSidecar);
  private final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(UInt64.ONE);

  @BeforeEach
  void setUp() {
    when(blobSidecarValidator.validate(any()))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.ACCEPT));
    blobSidecarManager.subscribeToReceivedBlobSidecar(receivedBlobSidecarListener);
  }

  @Test
  void validateAndPrepareForBlockImport_shouldPrepareBlobSidecar() {
    assertThatSafeFuture(
            blobSidecarManager.validateAndPrepareForBlockImport(blobSidecar, Optional.empty()))
        .isCompletedWithValue(InternalValidationResult.ACCEPT);

    verify(blockBlobSidecarsTrackersPool).onNewBlobSidecar(blobSidecar, RemoteOrigin.GOSSIP);
    verify(receivedBlobSidecarListener).onBlobSidecarReceived(blobSidecar);
    verify(futureBlobSidecars, never()).add(blobSidecar);

    assertThat(invalidBlobSidecarRoots.size()).isEqualTo(0);
  }

  @Test
  void validateAndPrepareForBlockImport_shouldSaveForTheFuture() {
    when(blobSidecarValidator.validate(any()))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.SAVE_FOR_FUTURE));

    assertThatSafeFuture(
            blobSidecarManager.validateAndPrepareForBlockImport(blobSidecar, Optional.empty()))
        .isCompletedWithValue(InternalValidationResult.SAVE_FOR_FUTURE);

    verify(blockBlobSidecarsTrackersPool, never()).onNewBlobSidecar(eq(blobSidecar), any());
    verify(receivedBlobSidecarListener, never()).onBlobSidecarReceived(blobSidecar);
    verify(futureBlobSidecars).add(blobSidecar);

    assertThat(invalidBlobSidecarRoots.size()).isEqualTo(0);
  }

  @Test
  void validateAndPrepareForBlockImport_shouldReject() {
    when(blobSidecarValidator.validate(any()))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.reject("no way")));

    assertThatSafeFuture(
            blobSidecarManager.validateAndPrepareForBlockImport(blobSidecar, Optional.empty()))
        .isCompletedWithValueMatching(InternalValidationResult::isReject);

    verify(blockBlobSidecarsTrackersPool, never()).onNewBlobSidecar(eq(blobSidecar), any());
    verify(receivedBlobSidecarListener, never()).onBlobSidecarReceived(blobSidecar);
    verify(futureBlobSidecars, never()).add(blobSidecar);

    assertThat(invalidBlobSidecarRoots)
        .matches(entry -> entry.containsKey(blobSidecar.hashTreeRoot()))
        .matches(entry -> entry.get(blobSidecar.hashTreeRoot()).isReject());
  }

  @Test
  void validateAndPrepareForBlockImport_shouldIgnore() {
    when(blobSidecarValidator.validate(any()))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.IGNORE));

    assertThatSafeFuture(
            blobSidecarManager.validateAndPrepareForBlockImport(blobSidecar, Optional.empty()))
        .isCompletedWithValue(InternalValidationResult.IGNORE);

    verify(blockBlobSidecarsTrackersPool, never()).onNewBlobSidecar(eq(blobSidecar), any());
    verify(receivedBlobSidecarListener, never()).onBlobSidecarReceived(blobSidecar);
    verify(futureBlobSidecars, never()).add(blobSidecar);

    assertThat(invalidBlobSidecarRoots.size()).isEqualTo(0);
  }

  @Test
  void validateAndPrepareForBlockImport_shouldRejectKnownInvalidBlobs() {
    invalidBlobSidecarRoots.put(
        blobSidecar.hashTreeRoot(), InternalValidationResult.reject("no way"));

    assertThatSafeFuture(
            blobSidecarManager.validateAndPrepareForBlockImport(blobSidecar, Optional.empty()))
        .isCompletedWithValueMatching(InternalValidationResult::isReject);

    verify(blobSidecarValidator, never()).validate(any());
    verify(blockBlobSidecarsTrackersPool, never()).onNewBlobSidecar(eq(blobSidecar), any());
    verify(receivedBlobSidecarListener, never()).onBlobSidecarReceived(blobSidecar);
    verify(futureBlobSidecars, never()).add(blobSidecar);

    assertThat(invalidBlobSidecarRoots.size()).isEqualTo(1);
  }

  @Test
  void prepareForBlockImport_shouldAddToPoolAndNotify() {
    blobSidecarManager.prepareForBlockImport(blobSidecar, RemoteOrigin.GOSSIP);

    verify(receivedBlobSidecarListener).onBlobSidecarReceived(blobSidecar);
    verify(blockBlobSidecarsTrackersPool).onNewBlobSidecar(blobSidecar, RemoteOrigin.GOSSIP);
  }

  @Test
  void onSlot_shouldInteractWithPoolAndFutureBlobs() {
    final List<BlobSidecar> futureBlobSidecarsList = List.of(dataStructureUtil.randomBlobSidecar());

    when(futureBlobSidecars.prune(UInt64.ONE)).thenReturn(futureBlobSidecarsList);

    blobSidecarManager.onSlot(UInt64.ONE);

    verify(blockBlobSidecarsTrackersPool).onSlot(UInt64.ONE);
    verify(futureBlobSidecars).onSlot(UInt64.ONE);

    verify(blockBlobSidecarsTrackersPool)
        .onNewBlobSidecar(futureBlobSidecarsList.getFirst(), RemoteOrigin.GOSSIP);
    verify(receivedBlobSidecarListener).onBlobSidecarReceived(futureBlobSidecarsList.getFirst());
  }

  @Test
  void createAvailabilityChecker_shouldReturnANotRequiredAvailabilityCheckerWhenBlockIsPreDeneb() {
    final Spec spec = TestSpecFactory.createMainnetCapella();
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock();

    assertThat(blobSidecarManager.createAvailabilityChecker(block))
        .isEqualTo(BlobSidecarsAvailabilityChecker.NOT_REQUIRED);
  }

  @Test
  void
      createAvailabilityCheckerAndValidateImmediately_shouldReturnANotRequiredAvailabilityCheckerWhenBlockIsPreDeneb() {
    final Spec spec = TestSpecFactory.createMainnetCapella();
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock();

    assertThat(blobSidecarManager.createAvailabilityCheckerAndValidateImmediately(block, List.of()))
        .isEqualTo(BlobSidecarsAndValidationResult.NOT_REQUIRED);
  }

  @Test
  void createAvailabilityChecker_shouldReturnAnAvailabilityChecker() {
    final ForkChoiceBlobSidecarsAvailabilityChecker forkChoiceBlobSidecarsAvailabilityChecker =
        mock(ForkChoiceBlobSidecarsAvailabilityChecker.class);
    final BlockBlobSidecarsTracker blockBlobSidecarsTracker = mock(BlockBlobSidecarsTracker.class);

    when(forkChoiceBlobSidecarsAvailabilityCheckerProvider.create(blockBlobSidecarsTracker))
        .thenReturn(forkChoiceBlobSidecarsAvailabilityChecker);
    when(blockBlobSidecarsTrackersPool.getOrCreateBlockBlobSidecarsTracker(block))
        .thenReturn(blockBlobSidecarsTracker);

    assertThat(blobSidecarManager.createAvailabilityChecker(block))
        .isSameAs(forkChoiceBlobSidecarsAvailabilityChecker);
  }

  @Test
  void createAvailabilityCheckerAndValidateImmediately_shouldReturnValidWhenComplete() {
    final BlockBlobSidecarsTracker blockBlobSidecarsTracker = mock(BlockBlobSidecarsTracker.class);
    final ForkChoiceBlobSidecarsAvailabilityChecker forkChoiceBlobSidecarsAvailabilityChecker =
        mock(ForkChoiceBlobSidecarsAvailabilityChecker.class);

    when(forkChoiceBlobSidecarsAvailabilityCheckerProvider.create(blockBlobSidecarsTracker))
        .thenReturn(forkChoiceBlobSidecarsAvailabilityChecker);
    when(unpooledBlockBlobSidecarsTrackerProvider.create(block))
        .thenReturn(blockBlobSidecarsTracker);
    when(blockBlobSidecarsTracker.add(any())).thenReturn(true);
    when(blockBlobSidecarsTracker.isComplete()).thenReturn(true);
    when(forkChoiceBlobSidecarsAvailabilityChecker.getAvailabilityCheckResult())
        .thenReturn(
            SafeFuture.completedFuture(BlobSidecarsAndValidationResult.validResult(blobSidecars)));

    assertThat(
            blobSidecarManager.createAvailabilityCheckerAndValidateImmediately(block, blobSidecars))
        .matches(BlobSidecarsAndValidationResult::isValid)
        .matches(
            result -> {
              assertThat(result.getBlobSidecars()).containsExactlyElementsOf(blobSidecars);
              return true;
            });

    verifyNoInteractions(blockBlobSidecarsTrackersPool);
  }

  @Test
  void createAvailabilityCheckerAndValidateImmediately_shouldReturnNotRequiredWhenPreDeneb() {
    final SignedBeaconBlock preDenebBlock =
        new DataStructureUtil(TestSpecFactory.createMinimalCapella()).randomSignedBeaconBlock();

    assertThat(
            blobSidecarManager.createAvailabilityCheckerAndValidateImmediately(
                preDenebBlock, blobSidecars))
        .matches(BlobSidecarsAndValidationResult::isNotRequired);

    verifyNoInteractions(blockBlobSidecarsTrackersPool);
  }

  @Test
  void
      createAvailabilityCheckerAndValidateImmediately_shouldReturnInvalidWhenBlobsHaveDuplicatedIndices() {
    final BlockBlobSidecarsTracker blockBlobSidecarsTracker = mock(BlockBlobSidecarsTracker.class);
    final ForkChoiceBlobSidecarsAvailabilityChecker forkChoiceBlobSidecarsAvailabilityChecker =
        mock(ForkChoiceBlobSidecarsAvailabilityChecker.class);

    when(forkChoiceBlobSidecarsAvailabilityCheckerProvider.create(blockBlobSidecarsTracker))
        .thenReturn(forkChoiceBlobSidecarsAvailabilityChecker);
    when(unpooledBlockBlobSidecarsTrackerProvider.create(block))
        .thenReturn(blockBlobSidecarsTracker);
    when(blockBlobSidecarsTracker.add(any())).thenReturn(false);
    when(blockBlobSidecarsTracker.isComplete()).thenReturn(true);

    assertThat(
            blobSidecarManager.createAvailabilityCheckerAndValidateImmediately(block, blobSidecars))
        .matches(BlobSidecarsAndValidationResult::isInvalid)
        .matches(
            result -> {
              assertThat(result.getCause()).isPresent();
              assertThat(result.getCause().orElseThrow())
                  .matches(cause -> cause instanceof IllegalStateException)
                  .hasMessage(
                      "Failed to add all blobs to tracker, possible blobs with same index or index out of blocks commitment range");
              return true;
            });

    verifyNoInteractions(blockBlobSidecarsTrackersPool);
  }

  @Test
  void
      createAvailabilityCheckerAndValidateImmediately_shouldReturnNotAvailableWhenBlobsAreIncomplete() {
    final BlockBlobSidecarsTracker blockBlobSidecarsTracker = mock(BlockBlobSidecarsTracker.class);
    final ForkChoiceBlobSidecarsAvailabilityChecker forkChoiceBlobSidecarsAvailabilityChecker =
        mock(ForkChoiceBlobSidecarsAvailabilityChecker.class);

    when(forkChoiceBlobSidecarsAvailabilityCheckerProvider.create(blockBlobSidecarsTracker))
        .thenReturn(forkChoiceBlobSidecarsAvailabilityChecker);
    when(unpooledBlockBlobSidecarsTrackerProvider.create(block))
        .thenReturn(blockBlobSidecarsTracker);
    when(blockBlobSidecarsTracker.add(any())).thenReturn(true);
    when(blockBlobSidecarsTracker.isComplete()).thenReturn(false);

    assertThat(
            blobSidecarManager.createAvailabilityCheckerAndValidateImmediately(block, blobSidecars))
        .matches(BlobSidecarsAndValidationResult::isNotAvailable);

    verifyNoInteractions(blockBlobSidecarsTrackersPool);
  }

  @Test
  void
      createAvailabilityCheckerAndValidateImmediately_shouldReturnTheAvailabilityCheckValidationResult() {
    final BlockBlobSidecarsTracker blockBlobSidecarsTracker = mock(BlockBlobSidecarsTracker.class);
    final ForkChoiceBlobSidecarsAvailabilityChecker forkChoiceBlobSidecarsAvailabilityChecker =
        mock(ForkChoiceBlobSidecarsAvailabilityChecker.class);

    when(forkChoiceBlobSidecarsAvailabilityCheckerProvider.create(blockBlobSidecarsTracker))
        .thenReturn(forkChoiceBlobSidecarsAvailabilityChecker);
    when(unpooledBlockBlobSidecarsTrackerProvider.create(block))
        .thenReturn(blockBlobSidecarsTracker);
    when(blockBlobSidecarsTracker.add(any())).thenReturn(true);
    when(blockBlobSidecarsTracker.isComplete()).thenReturn(true);

    final SafeFuture<BlobSidecarsAndValidationResult> result = new SafeFuture<>();

    when(forkChoiceBlobSidecarsAvailabilityChecker.getAvailabilityCheckResult()).thenReturn(result);

    assertThatThrownBy(
            () ->
                blobSidecarManager.createAvailabilityCheckerAndValidateImmediately(
                    block, blobSidecars))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(
            "Availability check expected to be done synchronously when providing immediate blobs");

    result.complete(BlobSidecarsAndValidationResult.validResult(blobSidecars));

    assertThat(
            blobSidecarManager.createAvailabilityCheckerAndValidateImmediately(block, blobSidecars))
        .isSameAs(result.join());

    verifyNoInteractions(blockBlobSidecarsTrackersPool);
  }
}
