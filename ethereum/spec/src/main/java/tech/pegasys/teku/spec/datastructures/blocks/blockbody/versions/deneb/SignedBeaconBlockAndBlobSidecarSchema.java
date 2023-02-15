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

import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema2;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockSchema;
import tech.pegasys.teku.spec.datastructures.execution.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.execution.versions.deneb.BlobSidecarSchema;

public class SignedBeaconBlockAndBlobSidecarSchema
    extends ContainerSchema2<SignedBeaconBlockAndBlobSidecar, SignedBeaconBlock, BlobSidecar> {

  SignedBeaconBlockAndBlobSidecarSchema(
      final SignedBeaconBlockSchema signedBeaconBlockSchema,
      final BlobSidecarSchema blobSidecarSchema) {
    super(
        "SignedBeaconBlockAndBlobSidecar",
        namedSchema("beacon_block", signedBeaconBlockSchema),
        namedSchema("blob_sidecar", blobSidecarSchema));
  }

  public static SignedBeaconBlockAndBlobSidecarSchema create(
      final SignedBeaconBlockSchema signedBeaconBlockSchema,
      final BlobSidecarSchema blobSidecarSchema) {
    return new SignedBeaconBlockAndBlobSidecarSchema(signedBeaconBlockSchema, blobSidecarSchema);
  }

  public SignedBeaconBlockAndBlobSidecar create(
      final SignedBeaconBlock signedBeaconBlock, final BlobSidecar blobSidecar) {
    return new SignedBeaconBlockAndBlobSidecar(this, signedBeaconBlock, blobSidecar);
  }

  @Override
  public SignedBeaconBlockAndBlobSidecar createFromBackingNode(final TreeNode node) {
    return new SignedBeaconBlockAndBlobSidecar(this, node);
  }

  public BlobSidecarSchema getBlobSidecarSchema() {
    return (BlobSidecarSchema) getFieldSchema1();
  }
}
