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

package tech.pegasys.teku.spec.datastructures.builder;

import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema3;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.config.SpecConfigDeneb;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.Blob;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobKzgCommitmentsSchema;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSchema;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.KZGCommitment;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.KZGProof;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.KZGProofSchema;

public class BlobsBundleSchema
    extends ContainerSchema3<
        BlobsBundle, SszList<KZGCommitment>, SszList<KZGProof>, SszList<Blob>> {

  public BlobsBundleSchema(
      final String containerName,
      final BlobSchema blobSchema,
      final BlobKzgCommitmentsSchema blobKzgCommitmentsSchema,
      final SpecConfigDeneb specConfig) {
    super(
        containerName,
        namedSchema("commitments", blobKzgCommitmentsSchema),
        namedSchema(
            "proofs",
            SszListSchema.create(
                KZGProofSchema.INSTANCE, specConfig.getMaxBlobCommitmentsPerBlock())),
        namedSchema(
            "blobs", SszListSchema.create(blobSchema, specConfig.getMaxBlobCommitmentsPerBlock())));
  }

  @SuppressWarnings("unchecked")
  public SszListSchema<KZGCommitment, ?> getCommitmentsSchema() {
    return (SszListSchema<KZGCommitment, ?>) getChildSchema(getFieldIndex("commitments"));
  }

  @SuppressWarnings("unchecked")
  public SszListSchema<KZGProof, ?> getProofsSchema() {
    return (SszListSchema<KZGProof, ?>) getChildSchema(getFieldIndex("proofs"));
  }

  @SuppressWarnings("unchecked")
  public SszListSchema<Blob, ?> getBlobsSchema() {
    return (SszListSchema<Blob, ?>) getChildSchema(getFieldIndex("blobs"));
  }

  @Override
  public BlobsBundle createFromBackingNode(final TreeNode node) {
    return new BlobsBundle(this, node);
  }

  public BlobsBundle createFromExecutionBlobsBundle(
      final tech.pegasys.teku.spec.datastructures.execution.BlobsBundle blobsBundle) {
    return new BlobsBundle(
        this,
        getCommitmentsSchema().createFromElements(blobsBundle.getCommitments()),
        getProofsSchema().createFromElements(blobsBundle.getProofs()),
        getBlobsSchema().createFromElements(blobsBundle.getBlobs()));
  }
}
