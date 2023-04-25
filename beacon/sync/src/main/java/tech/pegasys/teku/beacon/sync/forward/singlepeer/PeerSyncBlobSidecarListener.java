package tech.pegasys.teku.beacon.sync.forward.singlepeer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.p2p.rpc.RpcResponseListener;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;

public class PeerSyncBlobSidecarListener implements RpcResponseListener<BlobSidecar> {

  private final Map<UInt64, List<BlobSidecar>> blobSidecarsBySlot = new HashMap<>();

  private final UInt64 startSlot;
  private final UInt64 endSlot;

  public PeerSyncBlobSidecarListener(final UInt64 startSlot, final UInt64 endSlot) {
    this.startSlot = startSlot;
    this.endSlot = endSlot;
  }

  @Override
  public SafeFuture<?> onResponse(final BlobSidecar blobSidecar) {
    final UInt64 sidecarSlot = blobSidecar.getSlot();
    if (sidecarSlot.isLessThan(startSlot) || sidecarSlot.isGreaterThan(endSlot)) {
      final String exceptionMessage =
          String.format(
              "Received blob sidecar with slot %s is not in the requested slot range (%s - %s)",
              sidecarSlot, startSlot, endSlot);
      return SafeFuture.failedFuture(new IllegalArgumentException(exceptionMessage));
    }
    final List<BlobSidecar> blobSidecars =
        blobSidecarsBySlot.computeIfAbsent(sidecarSlot, __ -> new ArrayList<>());
    blobSidecars.add(blobSidecar);
    return SafeFuture.COMPLETE;
  }

  public Optional<List<BlobSidecar>> getReceivedBlobSidecars(final UInt64 slot) {
    return Optional.ofNullable(blobSidecarsBySlot.get(slot));
  }

  public void clearReceivedBlobSidecars() {
    blobSidecarsBySlot.clear();
  }
}
