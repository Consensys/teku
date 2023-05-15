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
import tech.pegasys.teku.infrastructure.json.types.CoreTypes;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecarSchema;

public class BlobSidecar {

  @JsonProperty("block_root")
  private Bytes32 blockRoot;

  @JsonProperty("index")
  private UInt64 index;

  @JsonProperty("slot")
  private UInt64 slot;

  @JsonProperty("block_parent_root")
  private Bytes32 blockParentRoot;

  @JsonProperty("proposer_index")
  private UInt64 proposerIndex;

  @JsonProperty("blob")
  public Bytes blob;

  @Schema(type = "string", format = "byte", description = DESCRIPTION_BYTES48)
  @JsonProperty("kzg_commitment")
  private Bytes kzgCommitment;

  @Schema(type = "string", format = "byte", description = DESCRIPTION_BYTES48)
  @JsonProperty("kzg_proof")
  private Bytes kzgProof;

  public static final DeserializableTypeDefinition<BlobSidecar> BLOB_SIDECAR_TYPE =
      DeserializableTypeDefinition.object(BlobSidecar.class)
          .name("SignedBeaconBlock")
          .initializer(BlobSidecar::new)
          .withField(
              "block_root",
              CoreTypes.BYTES32_TYPE,
              BlobSidecar::getBlockRoot,
              BlobSidecar::setBlockRoot)
          .withField("index", CoreTypes.UINT64_TYPE, BlobSidecar::getIndex, BlobSidecar::setIndex)
          .withField("slot", CoreTypes.UINT64_TYPE, BlobSidecar::getSlot, BlobSidecar::setSlot)
          .withField(
              "block_parent_root",
              CoreTypes.BYTES32_TYPE,
              BlobSidecar::getBlockParentRoot,
              BlobSidecar::setBlockParentRoot)
          .withField(
              "proposer_index",
              CoreTypes.UINT64_TYPE,
              BlobSidecar::getProposerIndex,
              BlobSidecar::setProposerIndex)
          .withField("blob", CoreTypes.BYTES_TYPE, BlobSidecar::getBlob, BlobSidecar::setBlob)
          .withField(
              "kzg_commitment",
              CoreTypes.BYTES_TYPE,
              BlobSidecar::getKzgCommitment,
              BlobSidecar::setKzgCommitment)
          .withField(
              "kzg_proof", CoreTypes.BYTES_TYPE, BlobSidecar::getKzgProof, BlobSidecar::setKzgProof)
          .build();

  public BlobSidecar(
      @JsonProperty("block_root") final Bytes32 blockRoot,
      @JsonProperty("index") final UInt64 index,
      @JsonProperty("slot") final UInt64 slot,
      @JsonProperty("block_parent_root") final Bytes32 blockParentRoot,
      @JsonProperty("proposer_index") final UInt64 proposerIndex,
      @JsonProperty("blob") final Bytes blob,
      @JsonProperty("kzg_commitment") final Bytes kzgCommitment,
      @JsonProperty("kzg_proof") final Bytes kzgProof) {
    this.blockRoot = blockRoot;
    this.index = index;
    this.slot = slot;
    this.blockParentRoot = blockParentRoot;
    this.proposerIndex = proposerIndex;
    this.blob = blob;
    this.kzgCommitment = kzgCommitment;
    this.kzgProof = kzgProof;
  }

  public BlobSidecar(
      final tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar blobSidecar) {
    this.blockRoot = blobSidecar.getBlockRoot();
    this.index = blobSidecar.getIndex();
    this.slot = blobSidecar.getSlot();
    this.blockParentRoot = blobSidecar.getBlockParentRoot();
    this.proposerIndex = blobSidecar.getProposerIndex();
    this.blob = blobSidecar.getBlob().getBytes();
    this.kzgCommitment = blobSidecar.getKZGCommitment().getBytesCompressed();
    this.kzgProof = blobSidecar.getKZGProof().getBytesCompressed();
  }

  public BlobSidecar() {}

  public Bytes32 getBlockRoot() {
    return blockRoot;
  }

  public void setBlockRoot(Bytes32 blockRoot) {
    this.blockRoot = blockRoot;
  }

  public UInt64 getIndex() {
    return index;
  }

  public void setIndex(UInt64 index) {
    this.index = index;
  }

  public UInt64 getSlot() {
    return slot;
  }

  public void setSlot(UInt64 slot) {
    this.slot = slot;
  }

  public Bytes32 getBlockParentRoot() {
    return blockParentRoot;
  }

  public void setBlockParentRoot(Bytes32 blockParentRoot) {
    this.blockParentRoot = blockParentRoot;
  }

  public UInt64 getProposerIndex() {
    return proposerIndex;
  }

  public void setProposerIndex(UInt64 proposerIndex) {
    this.proposerIndex = proposerIndex;
  }

  public Bytes getBlob() {
    return blob;
  }

  public void setBlob(Bytes blob) {
    this.blob = blob;
  }

  public Bytes getKzgCommitment() {
    return kzgCommitment;
  }

  public void setKzgCommitment(Bytes kzgCommitment) {
    this.kzgCommitment = kzgCommitment;
  }

  public Bytes getKzgProof() {
    return kzgProof;
  }

  public void setKzgProof(Bytes kzgProof) {
    this.kzgProof = kzgProof;
  }

  public static BlobSidecar create(
      final tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar blobSidecar) {
    return new BlobSidecar(blobSidecar);
  }

  public tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar
      asInternalBlobSidecar(final BlobSidecarSchema schema) {
    return schema.create(
        blockRoot,
        index,
        slot,
        blockParentRoot,
        proposerIndex,
        blob,
        Bytes48.wrap(kzgProof),
        Bytes48.wrap(kzgCommitment));
  }
}
