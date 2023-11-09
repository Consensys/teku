/*
 * Copyright Consensys Software Inc., 2023
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
import tech.pegasys.teku.spec.datastructures.type.SszSignature;

@Deprecated
public class SignedBlobSidecarOld
    extends Container2<SignedBlobSidecarOld, BlobSidecarOld, SszSignature> {

  SignedBlobSidecarOld(final SignedBlobSidecarSchemaOld type, final TreeNode backingNode) {
    super(type, backingNode);
  }

  public SignedBlobSidecarOld(
      final SignedBlobSidecarSchemaOld schema,
      final BlobSidecarOld blobSidecar,
      final BLSSignature signature) {
    super(schema, blobSidecar, new SszSignature(signature));
  }

  public BlobSidecarOld getMessage() {
    return getField0();
  }

  public BlobSidecarOld getBlobSidecar() {
    return getField0();
  }

  public BLSSignature getSignature() {
    return getField1().getSignature();
  }

  public UInt64 getSlot() {
    return getBlobSidecar().getSlot();
  }
}
