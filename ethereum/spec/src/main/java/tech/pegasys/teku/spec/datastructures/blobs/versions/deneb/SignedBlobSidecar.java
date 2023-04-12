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

import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.ssz.containers.Container2;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.execution.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;

public class SignedBlobSidecar extends Container2<SignedBlobSidecar, BlobSidecar, SszSignature> {

  SignedBlobSidecar(final SignedBlobSidecarSchema type, final TreeNode backingNode) {
    super(type, backingNode);
  }

  public SignedBlobSidecar(
      final SignedBlobSidecarSchema schema,
      final BlobSidecar blobSidecar,
      final BLSSignature signature) {
    super(schema, blobSidecar, new SszSignature(signature));
  }

  public BlobSidecar getMessage() {
    return getField0();
  }

  public BlobSidecar getBlobSidecar() {
    return getField0();
  }

  public BLSSignature getSignature() {
    return getField1().getSignature();
  }

  public UInt64 getSlot() {
    return getBlobSidecar().getSlot();
  }
}
