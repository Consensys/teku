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

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.networking.p2p.peer.Peer;
import tech.pegasys.teku.networking.p2p.rpc.RpcResponseListener;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BlobIdentifier;
import tech.pegasys.teku.spec.logic.versions.deneb.helpers.MiscHelpersDeneb;

public class BlobSidecarsByRootListenerValidatingProxy implements RpcResponseListener<BlobSidecar> {

  private final Peer peer;
  private final RpcResponseListener<BlobSidecar> blobSidecarResponseListener;
  private final Set<BlobIdentifier> blobIdentifiers;
  private final MiscHelpersDeneb miscHelpersDeneb;

  public BlobSidecarsByRootListenerValidatingProxy(
      final Peer peer,
      final RpcResponseListener<BlobSidecar> blobSidecarResponseListener,
      final MiscHelpersDeneb miscHelpersDeneb,
      final List<BlobIdentifier> blobIdentifiers) {
    this.peer = peer;
    this.blobSidecarResponseListener = blobSidecarResponseListener;
    this.miscHelpersDeneb = miscHelpersDeneb;
    this.blobIdentifiers = new HashSet<>(blobIdentifiers);
  }

  @Override
  public SafeFuture<?> onResponse(final BlobSidecar blobSidecar) {
    return SafeFuture.of(
        () -> {
          validateBlobSidecar(blobSidecar, blobIdentifiers, miscHelpersDeneb, peer);
          return blobSidecarResponseListener.onResponse(blobSidecar);
        });
  }

  public static void validateBlobSidecar(
      final BlobSidecar blobSidecar,
      final Set<BlobIdentifier> expectedBlobIdentifiers,
      final MiscHelpersDeneb miscHelpersDeneb,
      final Peer peer) {
    final BlobIdentifier blobIdentifier =
        new BlobIdentifier(blobSidecar.getBlockRoot(), blobSidecar.getIndex());
    if (!expectedBlobIdentifiers.contains(blobIdentifier)) {
      throw new BlobSidecarsResponseInvalidResponseException(
          peer, BLOB_SIDECAR_UNEXPECTED_IDENTIFIER);
    }

    if (!miscHelpersDeneb.verifyBlobSidecar(blobSidecar)) {
      throw new BlobSidecarsResponseInvalidResponseException(
          peer, BLOB_SIDECAR_KZG_VERIFICATION_FAILED);
    }
  }
}
