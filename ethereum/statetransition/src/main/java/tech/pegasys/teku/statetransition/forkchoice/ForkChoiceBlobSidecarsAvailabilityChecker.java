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

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import tech.pegasys.teku.dataproviders.lookup.BlobSidecarsProvider;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.logic.versions.deneb.blobs.BlobSidecarsAndValidationResult;
import tech.pegasys.teku.spec.logic.versions.deneb.blobs.BlobSidecarsAvailabilityChecker;
import tech.pegasys.teku.storage.client.RecentChainData;

public class ForkChoiceBlobSidecarsAvailabilityChecker implements BlobSidecarsAvailabilityChecker {

  private final Spec spec;
  private final SpecVersion specVersion;
  private final RecentChainData recentChainData;
  private final SignedBeaconBlock block;
  private final BlobSidecarsProvider blobSidecarsProvider;

  private Optional<SafeFuture<BlobSidecarsAndValidationResult>> validationResult = Optional.empty();

  public ForkChoiceBlobSidecarsAvailabilityChecker(
      final Spec spec,
      final SpecVersion specVersion,
      final RecentChainData recentChainData,
      final SignedBeaconBlock block,
      final BlobSidecarsProvider blobSidecarsProvider) {
    this.spec = spec;
    this.specVersion = specVersion;
    this.recentChainData = recentChainData;
    this.block = block;
    this.blobSidecarsProvider = blobSidecarsProvider;
  }

  @Override
  public boolean initiateDataAvailabilityCheck() {
    validationResult =
        Optional.of(blobSidecarsProvider.getBlobSidecars(block).thenCompose(this::validate));
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
    try {
      if (!specVersion
          .miscHelpers()
          .isDataAvailable(
              block.getSlot(),
              block.getRoot(),
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

  private boolean isBlockInDataAvailabilityWindow() {
    return spec.isAvailabilityOfBlobSidecarsRequiredAtSlot(
        recentChainData.getStore(), block.getSlot());
  }

  private SszList<SszKZGCommitment> getKzgCommitmentsInBlock() {
    return block.getMessage().getBody().toVersionDeneb().orElseThrow().getBlobKzgCommitments();
  }
}
