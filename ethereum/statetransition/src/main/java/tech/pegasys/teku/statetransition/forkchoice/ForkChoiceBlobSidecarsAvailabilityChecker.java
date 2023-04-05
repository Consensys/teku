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

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import tech.pegasys.teku.dataproviders.lookup.BlobSidecarsProvider;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb.BeaconBlockBodyDeneb;
import tech.pegasys.teku.spec.datastructures.execution.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
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
          if (blobSidecars.size() > 0) {
            return internalValidate(blobSidecars);
          }

          // when no blobs are available, we check if it is ok to not have them based on
          // the required availability window.
          if (isBlockInDataAvailabilityWindow()) {
            return BlobSidecarsAndValidationResult.NOT_AVAILABLE;
          }

          // block is older than the availability window
          return BlobSidecarsAndValidationResult.NOT_REQUIRED;
        });
  }

  private BlobSidecarsAndValidationResult internalValidate(final List<BlobSidecar> blobSidecars) {
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
}
