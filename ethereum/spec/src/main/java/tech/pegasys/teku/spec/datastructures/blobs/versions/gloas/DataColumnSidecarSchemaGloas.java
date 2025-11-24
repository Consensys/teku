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

package tech.pegasys.teku.spec.datastructures.blobs.versions.gloas;

import java.util.function.Consumer;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema6;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.config.SpecConfigGloas;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecarBuilder;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecarSchema;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.DataColumn;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.DataColumnSchema;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitmentSchema;
import tech.pegasys.teku.spec.datastructures.type.SszKZGProof;
import tech.pegasys.teku.spec.datastructures.type.SszKZGProofSchema;

public class DataColumnSidecarSchemaGloas
    extends ContainerSchema6<
        DataColumnSidecarGloas,
        SszUInt64,
        DataColumn,
        SszList<SszKZGCommitment>,
        SszList<SszKZGProof>,
        SszUInt64,
        SszBytes32>
    implements DataColumnSidecarSchema<DataColumnSidecarGloas> {

  public DataColumnSidecarSchemaGloas(
      final DataColumnSchema dataColumnSchema, final SpecConfigGloas specConfig) {
    super(
        "DataColumnSidecarGloas",
        namedSchema(FIELD_INDEX, SszPrimitiveSchemas.UINT64_SCHEMA),
        namedSchema(FIELD_BLOB, dataColumnSchema),
        namedSchema(
            FIELD_KZG_COMMITMENTS,
            SszListSchema.create(
                SszKZGCommitmentSchema.INSTANCE, specConfig.getMaxBlobCommitmentsPerBlock())),
        namedSchema(
            FIELD_KZG_PROOFS,
            SszListSchema.create(
                SszKZGProofSchema.INSTANCE, specConfig.getMaxBlobCommitmentsPerBlock())),
        namedSchema(FIELD_SLOT, SszPrimitiveSchemas.UINT64_SCHEMA),
        namedSchema(FIELD_BEACON_BLOCK_ROOT, SszPrimitiveSchemas.BYTES32_SCHEMA));
  }

  @Override
  @SuppressWarnings("unchecked")
  public SszListSchema<SszKZGCommitment, ?> getKzgCommitmentsSchema() {
    return (SszListSchema<SszKZGCommitment, ?>)
        getChildSchema(getFieldIndex(FIELD_KZG_COMMITMENTS));
  }

  @Override
  @SuppressWarnings("unchecked")
  public SszListSchema<SszKZGProof, ?> getKzgProofsSchema() {
    return (SszListSchema<SszKZGProof, ?>) getChildSchema(getFieldIndex(FIELD_KZG_PROOFS));
  }

  @Override
  public DataColumnSidecar create(final Consumer<DataColumnSidecarBuilder> builderConsumer) {
    final DataColumnSidecarBuilderGloas builder = new DataColumnSidecarBuilderGloas().schema(this);
    builderConsumer.accept(builder);
    return builder.build();
  }

  @Override
  public DataColumnSidecarGloas createFromBackingNode(final TreeNode node) {
    return new DataColumnSidecarGloas(this, node);
  }
}
