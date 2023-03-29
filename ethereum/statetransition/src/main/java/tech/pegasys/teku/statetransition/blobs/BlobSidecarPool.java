package tech.pegasys.teku.statetransition.blobs;

import java.util.Collections;
import java.util.List;
import tech.pegasys.teku.spec.datastructures.execution.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BlobIdentifier;

public interface BlobSidecarPool {

  BlobSidecarPool NOOP =
      new BlobSidecarPool() {
        @Override
        public void onNewBlobSidecar(final BlobSidecar blobSidecar) {}

        @Override
        public boolean containsBlobSidecar(final BlobIdentifier blobIdentifier) {
          return false;
        }

        @Override
        public List<BlobIdentifier> getAllRequiredBlobSidecars() {
          return Collections.emptyList();
        }

        @Override
        public void subscribeRequiredBlobSidecar(
            final RequiredBlobSidecarSubscriber requiredBlobSidecarSubscriber) {}
      };

  void onNewBlobSidecar(BlobSidecar blobSidecar);

  boolean containsBlobSidecar(BlobIdentifier blobIdentifier);

  List<BlobIdentifier> getAllRequiredBlobSidecars();

  void subscribeRequiredBlobSidecar(RequiredBlobSidecarSubscriber requiredBlobSidecarSubscriber);
}
