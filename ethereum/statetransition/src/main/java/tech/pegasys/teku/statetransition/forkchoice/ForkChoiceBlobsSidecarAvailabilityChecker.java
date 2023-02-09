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

package tech.pegasys.teku.statetransition.forkchoice;

import static tech.pegasys.teku.spec.config.Constants.MIN_EPOCHS_FOR_BLOBS_SIDECARS_REQUESTS;

import java.util.Optional;
import java.util.stream.Collectors;
import tech.pegasys.teku.dataproviders.lookup.BlobsSidecarProvider;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb.BeaconBlockBodyDeneb;
import tech.pegasys.teku.spec.datastructures.execution.versions.deneb.BlobsSidecar;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.logic.versions.deneb.blobs.BlobsSidecarAvailabilityChecker;
import tech.pegasys.teku.storage.client.RecentChainData;

public class ForkChoiceBlobsSidecarAvailabilityChecker implements BlobsSidecarAvailabilityChecker {

  private final SpecVersion specVersion;
  private final RecentChainData recentChainData;
  private final SignedBeaconBlock block;
  private final BlobsSidecarProvider blobsSidecarProvider;

  private Optional<SafeFuture<BlobsSidecarAndValidationResult>> validationResult = Optional.empty();

  public ForkChoiceBlobsSidecarAvailabilityChecker(
      final SpecVersion specVersion,
      final RecentChainData recentChainData,
      final SignedBeaconBlock block,
      final BlobsSidecarProvider blobsSidecarProvider) {
    this.specVersion = specVersion;
    this.recentChainData = recentChainData;
    this.block = block;
    this.blobsSidecarProvider = blobsSidecarProvider;
  }

  @Override
  public boolean initiateDataAvailabilityCheck() {
    validationResult =
        Optional.of(blobsSidecarProvider.getBlobsSidecar(block).thenCompose(this::validate));
    return true;
  }

  @Override
  public SafeFuture<BlobsSidecarAndValidationResult> getAvailabilityCheckResult() {
    return validationResult.orElse(NOT_REQUIRED_RESULT_FUTURE);
  }

  @Override
  public SafeFuture<BlobsSidecarAndValidationResult> validate(
      final Optional<BlobsSidecar> blobsSidecar) {
    return SafeFuture.of(
        () -> {
          // in the current Deneb specs, the blobsSidecar is immediately available with the block
          // so if we have it we do want to validate it regardless
          if (blobsSidecar.isPresent()) {
            return internalValidate(blobsSidecar.get());
          }

          // when blobs are not available, we check if it is ok to not have them based on
          // the required availability window.
          if (isBlockInDataAvailabilityWindow()) {
            return BlobsSidecarAndValidationResult.NOT_AVAILABLE;
          }

          // block is older than the availability window
          return BlobsSidecarAndValidationResult.NOT_REQUIRED;
        });
  }

  private BlobsSidecarAndValidationResult internalValidate(final BlobsSidecar blobsSidecar) {
    final BeaconBlockBodyDeneb blockBody =
        block
            .getBeaconBlock()
            .map(BeaconBlock::getBody)
            .flatMap(BeaconBlockBody::toVersionDeneb)
            .orElseThrow();
    try {
      if (!specVersion
          .miscHelpers()
          .isDataAvailable(
              block.getSlot(),
              block.getRoot(),
              blockBody.getBlobKzgCommitments().stream()
                  .map(SszKZGCommitment::getKZGCommitment)
                  .collect(Collectors.toUnmodifiableList()),
              blobsSidecar)) {
        return BlobsSidecarAndValidationResult.invalidResult(blobsSidecar);
      }
    } catch (final Exception ex) {
      return BlobsSidecarAndValidationResult.invalidResult(blobsSidecar, ex);
    }

    return BlobsSidecarAndValidationResult.validResult(blobsSidecar);
  }

  private boolean isBlockInDataAvailabilityWindow() {
    final UInt64 currentSlot = recentChainData.getCurrentSlot().orElseThrow();

    final UInt64 oldestSlotRequiringBlobs =
        currentSlot.minusMinZero(
            (long) specVersion.getSlotsPerEpoch() * MIN_EPOCHS_FOR_BLOBS_SIDECARS_REQUESTS);

    return block.getSlot().isGreaterThanOrEqualTo(oldestSlotRequiringBlobs);
  }
}
