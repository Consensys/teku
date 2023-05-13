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

package tech.pegasys.teku.statetransition.blobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.SignedBlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.logic.versions.deneb.blobs.BlobSidecarsAvailabilityChecker;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.blobs.BlobSidecarManager.ReceivedBlobSidecarListener;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoiceBlobSidecarsAvailabilityChecker;
import tech.pegasys.teku.statetransition.util.BlobSidecarPoolImpl;
import tech.pegasys.teku.statetransition.util.FutureItems;
import tech.pegasys.teku.statetransition.validation.BlobSidecarValidator;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.storage.api.StorageQueryChannel;
import tech.pegasys.teku.storage.client.RecentChainData;

public class BlobSidecarManagerTest {
  private final Spec spec = TestSpecFactory.createMinimalDeneb();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();
  private final RecentChainData recentChainData = mock(RecentChainData.class);
  private final StorageQueryChannel storageQueryChannel = mock(StorageQueryChannel.class);
  private final BlobSidecarValidator blobSidecarValidator = mock(BlobSidecarValidator.class);
  private final BlobSidecarPoolImpl blobSidecarPool = mock(BlobSidecarPoolImpl.class);
  private final Map<Bytes32, InternalValidationResult> invalidBlobSidecarRoots = new HashMap<>();

  @SuppressWarnings("unchecked")
  private final FutureItems<SignedBlobSidecar> futureBlobSidecars = mock(FutureItems.class);

  private final BlobSidecarManagerImpl blobSidecarManager =
      new BlobSidecarManagerImpl(
          spec,
          asyncRunner,
          recentChainData,
          blobSidecarPool,
          blobSidecarValidator,
          futureBlobSidecars,
          invalidBlobSidecarRoots,
          storageQueryChannel);

  private final ReceivedBlobSidecarListener receivedBlobSidecarListener =
      mock(ReceivedBlobSidecarListener.class);

  private final SignedBlobSidecar signedBlobSidecar = dataStructureUtil.randomSignedBlobSidecar();
  private final BlobSidecar blobSidecar = signedBlobSidecar.getBlobSidecar();

