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

import tech.pegasys.teku.infrastructure.ssz.containers.Container2;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobsSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;

public class SignedBeaconBlockAndBlobsSidecar
    extends Container2<SignedBeaconBlockAndBlobsSidecar, SignedBeaconBlock, BlobsSidecar> {

  SignedBeaconBlockAndBlobsSidecar(
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
