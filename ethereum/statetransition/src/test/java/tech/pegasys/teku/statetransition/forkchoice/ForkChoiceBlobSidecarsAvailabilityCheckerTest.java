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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.assertArg;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableSortedMap;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.stubbing.OngoingStubbing;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.SafeFutureAssert;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.async.Waiter;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZGCommitment;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
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
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();
  private final UpdatableStore store = mock(UpdatableStore.class);
  private final BlockBlobSidecarsTracker blockBlobSidecarsTracker =
      mock(BlockBlobSidecarsTracker.class);
  private final MiscHelpers miscHelpers = mock(MiscHelpers.class);
  private final RecentChainData recentChainData = mock(RecentChainData.class);

  private SignedBeaconBlock block;
  private List<BlobSidecar> blobSidecarsComplete;
  private List<KZGCommitment> kzgCommitmentsComplete;

  private List<BlobSidecar> blobSidecarsInitial;
  private List<BlobSidecar> blobSidecarsAdditional;

  private final SafeFuture<Void> trackerCompletionFuture = new SafeFuture<>();

  private ForkChoiceBlobSidecarsAvailabilityChecker blobSidecarsAvailabilityChecker;

  @BeforeEach
  void setUp() {
    when(spec.atSlot(any())).thenReturn(specVersion);
    when(specVersion.miscHelpers()).thenReturn(miscHelpers);
    when(recentChainData.getStore()).thenReturn(store);
    when(blockBlobSidecarsTracker.getCompletionFuture()).thenReturn(trackerCompletionFuture);
  }

  @ParameterizedTest
  @EnumSource(Availability.class)
  void shouldVerifyAvailableBlobsInTwoBatches(final Availability availability) throws Exception {
    prepareInitialAvailability(availability);

    final SafeFuture<BlobSidecarsAndValidationResult> availabilityCheckResult =
        blobSidecarsAvailabilityChecker.getAvailabilityCheckResult();

    SafeFutureAssert.assertThatSafeFuture(availabilityCheckResult).isNotCompleted();

    // initiate availability check
    assertThat(blobSidecarsAvailabilityChecker.initiateDataAvailabilityCheck()).isTrue();

    // all validation on a separate thread, so no interaction so far.
    SafeFutureAssert.assertThatSafeFuture(availabilityCheckResult).isNotCompleted();
    verifyDataAvailabilityNeverCalled();
    verify(blockBlobSidecarsTracker, never()).getBlobSidecars();

    // mock kzg availability check to be OK for the initial set
    whenDataAvailability(blobSidecarsInitial).thenReturn(true);

    // let availability check to be performed.
    asyncRunner.executeDueActions();

    // verify that kzg validation has been performed for the initial batch
    verifyValidationAndDataAvailabilityCall(blobSidecarsInitial, false);

    // mock the additional check to be OK.
    whenDataAvailability(blobSidecarsAdditional).thenReturn(true);

    // let the tracker complete with all blobSidecars
    completeTrackerWith(blobSidecarsComplete);

    Waiter.waitFor(availabilityCheckResult);

    // verify that kzg validation has been performed for the additional batch
    verifyValidationAndDataAvailabilityCall(blobSidecarsAdditional, true);

    assertAvailable(availabilityCheckResult);

    // no interaction since last verify
    verifyNoInteractions(miscHelpers);
  }

  @Test
  void shouldVerifyAvailableBlobsInOneBatch() throws Exception {
    prepareInitialAvailability(Availability.FULL);

    final SafeFuture<BlobSidecarsAndValidationResult> availabilityCheckResult =
        blobSidecarsAvailabilityChecker.getAvailabilityCheckResult();

    SafeFutureAssert.assertThatSafeFuture(availabilityCheckResult).isNotCompleted();

    // initiate availability check
    assertThat(blobSidecarsAvailabilityChecker.initiateDataAvailabilityCheck()).isTrue();

    // mock kzg availability check to be OK for the initial set
    whenDataAvailability(blobSidecarsComplete).thenReturn(true);

    // tracker is completed in advance
    completeTrackerWith(blobSidecarsComplete);

    // let availability check to be performed.
    asyncRunner.executeDueActions();

    // verify that kzg validation has been performed for the initial batch
    verifyValidationAndDataAvailabilityCall(blobSidecarsComplete, true);

    Waiter.waitFor(availabilityCheckResult);

    assertAvailable(availabilityCheckResult);

    // no interaction since last verify
    verifyNoInteractions(miscHelpers);
  }

  @Test
  void shouldReturnNotAvailableOnTimeout() throws Exception {
    prepareForImmediateTimeout();

    final SafeFuture<BlobSidecarsAndValidationResult> availabilityCheckResult =
        blobSidecarsAvailabilityChecker.getAvailabilityCheckResult();

    assertThat(blobSidecarsAvailabilityChecker.initiateDataAvailabilityCheck()).isTrue();

    SafeFutureAssert.assertThatSafeFuture(availabilityCheckResult).isNotCompleted();
    verifyDataAvailabilityNeverCalled();
    verify(blockBlobSidecarsTracker, never()).getBlobSidecars();

    whenDataAvailability(blobSidecarsInitial).thenReturn(true);

    asyncRunner.executeDueActions();

    Waiter.waitFor(availabilityCheckResult);

    assertNotAvailableDueToTimeout(availabilityCheckResult);
  }

  @Test
  void shouldReturnNotRequiredWhenBlockIsOutsideAvailabilityWindow() {
    prepareBlockAndBlobSidecarsOutsideAvailabilityWindow();

    final SafeFuture<BlobSidecarsAndValidationResult> availabilityCheckResult =
        blobSidecarsAvailabilityChecker.getAvailabilityCheckResult();

    SafeFutureAssert.assertThatSafeFuture(availabilityCheckResult).isNotCompleted();

    assertThat(blobSidecarsAvailabilityChecker.initiateDataAvailabilityCheck()).isTrue();
    SafeFutureAssert.assertThatSafeFuture(availabilityCheckResult).isNotCompleted();
    asyncRunner.executeDueActions();
    assertNotRequired(availabilityCheckResult);
  }

  @ParameterizedTest
  @EnumSource(value = BatchFailure.class)
  void shouldReturnNotAvailableIfFirstBatchFails(final BatchFailure batchFailure) {
    prepareInitialAvailability(Availability.PARTIAL);

    final Optional<Throwable> cause =
        switch (batchFailure) {
          case BLOB_SIDECAR_VALIDATION_EXCEPTION, IS_DATA_AVAILABLE_EXCEPTION -> Optional.of(
              new RuntimeException("oops"));
          default -> Optional.empty();
        };

    final SafeFuture<BlobSidecarsAndValidationResult> availabilityCheckResult =
        blobSidecarsAvailabilityChecker.getAvailabilityCheckResult();

    SafeFutureAssert.assertThatSafeFuture(availabilityCheckResult).isNotCompleted();

    // initiate availability check
    assertThat(blobSidecarsAvailabilityChecker.initiateDataAvailabilityCheck()).isTrue();

    switch (batchFailure) {
        // blobsidecar validation check failure for the initial set
      case BLOB_SIDECAR_VALIDATION_EXCEPTION:
        throwWhenValidatingBlobSidecarsBatchAgainstBlock(blobSidecarsInitial, cause.get());
        break;

        // mock kzg availability check failure for the initial set
      case IS_DATA_AVAILABLE_EXCEPTION:
        whenDataAvailability(blobSidecarsInitial).thenThrow(cause.get());
        break;

      case IS_DATA_AVAILABLE_RETURN_FALSE:
        whenDataAvailability(blobSidecarsInitial).thenReturn(false);
        break;
    }

    asyncRunner.executeDueActions();

    assertInvalid(availabilityCheckResult, blobSidecarsInitial, cause);
  }

  @ParameterizedTest
  @EnumSource(value = BatchFailure.class)
  void shouldReturnNotAvailableIfSecondBatchFails(final BatchFailure batchFailure) {
    prepareInitialAvailability(Availability.PARTIAL);

    final Optional<Throwable> cause =
        switch (batchFailure) {
          case BLOB_SIDECAR_VALIDATION_EXCEPTION, IS_DATA_AVAILABLE_EXCEPTION -> Optional.of(
              new RuntimeException("oops"));
          default -> Optional.empty();
        };

    final SafeFuture<BlobSidecarsAndValidationResult> availabilityCheckResult =
        blobSidecarsAvailabilityChecker.getAvailabilityCheckResult();

    SafeFutureAssert.assertThatSafeFuture(availabilityCheckResult).isNotCompleted();

    // initiate availability check
    assertThat(blobSidecarsAvailabilityChecker.initiateDataAvailabilityCheck()).isTrue();

    // mock kzg availability check to be OK for the initial set
    whenDataAvailability(blobSidecarsInitial).thenReturn(true);

    // let availability check to be performed.
    asyncRunner.executeDueActions();

    switch (batchFailure) {
        // blobsidecar validation check failure for the additional set
      case BLOB_SIDECAR_VALIDATION_EXCEPTION:
        throwWhenValidatingBlobSidecarsBatchAgainstBlock(blobSidecarsAdditional, cause.get());
        break;

        // mock kzg availability check failure for the additional set
      case IS_DATA_AVAILABLE_EXCEPTION:
        whenDataAvailability(blobSidecarsAdditional).thenThrow(cause.get());
        break;

      case IS_DATA_AVAILABLE_RETURN_FALSE:
        whenDataAvailability(blobSidecarsAdditional).thenReturn(false);
        break;
    }

    // let the tracker complete with all blobSidecars
    completeTrackerWith(blobSidecarsComplete);

    assertInvalid(availabilityCheckResult, blobSidecarsAdditional, cause);
  }

  @Test
  void shouldThrowIfTrackerLiesWithCompletionButItIsNot() {
    prepareInitialAvailability(Availability.PARTIAL);

    final SafeFuture<BlobSidecarsAndValidationResult> availabilityCheckResult =
        blobSidecarsAvailabilityChecker.getAvailabilityCheckResult();

    SafeFutureAssert.assertThatSafeFuture(availabilityCheckResult).isNotCompleted();

    // initiate availability check
    assertThat(blobSidecarsAvailabilityChecker.initiateDataAvailabilityCheck()).isTrue();

    // all validation on a separate thread, so no interaction so far.
    SafeFutureAssert.assertThatSafeFuture(availabilityCheckResult).isNotCompleted();
    verifyDataAvailabilityNeverCalled();
    verify(blockBlobSidecarsTracker, never()).getBlobSidecars();

    // mock kzg availability check to be OK for the initial set
    whenDataAvailability(blobSidecarsInitial).thenReturn(true);

    // let availability check to be performed.
    asyncRunner.executeDueActions();

    // verify that kzg validation has been performed for the initial batch
    verifyValidationAndDataAvailabilityCall(blobSidecarsInitial, false);

    // we complete the blobs without index 3
    final List<BlobSidecar> partialBlobs = blobSidecarsComplete.subList(1, 2);
    // we lie on availability check too (not actually possible)
    whenDataAvailability(partialBlobs).thenReturn(true);

    final List<BlobSidecar> expectedIncompleteBlobSidecar = new ArrayList<>();
    expectedIncompleteBlobSidecar.add(blobSidecarsComplete.get(0)); // blob 0
    expectedIncompleteBlobSidecar.add(blobSidecarsComplete.get(1)); // blob 1
    expectedIncompleteBlobSidecar.add(blobSidecarsComplete.get(2)); // blob 2

    throwWhenVerifyingBlobSidecarCompleteness(
        expectedIncompleteBlobSidecar, new IllegalArgumentException("oops"));

    // let the tracker complete with all blobSidecars
    completeTrackerWith(partialBlobs);

    SafeFutureAssert.assertThatSafeFuture(availabilityCheckResult)
        .isCompletedExceptionallyWith(IllegalArgumentException.class)
        .hasMessage("Validated blobs are less than commitments present in block.");
  }

  @Test
  void validateImmediately_shouldReturnAvailable() {
    prepareInitialAvailability(Availability.FULL);

    whenDataAvailability(blobSidecarsComplete).thenReturn(true);

    assertAvailable(
        SafeFuture.completedFuture(
            blobSidecarsAvailabilityChecker.validateImmediately(blobSidecarsComplete)));
  }

  @Test
  void validateImmediately_shouldThrowIfCompletenessCheckFails() {
    prepareInitialAvailability(Availability.FULL);

    whenDataAvailability(blobSidecarsComplete).thenReturn(true);
    doThrow(new IllegalArgumentException("oops"))
        .when(miscHelpers)
        .verifyBlobSidecarCompleteness(any(), any());

    assertThatThrownBy(
            () -> blobSidecarsAvailabilityChecker.validateImmediately(blobSidecarsComplete))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Validated blobs are less than commitments present in block.");
  }

  @Test
  void validateImmediately_shouldReturnNotAvailableWithEmptyBlobsButRequired() {
    prepareInitialAvailability(Availability.FULL);

    assertNotAvailable(
        SafeFuture.completedFuture(
            blobSidecarsAvailabilityChecker.validateImmediately(Collections.emptyList())));
  }

  @Test
  void validateImmediately_shouldReturnAvailableOnEmptyBlobs() {
    prepareInitialAvailabilityWithEmptyCommitmentsBlock();

    assertAvailable(
        SafeFuture.completedFuture(
            blobSidecarsAvailabilityChecker.validateImmediately(Collections.emptyList())));
  }

  @Test
  void validateImmediately_shouldReturnNotRequiredWhenBlockIsOutsideAvailabilityWindow() {
    prepareBlockAndBlobSidecarsOutsideAvailabilityWindow();

    assertNotRequired(
        SafeFuture.completedFuture(
            blobSidecarsAvailabilityChecker.validateImmediately(Collections.emptyList())));
  }

  private void assertNotRequired(
      final SafeFuture<BlobSidecarsAndValidationResult> availabilityOrValidityCheck) {
    assertThat(availabilityOrValidityCheck)
        .isCompletedWithValueMatching(result -> !result.isFailure(), "is not failure")
        .isCompletedWithValueMatching(result -> !result.isValid(), "is not valid")
        .isCompletedWithValueMatching(
            BlobSidecarsAndValidationResult::isNotRequired, "is not required")
        .isCompletedWithValueMatching(
            result -> result.getBlobSidecars().isEmpty(), "doesn't have blob sidecars");
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
            result -> result.getCause().equals(cause), "matches the cause");
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
        .isCompletedWithValueMatching(BlobSidecarsAndValidationResult::isFailure, "is failure")
        .isCompletedWithValueMatching(result -> !result.isValid(), "is not valid")
        .isCompletedWithValueMatching(
            result -> result.getValidationResult() == BlobSidecarsValidationResult.NOT_AVAILABLE,
            "is not available")
        .isCompletedWithValueMatching(
            result -> result.getBlobSidecars().isEmpty(), "doesn't have blob sidecars");
  }

  private void assertAvailable(
      SafeFuture<BlobSidecarsAndValidationResult> availabilityOrValidityCheck) {
    assertThat(availabilityOrValidityCheck)
        .isCompletedWithValueMatching(result -> !result.isFailure(), "is not failure")
        .isCompletedWithValueMatching(BlobSidecarsAndValidationResult::isValid, "is valid")
        .isCompletedWithValueMatching(
            result -> result.getValidationResult() == BlobSidecarsValidationResult.VALID,
            "is valid")
        .isCompletedWithValueMatching(
            result -> result.getBlobSidecars().equals(blobSidecarsComplete), "has blob sidecars");
  }

  private void prepareInitialAvailabilityWithEmptyCommitmentsBlock() {
    prepareInitialAvailability(
        Availability.FULL,
        Optional.of(dataStructureUtil.randomSignedBeaconBlockWithEmptyCommitments()));
  }

  private void prepareForImmediateTimeout() {
    prepareInitialAvailability(Availability.PARTIAL, Optional.empty(), Duration.ZERO);
  }

  private void prepareInitialAvailability(final Availability blobsAvailability) {
    prepareInitialAvailability(blobsAvailability, Optional.empty(), Duration.ofSeconds(30));
  }

  private void prepareInitialAvailability(
      final Availability blobsAvailability, final Optional<SignedBeaconBlock> providedBlock) {
    prepareInitialAvailability(blobsAvailability, providedBlock, Duration.ofSeconds(30));
  }

  private void prepareInitialAvailability(
      final Availability blobsAvailability,
      final Optional<SignedBeaconBlock> providedBlock,
      final Duration timeout) {
    block = providedBlock.orElse(dataStructureUtil.randomSignedBeaconBlockWithCommitments(4));
    blobSidecarsComplete = dataStructureUtil.randomBlobSidecarsForBlock(block);
    kzgCommitmentsComplete =
        block
            .getBeaconBlock()
            .orElseThrow()
            .getBody()
            .toVersionDeneb()
            .orElseThrow()
            .getBlobKzgCommitments()
            .stream()
            .map(SszKZGCommitment::getKZGCommitment)
            .toList();

    when(spec.isAvailabilityOfBlobSidecarsRequiredAtSlot(store, block.getSlot())).thenReturn(true);

    switch (blobsAvailability) {
      case FULL:
        blobSidecarsInitial = blobSidecarsComplete;
        blobSidecarsAdditional = List.of();
        break;
      case EMPTY:
        blobSidecarsInitial = List.of();
        blobSidecarsAdditional = blobSidecarsComplete;
        break;
      case PARTIAL:
        blobSidecarsInitial = List.of(blobSidecarsComplete.get(0), blobSidecarsComplete.get(2));
        blobSidecarsAdditional = List.of(blobSidecarsComplete.get(1), blobSidecarsComplete.get(3));
        break;
    }

    final ImmutableSortedMap.Builder<UInt64, BlobSidecar> mapBuilder =
        ImmutableSortedMap.naturalOrder();
    blobSidecarsInitial.forEach(blobSidecar -> mapBuilder.put(blobSidecar.getIndex(), blobSidecar));

    when(blockBlobSidecarsTracker.getBlock()).thenReturn(block.getBeaconBlock());
    when(blockBlobSidecarsTracker.getBlobSidecars()).thenReturn(mapBuilder.build());
    when(blockBlobSidecarsTracker.getSlotAndBlockRoot()).thenReturn(block.getSlotAndBlockRoot());

    blobSidecarsAvailabilityChecker =
        new ForkChoiceBlobSidecarsAvailabilityChecker(
            spec, asyncRunner, recentChainData, blockBlobSidecarsTracker, timeout);
  }

  private void completeTrackerWith(final List<BlobSidecar> blobSidecars) {
    final ImmutableSortedMap.Builder<UInt64, BlobSidecar> mapBuilder =
        ImmutableSortedMap.naturalOrder();
    blobSidecars.forEach(blobSidecar -> mapBuilder.put(blobSidecar.getIndex(), blobSidecar));
    when(blockBlobSidecarsTracker.getBlobSidecars()).thenReturn(mapBuilder.build());
    when(blockBlobSidecarsTracker.isCompleted()).thenReturn(true);
    trackerCompletionFuture.complete(null);
  }

  private OngoingStubbing<Boolean> whenDataAvailability(final List<BlobSidecar> blobSidecars) {
    return when(miscHelpers.verifyBlobKzgProofBatch(eq(blobSidecars)));
  }

  private void throwWhenValidatingBlobSidecarsBatchAgainstBlock(
      final List<BlobSidecar> blobSidecars, final Throwable cause) {
    doThrow(cause)
        .when(miscHelpers)
        .validateBlobSidecarsBatchAgainstBlock(
            eq(blobSidecars),
            argThat(block -> block.equals(this.block.getBeaconBlock().orElseThrow())),
            assertArg(
                kzgCommitmentsArg ->
                    assertThat(kzgCommitmentsArg).isEqualTo(kzgCommitmentsComplete)));
  }

  private void throwWhenVerifyingBlobSidecarCompleteness(
      final List<BlobSidecar> blobSidecars, final Throwable cause) {
    doThrow(cause)
        .when(miscHelpers)
        .verifyBlobSidecarCompleteness(
            eq(blobSidecars),
            assertArg(
                kzgCommitmentsArg ->
                    assertThat(kzgCommitmentsArg).isEqualTo(kzgCommitmentsComplete)));
  }

  private void verifyValidationAndDataAvailabilityCall(
      final List<BlobSidecar> blobSidecars, final boolean isFinalValidation) {
    verify(miscHelpers, times(1))
        .validateBlobSidecarsBatchAgainstBlock(
            eq(blobSidecars),
            argThat(block -> block.equals(this.block.getBeaconBlock().orElseThrow())),
            assertArg(
                kzgCommitmentsArg ->
                    assertThat(kzgCommitmentsArg).isEqualTo(kzgCommitmentsComplete)));

    verify(miscHelpers, times(1)).verifyBlobKzgProofBatch(eq(blobSidecars));

    if (isFinalValidation) {
      verify(miscHelpers, times(1))
          .verifyBlobSidecarCompleteness(
              eq(blobSidecarsComplete),
              assertArg(
                  kzgCommitmentsArg ->
                      assertThat(kzgCommitmentsArg).isEqualTo(kzgCommitmentsComplete)));
    }

    // assume we verified all interaction before resetting
    verifyNoMoreInteractions(miscHelpers);
    reset(miscHelpers);
  }

  private void verifyDataAvailabilityNeverCalled() {
    verify(miscHelpers, never()).verifyBlobKzgProofBatch(any());
  }

  private void prepareBlockAndBlobSidecarsOutsideAvailabilityWindow() {
    block = dataStructureUtil.randomSignedBeaconBlock();
    blobSidecarsComplete = dataStructureUtil.randomBlobSidecarsForBlock(block);

    final ImmutableSortedMap.Builder<UInt64, BlobSidecar> mapBuilder =
        ImmutableSortedMap.naturalOrder();
    blobSidecarsComplete.forEach(
        blobSidecar -> mapBuilder.put(blobSidecar.getIndex(), blobSidecar));

    when(spec.isAvailabilityOfBlobSidecarsRequiredAtSlot(store, block.getSlot())).thenReturn(false);
    when(blockBlobSidecarsTracker.getBlock()).thenReturn(block.getBeaconBlock());
    when(blockBlobSidecarsTracker.getCompletionFuture()).thenReturn(SafeFuture.COMPLETE);
    when(blockBlobSidecarsTracker.getBlobSidecars()).thenReturn(ImmutableSortedMap.of());
    when(blockBlobSidecarsTracker.getSlotAndBlockRoot()).thenReturn(block.getSlotAndBlockRoot());

    blobSidecarsAvailabilityChecker =
        new ForkChoiceBlobSidecarsAvailabilityChecker(
            spec, asyncRunner, recentChainData, blockBlobSidecarsTracker, Duration.ofSeconds(30));
  }

  private enum Availability {
    EMPTY,
    PARTIAL,
    FULL
  }

  private enum BatchFailure {
    BLOB_SIDECAR_VALIDATION_EXCEPTION,
    IS_DATA_AVAILABLE_EXCEPTION,
    IS_DATA_AVAILABLE_RETURN_FALSE,
  }
}
