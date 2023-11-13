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

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import tech.pegasys.teku.kzg.KZG;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.BlobSidecarsResponseInvalidResponseException.InvalidResponseType;
import tech.pegasys.teku.networking.p2p.peer.Peer;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecarOld;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BlobIdentifier;

public class BlobSidecarsByRootValidator extends AbstractBlobSidecarsValidator {

  private final Set<BlobIdentifier> expectedBlobIdentifiers;

  public BlobSidecarsByRootValidator(
      final Peer peer,
      final Spec spec,
      final KZG kzg,
      final List<BlobIdentifier> expectedBlobIdentifiers) {
    super(peer, spec, kzg);
    this.expectedBlobIdentifiers = Collections.newSetFromMap(new ConcurrentHashMap<>());
    this.expectedBlobIdentifiers.addAll(expectedBlobIdentifiers);
  }

  public void validate(final BlobSidecarOld blobSidecar) {
    final BlobIdentifier blobIdentifier =
        new BlobIdentifier(blobSidecar.getBlockRoot(), blobSidecar.getIndex());
    if (!expectedBlobIdentifiers.contains(blobIdentifier)) {
      throw new BlobSidecarsResponseInvalidResponseException(
          peer, InvalidResponseType.BLOB_SIDECAR_UNEXPECTED_IDENTIFIER);
    }

    verifyKzg(blobSidecar);

    expectedBlobIdentifiers.remove(blobIdentifier);
  }
}
