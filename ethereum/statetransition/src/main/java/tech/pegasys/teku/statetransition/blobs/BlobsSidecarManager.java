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

package tech.pegasys.teku.statetransition.blobs;

import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.execution.versions.deneb.BlobsSidecar;
import tech.pegasys.teku.spec.logic.versions.deneb.blobs.BlobsSidecarAvailabilityChecker;

public interface BlobsSidecarManager {
  BlobsSidecarManager NOOP =
      new BlobsSidecarManager() {
        @Override
        public boolean isStorageOfBlobsSidecarRequired(final UInt64 slot) {
          return false;
        }

        @Override
        public void storeUnconfirmedValidatedBlobsSidecar(final BlobsSidecar blobsSidecar) {}

        @Override
        public void storeUnconfirmedBlobsSidecar(final BlobsSidecar blobsSidecar) {}

        @Override
        public void discardBlobsSidecarByBlock(final SignedBeaconBlock block) {}

        @Override
        public BlobsSidecarAvailabilityChecker createAvailabilityChecker(
            final SignedBeaconBlock block) {
          return BlobsSidecarAvailabilityChecker.NOOP;
        }
      };

  boolean isStorageOfBlobsSidecarRequired(UInt64 slot);

  void storeUnconfirmedValidatedBlobsSidecar(BlobsSidecar blobsSidecar);

  void storeUnconfirmedBlobsSidecar(BlobsSidecar blobsSidecar);

  void discardBlobsSidecarByBlock(SignedBeaconBlock block);

  BlobsSidecarAvailabilityChecker createAvailabilityChecker(SignedBeaconBlock block);
}
