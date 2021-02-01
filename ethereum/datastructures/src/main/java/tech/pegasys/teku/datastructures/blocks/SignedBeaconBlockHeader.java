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
import tech.pegasys.teku.ssz.backing.VectorViewRead;
import tech.pegasys.teku.ssz.backing.containers.Container2;
import tech.pegasys.teku.ssz.backing.containers.ContainerType2;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.type.ComplexViewTypes;
import tech.pegasys.teku.ssz.backing.view.BasicViews.ByteView;
import tech.pegasys.teku.ssz.backing.view.ViewUtils;
import tech.pegasys.teku.ssz.sos.SszTypeDescriptor;

public class SignedBeaconBlockHeader
    extends Container2<SignedBeaconBlockHeader, BeaconBlockHeader, VectorViewRead<ByteView>> {

  public static class SignedBeaconBlockHeaderType
      extends ContainerType2<SignedBeaconBlockHeader, BeaconBlockHeader, VectorViewRead<ByteView>> {

    public SignedBeaconBlockHeaderType() {
      super(
          "SignedBeaconBlockHeader",
          namedType("message", BeaconBlockHeader.TYPE),
          namedType("signature", ComplexViewTypes.BYTES_96_TYPE));
    }

    @Override
    public SignedBeaconBlockHeader createFromBackingNode(TreeNode node) {
      return new SignedBeaconBlockHeader(this, node);
    }
  }

  @SszTypeDescriptor
  public static final SignedBeaconBlockHeaderType TYPE = new SignedBeaconBlockHeaderType();

  private BLSSignature signatureCache;

  private SignedBeaconBlockHeader(SignedBeaconBlockHeaderType type, TreeNode backingNode) {
    super(type, backingNode);
  }

  public SignedBeaconBlockHeader(final BeaconBlockHeader message, final BLSSignature signature) {
    super(TYPE, message, ViewUtils.createVectorFromBytes(signature.toBytesCompressed()));
    signatureCache = signature;
  }

  public BeaconBlockHeader getMessage() {
    return getField0();
  }

  public BLSSignature getSignature() {
    if (signatureCache == null) {
      signatureCache = BLSSignature.fromBytesCompressed(ViewUtils.getAllBytes(getField1()));
    }
    return signatureCache;
  }
}
