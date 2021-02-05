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

package tech.pegasys.teku.datastructures.blocks;

import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.ssz.backing.SszVector;
import tech.pegasys.teku.ssz.backing.containers.Container2;
import tech.pegasys.teku.ssz.backing.containers.ContainerType2;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.schema.SszComplexSchemas;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives.SszByte;
import tech.pegasys.teku.ssz.backing.view.SszUtils;

public class SignedBeaconBlockHeader
    extends Container2<SignedBeaconBlockHeader, BeaconBlockHeader, SszVector<SszByte>> {

  public static class SignedBeaconBlockHeaderType
      extends ContainerType2<SignedBeaconBlockHeader, BeaconBlockHeader, SszVector<SszByte>> {

    public SignedBeaconBlockHeaderType() {
      super(
          "SignedBeaconBlockHeader",
          namedSchema("message", BeaconBlockHeader.TYPE),
          namedSchema("signature", SszComplexSchemas.BYTES_96_SCHEMA));
    }

    @Override
    public SignedBeaconBlockHeader createFromBackingNode(TreeNode node) {
      return new SignedBeaconBlockHeader(this, node);
    }
  }

  public static final SignedBeaconBlockHeaderType TYPE = new SignedBeaconBlockHeaderType();

  private BLSSignature signatureCache;

  private SignedBeaconBlockHeader(SignedBeaconBlockHeaderType type, TreeNode backingNode) {
    super(type, backingNode);
  }

  public SignedBeaconBlockHeader(final BeaconBlockHeader message, final BLSSignature signature) {
    super(TYPE, message, SszUtils.createVectorFromBytes(signature.toBytesCompressed()));
    signatureCache = signature;
  }

  public BeaconBlockHeader getMessage() {
    return getField0();
  }

  public BLSSignature getSignature() {
    if (signatureCache == null) {
      signatureCache = BLSSignature.fromBytesCompressed(SszUtils.getAllBytes(getField1()));
    }
    return signatureCache;
  }
}
