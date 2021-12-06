/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.spec.datastructures.blocks;

import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.ssz.containers.Container2;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema2;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;
import tech.pegasys.teku.spec.datastructures.type.SszSignatureSchema;

public class SignedBeaconBlockHeader
    extends Container2<SignedBeaconBlockHeader, BeaconBlockHeader, SszSignature> {

  public static class SignedBeaconBlockHeaderSchema
      extends ContainerSchema2<SignedBeaconBlockHeader, BeaconBlockHeader, SszSignature> {

    public SignedBeaconBlockHeaderSchema() {
      super(
          "SignedBeaconBlockHeader",
          namedSchema("message", BeaconBlockHeader.SSZ_SCHEMA),
          namedSchema("signature", SszSignatureSchema.INSTANCE));
    }

    @Override
    public SignedBeaconBlockHeader createFromBackingNode(TreeNode node) {
      return new SignedBeaconBlockHeader(this, node);
    }
  }

  public static final SignedBeaconBlockHeaderSchema SSZ_SCHEMA =
      new SignedBeaconBlockHeaderSchema();

  private SignedBeaconBlockHeader(SignedBeaconBlockHeaderSchema type, TreeNode backingNode) {
    super(type, backingNode);
  }

  public SignedBeaconBlockHeader(final BeaconBlockHeader message, final BLSSignature signature) {
    super(SSZ_SCHEMA, message, new SszSignature(signature));
  }

  public BeaconBlockHeader getMessage() {
    return getField0();
  }

  public BLSSignature getSignature() {
    return getField1().getSignature();
  }
}
