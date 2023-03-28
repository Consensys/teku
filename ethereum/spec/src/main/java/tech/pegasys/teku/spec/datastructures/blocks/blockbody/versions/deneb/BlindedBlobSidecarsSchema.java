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

package tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb;

import java.util.List;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema1;
import tech.pegasys.teku.infrastructure.ssz.schema.SszFieldName;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.config.SpecConfigDeneb;

public class BlindedBlobSidecarsSchema
    extends ContainerSchema1<BlindedBlobSidecars, SszList<BlindedBlobSidecar>> {

  static final SszFieldName FIELD_BLINDED_BLOB_SIDECARS = () -> "blinded_blob_sidecars";

  BlindedBlobSidecarsSchema(
      final SpecConfigDeneb specConfig, final BlindedBlobSidecarSchema blindedBlobSidecarSchema) {
    super(
        "BlindedBlobSidecars",
        namedSchema(
            FIELD_BLINDED_BLOB_SIDECARS,
            SszListSchema.create(blindedBlobSidecarSchema, specConfig.getMaxBlobsPerBlock())));
  }

  public static BlindedBlobSidecarsSchema create(
      final SpecConfigDeneb specConfig, final BlindedBlobSidecarSchema blindedBlobSidecarSchema) {
    return new BlindedBlobSidecarsSchema(specConfig, blindedBlobSidecarSchema);
  }

  public SszListSchema<BlindedBlobSidecar, ?> getBlindedBlobSidecarsSchema() {
    return (SszListSchema<BlindedBlobSidecar, ?>)
        getChildSchema(getFieldIndex(FIELD_BLINDED_BLOB_SIDECARS));
  }

  public BlindedBlobSidecarSchema getBlindedBlobSidecarSchema() {
    return (BlindedBlobSidecarSchema) getBlindedBlobSidecarsSchema().getElementSchema();
  }

  public BlindedBlobSidecars create(final List<BlindedBlobSidecar> blindedBlobSidecars) {
    return new BlindedBlobSidecars(this, blindedBlobSidecars);
  }

  @Override
  public BlindedBlobSidecars createFromBackingNode(final TreeNode node) {
    return new BlindedBlobSidecars(this, node);
  }
}
