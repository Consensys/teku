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

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.execution.versions.eip4844.BlobsSidecar;
import tech.pegasys.teku.spec.logic.versions.eip4844.blobs.BlobsSidecarAvailabilityChecker;

public interface BlobsManager {
  BlobsManager NOOP =
      new BlobsManager() {
        @Override
        public SafeFuture<Void> importBlobs(BlobsSidecar blobsSidecar) {
          return SafeFuture.COMPLETE;
        }

        @Override
        public SafeFuture<Optional<BlobsSidecar>> getBlobsByBlockRoot(Bytes32 root) {
          return SafeFuture.completedFuture(Optional.empty());
        }

        @Override
        public SafeFuture<Void> discardBlobsByBlockRoot(Bytes32 blockRoot) {
          return SafeFuture.COMPLETE;
        }

        @Override
        public BlobsSidecarAvailabilityChecker createAvailabilityChecker(SignedBeaconBlock block) {
          return BlobsSidecarAvailabilityChecker.NOOP;
        }
      };

  SafeFuture<Void> importBlobs(BlobsSidecar blobsSidecar);

  SafeFuture<Optional<BlobsSidecar>> getBlobsByBlockRoot(Bytes32 root);

  SafeFuture<Void> discardBlobsByBlockRoot(Bytes32 blockRoot);

  BlobsSidecarAvailabilityChecker createAvailabilityChecker(SignedBeaconBlock block);

  enum BlobsSidecarValidationResult {
    NOT_REQUIRED,
    NOT_AVAILABLE,
    INVALID,
    VALID
  }
}
