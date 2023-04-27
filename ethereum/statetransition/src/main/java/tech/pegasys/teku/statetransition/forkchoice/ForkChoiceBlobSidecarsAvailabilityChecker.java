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

import static tech.pegasys.teku.spec.logic.versions.deneb.blobs.BlobSidecarsAndValidationResult.NOT_REQUIRED_RESULT_FUTURE;

import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.ssz.SszList;
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

  private final Spec spec;
  private final AsyncRunner asyncRunner;
  private final RecentChainData recentChainData;
  private final BlockBlobSidecarsTracker blockBlobSidecarsTracker;

  private final NavigableMap<UInt64, BlobSidecar> validatedBlobSidecars =
      new ConcurrentSkipListMap<>();

  private Optional<SafeFuture<BlobSidecarsAndValidationResult>> validationResult = Optional.empty();

  public ForkChoiceBlobSidecarsAvailabilityChecker(
      final Spec spec,
      final AsyncRunner asyncRunner,
      final RecentChainData recentChainData,
      final BlockBlobSidecarsTracker blockBlobSidecarsTracker) {
    this.spec = spec;
    this.asyncRunner = asyncRunner;
    this.recentChainData = recentChainData;
    this.blockBlobSidecarsTracker = blockBlobSidecarsTracker;
  }

  @Override
  public boolean initiateDataAvailabilityCheck() {
    validationResult =
        Optional.of(
            asyncRunner.runAsync(this::validatePartially).thenCompose(this::completeValidation));

    return true;
  }

  @Override
  public SafeFuture<BlobSidecarsAndValidationResult> getAvailabilityCheckResult() {
    return validationResult.orElse(NOT_REQUIRED_RESULT_FUTURE);
  }

  @Override
  public SafeFuture<BlobSidecarsAndValidationResult> validate(
      final List<BlobSidecar> blobSidecars) {
    return SafeFuture.of(
        () -> {
          if (!blobSidecars.isEmpty()) {
            return internalValidate(blobSidecars);
          }

          // When no blobs are available, it is ok to not have them (NOT_REQUIRED) if:

          // 1. The block is not in the availability window
          if (!isBlockInDataAvailabilityWindow()) {
            return BlobSidecarsAndValidationResult.NOT_REQUIRED;
          }
          // 2. The number of kzg commitments in the block is 0
          if (getKzgCommitmentsInBlock().isEmpty()) {
            return BlobSidecarsAndValidationResult.NOT_REQUIRED;
          }

          return BlobSidecarsAndValidationResult.NOT_AVAILABLE;
        });
  }

  private BlobSidecarsAndValidationResult internalValidate(final List<BlobSidecar> blobSidecars) {
    final SlotAndBlockRoot slotAndBlockRoot = blockBlobSidecarsTracker.getSlotAndBlockRoot();
    try {
      if (!spec.atSlot(slotAndBlockRoot.getSlot())
          .miscHelpers()
          .isDataAvailable(
              slotAndBlockRoot.getSlot(),
              slotAndBlockRoot.getBlockRoot(),
              getKzgCommitmentsInBlock().stream()
                  .map(SszKZGCommitment::getKZGCommitment)
                  .collect(Collectors.toUnmodifiableList()),
              blobSidecars)) {
        return BlobSidecarsAndValidationResult.invalidResult(blobSidecars);
      }
    } catch (final Exception ex) {
      return BlobSidecarsAndValidationResult.invalidResult(blobSidecars, ex);
    }

    return BlobSidecarsAndValidationResult.validResult(blobSidecars);
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

  private Optional<BlobSidecarsAndValidationResult> validatePartially() {
    final SlotAndBlockRoot slotAndBlockRoot = blockBlobSidecarsTracker.getSlotAndBlockRoot();

    final SszList<SszKZGCommitment> kzgCommitmentsInBlock = getKzgCommitmentsInBlock();

    final List<KZGCommitment> kzgCommitments;
    final List<BlobSidecar> blobSidecars;

    final boolean performCompleteValidation =
        blockBlobSidecarsTracker.getCompletionFuture().isDone();

    if (performCompleteValidation) {
      kzgCommitments = new ArrayList<>();
      blobSidecars =
          blockBlobSidecarsTracker.getBlobSidecars().values().stream()
              .peek(
                  blobSidecar ->
                      kzgCommitments.add(
                          kzgCommitmentsInBlock
                              .get(blobSidecar.getIndex().intValue())
                              .getKZGCommitment()))
              .collect(Collectors.toUnmodifiableList());
    } else {
      kzgCommitments =
          kzgCommitmentsInBlock.stream()
              .map(SszKZGCommitment::getKZGCommitment)
              .collect(Collectors.toUnmodifiableList());
      blobSidecars = List.copyOf(blockBlobSidecarsTracker.getBlobSidecars().values());
    }

    final BlobSidecarsAndValidationResult result = internalValidate(blobSidecars, kzgCommitments);

    if (result.isFailure()) {
      return Optional.of(result);
    }

    if (performCompleteValidation) {
      return Optional.of(result);
    }

    // cache partially validated blobs
    blobSidecars.forEach(
        blobSidecar -> validatedBlobSidecars.put(blobSidecar.getIndex(), blobSidecar));

    return Optional.empty();
  }

  private SafeFuture<BlobSidecarsAndValidationResult> completeValidation(
      final Optional<BlobSidecarsAndValidationResult> maybeBlobSidecarsAndValidationResult) {

    if (maybeBlobSidecarsAndValidationResult.isPresent()) {
      // already fully validated or partial validation already failed
      return SafeFuture.completedFuture(maybeBlobSidecarsAndValidationResult.get());
    }

    return blockBlobSidecarsTracker
        .getCompletionFuture()
        .orTimeout(4, TimeUnit.SECONDS)
        .thenApply(
            __ -> {
              final SszList<SszKZGCommitment> kzgCommitmentsInBlock = getKzgCommitmentsInBlock();
              final List<KZGCommitment> kzgCommitments = new ArrayList<>();
              final List<BlobSidecar> additionalBlobSidecarsToBeValidated =
                  blockBlobSidecarsTracker.getBlobSidecars().entrySet().stream()
                      .filter(entry -> !validatedBlobSidecars.containsKey(entry.getKey()))
                      .peek(
                          entry ->
                              kzgCommitments.add(
                                  kzgCommitmentsInBlock
                                      .get(entry.getKey().intValue())
                                      .getKZGCommitment()))
                      .map(Entry::getValue)
                      .collect(Collectors.toUnmodifiableList());

              return internalValidate(additionalBlobSidecarsToBeValidated, kzgCommitments);
            });
  }

  private boolean isBlockInDataAvailabilityWindow() {
    return spec.isAvailabilityOfBlobSidecarsRequiredAtSlot(
        recentChainData.getStore(), blockBlobSidecarsTracker.getSlotAndBlockRoot().getSlot());
  }

  private SszList<SszKZGCommitment> getKzgCommitmentsInBlock() {
    return blockBlobSidecarsTracker
        .getBlockBody()
        .orElseThrow()
        .toVersionDeneb()
        .orElseThrow()
        .getBlobKzgCommitments();
  }
}
