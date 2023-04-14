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

package tech.pegasys.teku.spec.datastructures.blobs.versions.deneb;

import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.Bytes48;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema8;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZGCommitment;
import tech.pegasys.teku.kzg.KZGProof;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitmentSchema;
import tech.pegasys.teku.spec.datastructures.type.SszKZGProof;
import tech.pegasys.teku.spec.datastructures.type.SszKZGProofSchema;

public class BlindedBlobSidecarSchema
    extends ContainerSchema8<
        BlindedBlobSidecar,
        SszBytes32,
        SszUInt64,
        SszUInt64,
        SszBytes32,
        SszUInt64,
        SszBytes32,
        SszKZGCommitment,
        SszKZGProof> {

  BlindedBlobSidecarSchema() {
    super(
        "BlindedBlobSidecar",
        namedSchema("block_root", SszPrimitiveSchemas.BYTES32_SCHEMA),
        namedSchema("index", SszPrimitiveSchemas.UINT64_SCHEMA),
        namedSchema("slot", SszPrimitiveSchemas.UINT64_SCHEMA),
        namedSchema("block_parent_root", SszPrimitiveSchemas.BYTES32_SCHEMA),
        namedSchema("proposer_index", SszPrimitiveSchemas.UINT64_SCHEMA),
        namedSchema("blob_root", SszPrimitiveSchemas.BYTES32_SCHEMA),
        namedSchema("kzg_commitment", SszKZGCommitmentSchema.INSTANCE),
        namedSchema("kzg_proof", SszKZGProofSchema.INSTANCE));
  }

  public static BlindedBlobSidecarSchema create() {
    return new BlindedBlobSidecarSchema();
  }

  public BlindedBlobSidecar create(
      final Bytes32 blockRoot,
      final UInt64 index,
      final UInt64 slot,
      final Bytes32 blockParentRoot,
      final UInt64 proposerIndex,
      final Bytes32 blobRoot,
      final Bytes48 kzgCommitment,
      final Bytes48 kzgProof) {
    return new BlindedBlobSidecar(
        this,
        blockRoot,
        index,
        slot,
        blockParentRoot,
        proposerIndex,
        blobRoot,
        KZGCommitment.fromBytesCompressed(kzgCommitment),
        KZGProof.fromBytesCompressed(kzgProof));
  }

  public BlindedBlobSidecar create(final BlobSidecar blobSidecar) {
    return new BlindedBlobSidecar(
        this,
        blobSidecar.getBlockRoot(),
        blobSidecar.getIndex(),
        blobSidecar.getSlot(),
        blobSidecar.getBlockParentRoot(),
        blobSidecar.getProposerIndex(),
        blobSidecar.getBlob().hashTreeRoot(),
        blobSidecar.getKZGCommitment(),
        blobSidecar.getKZGProof());
  }

  @Override
  public BlindedBlobSidecar createFromBackingNode(TreeNode node) {
    return new BlindedBlobSidecar(this, node);
  }
}
