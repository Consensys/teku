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

package tech.pegasys.teku.api.schema.deneb;

import static tech.pegasys.teku.api.schema.SchemaConstants.DESCRIPTION_BYTES48;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.Bytes48;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb.BlindedBlobSidecarSchema;

public class BlindedBlobSidecar {

  @JsonProperty("block_root")
  private final Bytes32 blockRoot;

  @JsonProperty("index")
  private final UInt64 index;

  @JsonProperty("slot")
  private final UInt64 slot;

  @JsonProperty("block_parent_root")
  private final Bytes32 blockParentRoot;

  @JsonProperty("proposer_index")
  private final UInt64 proposerIndex;

  @JsonProperty("blob_root")
  public final Bytes32 blob_root;

  @Schema(type = "string", format = "byte", description = DESCRIPTION_BYTES48)
  @JsonProperty("kzg_commitment")
  private final Bytes kzgCommitment;

  @Schema(type = "string", format = "byte", description = DESCRIPTION_BYTES48)
  @JsonProperty("kzg_proof")
  private final Bytes kzgProof;

  public BlindedBlobSidecar(
      @JsonProperty("block_root") final Bytes32 blockRoot,
      @JsonProperty("index") final UInt64 index,
      @JsonProperty("slot") final UInt64 slot,
      @JsonProperty("block_parent_root") final Bytes32 blockParentRoot,
      @JsonProperty("proposer_index") final UInt64 proposerIndex,
      @JsonProperty("blob_root") final Bytes32 blob_root,
      @JsonProperty("kzg_commitment") final Bytes kzgCommitment,
      @JsonProperty("kzg_proof") final Bytes kzgProof) {
    this.blockRoot = blockRoot;
    this.index = index;
    this.slot = slot;
    this.blockParentRoot = blockParentRoot;
    this.proposerIndex = proposerIndex;
    this.blob_root = blob_root;
    this.kzgCommitment = kzgCommitment;
    this.kzgProof = kzgProof;
  }

  public BlindedBlobSidecar(
      final tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb.BlindedBlobSidecar
          blobSidecar) {
    this.blockRoot = blobSidecar.getBlockRoot();
    this.index = blobSidecar.getIndex();
    this.slot = blobSidecar.getSlot();
    this.blockParentRoot = blobSidecar.getBlockParentRoot();
    this.proposerIndex = blobSidecar.getProposerIndex();
    this.blob_root = blobSidecar.getBlobRoot();
    this.kzgCommitment = blobSidecar.getKZGCommitment().getBytesCompressed();
    this.kzgProof = blobSidecar.getKZGProof().getBytesCompressed();
  }

  public static BlindedBlobSidecar create(
      final tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb.BlindedBlobSidecar
          blindedBlobSidecar) {
    return new BlindedBlobSidecar(blindedBlobSidecar);
  }

  public tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb.BlindedBlobSidecar
      asInternalBlindedBlobSidecar(final BlindedBlobSidecarSchema schema) {
    return schema.create(
        blockRoot,
        index,
        slot,
        blockParentRoot,
        proposerIndex,
        blob_root,
        Bytes48.wrap(kzgProof),
        Bytes48.wrap(kzgCommitment));
  }
}
