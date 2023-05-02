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

import static com.google.common.base.Preconditions.checkState;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Suppliers;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.SortedMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.exceptions.ExceptionUtil;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZGCommitment;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.logic.versions.deneb.blobs.BlobSidecarsAndValidationResult;
import tech.pegasys.teku.spec.logic.versions.deneb.blobs.BlobSidecarsAvailabilityChecker;
import tech.pegasys.teku.statetransition.blobs.BlockBlobSidecarsTracker;
import tech.pegasys.teku.storage.client.RecentChainData;

public class ForkChoiceBlobSidecarsAvailabilityChecker implements BlobSidecarsAvailabilityChecker {
  private static final Duration DEFAULT_WAIT_FOR_TRACKER_COMPLETION_TIMEOUT = Duration.ofSeconds(4);
  private static final Logger LOG = LogManager.getLogger();

  private final Spec spec;
  private final AsyncRunner asyncRunner;
  private final RecentChainData recentChainData;
  private final BlockBlobSidecarsTracker blockBlobSidecarsTracker;

  private final NavigableMap<UInt64, BlobSidecar> validatedBlobSidecars =
      new ConcurrentSkipListMap<>();

  private final SafeFuture<BlobSidecarsAndValidationResult> validationResult = new SafeFuture<>();

  private final Supplier<List<KZGCommitment>> kzgCommitmentsSupplier =
      createLazyKzgCommitmentsSupplier(this);

  private final Duration waitForTrackerCompletionTimeout;

  public ForkChoiceBlobSidecarsAvailabilityChecker(
      final Spec spec,
      final AsyncRunner asyncRunner,
      final RecentChainData recentChainData,
      final BlockBlobSidecarsTracker blockBlobSidecarsTracker) {
    this.spec = spec;
    this.asyncRunner = asyncRunner;
    this.recentChainData = recentChainData;
    this.blockBlobSidecarsTracker = blockBlobSidecarsTracker;
    this.waitForTrackerCompletionTimeout = DEFAULT_WAIT_FOR_TRACKER_COMPLETION_TIMEOUT;
  }

  @VisibleForTesting
  ForkChoiceBlobSidecarsAvailabilityChecker(
      final Spec spec,
      final AsyncRunner asyncRunner,
      final RecentChainData recentChainData,
      final BlockBlobSidecarsTracker blockBlobSidecarsTracker,
      final Duration waitForTrackerCompletionTimeout) {
    this.spec = spec;
    this.asyncRunner = asyncRunner;
    this.recentChainData = recentChainData;
    this.blockBlobSidecarsTracker = blockBlobSidecarsTracker;
    this.waitForTrackerCompletionTimeout = waitForTrackerCompletionTimeout;
  }

  @Override
  public boolean initiateDataAvailabilityCheck() {
    asyncRunner
        .runAsync(this::validatePartially)
        .thenCompose(this::completeValidation)
        .propagateTo(validationResult);

    return true;
  }

  @Override
  public SafeFuture<BlobSidecarsAndValidationResult> getAvailabilityCheckResult() {
    return validationResult;
  }

  @Override
  public SafeFuture<BlobSidecarsAndValidationResult> validate(
      final List<BlobSidecar> blobSidecars) {
    return SafeFuture.of(
        () -> {
          if (!blobSidecars.isEmpty()) {
            return internalValidate(blobSidecars);
          }

          if (!isBlockInDataAvailabilityWindow()) {
            return BlobSidecarsAndValidationResult.NOT_REQUIRED;
          }

          if (kzgCommitmentsSupplier.get().isEmpty()) {
            return BlobSidecarsAndValidationResult.validResult(List.of());
          }

          return BlobSidecarsAndValidationResult.NOT_AVAILABLE;
        });
  }

  private BlobSidecarsAndValidationResult internalValidate(final List<BlobSidecar> blobSidecars) {
    return internalValidate(blobSidecars, kzgCommitmentsSupplier.get());
  }

  private BlobSidecarsAndValidationResult internalValidate(
      final List<BlobSidecar> blobSidecars, final List<KZGCommitment> kzgCommitments) {
    final SlotAndBlockRoot slotAndBlockRoot = blockBlobSidecarsTracker.getSlotAndBlockRoot();
    try {
      if (!spec.atSlot(slotAndBlockRoot.getSlot())
          .miscHelpers()
          .isDataAvailable(
              slotAndBlockRoot.getSlot(),
              slotAndBlockRoot.getBlockRoot(),
              kzgCommitments,
              blobSidecars)) {
        return BlobSidecarsAndValidationResult.invalidResult(blobSidecars);
      }
    } catch (final Exception ex) {
      return BlobSidecarsAndValidationResult.invalidResult(blobSidecars, ex);
    }

    return BlobSidecarsAndValidationResult.validResult(blobSidecars);
  }

