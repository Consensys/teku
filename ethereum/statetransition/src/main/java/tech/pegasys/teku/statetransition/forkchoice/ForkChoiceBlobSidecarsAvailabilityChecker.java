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

import static tech.pegasys.teku.spec.constants.NetworkConstants.INTERVALS_PER_SLOT;
import static tech.pegasys.teku.spec.constants.NetworkConstants.INTERVALS_PER_SLOT_EIP7732;

import com.google.common.annotations.VisibleForTesting;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeoutException;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.exceptions.ExceptionUtil;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZG;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
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
  private final Spec spec;
  private final RecentChainData recentChainData;
  private final BlockBlobSidecarsTracker blockBlobSidecarsTracker;
  private final KZG kzg;

  private final SafeFuture<BlobSidecarsAndValidationResult> validationResult = new SafeFuture<>();

  private final Duration waitForTrackerCompletionTimeout;

  public ForkChoiceBlobSidecarsAvailabilityChecker(
      final Spec spec,
      final RecentChainData recentChainData,
      final BlockBlobSidecarsTracker blockBlobSidecarsTracker,
      final KZG kzg) {
    this.spec = spec;
    this.recentChainData = recentChainData;
    this.blockBlobSidecarsTracker = blockBlobSidecarsTracker;
    this.kzg = kzg;
    this.waitForTrackerCompletionTimeout =
        calculateCompletionTimeout(spec, blockBlobSidecarsTracker.getSlotAndBlockRoot().getSlot());
  }

  @VisibleForTesting
  ForkChoiceBlobSidecarsAvailabilityChecker(
      final Spec spec,
      final RecentChainData recentChainData,
      final BlockBlobSidecarsTracker blockBlobSidecarsTracker,
      final KZG kzg,
      final Duration waitForTrackerCompletionTimeout) {
    this.spec = spec;
    this.recentChainData = recentChainData;
    this.blockBlobSidecarsTracker = blockBlobSidecarsTracker;
    this.kzg = kzg;
    this.waitForTrackerCompletionTimeout = waitForTrackerCompletionTimeout;
  }

  @Override
  public boolean initiateDataAvailabilityCheck() {
    blockBlobSidecarsTracker
        .getCompletionFuture()
        .orTimeout(waitForTrackerCompletionTimeout)
        .thenApply(__ -> validateCompletedBlobSidecars())
        .exceptionallyCompose(
            error ->
                ExceptionUtil.getCause(error, TimeoutException.class)
                    .map(
                        timeoutException -> {
                          if (isBlockOutsideDataAvailabilityWindow()) {
                            return SafeFuture.completedFuture(
                                BlobSidecarsAndValidationResult.NOT_REQUIRED);
                          }
                          return SafeFuture.completedFuture(
                              BlobSidecarsAndValidationResult.notAvailable(timeoutException));
                        })
                    .orElseGet(() -> SafeFuture.failedFuture(error)))
        .propagateTo(validationResult);
    return true;
  }

  private BlobSidecarsAndValidationResult validateCompletedBlobSidecars() {
    final MiscHelpers miscHelpers =
        spec.atSlot(blockBlobSidecarsTracker.getSlotAndBlockRoot().getSlot()).miscHelpers();
    final List<BlobSidecar> blobSidecars =
        List.copyOf(blockBlobSidecarsTracker.getBlobSidecars().values());
    final SignedBeaconBlock block = blockBlobSidecarsTracker.getBlock().orElseThrow();

    try {
      if (!miscHelpers.verifyBlobKzgProofBatch(kzg, blobSidecars)) {
        return BlobSidecarsAndValidationResult.invalidResult(
            blobSidecars,
            new IllegalStateException("Blob sidecars failed verify_blob_kzg_proof_batch check"));
      }

      blockBlobSidecarsTracker
          .getExecutionPayloadEnvelope()
          .ifPresentOrElse(
              executionPayloadEnvelope ->
                  // ePBS
                  miscHelpers.verifyBlobSidecarCompleteness(blobSidecars, executionPayloadEnvelope),
              () -> miscHelpers.verifyBlobSidecarCompleteness(blobSidecars, block));
    } catch (final Exception ex) {
      return BlobSidecarsAndValidationResult.invalidResult(blobSidecars, ex);
    }

    if (!miscHelpers.verifyBlobSidecarBlockHeaderSignatureViaValidatedSignedBlock(
        blobSidecars, block)) {
      return BlobSidecarsAndValidationResult.invalidResult(
          blobSidecars,
          new IllegalStateException("Blob sidecars block header does not match signed block"));
    }

    return BlobSidecarsAndValidationResult.validResult(blobSidecars);
  }

  @Override
  public SafeFuture<BlobSidecarsAndValidationResult> getAvailabilityCheckResult() {
    return validationResult;
  }

  private boolean isBlockOutsideDataAvailabilityWindow() {
    return !spec.isAvailabilityOfBlobSidecarsRequiredAtSlot(
        recentChainData.getStore(), blockBlobSidecarsTracker.getSlotAndBlockRoot().getSlot());
  }

  static Duration calculateCompletionTimeout(final Spec spec, final UInt64 slot) {
    final SpecVersion specVersion = spec.atSlot(slot);
    final boolean ePBS = specVersion.getMilestone().isGreaterThanOrEqualTo(SpecMilestone.EIP7732);
    final int slotInterval = ePBS ? INTERVALS_PER_SLOT_EIP7732 : INTERVALS_PER_SLOT;
    final long millisPerSlot = specVersion.getConfig().getSecondsPerSlot() * 1000L;
    // give time of 3 intervals for ePBS blobs
    if (ePBS) {
      return Duration.ofMillis((millisPerSlot / slotInterval) * 3);
    }
    return Duration.ofMillis(millisPerSlot / slotInterval);
  }
}
