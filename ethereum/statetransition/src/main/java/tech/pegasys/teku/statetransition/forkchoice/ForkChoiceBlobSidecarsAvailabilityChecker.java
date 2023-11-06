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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.exceptions.ExceptionUtil;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZG;
import tech.pegasys.teku.kzg.KZGCommitment;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecarOld;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb.BeaconBlockBodyDeneb;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;
import tech.pegasys.teku.spec.logic.versions.deneb.blobs.BlobSidecarsAndValidationResult;
import tech.pegasys.teku.spec.logic.versions.deneb.blobs.BlobSidecarsAvailabilityChecker;
import tech.pegasys.teku.statetransition.blobs.BlockBlobSidecarsTracker;
import tech.pegasys.teku.storage.client.RecentChainData;

/**
 * Performs complete data availability check <a
 * href="https://github.com/ethereum/consensus-specs/blob/dev/specs/deneb/fork-choice.md#is_data_available">is_data_available</a>
 */
public class ForkChoiceBlobSidecarsAvailabilityChecker implements BlobSidecarsAvailabilityChecker {
  private static final Logger LOG = LogManager.getLogger();

  private final Spec spec;
  private final AsyncRunner asyncRunner;
  private final RecentChainData recentChainData;
  private final BlockBlobSidecarsTracker blockBlobSidecarsTracker;
  private final KZG kzg;

  private final NavigableMap<UInt64, BlobSidecarOld> validatedBlobSidecars =
      new ConcurrentSkipListMap<>();

  private final SafeFuture<BlobSidecarsAndValidationResult> validationResult = new SafeFuture<>();

  private final Supplier<List<KZGCommitment>> kzgCommitmentsFromBlockSupplier =
      createLazyKzgCommitmentsSupplier(this);

  private final Duration waitForTrackerCompletionTimeout;

  public ForkChoiceBlobSidecarsAvailabilityChecker(
      final Spec spec,
      final AsyncRunner asyncRunner,
      final RecentChainData recentChainData,
      final BlockBlobSidecarsTracker blockBlobSidecarsTracker,
      final KZG kzg) {
    this.spec = spec;
    this.asyncRunner = asyncRunner;
    this.recentChainData = recentChainData;
    this.blockBlobSidecarsTracker = blockBlobSidecarsTracker;
    this.kzg = kzg;
    this.waitForTrackerCompletionTimeout =
        calculateCompletionTimeout(spec, blockBlobSidecarsTracker.getSlotAndBlockRoot().getSlot());
  }

  @VisibleForTesting
  ForkChoiceBlobSidecarsAvailabilityChecker(
      final Spec spec,
      final AsyncRunner asyncRunner,
      final RecentChainData recentChainData,
      final BlockBlobSidecarsTracker blockBlobSidecarsTracker,
      final KZG kzg,
      final Duration waitForTrackerCompletionTimeout) {
    this.spec = spec;
    this.asyncRunner = asyncRunner;
    this.recentChainData = recentChainData;
    this.blockBlobSidecarsTracker = blockBlobSidecarsTracker;
    this.kzg = kzg;
    this.waitForTrackerCompletionTimeout = waitForTrackerCompletionTimeout;
  }

  @Override
  public boolean initiateDataAvailabilityCheck() {
    asyncRunner
        .runAsync(this::validateImmediatelyAvailable)
        .thenCompose(this::completeValidation)
        .thenApply(this::performCompletenessValidation)
        .propagateTo(validationResult);

    return true;
  }

  @Override
  public SafeFuture<BlobSidecarsAndValidationResult> getAvailabilityCheckResult() {
    return validationResult;
  }