  /**
   * Step 1 of 2
   *
   * <p>This function performs a partial validation with blobs that are currently available from the
   * tracker. If all blobs are already available, it performs a full validation.
   *
   * @return a validation result only in case it is a definitive result. If the validation needs to
   *     be completed it returns empty
   */
  private Optional<BlobSidecarsAndValidationResult> validatePartially() {
    final List<KZGCommitment> kzgCommitmentsInBlock = kzgCommitmentsSupplier.get();

    final List<KZGCommitment> kzgCommitmentsToValidate;
    final List<BlobSidecar> blobSidecarsToValidate;

    final boolean performCompleteValidation = blockBlobSidecarsTracker.isCompleted();

    if (performCompleteValidation) {
      // the tracker contains all required blobs for our block: we can perform a complete validation
      kzgCommitmentsToValidate = kzgCommitmentsInBlock;
      blobSidecarsToValidate = List.copyOf(blockBlobSidecarsTracker.getBlobSidecars().values());
    } else {
      // prepare partial validation by matching the currently available blobs with the corresponding
      // commitments from the block
      kzgCommitmentsToValidate = new ArrayList<>();
      blobSidecarsToValidate =
          blockBlobSidecarsTracker.getBlobSidecars().values().stream()
              .peek(
                  blobSidecar ->
                      kzgCommitmentsToValidate.add(
                          kzgCommitmentsInBlock.get(blobSidecar.getIndex().intValue())))
              .collect(Collectors.toUnmodifiableList());

      if (blobSidecarsToValidate.isEmpty()
          && kzgCommitmentsInBlock.size() > 0
          && !isBlockInDataAvailabilityWindow()) {
        // there are no available blobs so far, but we are outside the availability window. We can
        // skip additional checks
        return Optional.of(BlobSidecarsAndValidationResult.NOT_REQUIRED);
      }
    }

    // perform the actual validation
    final BlobSidecarsAndValidationResult result =
        internalValidate(blobSidecarsToValidate, kzgCommitmentsToValidate);

    if (result.isFailure()) {
      return Optional.of(result);
    }

    if (performCompleteValidation) {
      return Optional.of(result);
    }

    // cache partially validated blobs
    blobSidecarsToValidate.forEach(
        blobSidecar -> validatedBlobSidecars.put(blobSidecar.getIndex(), blobSidecar));

    return Optional.empty();
  }

  /**
   * Step 2 of 2
   *
   * <p>This function completes the validation if necessary. If there are still blobs to validate,
   * it waits for the tracker to complete and then performs the final validation by computing and
   * validating the non-yet validated blobs-commitments, and then computes the final validation
   * result with the entire list of blobs
   *
   * @param maybeBlobSidecarsAndValidationResult is the validation result coming from Step 1.
   * @return final validation result
   */
  private SafeFuture<BlobSidecarsAndValidationResult> completeValidation(
      final Optional<BlobSidecarsAndValidationResult> maybeBlobSidecarsAndValidationResult) {

    return maybeBlobSidecarsAndValidationResult
        .map(SafeFuture::completedFuture)
        .orElseGet(
            () ->
                blockBlobSidecarsTracker
                    .getCompletionFuture()
                    .orTimeout(waitForTrackerCompletionTimeout)
                    .thenApplyChecked(__ -> internalValidateAdditional())
                    .thenApply(this::internalComputeFinalValidationResult)
                    .exceptionallyCompose(
                        error ->
                            ExceptionUtil.getCause(error, TimeoutException.class)
                                .map(
                                    timeoutException ->
                                        SafeFuture.completedFuture(
                                            BlobSidecarsAndValidationResult.notAvailable(
                                                timeoutException)))
                                .orElseGet(() -> SafeFuture.failedFuture(error))));
  }

