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
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.eip4844.BeaconBlockBodyEip4844;
import tech.pegasys.teku.spec.datastructures.execution.versions.eip4844.BlobsSidecar;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.logic.versions.eip4844.blobs.BlobsSidecarAvailabilityChecker;
import tech.pegasys.teku.storage.client.RecentChainData;

public class ForkChoiceBlobsSidecarAvailabilityChecker implements BlobsSidecarAvailabilityChecker {
  private SpecVersion specVersion;
  private RecentChainData recentChainData;
  private SignedBeaconBlock block;
  private Optional<BlobsSidecar> blobsSidecar;

  public ForkChoiceBlobsSidecarAvailabilityChecker(
      final SpecVersion specVersion,
      final RecentChainData recentChainData,
      final SignedBeaconBlock block,
      final Optional<BlobsSidecar> blobsSidecar) {
    this.specVersion = specVersion;
    this.recentChainData = recentChainData;
    this.block = block;
    this.blobsSidecar = blobsSidecar;
  }

  @Override
  public boolean retrieveBlobsSidecar() {
    return true;
  }

  @Override
  public SafeFuture<BlobsSidecarAndValidationResult> validateBlobsSidecar() {

    if (!isBlockInDataAvailabilityWindow()) {
      return BlobsSidecarAvailabilityChecker.NOT_REQUIRED_RESULT;
    }

    if (blobsSidecar.isEmpty()) {
      return BlobsSidecarAvailabilityChecker.NOT_AVAILABLE_RESULT;
    }

    final BeaconBlockBodyEip4844 blockBody =
        block
            .getBeaconBlock()
            .map(BeaconBlock::getBody)
            .flatMap(BeaconBlockBody::toVersionEip4844)
            .orElseThrow();

    if (!specVersion
        .miscHelpers()
        .isDataAvailable(
            block.getSlot(),
            block.getBodyRoot(),
            blockBody.getBlobKzgCommitments().stream()
                .map(SszKZGCommitment::getKZGCommitment)
                .collect(Collectors.toUnmodifiableList()),
            blobsSidecar.get())) {
      return BlobsSidecarAvailabilityChecker.invalidResult(blobsSidecar);
    }

    return BlobsSidecarAvailabilityChecker.validResult(blobsSidecar);
  }

  private boolean isBlockInDataAvailabilityWindow() {
    final UInt64 currentSlot = recentChainData.getCurrentSlot().orElseThrow();

    if (block
        .getSlot()
        .minusMinZero(currentSlot)
        .isGreaterThan(
            (long) specVersion.getSlotsPerEpoch() * MIN_EPOCHS_FOR_BLOBS_SIDECARS_REQUESTS)) {
      return false;
    }

    return true;
  }
}