  @BeforeEach
  void setUp() {
    when(blobSidecarValidator.validate(any()))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.ACCEPT));
    blobSidecarManager.subscribeToReceivedBlobSidecar(receivedBlobSidecarListener);
  }

  @Test
  void validateAndPrepareForBlockImport_shouldPrepareBlobSidecar() {
    assertThatSafeFuture(blobSidecarManager.validateAndPrepareForBlockImport(signedBlobSidecar))
        .isCompletedWithValue(InternalValidationResult.ACCEPT);

    verify(blobSidecarPool).onNewBlobSidecar(blobSidecar);
    verify(receivedBlobSidecarListener).onBlobSidecarReceived(blobSidecar);
    verify(futureBlobSidecars, never()).add(signedBlobSidecar);

    assertThat(invalidBlobSidecarRoots.size()).isEqualTo(0);
  }

  @Test
  void validateAndPrepareForBlockImport_shouldSaveForTheFuture() {
    when(blobSidecarValidator.validate(any()))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.SAVE_FOR_FUTURE));

    assertThatSafeFuture(blobSidecarManager.validateAndPrepareForBlockImport(signedBlobSidecar))
        .isCompletedWithValue(InternalValidationResult.SAVE_FOR_FUTURE);

    verify(blobSidecarPool, never()).onNewBlobSidecar(blobSidecar);
    verify(receivedBlobSidecarListener, never()).onBlobSidecarReceived(blobSidecar);
    verify(futureBlobSidecars).add(signedBlobSidecar);

    assertThat(invalidBlobSidecarRoots.size()).isEqualTo(0);
  }

  @Test
  void validateAndPrepareForBlockImport_shouldReject() {
    when(blobSidecarValidator.validate(any()))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.reject("no way")));

    assertThatSafeFuture(blobSidecarManager.validateAndPrepareForBlockImport(signedBlobSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isReject);

    verify(blobSidecarPool, never()).onNewBlobSidecar(blobSidecar);
    verify(receivedBlobSidecarListener, never()).onBlobSidecarReceived(blobSidecar);
    verify(futureBlobSidecars, never()).add(signedBlobSidecar);

    assertThat(invalidBlobSidecarRoots)
        .matches(entry -> entry.containsKey(blobSidecar.hashTreeRoot()))
        .matches(entry -> entry.get(blobSidecar.hashTreeRoot()).isReject());
  }

  @Test
  void validateAndPrepareForBlockImport_shouldIgnore() {
    when(blobSidecarValidator.validate(any()))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.IGNORE));

    assertThatSafeFuture(blobSidecarManager.validateAndPrepareForBlockImport(signedBlobSidecar))
        .isCompletedWithValue(InternalValidationResult.IGNORE);

    verify(blobSidecarPool, never()).onNewBlobSidecar(blobSidecar);
    verify(receivedBlobSidecarListener, never()).onBlobSidecarReceived(blobSidecar);
    verify(futureBlobSidecars, never()).add(signedBlobSidecar);

    assertThat(invalidBlobSidecarRoots.size()).isEqualTo(0);
  }

  @Test
  void validateAndPrepareForBlockImport_shouldRejectKnownInvalidBlobs() {
    invalidBlobSidecarRoots.put(
        blobSidecar.hashTreeRoot(), InternalValidationResult.reject("no way"));

    assertThatSafeFuture(blobSidecarManager.validateAndPrepareForBlockImport(signedBlobSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isReject);

    verify(blobSidecarValidator, never()).validate(any());
    verify(blobSidecarPool, never()).onNewBlobSidecar(blobSidecar);
    verify(receivedBlobSidecarListener, never()).onBlobSidecarReceived(blobSidecar);
    verify(futureBlobSidecars, never()).add(signedBlobSidecar);

    assertThat(invalidBlobSidecarRoots.size()).isEqualTo(1);
  }

  @Test
  void prepareForBlockImport_shouldAddToPoolAndNotify() {

    blobSidecarManager.prepareForBlockImport(blobSidecar);

    verify(receivedBlobSidecarListener).onBlobSidecarReceived(blobSidecar);
    verify(blobSidecarPool).onNewBlobSidecar(blobSidecar);
  }

  @Test
  void onSlot_shouldInteractWithPoolAndFutureBlobs() {
    final List<SignedBlobSidecar> futureBlobSidecarsList =
        List.of(dataStructureUtil.randomSignedBlobSidecar());

    when(futureBlobSidecars.prune(UInt64.ONE)).thenReturn(futureBlobSidecarsList);

    blobSidecarManager.onSlot(UInt64.ONE);

    verify(blobSidecarPool).onSlot(UInt64.ONE);
    verify(futureBlobSidecars).onSlot(UInt64.ONE);

    verify(blobSidecarPool).onNewBlobSidecar(futureBlobSidecarsList.get(0).getBlobSidecar());
    verify(receivedBlobSidecarListener)
        .onBlobSidecarReceived(futureBlobSidecarsList.get(0).getBlobSidecar());
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
  void createAvailabilityChecker_shouldReturnAnAvailabilityChecker() {
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(UInt64.ONE);

    final BlockBlobSidecarsTracker blockBlobSidecarsTracker = mock(BlockBlobSidecarsTracker.class);
    when(blockBlobSidecarsTracker.getSlotAndBlockRoot()).thenReturn(block.getSlotAndBlockRoot());

    when(blobSidecarPool.getOrCreateBlockBlobSidecarsTracker(block))
        .thenReturn(blockBlobSidecarsTracker);

    assertThat(blobSidecarManager.createAvailabilityChecker(block))
        .isInstanceOf(ForkChoiceBlobSidecarsAvailabilityChecker.class);
  }
}
