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
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.SignedBlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.logic.versions.deneb.blobs.BlobSidecarsAvailabilityChecker;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;

public interface BlobSidecarManager {
  BlobSidecarManager NOOP =
      new BlobSidecarManager() {

        @Override
        public SafeFuture<InternalValidationResult> validateAndPrepareForBlockImport(
            final SignedBlobSidecar signedBlobSidecar) {
          return SafeFuture.completedFuture(InternalValidationResult.ACCEPT);
        }

        @Override
        public SafeFuture<Void> prepareForBlockImport(final BlobSidecar blobSidecar) {
          return SafeFuture.COMPLETE;
        }

        @Override
        public void subscribeToPreparedBlobSidecars(
            final ImportedBlobSidecarListener importedBlobSidecarListener) {}

        @Override
        public boolean isAvailabilityRequiredAtSlot(final UInt64 slot) {
          return false;
        }

        @Override
        public void storeNoBlobsSlot(SlotAndBlockRoot slotAndBlockRoot) {}

        @Override
        public void storeBlobSidecar(final BlobSidecar blobSidecar) {}

        @Override
        public void discardBlobSidecarsByBlock(final SignedBeaconBlock block) {}

        @Override
        public BlobSidecarsAvailabilityChecker createAvailabilityChecker(
            final SignedBeaconBlock block) {
          return BlobSidecarsAvailabilityChecker.NOOP;
        }
      };

  SafeFuture<InternalValidationResult> validateAndPrepareForBlockImport(
      SignedBlobSidecar signedBlobSidecar);

  SafeFuture<Void> prepareForBlockImport(BlobSidecar blobSidecar);

  void subscribeToPreparedBlobSidecars(ImportedBlobSidecarListener importedBlobSidecarListener);

  boolean isAvailabilityRequiredAtSlot(UInt64 slot);

  void storeNoBlobsSlot(SlotAndBlockRoot slotAndBlockRoot);

  void storeBlobSidecar(BlobSidecar blobSidecar);

  void discardBlobSidecarsByBlock(SignedBeaconBlock block);

  BlobSidecarsAvailabilityChecker createAvailabilityChecker(SignedBeaconBlock block);

  interface ImportedBlobSidecarListener {
    void onBlobSidecarImported(BlobSidecar blobSidecar);
  }
}
