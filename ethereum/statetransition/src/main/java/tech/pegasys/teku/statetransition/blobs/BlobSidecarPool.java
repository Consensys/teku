/*
 * Copyright ConsenSys Software Inc., 2023
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

package tech.pegasys.teku.statetransition.blobs;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BlobIdentifier;

public interface BlobSidecarPool extends SlotEventsChannel {

  BlobSidecarPool NOOP =
      new BlobSidecarPool() {
        @Override
        public void onSlot(UInt64 slot) {}

        @Override
        public void onNewBlobSidecar(final BlobSidecar blobSidecar) {}

        @Override
        public void onNewBlock(final SignedBeaconBlock block) {}

        @Override
        public void onBlobSidecarsFromSync(
            final SignedBeaconBlock block, final List<BlobSidecar> blobSidecars) {}

        @Override
        public boolean containsBlobSidecar(final BlobIdentifier blobIdentifier) {
          return false;
        }

        @Override
        public BlockBlobSidecarsTracker getBlockBlobsSidecarsTracker(SignedBeaconBlock block) {
          throw new UnsupportedOperationException();
        }

        @Override
        public Set<BlobIdentifier> getAllRequiredBlobSidecars() {
          return Collections.emptySet();
        }

        @Override
        public void subscribeRequiredBlobSidecar(
            final RequiredBlobSidecarSubscriber requiredBlobSidecarSubscriber) {}

        @Override
        public void subscribeRequiredBlobSidecarDropped(
            final RequiredBlobSidecarDroppedSubscriber requiredBlobSidecarDroppedSubscriber) {}

        @Override
        public void subscribeRequiredBlockRoot(
            final RequiredBlockRootSubscriber requiredBlockRootSubscriber) {}

        @Override
        public void subscribeRequiredBlockRootDropped(
            final RequiredBlockRootDroppedSubscriber requiredBlockRootDroppedSubscriber) {}
      };

  void onNewBlobSidecar(BlobSidecar blobSidecar);

  void onNewBlock(SignedBeaconBlock block);

  void onBlobSidecarsFromSync(SignedBeaconBlock block, List<BlobSidecar> blobSidecars);

  boolean containsBlobSidecar(BlobIdentifier blobIdentifier);

  Set<BlobIdentifier> getAllRequiredBlobSidecars();

  BlockBlobSidecarsTracker getBlockBlobsSidecarsTracker(SignedBeaconBlock block);

  void subscribeRequiredBlobSidecar(RequiredBlobSidecarSubscriber requiredBlobSidecarSubscriber);

  void subscribeRequiredBlobSidecarDropped(
      RequiredBlobSidecarDroppedSubscriber requiredBlobSidecarDroppedSubscriber);

  void subscribeRequiredBlockRoot(RequiredBlockRootSubscriber requiredBlockRootSubscriber);

  void subscribeRequiredBlockRootDropped(
      RequiredBlockRootDroppedSubscriber requiredBlockRootDroppedSubscriber);

  interface RequiredBlobSidecarSubscriber {
    void onRequiredBlobSidecar(BlobIdentifier blobIdentifier);
  }

  interface RequiredBlobSidecarDroppedSubscriber {
    void onRequiredBlobSidecarDropped(BlobIdentifier blobIdentifier);
  }

  interface RequiredBlockRootSubscriber {
    void onRequiredBlockRoot(Bytes32 blockRoot);
  }

  interface RequiredBlockRootDroppedSubscriber {
    void onRequiredBlockRootDropped(Bytes32 blockRoot);
  }
}
