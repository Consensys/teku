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

public class SignedBlindedBlobSidecarsSchema
    extends ContainerSchema1<SignedBlindedBlobSidecars, SszList<SignedBlindedBlobSidecar>> {

  static final SszFieldName FIELD_SIGNED_BLINDED_BLOB_SIDECARS =
      () -> "signed_blinded_blob_sidecars";

  SignedBlindedBlobSidecarsSchema(
      final SpecConfigDeneb specConfig,
      final SignedBlindedBlobSidecarSchema signedBlindedBlobSidecarSchema) {
    super(
        "SignedBlobSidecarsSchema",
        namedSchema(
            FIELD_SIGNED_BLINDED_BLOB_SIDECARS,
            SszListSchema.create(
                signedBlindedBlobSidecarSchema, specConfig.getMaxBlobsPerBlock())));
  }

  public static SignedBlindedBlobSidecarsSchema create(
      final SpecConfigDeneb specConfig,
      final SignedBlindedBlobSidecarSchema signedBlindedBlobSidecarSchema) {
    return new SignedBlindedBlobSidecarsSchema(specConfig, signedBlindedBlobSidecarSchema);
  }

  @SuppressWarnings("unchecked")
  public SszListSchema<SignedBlindedBlobSidecar, ?> getSignedBlindedBlobSidecarsSchema() {
    return (SszListSchema<SignedBlindedBlobSidecar, ?>)
        getChildSchema(getFieldIndex(FIELD_SIGNED_BLINDED_BLOB_SIDECARS));
  }

  public SignedBlindedBlobSidecarSchema getSignedBlindedBlobSidecarSchema() {
    return (SignedBlindedBlobSidecarSchema) getSignedBlindedBlobSidecarsSchema().getElementSchema();
  }

  public SignedBlindedBlobSidecars create(
      final List<SignedBlindedBlobSidecar> signedBlindedBlobSidecars) {
    return new SignedBlindedBlobSidecars(this, signedBlindedBlobSidecars);
  }

  @Override
  public SignedBlindedBlobSidecars createFromBackingNode(final TreeNode node) {
    return new SignedBlindedBlobSidecars(this, node);
  }
}
