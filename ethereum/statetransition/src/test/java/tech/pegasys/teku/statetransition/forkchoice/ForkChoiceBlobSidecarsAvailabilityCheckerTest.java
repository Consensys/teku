/*
 * Copyright Consensys Software Inc., 2023
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

package tech.pegasys.teku.statetransition.forkchoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;

import com.google.common.collect.ImmutableSortedMap;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.Waiter;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZG;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;
import tech.pegasys.teku.spec.logic.versions.deneb.blobs.BlobSidecarsAndValidationResult;
import tech.pegasys.teku.spec.logic.versions.deneb.blobs.BlobSidecarsValidationResult;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.blobs.BlockBlobSidecarsTracker;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.store.UpdatableStore;

public class ForkChoiceBlobSidecarsAvailabilityCheckerTest {

  private final DataStructureUtil dataStructureUtil =
      new DataStructureUtil(TestSpecFactory.createMinimalDeneb());

  private final Spec spec = mock(Spec.class);
  private final SpecVersion specVersion = mock(SpecVersion.class);
  private final MiscHelpers miscHelpers = mock(MiscHelpers.class);
  private final KZG kzg = mock(KZG.class);
  private final UpdatableStore store = mock(UpdatableStore.class);
  private final BlockBlobSidecarsTracker blockBlobSidecarsTracker =
      mock(BlockBlobSidecarsTracker.class);
  private final RecentChainData recentChainData = mock(RecentChainData.class);

  private SignedBeaconBlock block;
  private List<BlobSidecar> blobSidecarsComplete;

  private final SafeFuture<Void> trackerCompletionFuture = new SafeFuture<>();

  private ForkChoiceBlobSidecarsAvailabilityChecker blobSidecarsAvailabilityChecker;

  @BeforeEach
  void setUp() {
    when(spec.atSlot(any())).thenReturn(specVersion);
    when(specVersion.miscHelpers()).thenReturn(miscHelpers);
    when(recentChainData.getStore()).thenReturn(store);
    when(blockBlobSidecarsTracker.getCompletionFuture()).thenReturn(trackerCompletionFuture);
  }

  @Test
  void shouldVerifyValidAvailableBlobs() throws Exception {
    prepareInitialAvailability();

    when(miscHelpers.verifyBlobKzgProofBatch(any(), any())).thenReturn(true);
    when(miscHelpers.verifyBlobSidecarBlockHeaderSignatureViaValidatedSignedBlock(anyList(), any()))
        .thenReturn(true);

    final SafeFuture<BlobSidecarsAndValidationResult> availabilityCheckResult =
        runAvailabilityCheck();

    assertAvailable(availabilityCheckResult);
  }

  @Test
  void shouldVerifyInvalidBlobsDueToWrongBlockHeader() throws Exception {
    prepareInitialAvailability();

    when(miscHelpers.verifyBlobKzgProofBatch(any(), any())).thenReturn(true);
    when(miscHelpers.verifyBlobSidecarBlockHeaderSignatureViaValidatedSignedBlock(anyList(), any()))
        .thenReturn(false);

    final SafeFuture<BlobSidecarsAndValidationResult> availabilityCheckResult =
        runAvailabilityCheck();

    assertInvalid(
        availabilityCheckResult,
        blobSidecarsComplete,
        Optional.of(
            new IllegalStateException("Blob sidecars block header does not match signed block")));
  }

  @Test
  void shouldVerifyInvalidBlobsDueToWrongKzg() throws Exception {
    prepareInitialAvailability();

    when(miscHelpers.verifyBlobKzgProofBatch(any(), any())).thenReturn(false);
    when(miscHelpers.verifyBlobSidecarBlockHeaderSignatureViaValidatedSignedBlock(anyList(), any()))
        .thenReturn(true);

    final SafeFuture<BlobSidecarsAndValidationResult> availabilityCheckResult =
        runAvailabilityCheck();

    assertInvalid(availabilityCheckResult, blobSidecarsComplete, Optional.empty());
  }

  @Test
  void shouldVerifyInvalidBlobsWhenKzgValidationThrows() throws Exception {
    prepareInitialAvailability();

    final RuntimeException error = new RuntimeException("oops");

    when(miscHelpers.verifyBlobKzgProofBatch(any(), any())).thenThrow(error);

    final SafeFuture<BlobSidecarsAndValidationResult> availabilityCheckResult =
        runAvailabilityCheck();

    assertInvalid(availabilityCheckResult, blobSidecarsComplete, Optional.of(error));
  }

  @Test
  void shouldVerifyInvalidBlobsWhenCompletenessValidationThrows() throws Exception {
    prepareInitialAvailability();

    final RuntimeException error = new RuntimeException("oops");

    when(miscHelpers.verifyBlobKzgProofBatch(any(), any())).thenReturn(true);
    when(miscHelpers.verifyBlobSidecarBlockHeaderSignatureViaValidatedSignedBlock(anyList(), any()))
        .thenReturn(true);
    doThrow(error).when(miscHelpers).verifyBlobSidecarCompleteness(anyList(), any());

    final SafeFuture<BlobSidecarsAndValidationResult> availabilityCheckResult =
        runAvailabilityCheck();

    assertInvalid(availabilityCheckResult, blobSidecarsComplete, Optional.of(error));
  }

  @Test
  void shouldFailIfTrackerCompletesWithFailure() {
    prepareInitialAvailability();

    final SafeFuture<BlobSidecarsAndValidationResult> availabilityCheckResult =
        blobSidecarsAvailabilityChecker.getAvailabilityCheckResult();

    assertThatSafeFuture(availabilityCheckResult).isNotCompleted();

    // initiate availability check
    assertThat(blobSidecarsAvailabilityChecker.initiateDataAvailabilityCheck()).isTrue();

    assertThatSafeFuture(availabilityCheckResult).isNotCompleted();
    verify(blockBlobSidecarsTracker, never()).getBlobSidecars();

    // let the tracker complete with all blobSidecars
    trackerCompletionFuture.completeExceptionally(new RuntimeException("oops"));

    assertThatSafeFuture(availabilityCheckResult).isCompletedExceptionallyWithMessage("oops");
  }

  @Test
  void shouldReturnNotAvailableOnTimeout() throws Exception {
    prepareForImmediateTimeout();

    final SafeFuture<BlobSidecarsAndValidationResult> availabilityCheckResult =
        blobSidecarsAvailabilityChecker.getAvailabilityCheckResult();

    assertThat(blobSidecarsAvailabilityChecker.initiateDataAvailabilityCheck()).isTrue();

    verify(blockBlobSidecarsTracker, never()).getBlobSidecars();

    Waiter.waitFor(availabilityCheckResult);

    assertNotAvailableDueToTimeout(availabilityCheckResult);
  }

  @Test
  void shouldReturnNotRequiredWhenBlockIsOutsideAvailabilityWindow() throws Exception {
    prepareForImmediateTimeoutWithBlockAndBlobSidecarsOutsideAvailabilityWindow();

    final SafeFuture<BlobSidecarsAndValidationResult> availabilityCheckResult =
        blobSidecarsAvailabilityChecker.getAvailabilityCheckResult();

    assertThat(blobSidecarsAvailabilityChecker.initiateDataAvailabilityCheck()).isTrue();

    Waiter.waitFor(availabilityCheckResult);

    assertNotRequired(availabilityCheckResult);
  }

  private SafeFuture<BlobSidecarsAndValidationResult> runAvailabilityCheck() throws Exception {
    final SafeFuture<BlobSidecarsAndValidationResult> availabilityCheckResult =
        blobSidecarsAvailabilityChecker.getAvailabilityCheckResult();

    assertThatSafeFuture(availabilityCheckResult).isNotCompleted();

    // initiate availability check
    assertThat(blobSidecarsAvailabilityChecker.initiateDataAvailabilityCheck()).isTrue();

    assertThatSafeFuture(availabilityCheckResult).isNotCompleted();
    verify(blockBlobSidecarsTracker, never()).getBlobSidecars();

    // let the tracker complete with all blobSidecars
    completeTrackerWith(blobSidecarsComplete);

    Waiter.waitFor(availabilityCheckResult);

    return availabilityCheckResult;
  }

  private void assertInvalid(
      final SafeFuture<BlobSidecarsAndValidationResult> availabilityOrValidityCheck,
      final List<BlobSidecar> invalidBlobs,
      final Optional<Throwable> cause) {
    assertThat(availabilityOrValidityCheck)
        .isCompletedWithValueMatching(result -> !result.isValid(), "is not valid")
        .isCompletedWithValueMatching(
            result -> result.getValidationResult() == BlobSidecarsValidationResult.INVALID,
            "is not available")
        .isCompletedWithValueMatching(
            result -> result.getBlobSidecars().equals(invalidBlobs), "doesn't have blob sidecars")
        .isCompletedWithValueMatching(
            result -> {
              if (cause.isEmpty() != result.getCause().isEmpty()) {
                return false;
              }
              return result
                  .getCause()
                  .map(
                      resultCause ->
                          resultCause.getClass().equals(cause.get().getClass())
                              && Objects.equals(resultCause.getMessage(), cause.get().getMessage())
                              && Objects.equals(resultCause.getCause(), cause.get().getCause()))
                  .orElse(true);
            },
            "matches the cause");
  }

  private void assertNotRequired(
      final SafeFuture<BlobSidecarsAndValidationResult> availabilityOrValidityCheck) {
    assertThat(availabilityOrValidityCheck)
        .isCompletedWithValueMatching(result -> !result.isValid(), "is not valid")
        .isCompletedWithValueMatching(
            BlobSidecarsAndValidationResult::isNotRequired, "is not required")
        .isCompletedWithValueMatching(
            result -> result.getBlobSidecars().isEmpty(), "doesn't have blob sidecars");
  }

  private void assertNotAvailableDueToTimeout(
      final SafeFuture<BlobSidecarsAndValidationResult> availabilityOrValidityCheck) {
    assertNotAvailable(availabilityOrValidityCheck);
    assertThat(availabilityOrValidityCheck)
        .isCompletedWithValueMatching(
            result -> result.getCause().orElseThrow() instanceof TimeoutException);
  }

  private void assertNotAvailable(
      final SafeFuture<BlobSidecarsAndValidationResult> availabilityOrValidityCheck) {
    assertThat(availabilityOrValidityCheck)
        .isCompletedWithValueMatching(result -> !result.isValid(), "is not valid")
        .isCompletedWithValueMatching(
            result -> result.getValidationResult() == BlobSidecarsValidationResult.NOT_AVAILABLE,
            "is not available")
        .isCompletedWithValueMatching(
            result -> result.getBlobSidecars().isEmpty(), "doesn't have blob sidecars");
  }

  private void assertAvailable(
      final SafeFuture<BlobSidecarsAndValidationResult> availabilityOrValidityCheck) {
    assertThat(availabilityOrValidityCheck)
        .isCompletedWithValueMatching(BlobSidecarsAndValidationResult::isValid, "is valid")
        .isCompletedWithValueMatching(
            result -> result.getValidationResult() == BlobSidecarsValidationResult.VALID,
            "is valid")
        .isCompletedWithValueMatching(
            result -> result.getBlobSidecars().equals(blobSidecarsComplete), "has blob sidecars");
  }

  private void prepareForImmediateTimeout() {
    prepareInitialAvailability(Optional.empty(), Duration.ZERO);
  }

  private void prepareInitialAvailability() {
    prepareInitialAvailability(Optional.empty(), Duration.ofSeconds(30));
  }

  private void prepareInitialAvailability(
      final Optional<SignedBeaconBlock> providedBlock, final Duration timeout) {
    block = providedBlock.orElse(dataStructureUtil.randomSignedBeaconBlockWithCommitments(4));
    blobSidecarsComplete = dataStructureUtil.randomBlobSidecarsForBlock(block);

    when(spec.isAvailabilityOfBlobSidecarsRequiredAtSlot(store, block.getSlot())).thenReturn(true);

    when(blockBlobSidecarsTracker.getBlock()).thenReturn(Optional.of(block));
    when(blockBlobSidecarsTracker.getBlobSidecars()).thenReturn(ImmutableSortedMap.of());
    when(blockBlobSidecarsTracker.getSlotAndBlockRoot()).thenReturn(block.getSlotAndBlockRoot());

    blobSidecarsAvailabilityChecker =
        new ForkChoiceBlobSidecarsAvailabilityChecker(
            spec, recentChainData, blockBlobSidecarsTracker, kzg, timeout);
  }

  private void completeTrackerWith(final List<BlobSidecar> blobSidecars) {
    final ImmutableSortedMap.Builder<UInt64, BlobSidecar> mapBuilder =
        ImmutableSortedMap.naturalOrder();
    blobSidecars.forEach(blobSidecar -> mapBuilder.put(blobSidecar.getIndex(), blobSidecar));
    when(blockBlobSidecarsTracker.getBlobSidecars()).thenReturn(mapBuilder.build());
    when(blockBlobSidecarsTracker.isComplete()).thenReturn(true);
    trackerCompletionFuture.complete(null);
  }

  private void prepareForImmediateTimeoutWithBlockAndBlobSidecarsOutsideAvailabilityWindow() {
    block = dataStructureUtil.randomSignedBeaconBlock();
    blobSidecarsComplete = dataStructureUtil.randomBlobSidecarsForBlock(block);

    final ImmutableSortedMap.Builder<UInt64, BlobSidecar> mapBuilder =
        ImmutableSortedMap.naturalOrder();
    blobSidecarsComplete.forEach(
        blobSidecar -> mapBuilder.put(blobSidecar.getIndex(), blobSidecar));

    when(spec.isAvailabilityOfBlobSidecarsRequiredAtSlot(store, block.getSlot())).thenReturn(false);
    when(blockBlobSidecarsTracker.getBlock()).thenReturn(Optional.of(block));
    when(blockBlobSidecarsTracker.getBlobSidecars()).thenReturn(ImmutableSortedMap.of());
    when(blockBlobSidecarsTracker.getSlotAndBlockRoot()).thenReturn(block.getSlotAndBlockRoot());

    blobSidecarsAvailabilityChecker =
        new ForkChoiceBlobSidecarsAvailabilityChecker(
            spec, recentChainData, blockBlobSidecarsTracker, kzg, Duration.ZERO);
  }
}
