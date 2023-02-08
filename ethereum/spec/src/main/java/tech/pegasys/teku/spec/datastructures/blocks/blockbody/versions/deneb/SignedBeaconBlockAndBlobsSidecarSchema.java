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

package tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb;

import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema2;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockSchema;
import tech.pegasys.teku.spec.datastructures.execution.versions.deneb.BlobsSidecar;
import tech.pegasys.teku.spec.datastructures.execution.versions.deneb.BlobsSidecarSchema;

public class SignedBeaconBlockAndBlobsSidecarSchema
    extends ContainerSchema2<SignedBeaconBlockAndBlobsSidecar, SignedBeaconBlock, BlobsSidecar> {

  SignedBeaconBlockAndBlobsSidecarSchema(
      final SignedBeaconBlockSchema signedBeaconBlockSchema,
      final BlobsSidecarSchema blobsSidecarSchema) {
    super(
        "SignedBeaconBlockAndBlobsSidecar",
        namedSchema("beacon_block", signedBeaconBlockSchema),
        namedSchema("blobs_sidecar", blobsSidecarSchema));
  }

  public static SignedBeaconBlockAndBlobsSidecarSchema create(
      final SignedBeaconBlockSchema signedBeaconBlockSchema,
      final BlobsSidecarSchema blobsSidecarSchema) {
    return new SignedBeaconBlockAndBlobsSidecarSchema(signedBeaconBlockSchema, blobsSidecarSchema);
  }

  public SignedBeaconBlockAndBlobsSidecar create(
      final SignedBeaconBlock signedBeaconBlock, final BlobsSidecar blobsSidecar) {
    return new SignedBeaconBlockAndBlobsSidecar(this, signedBeaconBlock, blobsSidecar);
  }

  @Override
  public SignedBeaconBlockAndBlobsSidecar createFromBackingNode(final TreeNode node) {
    return new SignedBeaconBlockAndBlobsSidecar(this, node);
  }

  public BlobsSidecarSchema getBlobsSidecarSchema() {
    return (BlobsSidecarSchema) getFieldSchema1();
  }
}
