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

package tech.pegasys.teku.spec.datastructures.execution.versions.eip4844;

import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema4;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszFieldName;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.config.SpecConfigEip4844;
import tech.pegasys.teku.spec.datastructures.type.SszKZGProof;
import tech.pegasys.teku.spec.datastructures.type.SszKZGProofSchema;

public class BlobsSidecarSchema
    extends ContainerSchema4<BlobsSidecar, SszBytes32, SszUInt64, SszList<Blob>, SszKZGProof> {

  static final SszFieldName FIELD_BLOBS = () -> "blobs";

  BlobsSidecarSchema(final SpecConfigEip4844 specConfig, final BlobSchema blobSchema) {
    super(
        "BlobsSidecar",
        namedSchema("beacon_block_root", SszPrimitiveSchemas.BYTES32_SCHEMA),
        namedSchema("beacon_block_slot", SszPrimitiveSchemas.UINT64_SCHEMA),
        namedSchema(
            FIELD_BLOBS, SszListSchema.create(blobSchema, specConfig.getMaxBlobsPerBlock())),
        namedSchema("kzg_aggregated_proof", SszKZGProofSchema.INSTANCE));
  }

  @SuppressWarnings("unchecked")
  public SszListSchema<Blob, ?> getBlobsSchema() {
    return (SszListSchema<Blob, ?>) getChildSchema(getFieldIndex(FIELD_BLOBS));
  }

  public static BlobsSidecarSchema create(
      final SpecConfigEip4844 specConfig, final BlobSchema blobSchema) {
    return new BlobsSidecarSchema(specConfig, blobSchema);
  }

  @Override
  public BlobsSidecar createFromBackingNode(final TreeNode node) {
    return new BlobsSidecar(this, node);
  }
}
