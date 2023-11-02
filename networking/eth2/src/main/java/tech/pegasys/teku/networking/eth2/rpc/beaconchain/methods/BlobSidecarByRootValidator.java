/*
 * Copyright Consensys Software Inc., 2022
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

package tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods;

import static tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.BlobSidecarsResponseInvalidResponseException.InvalidResponseType.BLOB_SIDECAR_KZG_VERIFICATION_FAILED;
import static tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.BlobSidecarsResponseInvalidResponseException.InvalidResponseType.BLOB_SIDECAR_UNEXPECTED_IDENTIFIER;

import java.util.Set;
import tech.pegasys.teku.kzg.KZG;
import tech.pegasys.teku.networking.p2p.peer.Peer;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BlobIdentifier;

public class BlobSidecarByRootValidator extends AbstractBlobSidecarsValidator {

  private final Peer peer;
  private final Set<BlobIdentifier> expectedBlobIdentifiers;

  public BlobSidecarByRootValidator(
      final Peer peer,
      final Spec spec,
      final KZG kzg,
      final Set<BlobIdentifier> expectedBlobIdentifiers) {
    super(spec, kzg);
    this.peer = peer;
    this.expectedBlobIdentifiers = expectedBlobIdentifiers;
  }

  public void validateBlobSidecar(final BlobSidecar blobSidecar) {
    final BlobIdentifier blobIdentifier =
        new BlobIdentifier(blobSidecar.getBlockRoot(), blobSidecar.getIndex());
    if (!expectedBlobIdentifiers.contains(blobIdentifier)) {
      throw new BlobSidecarsResponseInvalidResponseException(
          peer, BLOB_SIDECAR_UNEXPECTED_IDENTIFIER);
    }

    if (!verifyBlobSidecarKzg(blobSidecar)) {
      throw new BlobSidecarsResponseInvalidResponseException(
          peer, BLOB_SIDECAR_KZG_VERIFICATION_FAILED);
    }
  }
}
