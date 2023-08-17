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

package tech.pegasys.teku.api.schema.deneb;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.api.schema.KZGCommitment;
import tech.pegasys.teku.api.schema.KZGProof;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlindedBlobSidecarSchema;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;

public record BlindedBlobSidecar(
    @JsonProperty("block_root") Bytes32 blockRoot,
    @JsonProperty("index") UInt64 index,
    @JsonProperty("slot") UInt64 slot,
    @JsonProperty("block_parent_root") Bytes32 blockParentRoot,
    @JsonProperty("proposer_index") UInt64 proposerIndex,
    @JsonProperty("blob_root") Bytes32 blobRoot,
    @JsonProperty("kzg_commitment") KZGCommitment kzgCommitment,
    @JsonProperty("kzg_proof") KZGProof kzgProof) {

  public static BlindedBlobSidecar fromInternalBlindedBlobSidecar(
      tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlindedBlobSidecar sidecar) {
    return new BlindedBlobSidecar(
        sidecar.getBlockRoot(),
        sidecar.getIndex(),
        sidecar.getSlot(),
        sidecar.getBlockParentRoot(),
        sidecar.getProposerIndex(),
        sidecar.getBlobRoot(),
        new KZGCommitment(sidecar.getKZGCommitment()),
        new KZGProof(sidecar.getKZGProof()));
  }

  public static BlindedBlobSidecar fromInternalBlobSidecar(final BlobSidecar sidecar) {
    return new BlindedBlobSidecar(
        sidecar.getBlockRoot(),
        sidecar.getIndex(),
        sidecar.getSlot(),
        sidecar.getBlockParentRoot(),
        sidecar.getProposerIndex(),
        sidecar.getBlob().hashTreeRoot(),
        new KZGCommitment(sidecar.getKZGCommitment()),
        new KZGProof(sidecar.getKZGProof()));
  }

  public tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlindedBlobSidecar
      asInternalBlindedBlobSidecar(final SpecVersion spec) {
    final BlindedBlobSidecarSchema blindedBlobSidecarSchema =
        spec.getSchemaDefinitions().toVersionDeneb().orElseThrow().getBlindedBlobSidecarSchema();
    return new tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlindedBlobSidecar(
        blindedBlobSidecarSchema,
        blockRoot,
        index,
        slot,
        blockParentRoot,
        proposerIndex,
        blobRoot,
        kzgCommitment.asInternalKZGCommitment(),
        kzgProof.asInternalKZGProof());
  }
}
