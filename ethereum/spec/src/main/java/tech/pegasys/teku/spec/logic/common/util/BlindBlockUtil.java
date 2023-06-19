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

package tech.pegasys.teku.spec.logic.common.util;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.datastructures.blobs.SignedBlobSidecarsUnblinder;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.SignedBlindedBlobSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.SignedBlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockBlinder;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockUnblinder;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlindedBlockContainer;

public abstract class BlindBlockUtil {

  public SafeFuture<SignedBeaconBlock> unblindSignedBeaconBlock(
      final SignedBlindedBlockContainer signedBlindedBlockContainer,
      final Consumer<SignedBeaconBlockUnblinder> beaconBlockUnblinderConsumer) {
    final SignedBeaconBlockUnblinder beaconBlockUnblinder =
        createSignedBeaconBlockUnblinder(signedBlindedBlockContainer);
    beaconBlockUnblinderConsumer.accept(beaconBlockUnblinder);
    return beaconBlockUnblinder.unblind();
  }

  public SafeFuture<List<SignedBlobSidecar>> unblindSignedBlobSidecars(
      final List<SignedBlindedBlobSidecar> signedBlindedBlobSidecars,
      final Consumer<SignedBlobSidecarsUnblinder> blobSidecarsUnblinderConsumer) {
    final SignedBlobSidecarsUnblinder blobSidecarsUnblinder =
        createSignedBlobSidecarsUnblinder(signedBlindedBlobSidecars)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Unblinder was not available but blob sidecars were blinded."));
    blobSidecarsUnblinderConsumer.accept(blobSidecarsUnblinder);
    return blobSidecarsUnblinder.unblind();
  }

  public SignedBeaconBlock blindSignedBeaconBlock(final SignedBeaconBlock signedBeaconBlock) {
    return getSignedBeaconBlockBlinder().blind(signedBeaconBlock);
  }

  protected abstract SignedBeaconBlockUnblinder createSignedBeaconBlockUnblinder(
      final SignedBlindedBlockContainer signedBlindedBlockContainer);

  protected abstract Optional<SignedBlobSidecarsUnblinder> createSignedBlobSidecarsUnblinder(
      final List<SignedBlindedBlobSidecar> signedBlindedBlobSidecars);

  protected abstract SignedBeaconBlockBlinder getSignedBeaconBlockBlinder();
}
