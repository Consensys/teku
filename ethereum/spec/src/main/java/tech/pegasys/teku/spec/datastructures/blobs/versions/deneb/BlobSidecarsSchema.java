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

import java.util.List;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema1;
import tech.pegasys.teku.infrastructure.ssz.schema.SszFieldName;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.config.SpecConfigDeneb;

public class BlobSidecarsSchema extends ContainerSchema1<BlobSidecars, SszList<BlobSidecar>> {

  static final SszFieldName FIELD_BLOB_SIDECARS = () -> "blob_sidecars";

  BlobSidecarsSchema(final SpecConfigDeneb specConfig, final BlobSidecarSchema blobSidecarSchema) {
    super(
        "BlobSidecars",
        namedSchema(
            FIELD_BLOB_SIDECARS,
            SszListSchema.create(blobSidecarSchema, specConfig.getMaxBlobsPerBlock())));
  }

  public static BlobSidecarsSchema create(
      final SpecConfigDeneb specConfig, final BlobSidecarSchema blobSidecarSchema) {
    return new BlobSidecarsSchema(specConfig, blobSidecarSchema);
  }

  @SuppressWarnings("unchecked")
  public SszListSchema<BlobSidecar, ?> getBlobSidecarsSchema() {
    return (SszListSchema<BlobSidecar, ?>) getChildSchema(getFieldIndex(FIELD_BLOB_SIDECARS));
  }

  public BlobSidecarSchema getBlobSidecarSchema() {
    return (BlobSidecarSchema) getBlobSidecarsSchema().getElementSchema();
  }

  public BlobSidecars create(final List<BlobSidecar> blobSidecars) {
    return new BlobSidecars(this, blobSidecars);
  }

  @Override
  public BlobSidecars createFromBackingNode(final TreeNode node) {
    return new BlobSidecars(this, node);
  }
}
