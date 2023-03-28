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

public class SignedBlobSidecarsSchema
    extends ContainerSchema1<SignedBlobSidecars, SszList<SignedBlobSidecar>> {

  static final SszFieldName FIELD_SIGNED_BLOB_SIDECARS = () -> "signed_blob_sidecars";

  SignedBlobSidecarsSchema(
      final SpecConfigDeneb specConfig, final SignedBlobSidecarSchema signedBlobSidecarSchema) {
    super(
        "SignedBlobSidecarsSchema",
        namedSchema(
            FIELD_SIGNED_BLOB_SIDECARS,
            SszListSchema.create(signedBlobSidecarSchema, specConfig.getMaxBlobsPerBlock())));
  }

  public static SignedBlobSidecarsSchema create(
      final SpecConfigDeneb specConfig, final SignedBlobSidecarSchema signedBlobSidecarSchema) {
    return new SignedBlobSidecarsSchema(specConfig, signedBlobSidecarSchema);
  }

  public SszListSchema<SignedBlobSidecar, ?> getSignedBlobSidecarsSchema() {
    return (SszListSchema<SignedBlobSidecar, ?>)
        getChildSchema(getFieldIndex(FIELD_SIGNED_BLOB_SIDECARS));
  }

  public SignedBlobSidecarSchema getSignedBlobSidecarSchema() {
    return (SignedBlobSidecarSchema) getSignedBlobSidecarsSchema().getElementSchema();
  }

  public SignedBlobSidecars create(final List<SignedBlobSidecar> signedBlobSidecars) {
    return new SignedBlobSidecars(this, signedBlobSidecars);
  }

  @Override
  public SignedBlobSidecars createFromBackingNode(final TreeNode node) {
    return new SignedBlobSidecars(this, node);
  }
}
