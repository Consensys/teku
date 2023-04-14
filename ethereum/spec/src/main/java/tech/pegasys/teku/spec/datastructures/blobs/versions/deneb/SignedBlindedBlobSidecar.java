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
import tech.pegasys.teku.spec.datastructures.type.SszSignature;

public class SignedBlindedBlobSidecar
    extends Container2<SignedBlindedBlobSidecar, BlindedBlobSidecar, SszSignature> {

  SignedBlindedBlobSidecar(final SignedBlindedBlobSidecarSchema type, final TreeNode backingNode) {
    super(type, backingNode);
  }

  public SignedBlindedBlobSidecar(
      final SignedBlindedBlobSidecarSchema schema,
      final BlindedBlobSidecar blindedBlobSidecar,
      final BLSSignature signature) {
    super(schema, blindedBlobSidecar, new SszSignature(signature));
  }

  public BlindedBlobSidecar getBlindedBlobSidecar() {
    return getField0();
  }

  public BLSSignature getSignature() {
    return getField1().getSignature();
  }
}
