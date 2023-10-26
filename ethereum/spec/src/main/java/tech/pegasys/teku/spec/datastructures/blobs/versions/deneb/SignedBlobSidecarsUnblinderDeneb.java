/*
 * Copyright Consensys Software Inc., 2023
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

package tech.pegasys.teku.spec.datastructures.blobs.versions.deneb;

import java.util.List;
import java.util.function.Supplier;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.kzg.KZGCommitment;
import tech.pegasys.teku.kzg.KZGProof;
import tech.pegasys.teku.spec.datastructures.blobs.SignedBlobSidecarsUnblinder;
import tech.pegasys.teku.spec.datastructures.builder.BlobsBundle;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsDeneb;

public class SignedBlobSidecarsUnblinderDeneb implements SignedBlobSidecarsUnblinder {

  private final BlobSidecarSchema blobSidecarSchema;
  private final SignedBlobSidecarSchema signedBlobSidecarSchema;
  private final List<SignedBlindedBlobSidecar> signedBlindedBlobSidecars;

  private volatile SafeFuture<BlobsBundle> blobsBundleFuture;

  public SignedBlobSidecarsUnblinderDeneb(
      final SchemaDefinitionsDeneb schemaDefinitions,
      final List<SignedBlindedBlobSidecar> signedBlindedBlobSidecars) {
    this.blobSidecarSchema = schemaDefinitions.getBlobSidecarSchema();
    this.signedBlobSidecarSchema = schemaDefinitions.getSignedBlobSidecarSchema();
    this.signedBlindedBlobSidecars = signedBlindedBlobSidecars;
  }

  @Override
  public void setBlobsBundleSupplier(final Supplier<SafeFuture<BlobsBundle>> blobsBundleSupplier) {
    blobsBundleFuture = blobsBundleSupplier.get();
  }

  @Override
  public SafeFuture<List<SignedBlobSidecar>> unblind() {
    return blobsBundleFuture.thenApply(
        blobsBundle ->
            signedBlindedBlobSidecars.stream()
                .map(
                    signedBlindedBlobSidecar ->
                        unblindSignedBlindedBlobSidecar(signedBlindedBlobSidecar, blobsBundle))
                .toList());
  }

  private SignedBlobSidecar unblindSignedBlindedBlobSidecar(
      final SignedBlindedBlobSidecar signedBlindedBlobSidecar, final BlobsBundle blobsBundle) {
    final BlindedBlobSidecar blindedBlobSidecar = signedBlindedBlobSidecar.getBlindedBlobSidecar();
    final Blob blob = findBlobByIndex(blobsBundle, blindedBlobSidecar);
    final BlobSidecar unblindedBlobSidecar =
        blobSidecarSchema.create(
            blindedBlobSidecar.getBlockRoot(),
            blindedBlobSidecar.getIndex(),
            blindedBlobSidecar.getSlot(),
            blindedBlobSidecar.getBlockParentRoot(),
            blindedBlobSidecar.getProposerIndex(),
            blob,
            blindedBlobSidecar.getKZGCommitment(),
            blindedBlobSidecar.getKZGProof());
    return signedBlobSidecarSchema.create(
        unblindedBlobSidecar, signedBlindedBlobSidecar.getSignature());
  }

  private Blob findBlobByIndex(
      final BlobsBundle blobsBundle, final BlindedBlobSidecar blindedBlobSidecar) {
    final int index = blindedBlobSidecar.getIndex().intValue();
    if (index >= blobsBundle.getNumberOfBlobs()) {
      throw new IllegalArgumentException(
          String.format(
              "There are %d blobs in the BlobsBundle but a blinded blob sidecar with index %s has been requested to be unblinded",
              blobsBundle.getNumberOfBlobs(), index));
    }

    // Check that the blob root matches
    final Blob blob = blobsBundle.getBlobs().get(index);
    if (!blob.hashTreeRoot().equals(blindedBlobSidecar.getBlobRoot())) {
      throw new IllegalArgumentException(
          String.format(
              "The blob root in the BlobsBundle %s does not match the blob root in the blinded blob sidecar %s",
              blob.hashTreeRoot(), blindedBlobSidecar));
    }

    // Check that the commitment matches
    final KZGCommitment commitment = blobsBundle.getCommitments().get(index).getKZGCommitment();
    if (!commitment.equals(blindedBlobSidecar.getKZGCommitment())) {
      throw new IllegalArgumentException(
          String.format(
              "The commitment in the BlobsBundle %s does not match the commitment in the blinded blob sidecar %s",
              commitment, blindedBlobSidecar));
    }

    // Check that the proof matches
    final KZGProof proof = blobsBundle.getProofs().get(index).getKZGProof();
    if (!proof.equals(blindedBlobSidecar.getKZGProof())) {
      throw new IllegalArgumentException(
          String.format(
              "The proof in the BlobsBundle %s does not match the proof in the blinded blob sidecar %s",
              commitment, blindedBlobSidecar));
    }

    return blob;
  }
}
