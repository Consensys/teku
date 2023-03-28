package tech.pegasys.teku.beacon.sync.gossip.blobs;

import tech.pegasys.teku.spec.datastructures.execution.versions.deneb.BlobSidecar;

public interface BlobSidecarSubscriber {

  void onBlobSidecar(BlobSidecar blobSidecar);
}
