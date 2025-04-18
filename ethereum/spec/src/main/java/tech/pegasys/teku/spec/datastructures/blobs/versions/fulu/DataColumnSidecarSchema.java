/*
 * Copyright Consensys Software Inc., 2025
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

package tech.pegasys.teku.spec.datastructures.blobs.versions.fulu;

import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBytes32Vector;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema6;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszFieldName;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBytes32VectorSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZGCommitment;
import tech.pegasys.teku.kzg.KZGProof;
import tech.pegasys.teku.spec.config.SpecConfigFulu;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockHeaderSchema;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitmentSchema;
import tech.pegasys.teku.spec.datastructures.type.SszKZGProof;
import tech.pegasys.teku.spec.datastructures.type.SszKZGProofSchema;

public class DataColumnSidecarSchema
    extends ContainerSchema6<
        DataColumnSidecar,
        SszUInt64,
        DataColumn,
        SszList<SszKZGCommitment>,
        SszList<SszKZGProof>,
        SignedBeaconBlockHeader,
        SszBytes32Vector> {

  static final SszFieldName FIELD_BLOB = () -> "column";
  static final SszFieldName FIELD_KZG_COMMITMENTS = () -> "kzg_commitments";
  static final SszFieldName FIELD_KZG_PROOFS = () -> "kzg_proofs";
  static final SszFieldName FIELD_SIGNED_BLOCK_HEADER = () -> "signed_block_header";
  static final SszFieldName FIELD_KZG_COMMITMENTS_INCLUSION_PROOF =
      () -> "kzg_commitments_inclusion_proof";

  DataColumnSidecarSchema(
      final SignedBeaconBlockHeaderSchema signedBeaconBlockHeaderSchema,
      final DataColumnSchema dataColumnSchema,
      final SpecConfigFulu specConfig) {
    super(
        "DataColumnSidecar",
        namedSchema("index", SszPrimitiveSchemas.UINT64_SCHEMA),
        namedSchema(FIELD_BLOB, dataColumnSchema),
        namedSchema(
            FIELD_KZG_COMMITMENTS,
            SszListSchema.create(
                SszKZGCommitmentSchema.INSTANCE, specConfig.getMaxBlobCommitmentsPerBlock())),
        namedSchema(
            FIELD_KZG_PROOFS,
            SszListSchema.create(
                SszKZGProofSchema.INSTANCE, specConfig.getMaxBlobCommitmentsPerBlock())),
        namedSchema(FIELD_SIGNED_BLOCK_HEADER, signedBeaconBlockHeaderSchema),
        namedSchema(
            FIELD_KZG_COMMITMENTS_INCLUSION_PROOF,
            SszBytes32VectorSchema.create(
                specConfig.getKzgCommitmentsInclusionProofDepth().intValue())));
  }

  @SuppressWarnings("unchecked")
  public DataColumnSchema getDataColumnSszSchema() {
    return (DataColumnSchema) getChildSchema(getFieldIndex(FIELD_BLOB));
  }

  public SignedBeaconBlockHeaderSchema getSignedBlockHeaderSchema() {
    return (SignedBeaconBlockHeaderSchema) getFieldSchema4();
  }

  public SszBytes32VectorSchema<?> getKzgCommitmentsInclusionProofSchema() {
    return (SszBytes32VectorSchema<?>)
        getChildSchema(getFieldIndex(FIELD_KZG_COMMITMENTS_INCLUSION_PROOF));
  }

  @SuppressWarnings("unchecked")
  public SszListSchema<SszKZGCommitment, ?> getKzgCommitmentsSchema() {
    return (SszListSchema<SszKZGCommitment, ?>)
        getChildSchema(getFieldIndex(FIELD_KZG_COMMITMENTS));
  }

  @SuppressWarnings("unchecked")
  public SszListSchema<SszKZGProof, ?> getKzgProofsSchema() {
    return (SszListSchema<SszKZGProof, ?>) getChildSchema(getFieldIndex(FIELD_KZG_PROOFS));
  }

  public DataColumnSidecar create(
      final UInt64 index,
      final DataColumn dataColumn,
      final SszList<SszKZGCommitment> sszKzgCommitments,
      final SszList<SszKZGProof> sszKkzgProofs,
      final SignedBeaconBlockHeader signedBeaconBlockHeader,
      final List<Bytes32> kzgCommitmentsInclusionProof) {
    return new DataColumnSidecar(
        this,
        index,
        dataColumn,
        sszKzgCommitments,
        sszKkzgProofs,
        signedBeaconBlockHeader,
        kzgCommitmentsInclusionProof);
  }

  //  public DataColumnSidecar create(
  //      final UInt64 index,
  //      final Bytes blob,
  //      final Bytes48 kzgCommitment,
  //      final Bytes48 kzgProof,
  //      final SignedBeaconBlockHeader signedBeaconBlockHeader,
  //      final List<Bytes32> kzgCommitmentsInclusionProof) {
  //    return create(
  //        index,
  //        new Blob(getBlobSchema(), blob),
  //        KZGCommitment.fromBytesCompressed(kzgCommitment),
  //        KZGProof.fromBytesCompressed(kzgProof),
  //        signedBeaconBlockHeader,
  //        kzgCommitmentsInclusionProof);
  //  }

  public DataColumnSidecar create(
      final UInt64 index,
      final DataColumn dataColumn,
      final List<KZGCommitment> kzgCommitments,
      final List<KZGProof> kzgProofs,
      final SignedBeaconBlockHeader signedBeaconBlockHeader,
      final List<Bytes32> kzgCommitmentsInclusionProof) {
    return new DataColumnSidecar(
        this,
        index,
        dataColumn,
        kzgCommitments,
        kzgProofs,
        signedBeaconBlockHeader,
        kzgCommitmentsInclusionProof);
  }

  public static DataColumnSidecarSchema create(
      final SignedBeaconBlockHeaderSchema signedBeaconBlockHeaderSchema,
      final DataColumnSchema dataColumnSchema,
      final SpecConfigFulu specConfig) {
    return new DataColumnSidecarSchema(signedBeaconBlockHeaderSchema, dataColumnSchema, specConfig);
  }

  @Override
  public DataColumnSidecar createFromBackingNode(final TreeNode node) {
    return new DataColumnSidecar(this, node);
  }
}
