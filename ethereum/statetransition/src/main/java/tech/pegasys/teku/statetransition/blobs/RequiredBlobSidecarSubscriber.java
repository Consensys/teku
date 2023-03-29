package tech.pegasys.teku.statetransition.blobs;

import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BlobIdentifier;

public interface RequiredBlobSidecarSubscriber {

  void onRequiredBlobSidecar(BlobIdentifier blobIdentifier);
}
