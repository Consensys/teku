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

import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.execution.versions.eip4844.BlobsSidecar;
import tech.pegasys.teku.spec.logic.versions.eip4844.blobs.BlobsSidecarAvailabilityChecker;

public interface BlobsSidecarManager {
  BlobsSidecarManager NOOP =
      new BlobsSidecarManager() {
        @Override
        public void storeUnconfirmedValidatedBlobs(BlobsSidecar blobsSidecar) {}

        @Override
        public void storeUnconfirmedBlobs(BlobsSidecar blobsSidecar) {}

        @Override
        public void discardBlobsByBlock(SignedBeaconBlock block) {}

        @Override
        public BlobsSidecarAvailabilityChecker createAvailabilityChecker(SignedBeaconBlock block) {
          return BlobsSidecarAvailabilityChecker.NOOP;
        }
      };

  void storeUnconfirmedValidatedBlobs(BlobsSidecar blobsSidecar);

  void storeUnconfirmedBlobs(BlobsSidecar blobsSidecar);

  void discardBlobsByBlock(SignedBeaconBlock block);

  BlobsSidecarAvailabilityChecker createAvailabilityChecker(SignedBeaconBlock block);
}
