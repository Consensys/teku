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

package tech.pegasys.teku.spec.datastructures.builder.versions.fulu;

import static tech.pegasys.teku.kzg.KZG.FIELD_ELEMENTS_PER_EXT_BLOB;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.BLOB_KZG_COMMITMENTS_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.BLOB_SCHEMA;

import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema3;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.config.SpecConfigDeneb;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.Blob;
import tech.pegasys.teku.spec.datastructures.builder.BlobsBundleSchema;
import tech.pegasys.teku.spec.datastructures.execution.BlobsCellBundle;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.datastructures.type.SszKZGProof;
import tech.pegasys.teku.spec.datastructures.type.SszKZGProofSchema;
import tech.pegasys.teku.spec.schemas.registry.SchemaRegistry;

public class BlobsBundleSchemaFulu
    extends ContainerSchema3<
        BlobsBundleFulu, SszList<SszKZGCommitment>, SszList<SszKZGProof>, SszList<Blob>>
    implements BlobsBundleSchema<BlobsBundleFulu> {

  public BlobsBundleSchemaFulu(
      final SchemaRegistry schemaRegistry, final SpecConfigDeneb specConfig) {
    super(
        "BlobsBundle",
        namedSchema("commitments", schemaRegistry.get(BLOB_KZG_COMMITMENTS_SCHEMA)),
        namedSchema(
            "proofs",
            SszListSchema.create(
                SszKZGProofSchema.INSTANCE,
                (long) specConfig.getMaxBlobCommitmentsPerBlock() * FIELD_ELEMENTS_PER_EXT_BLOB)),
        namedSchema(
            "blobs",
            SszListSchema.create(
                schemaRegistry.get(BLOB_SCHEMA), specConfig.getMaxBlobCommitmentsPerBlock())));
  }

  @SuppressWarnings("unchecked")
  @Override
  public SszListSchema<SszKZGCommitment, ?> getCommitmentsSchema() {
    return (SszListSchema<SszKZGCommitment, ?>) getChildSchema(getFieldIndex("commitments"));
  }

  @SuppressWarnings("unchecked")
  @Override
  public SszListSchema<SszKZGProof, ?> getProofsSchema() {
    return (SszListSchema<SszKZGProof, ?>) getChildSchema(getFieldIndex("proofs"));
  }

  @SuppressWarnings("unchecked")
  @Override
  public SszListSchema<Blob, ?> getBlobsSchema() {
    return (SszListSchema<Blob, ?>) getChildSchema(getFieldIndex("blobs"));
  }

  @Override
  public BlobsBundleFulu createFromBackingNode(final TreeNode node) {
    return new BlobsBundleFulu(this, node);
  }

  public BlobsBundleFulu createFromExecutionBlobsCellBundle(final BlobsCellBundle blobsCellBundle) {
    return new BlobsBundleFulu(
        this,
        getCommitmentsSchema()
            .createFromElements(
                blobsCellBundle.getCommitments().stream().map(SszKZGCommitment::new).toList()),
        getProofsSchema()
            .createFromElements(
                blobsCellBundle.getProofs().stream().map(SszKZGProof::new).toList()),
        getBlobsSchema().createFromElements(blobsCellBundle.getBlobs()));
  }
}
