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

import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobsSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.SignedBlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.logic.versions.deneb.blobs.BlobsSidecarAvailabilityChecker;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;

public interface BlobsSidecarManager {
  BlobsSidecarManager NOOP =
      new BlobsSidecarManager() {

        @Override
        public SafeFuture<InternalValidationResult> validateAndImportBlobSidecar(
            final SignedBlobSidecar signedBlobSidecar) {
          return SafeFuture.completedFuture(InternalValidationResult.ACCEPT);
        }

        @Override
        public SafeFuture<Void> importBlobSidecar(final BlobSidecar blobSidecar) {
          return SafeFuture.COMPLETE;
        }

        @Override
        public void subscribeToImportedBlobSidecars(
            final ImportedBlobSidecarListener importedBlobSidecarListener) {}

        @Override
        public boolean isAvailabilityRequiredAtSlot(final UInt64 slot) {
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

  SafeFuture<InternalValidationResult> validateAndImportBlobSidecar(
      SignedBlobSidecar signedBlobSidecar);

  SafeFuture<Void> importBlobSidecar(BlobSidecar blobSidecar);

  void subscribeToImportedBlobSidecars(ImportedBlobSidecarListener importedBlobSidecarListener);

  boolean isAvailabilityRequiredAtSlot(UInt64 slot);

  void storeUnconfirmedValidatedBlobsSidecar(BlobsSidecar blobsSidecar);

  void storeUnconfirmedBlobsSidecar(BlobsSidecar blobsSidecar);

  void discardBlobsSidecarByBlock(SignedBeaconBlock block);

  BlobsSidecarAvailabilityChecker createAvailabilityChecker(SignedBeaconBlock block);

  interface ImportedBlobSidecarListener {
    void onBlobSidecarImported(BlobSidecar blobSidecar);
  }
}
