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

package tech.pegasys.teku.spec.datastructures.builder;

import java.util.stream.Collectors;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema3;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.config.SpecConfigDeneb;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.Blob;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSchema;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitmentSchema;
import tech.pegasys.teku.spec.datastructures.type.SszKZGProof;
import tech.pegasys.teku.spec.datastructures.type.SszKZGProofSchema;

public class BlobsBundleSchema
    extends ContainerSchema3<
        BlobsBundle, SszList<SszKZGCommitment>, SszList<SszKZGProof>, SszList<Blob>> {

  public BlobsBundleSchema(
      final String containerName, final BlobSchema blobSchema, final SpecConfigDeneb specConfig) {
    super(
        containerName,
        namedSchema(
            "commitments",
            SszListSchema.create(
                SszKZGCommitmentSchema.INSTANCE, specConfig.getMaxBlobsPerBlock())),
        namedSchema(
            "proofs",
            SszListSchema.create(SszKZGProofSchema.INSTANCE, specConfig.getMaxBlobsPerBlock())),
        namedSchema("blobs", SszListSchema.create(blobSchema, specConfig.getMaxBlobsPerBlock())));
  }

  @SuppressWarnings("unchecked")
  public SszListSchema<SszKZGCommitment, ?> getCommitmentsSchema() {
    return (SszListSchema<SszKZGCommitment, ?>) getChildSchema(getFieldIndex("commitments"));
  }

  @SuppressWarnings("unchecked")
  public SszListSchema<SszKZGProof, ?> getProofsSchema() {
    return (SszListSchema<SszKZGProof, ?>) getChildSchema(getFieldIndex("proofs"));
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
        getCommitmentsSchema()
            .createFromElements(
                blobsBundle.getCommitments().stream()
                    .map(SszKZGCommitment::new)
                    .collect(Collectors.toList())),
        getProofsSchema()
            .createFromElements(
                blobsBundle.getProofs().stream()
                    .map(SszKZGProof::new)
                    .collect(Collectors.toList())),
        getBlobsSchema().createFromElements(blobsBundle.getBlobs()));
  }
}