  /**
   * Knowing that:
   *
   * <p>(1) `validatedBlobSidecars` contains already validated blobsSidecars
   *
   * <p>(2) `blockBlobSidecarsTracker` is now completed
   *
   * <p>This function computes and validates a batch of BlobSidecars-kzgCommitments that needs to be
   * additionally validated to verify the entire set
   *
   * @return validation result for the batch
   */
  private BlobSidecarsAndValidationResult internalValidateAdditional() {
    checkState(
        blockBlobSidecarsTracker.isCompleted(),
        "BlobSidecar tracker assumed to be completed but it is not.");

    final List<KZGCommitment> additionalKzgCommitmentsToBeValidated = new ArrayList<>();
    final List<BlobSidecar> additionalBlobSidecarsToBeValidated = new ArrayList<>();

    final List<KZGCommitment> kzgCommitmentsInBlock = kzgCommitmentsSupplier.get();
    final SortedMap<UInt64, BlobSidecar> completeBlobSidecars =
        blockBlobSidecarsTracker.getBlobSidecars();

    UInt64.range(UInt64.ZERO, UInt64.valueOf(kzgCommitmentsInBlock.size()))
        .forEachOrdered(
            index -> {
              if (!validatedBlobSidecars.containsKey(index)) {
                additionalKzgCommitmentsToBeValidated.add(
                    kzgCommitmentsInBlock.get(index.intValue()));

                Optional.ofNullable(completeBlobSidecars.get(index))
                    .ifPresentOrElse(
                        additionalBlobSidecarsToBeValidated::add,
                        () ->
                            LOG.error(
                                "Index {} not found in blob sidecar returned by tracker for block {}",
                                index,
                                blockBlobSidecarsTracker.getSlotAndBlockRoot().toLogString()));
              }
            });

    return internalValidate(
        additionalBlobSidecarsToBeValidated, additionalKzgCommitmentsToBeValidated);
  }

  /**
   * Computes the final validation result combining the already validated blobs from
   * `validatedBlobSidecars` and the additional validation result containing (if validated) the
   * required additional blobs to reconstruct the full list that will have to match the kzg
   * commitments from the block
   *
   * @param additionalBlobSidecarsAndValidationResult
   * @return
   */
  private BlobSidecarsAndValidationResult internalComputeFinalValidationResult(
      final BlobSidecarsAndValidationResult additionalBlobSidecarsAndValidationResult) {
    if (additionalBlobSidecarsAndValidationResult.isFailure()) {
      return additionalBlobSidecarsAndValidationResult;
    }

    // put all additional validated blobSidecar in validatedBlobSidecars which now should
    // be complete
    additionalBlobSidecarsAndValidationResult
        .getBlobSidecars()
        .forEach(blobSidecar -> validatedBlobSidecars.put(blobSidecar.getIndex(), blobSidecar));

    // let's create the final list of validated BlobSidecars making sure that indices and
    // final order are consistent
    final List<BlobSidecar> completeValidatedBlobSidecars = new ArrayList<>();
    validatedBlobSidecars.forEach(
        (index, blobSidecar) -> {
          checkState(
              index.equals(blobSidecar.getIndex())
                  && index.equals(UInt64.valueOf(completeValidatedBlobSidecars.size())),
              "Inconsistency detected during blob sidecar validation");
          completeValidatedBlobSidecars.add(blobSidecar);
        });

    if (completeValidatedBlobSidecars.size() < kzgCommitmentsSupplier.get().size()) {
      // we haven't verified enough blobs to match the commitments present in the block

      checkState(
          !isBlockInDataAvailabilityWindow(),
          "Validated blobs are less than commitments present in block");

      return BlobSidecarsAndValidationResult.NOT_REQUIRED;
    }

    return BlobSidecarsAndValidationResult.validResult(
        Collections.unmodifiableList(completeValidatedBlobSidecars));
  }

  private boolean isBlockInDataAvailabilityWindow() {
    return spec.isAvailabilityOfBlobSidecarsRequiredAtSlot(
        recentChainData.getStore(), blockBlobSidecarsTracker.getSlotAndBlockRoot().getSlot());
  }

  static Supplier<List<KZGCommitment>> createLazyKzgCommitmentsSupplier(
      final ForkChoiceBlobSidecarsAvailabilityChecker availabilityChecker) {
    return Suppliers.memoize(
        () ->
            availabilityChecker
                .blockBlobSidecarsTracker
                .getBlockBody()
                .orElseThrow()
                .toVersionDeneb()
                .orElseThrow()
                .getBlobKzgCommitments()
                .stream()
                .map(SszKZGCommitment::getKZGCommitment)
                .collect(Collectors.toUnmodifiableList()));
  }
}
