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

package tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.eip4844;

import tech.pegasys.teku.infrastructure.ssz.containers.Container2;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema2;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockSchema;
import tech.pegasys.teku.spec.datastructures.execution.versions.eip4844.BlobsSidecar;

public class SignedBeaconBlockAndBlobsSidecar
    extends Container2<SignedBeaconBlockAndBlobsSidecar, SignedBeaconBlock, BlobsSidecar> {

  public static class SignedBeaconBlockAndBlobsSidecarSchema
      extends ContainerSchema2<SignedBeaconBlockAndBlobsSidecar, SignedBeaconBlock, BlobsSidecar> {

    SignedBeaconBlockAndBlobsSidecarSchema(
        final SignedBeaconBlockSchema signedBeaconBlockSchema,
        final BlobsSidecar.BlobsSidecarSchema blobsSidecarSchema) {
      super(
          "SignedBeaconBlockAndBlobsSidecar",
          namedSchema("beacon_block", signedBeaconBlockSchema),
          namedSchema("blobs_sidecar", blobsSidecarSchema));
    }

    @Override
    public SignedBeaconBlockAndBlobsSidecar createFromBackingNode(final TreeNode node) {
      return new SignedBeaconBlockAndBlobsSidecar(this, node);
    }
  }

  public static SignedBeaconBlockAndBlobsSidecarSchema create(
      final SignedBeaconBlockSchema signedBeaconBlockSchema,
      final BlobsSidecar.BlobsSidecarSchema blobsSidecarSchema) {
    return new SignedBeaconBlockAndBlobsSidecarSchema(signedBeaconBlockSchema, blobsSidecarSchema);
  }

  private SignedBeaconBlockAndBlobsSidecar(
      final SignedBeaconBlockAndBlobsSidecarSchema type, final TreeNode backingNode) {
    super(type, backingNode);
  }

  public SignedBeaconBlockAndBlobsSidecar(
      final SignedBeaconBlockAndBlobsSidecarSchema schema,
      final SignedBeaconBlock signedBeaconBlock,
      final BlobsSidecar blobsSidecar) {
    super(schema, signedBeaconBlock, blobsSidecar);
  }

  public SignedBeaconBlock getSignedBeaconBlock() {
    return getField0();
  }

  public BlobsSidecar getBlobsSidecar() {
    return getField1();
  }
}
