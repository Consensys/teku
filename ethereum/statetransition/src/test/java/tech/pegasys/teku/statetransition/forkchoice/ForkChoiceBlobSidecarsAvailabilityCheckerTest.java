/*
 * Copyright ConsenSys Software Inc., 2023
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
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.assertArg;
import static org.mockito.ArgumentMatchers.eq;
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
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentMatcher;
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
  private List<KZGCommitment> kzgCommitmentsInitial;
  private List<BlobSidecar> blobSidecarsAdditional;
  private List<KZGCommitment> kzgCommitmentsAdditional;

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
    whenDataAvailability(blobSidecarsInitial, kzgCommitmentsInitial).thenReturn(true);

    // let availability check to be performed.
    asyncRunner.executeDueActions();

    // verify that kzg validation has been performed for the initial batch
    verifyDataAvailabilityCall(blobSidecarsInitial, kzgCommitmentsInitial);

    // mock the additional check to be OK.
    whenDataAvailability(blobSidecarsAdditional, kzgCommitmentsAdditional).thenReturn(true);

    // let the tracker complete with all blobSidecars
    completeTrackerWith(blobSidecarsComplete);

    Waiter.waitFor(availabilityCheckResult);

    // verify that kzg validation has been performed for the additional batch
    verifyDataAvailabilityCall(blobSidecarsAdditional, kzgCommitmentsAdditional);

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
    whenDataAvailability(blobSidecarsComplete, kzgCommitmentsComplete).thenReturn(true);

    // tracker is completed in advance
    completeTrackerWith(blobSidecarsComplete);

    // let availability check to be performed.
    asyncRunner.executeDueActions();

    // verify that kzg validation has been performed for the initial batch
    verifyDataAvailabilityCall(blobSidecarsComplete, kzgCommitmentsComplete);

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

    whenDataAvailability(blobSidecarsInitial, kzgCommitmentsInitial).thenReturn(true);

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
  @ValueSource(booleans = {true, false})
  void shouldReturnNotAvailableIfFirstBatchFails(final boolean failByException) {
    prepareInitialAvailability(Availability.PARTIAL);

    final Optional<Throwable> cause =
        failByException ? Optional.of(new RuntimeException("oops")) : Optional.empty();

    final SafeFuture<BlobSidecarsAndValidationResult> availabilityCheckResult =
        blobSidecarsAvailabilityChecker.getAvailabilityCheckResult();

    SafeFutureAssert.assertThatSafeFuture(availabilityCheckResult).isNotCompleted();

    // initiate availability check
    assertThat(blobSidecarsAvailabilityChecker.initiateDataAvailabilityCheck()).isTrue();

    // mock kzg availability check failure for the initial set
    if (failByException) {
      whenDataAvailability(blobSidecarsInitial, kzgCommitmentsInitial).thenThrow(cause.get());
    } else {
      whenDataAvailability(blobSidecarsInitial, kzgCommitmentsInitial).thenReturn(false);
    }

    asyncRunner.executeDueActions();

    assertInvalid(availabilityCheckResult, blobSidecarsInitial, cause);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void shouldReturnNotAvailableIfSecondBatchFails(final boolean failByException) {
    prepareInitialAvailability(Availability.PARTIAL);

    final Optional<Throwable> cause =
        failByException ? Optional.of(new RuntimeException("oops")) : Optional.empty();

    final SafeFuture<BlobSidecarsAndValidationResult> availabilityCheckResult =
        blobSidecarsAvailabilityChecker.getAvailabilityCheckResult();

    SafeFutureAssert.assertThatSafeFuture(availabilityCheckResult).isNotCompleted();

    // initiate availability check
    assertThat(blobSidecarsAvailabilityChecker.initiateDataAvailabilityCheck()).isTrue();

    // mock kzg availability check to be OK for the initial set
    whenDataAvailability(blobSidecarsInitial, kzgCommitmentsInitial).thenReturn(true);

    // let availability check to be performed.
    asyncRunner.executeDueActions();

    // mock kzg availability check failure for the initial set
    if (failByException) {
      whenDataAvailability(blobSidecarsAdditional, kzgCommitmentsAdditional).thenThrow(cause.get());
    } else {
      whenDataAvailability(blobSidecarsAdditional, kzgCommitmentsAdditional).thenReturn(false);
    }

    // let the tracker complete with all blobSidecars
    completeTrackerWith(blobSidecarsComplete);

    assertInvalid(availabilityCheckResult, blobSidecarsAdditional, cause);
  }

  @Test
  void shouldReturnNotAvailableIfTrackerLiesWithCompletionButItIsNot() {
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
    whenDataAvailability(blobSidecarsInitial, kzgCommitmentsInitial).thenReturn(true);

    // let availability check to be performed.
    asyncRunner.executeDueActions();

    // verify that kzg validation has been performed for the initial batch
    verifyDataAvailabilityCall(blobSidecarsInitial, kzgCommitmentsInitial);

    // we complete the blobs without index 3
    final List<BlobSidecar> partialBlobs = blobSidecarsComplete.subList(1, 2);
    // we lie on availability check too (not actually possible)
    whenDataAvailability(partialBlobs, kzgCommitmentsAdditional).thenReturn(true);

    // let the tracker complete with all blobSidecars
    completeTrackerWith(partialBlobs);

    SafeFutureAssert.assertThatSafeFuture(availabilityCheckResult)
        .isCompletedExceptionallyWith(IllegalStateException.class);
  }

  @Test
  void validate_shouldReturnAvailable() {
    prepareInitialAvailability(Availability.FULL);

    whenDataAvailability(blobSidecarsComplete, kzgCommitmentsComplete).thenReturn(true);

    assertAvailable(
        SafeFuture.completedFuture(
            blobSidecarsAvailabilityChecker.validateImmediately(blobSidecarsComplete)));
  }

  @Test
  void validate_shouldNotAvailable() {
    prepareInitialAvailability(Availability.FULL);

    assertNotAvailable(
        SafeFuture.completedFuture(
            blobSidecarsAvailabilityChecker.validateImmediately(Collections.emptyList())));
  }

  @Test
  void validate_shouldReturnAvailableOnEmptyBlobs() {
    prepareInitialAvailabilityWithEmptyCommitmentsBlock();

    assertAvailable(
        SafeFuture.completedFuture(
            blobSidecarsAvailabilityChecker.validateImmediately(Collections.emptyList())));
  }

  @Test
  void validate_shouldReturnNotRequiredWhenBlockIsOutsideAvailabilityWindow() {
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
            .collect(Collectors.toUnmodifiableList());

    when(spec.isAvailabilityOfBlobSidecarsRequiredAtSlot(store, block.getSlot())).thenReturn(true);

    switch (blobsAvailability) {
      case FULL:
        blobSidecarsInitial = blobSidecarsComplete;
        kzgCommitmentsInitial = kzgCommitmentsComplete;
        blobSidecarsAdditional = List.of();
        kzgCommitmentsAdditional = List.of();
        break;
      case EMPTY:
        blobSidecarsInitial = List.of();
        kzgCommitmentsInitial = List.of();
        blobSidecarsAdditional = blobSidecarsComplete;
        kzgCommitmentsAdditional = kzgCommitmentsComplete;
        break;
      case PARTIAL:
        blobSidecarsInitial = List.of(blobSidecarsComplete.get(0), blobSidecarsComplete.get(2));
        kzgCommitmentsInitial =
            List.of(kzgCommitmentsComplete.get(0), kzgCommitmentsComplete.get(2));

        blobSidecarsAdditional = List.of(blobSidecarsComplete.get(1), blobSidecarsComplete.get(3));
        kzgCommitmentsAdditional =
            List.of(kzgCommitmentsComplete.get(1), kzgCommitmentsComplete.get(3));
        break;
    }

    final ImmutableSortedMap.Builder<UInt64, BlobSidecar> mapBuilder =
        ImmutableSortedMap.naturalOrder();
    blobSidecarsInitial.forEach(blobSidecar -> mapBuilder.put(blobSidecar.getIndex(), blobSidecar));

    when(blockBlobSidecarsTracker.getBlockBody())
        .thenReturn(block.getMessage().getBody().toVersionDeneb());
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

  private OngoingStubbing<Boolean> whenDataAvailability(
      final List<BlobSidecar> blobSidecars, final List<KZGCommitment> kzgCommitments) {
    return when(
        miscHelpers.isDataAvailable(
            eq(block.getSlot()),
            eq(block.getRoot()),
            argThat(new KzgCommitmentsArgumentMatcher(kzgCommitments)),
            eq(blobSidecars)));
  }

  private void verifyDataAvailabilityCall(
      final List<BlobSidecar> blobSidecars, final List<KZGCommitment> kzgCommitments) {

    verify(miscHelpers, times(1))
        .isDataAvailable(
            eq(block.getSlot()),
            eq(block.getRoot()),
            assertArg(kzgCommitmentsArg -> assertThat(kzgCommitmentsArg).isEqualTo(kzgCommitments)),
            eq(blobSidecars));

    // assume we verified all interaction before resetting
    verifyNoMoreInteractions(miscHelpers);
    reset(miscHelpers);
  }

  private void verifyDataAvailabilityNeverCalled() {
    verify(miscHelpers, never()).isDataAvailable(any(), any(), any(), any());
  }

  private void prepareBlockAndBlobSidecarsOutsideAvailabilityWindow() {
    block = dataStructureUtil.randomSignedBeaconBlock();
    blobSidecarsComplete = dataStructureUtil.randomBlobSidecarsForBlock(block);

    final ImmutableSortedMap.Builder<UInt64, BlobSidecar> mapBuilder =
        ImmutableSortedMap.naturalOrder();
    blobSidecarsComplete.forEach(
        blobSidecar -> mapBuilder.put(blobSidecar.getIndex(), blobSidecar));

    when(spec.isAvailabilityOfBlobSidecarsRequiredAtSlot(store, block.getSlot())).thenReturn(false);
    when(blockBlobSidecarsTracker.getBlockBody())
        .thenReturn(block.getMessage().getBody().toVersionDeneb());
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

  private static class KzgCommitmentsArgumentMatcher
      implements ArgumentMatcher<List<KZGCommitment>> {

    private final List<KZGCommitment> kzgCommitments;

    private KzgCommitmentsArgumentMatcher(final List<KZGCommitment> kzgCommitments) {
      this.kzgCommitments = kzgCommitments;
    }

    @Override
    public boolean matches(final List<KZGCommitment> argument) {
      return kzgCommitments.equals(argument);
    }
  }
}
