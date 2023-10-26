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

import static tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.BlobSidecarsByRangeResponseInvalidResponseException.InvalidResponseType.BLOB_SIDECAR_KZG_VERIFICATION_FAILED;
import static tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.BlobSidecarsByRangeResponseInvalidResponseException.InvalidResponseType.BLOB_SIDECAR_SLOT_NOT_IN_RANGE;
import static tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.BlobSidecarsByRangeResponseInvalidResponseException.InvalidResponseType.BLOB_SIDECAR_UNEXPECTED_INDEX;
import static tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.BlobSidecarsByRangeResponseInvalidResponseException.InvalidResponseType.BLOB_SIDECAR_UNEXPECTED_SLOT;
import static tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.BlobSidecarsByRangeResponseInvalidResponseException.InvalidResponseType.BLOB_SIDECAR_UNKNOWN_PARENT;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.p2p.peer.Peer;
import tech.pegasys.teku.networking.p2p.rpc.RpcResponseListener;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.logic.versions.deneb.helpers.MiscHelpersDeneb;

public class BlobSidecarsByRangeListenerValidatingProxy
    implements RpcResponseListener<BlobSidecar> {

  private final Peer peer;
  private final RpcResponseListener<BlobSidecar> blobSidecarResponseListener;
  private final UInt64 startSlot;
  private final UInt64 endSlot;
  private final Integer maxBlobsPerBlock;
  private final MiscHelpersDeneb miscHelpersDeneb;

  private volatile Optional<BlobSidecarSummary> maybeLastBlobSidecarSummary = Optional.empty();

  public BlobSidecarsByRangeListenerValidatingProxy(
      final Peer peer,
      final RpcResponseListener<BlobSidecar> blobSidecarResponseListener,
      final Integer maxBlobsPerBlock,
      final MiscHelpersDeneb miscHelpersDeneb,
      final UInt64 startSlot,
      final UInt64 count) {
    this.peer = peer;
    this.blobSidecarResponseListener = blobSidecarResponseListener;
    this.maxBlobsPerBlock = maxBlobsPerBlock;
    this.miscHelpersDeneb = miscHelpersDeneb;
    this.startSlot = startSlot;
    this.endSlot = startSlot.plus(count);
  }

  @Override
  public SafeFuture<?> onResponse(final BlobSidecar blobSidecar) {
    return SafeFuture.of(
        () -> {
          final UInt64 blobSidecarSlot = blobSidecar.getSlot();
          if (!blobSidecarSlotIsInRange(blobSidecarSlot)) {
            throw new BlobSidecarsByRangeResponseInvalidResponseException(
                peer, BLOB_SIDECAR_SLOT_NOT_IN_RANGE);
          }

          if (blobSidecar.getIndex().isGreaterThanOrEqualTo(maxBlobsPerBlock)) {
            throw new BlobSidecarsByRangeResponseInvalidResponseException(
                peer, BLOB_SIDECAR_UNEXPECTED_INDEX);
          }

          final BlobSidecarSummary blobSidecarSummary = BlobSidecarSummary.create(blobSidecar);
          verifyBlobSidecarIsAfterLast(blobSidecarSummary);

          if (!miscHelpersDeneb.verifyBlobSidecar(blobSidecar)) {
            throw new BlobSidecarsByRangeResponseInvalidResponseException(
                peer, BLOB_SIDECAR_KZG_VERIFICATION_FAILED);
          }

          maybeLastBlobSidecarSummary = Optional.of(blobSidecarSummary);
          return blobSidecarResponseListener.onResponse(blobSidecar);
        });
  }

  private boolean blobSidecarSlotIsInRange(final UInt64 blobSidecarSlot) {
    return blobSidecarSlot.isGreaterThanOrEqualTo(startSlot)
        && blobSidecarSlot.isLessThanOrEqualTo(endSlot);
  }

  private void verifyBlobSidecarIsAfterLast(final BlobSidecarSummary blobSidecarSummary) {
    if (maybeLastBlobSidecarSummary.isEmpty()) {
      if (!blobSidecarSummary.index().equals(UInt64.ZERO)) {
        throw new BlobSidecarsByRangeResponseInvalidResponseException(
            peer, BLOB_SIDECAR_UNEXPECTED_INDEX);
      }
      return;
    }

    if (blobSidecarSummary.inTheSameBlock(maybeLastBlobSidecarSummary.get())) {
      if (!blobSidecarSummary.index().equals(maybeLastBlobSidecarSummary.get().index().plus(1))) {
        throw new BlobSidecarsByRangeResponseInvalidResponseException(
            peer, BLOB_SIDECAR_UNEXPECTED_INDEX);
      }
    } else {

      if (!blobSidecarSummary.index().equals(UInt64.ZERO)) {
        throw new BlobSidecarsByRangeResponseInvalidResponseException(
            peer, BLOB_SIDECAR_UNEXPECTED_INDEX);
      }

      if (!blobSidecarSummary
          .blockParentRoot()
          .equals(maybeLastBlobSidecarSummary.get().blockRoot())) {
        throw new BlobSidecarsByRangeResponseInvalidResponseException(
            peer, BLOB_SIDECAR_UNKNOWN_PARENT);
      }

      if (!blobSidecarSummary.slot().isGreaterThan(maybeLastBlobSidecarSummary.get().slot())) {
        throw new BlobSidecarsByRangeResponseInvalidResponseException(
            peer, BLOB_SIDECAR_UNEXPECTED_SLOT);
      }
    }
  }

  record BlobSidecarSummary(Bytes32 blockRoot, UInt64 index, UInt64 slot, Bytes32 blockParentRoot) {
    public static BlobSidecarSummary create(final BlobSidecar blobSidecar) {
      return new BlobSidecarSummary(
          blobSidecar.getBlockRoot(),
          blobSidecar.getIndex(),
          blobSidecar.getSlot(),
          blobSidecar.getBlockParentRoot());
    }

    public boolean inTheSameBlock(final BlobSidecarSummary blobSidecarSummary) {
      return this.blockParentRoot.equals(blobSidecarSummary.blockParentRoot)
          && this.blockRoot.equals(blobSidecarSummary.blockRoot)
          && this.slot.equals(blobSidecarSummary.slot);
    }
  }
}