  @Override
  public BlobSidecarsAndValidationResult validateImmediately(
      final List<BlobSidecarOld> blobSidecars) {

    final List<KZGCommitment> kzgCommitmentsFromBlock = kzgCommitmentsFromBlockSupplier.get();

    if (!blobSidecars.isEmpty()) {
      final BlobSidecarsAndValidationResult blobSidecarsAndValidationResult =
          validateBatch(blobSidecars);

      return performCompletenessValidation(blobSidecarsAndValidationResult);
    }

    // no blob sidecars

    if (isBlockOutsideDataAvailabilityWindow()) {
      return BlobSidecarsAndValidationResult.NOT_REQUIRED;
    }

    if (kzgCommitmentsFromBlock.isEmpty()) {
      return BlobSidecarsAndValidationResult.validResult(List.of());
    }

    return BlobSidecarsAndValidationResult.NOT_AVAILABLE;
  }

  private BlobSidecarsAndValidationResult validateBatch(final List<BlobSidecarOld> blobSidecars) {
    final BeaconBlock block = blockBlobSidecarsTracker.getBlock().orElseThrow();
    final SlotAndBlockRoot slotAndBlockRoot = blockBlobSidecarsTracker.getSlotAndBlockRoot();

    final MiscHelpers miscHelpers = spec.atSlot(slotAndBlockRoot.getSlot()).miscHelpers();

    try {
      miscHelpers.validateBlobSidecarsBatchAgainstBlock(
          blobSidecars, block, kzgCommitmentsFromBlockSupplier.get());

      if (!miscHelpers.verifyBlobKzgProofBatch(kzg, blobSidecars)) {
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
  private Optional<BlobSidecarsAndValidationResult> validateImmediatelyAvailable() {
    final List<KZGCommitment> kzgCommitmentsInBlock = kzgCommitmentsFromBlockSupplier.get();

    final List<BlobSidecarOld> blobSidecarsToValidate;

    final boolean performCompleteValidation = blockBlobSidecarsTracker.isCompleted();

    if (performCompleteValidation) {
      // the tracker contains all required blobs for our block: we can perform a complete validation
      blobSidecarsToValidate = List.copyOf(blockBlobSidecarsTracker.getBlobSidecars().values());
      LOG.debug(
          "The expected {} BlobSidecars have already been received. Performing full validation.",
          blobSidecarsToValidate.size());
    } else {
      // prepare partial validation by matching the currently available blobs with the corresponding
      // commitments from the block
      blobSidecarsToValidate =
          blockBlobSidecarsTracker.getBlobSidecars().values().stream().toList();

      LOG.debug(
          "{} out of {} BlobSidecars have been received so far. Performing partial validation.",
          blobSidecarsToValidate.size(),
          kzgCommitmentsInBlock.size());

      if (blobSidecarsToValidate.isEmpty()
          && !kzgCommitmentsInBlock.isEmpty()
          && isBlockOutsideDataAvailabilityWindow()) {
        // there are no available blobs so far, but we are outside the availability window. We can
        // skip additional checks
        return Optional.of(BlobSidecarsAndValidationResult.NOT_REQUIRED);
      }
    }

    // perform the actual validation
    final BlobSidecarsAndValidationResult result = validateBatch(blobSidecarsToValidate);

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
                    .thenApplyChecked(__ -> computeAndValidateRemaining())
                    .thenApply(this::computeFinalValidationResult)
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
   * <p>(1) `validatedBlobSidecars` contains already validated blobSidecars
   *
   * <p>(2) `blockBlobSidecarsTracker` is now completed
   *
   * <p>This function computes and validates a batch of BlobSidecars-kzgCommitments that needs to be
   * additionally validated to verify the entire set
   *
   * @return validation result for the batch
   */
  private BlobSidecarsAndValidationResult computeAndValidateRemaining() {
    checkState(
        blockBlobSidecarsTracker.isCompleted(),
        "BlobSidecar tracker assumed to be completed but it is not.");

    final List<BlobSidecarOld> additionalBlobSidecarsToBeValidated = new ArrayList<>();

    final List<KZGCommitment> kzgCommitmentsInBlock = kzgCommitmentsFromBlockSupplier.get();
    final SortedMap<UInt64, BlobSidecarOld> completeBlobSidecars =
        blockBlobSidecarsTracker.getBlobSidecars();

    UInt64.range(UInt64.ZERO, UInt64.valueOf(kzgCommitmentsInBlock.size()))
        .forEachOrdered(
            index -> {
              if (!validatedBlobSidecars.containsKey(index)) {

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

    LOG.debug(
        "Remaining {} out of {} BlobSidecars have been received. Completing validation.",
        additionalBlobSidecarsToBeValidated.size(),
        kzgCommitmentsInBlock.size());

    return validateBatch(additionalBlobSidecarsToBeValidated);
  }

  /**
   * Computes the final validation result combining the already validated blobs from
   * `validatedBlobSidecars`
   */
  private BlobSidecarsAndValidationResult computeFinalValidationResult(
      final BlobSidecarsAndValidationResult additionalBlobSidecarsAndValidationResult) {
    if (additionalBlobSidecarsAndValidationResult.isFailure()) {
      return additionalBlobSidecarsAndValidationResult;
    }

    // put all additional validated blobSidecar in validatedBlobSidecars which now should
    // be complete
    additionalBlobSidecarsAndValidationResult
        .getBlobSidecars()
        .forEach(blobSidecar -> validatedBlobSidecars.put(blobSidecar.getIndex(), blobSidecar));

    final List<BlobSidecarOld> completeValidatedBlobSidecars =
        new ArrayList<>(validatedBlobSidecars.values());

    return BlobSidecarsAndValidationResult.validResult(
        Collections.unmodifiableList(completeValidatedBlobSidecars));
  }

  /** Makes sure that final blob sidecar list is complete */
  private BlobSidecarsAndValidationResult performCompletenessValidation(
      final BlobSidecarsAndValidationResult blobSidecarsAndValidationResult) {

    if (blobSidecarsAndValidationResult.isFailure()
        || blobSidecarsAndValidationResult.isNotRequired()) {
      return blobSidecarsAndValidationResult;
    }

    try {
      spec.atSlot(blockBlobSidecarsTracker.getSlotAndBlockRoot().getSlot())
          .miscHelpers()
          .verifyBlobSidecarCompleteness(
              blobSidecarsAndValidationResult.getBlobSidecars(),
              kzgCommitmentsFromBlockSupplier.get());
    } catch (final IllegalArgumentException ex) {
      if (!isBlockOutsideDataAvailabilityWindow()) {
        return BlobSidecarsAndValidationResult.invalidResult(
            blobSidecarsAndValidationResult.getBlobSidecars(),
            new IllegalArgumentException(
                "Validated blobs are less than commitments present in block.", ex));
      }

      LOG.error(
          "Inconsistent state detected: validated blobs is less then commitments in block. Since slot is outside availability the window we can consider blobs as NOT_REQUIRED.");
      return BlobSidecarsAndValidationResult.NOT_REQUIRED;
    }

    return blobSidecarsAndValidationResult;
  }

  private boolean isBlockOutsideDataAvailabilityWindow() {
    return !spec.isAvailabilityOfBlobSidecarsRequiredAtSlot(
        recentChainData.getStore(), blockBlobSidecarsTracker.getSlotAndBlockRoot().getSlot());
  }

  static Supplier<List<KZGCommitment>> createLazyKzgCommitmentsSupplier(
      final ForkChoiceBlobSidecarsAvailabilityChecker availabilityChecker) {
    return Suppliers.memoize(
        () ->
            BeaconBlockBodyDeneb.required(
                    availabilityChecker.blockBlobSidecarsTracker.getBlock().orElseThrow().getBody())
                .getBlobKzgCommitments()
                .stream()
                .map(SszKZGCommitment::getKZGCommitment)
                .toList());
  }

  static Duration calculateCompletionTimeout(final Spec spec, final UInt64 slot) {
    return Duration.ofMillis((spec.atSlot(slot).getConfig().getSecondsPerSlot() * 1000L) / 3);
  }
}
