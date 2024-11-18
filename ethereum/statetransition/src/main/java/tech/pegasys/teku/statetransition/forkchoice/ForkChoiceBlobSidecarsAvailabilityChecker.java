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
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
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
private final Spec spec;
  private final RecentChainData recentChainData;
  private final BlockBlobSidecarsTracker blockBlobSidecarsTracker;

  private final SafeFuture<BlobSidecarsAndValidationResult> validationResult = new SafeFuture<>();

  private final Duration waitForTrackerCompletionTimeout;

  public ForkChoiceBlobSidecarsAvailabilityChecker(
      final Spec spec,
      final RecentChainData recentChainData,
      final BlockBlobSidecarsTracker blockBlobSidecarsTracker) {
    this.spec = spec;
    this.recentChainData = recentChainData;
    this.blockBlobSidecarsTracker = blockBlobSidecarsTracker;
    this.waitForTrackerCompletionTimeout =
        calculateCompletionTimeout(spec, blockBlobSidecarsTracker.getSlotAndBlockRoot().getSlot());
  }

  @VisibleForTesting
  ForkChoiceBlobSidecarsAvailabilityChecker(
      final Spec spec,
      final RecentChainData recentChainData,
      final BlockBlobSidecarsTracker blockBlobSidecarsTracker,
      final Duration waitForTrackerCompletionTimeout) {
    this.spec = spec;
    this.recentChainData = recentChainData;
    this.blockBlobSidecarsTracker = blockBlobSidecarsTracker;
    this.waitForTrackerCompletionTimeout = waitForTrackerCompletionTimeout;
  }

  @Override
  public boolean initiateDataAvailabilityCheck() {
    blockBlobSidecarsTracker
            .getCompletionFuture()
            .orTimeout(waitForTrackerCompletionTimeout)
            .thenApply(__ -> BlobSidecarsAndValidationResult.validResult(List.copyOf(blockBlobSidecarsTracker.getBlobSidecars().values())))
            .exceptionallyCompose(
                    error ->
                            ExceptionUtil.getCause(error, TimeoutException.class)
                                    .map(
                                            timeoutException -> {
                                              if(isBlockOutsideDataAvailabilityWindow()) {
                                                return SafeFuture.completedFuture(BlobSidecarsAndValidationResult.NOT_REQUIRED);
                                              }
                                                    return SafeFuture.completedFuture(
                                                            BlobSidecarsAndValidationResult.notAvailable(
                                                                    timeoutException));})
                                    .orElseGet(() -> SafeFuture.failedFuture(error)))
            .propagateTo(validationResult);
    return true;
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
    return Duration.ofMillis((spec.atSlot(slot).getConfig().getSecondsPerSlot() * 1000L) / 3);
  }
}
