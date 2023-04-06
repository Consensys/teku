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

import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema2;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockFields;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;
import tech.pegasys.teku.spec.datastructures.type.SszSignatureSchema;

public class SignedBlindedBlobSidecarSchema
    extends ContainerSchema2<SignedBlindedBlobSidecar, BlindedBlobSidecar, SszSignature> {

  SignedBlindedBlobSidecarSchema(final BlindedBlobSidecarSchema blindedBlobSidecarSchema) {
    super(
        "SignedBlindedBlobSidecar",
        namedSchema("message", blindedBlobSidecarSchema),
        namedSchema(SignedBeaconBlockFields.SIGNATURE, SszSignatureSchema.INSTANCE));
  }

  public static SignedBlindedBlobSidecarSchema create(
      final BlindedBlobSidecarSchema blindedBlobSidecarSchema) {
    return new SignedBlindedBlobSidecarSchema(blindedBlobSidecarSchema);
  }

  public SignedBlindedBlobSidecar create(
      final BlindedBlobSidecar blindedBlobSidecar, final BLSSignature signature) {
    return new SignedBlindedBlobSidecar(this, blindedBlobSidecar, signature);
  }

  @Override
  public SignedBlindedBlobSidecar createFromBackingNode(final TreeNode node) {
    return new SignedBlindedBlobSidecar(this, node);
  }

  public BlindedBlobSidecarSchema getBlindedBlobSidecarSchema() {
    return (BlindedBlobSidecarSchema) getFieldSchema0();
  }
}
