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

package tech.pegasys.teku.datastructures.operations;

import com.google.common.base.MoreObjects;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlockHeader;
import tech.pegasys.teku.datastructures.operations.ProposerSlashing.ProposerSlashingType;
import tech.pegasys.teku.datastructures.util.Merkleizable;
import tech.pegasys.teku.ssz.SSZTypes.SSZContainer;
import tech.pegasys.teku.ssz.backing.VectorViewRead;
import tech.pegasys.teku.ssz.backing.containers.Container2;
import tech.pegasys.teku.ssz.backing.containers.ContainerType2;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.type.BasicViewTypes;
import tech.pegasys.teku.ssz.backing.type.VectorViewType;
import tech.pegasys.teku.ssz.backing.view.BasicViews.ByteView;
import tech.pegasys.teku.ssz.backing.view.ViewUtils;
import tech.pegasys.teku.ssz.sos.SimpleOffsetSerializable;
import tech.pegasys.teku.ssz.sos.SszTypeDescriptor;

public class SignedVoluntaryExit
    extends Container2<SignedVoluntaryExit, VoluntaryExit, VectorViewRead<ByteView>>
    implements SimpleOffsetSerializable, SSZContainer, Merkleizable {

  static class SignedVoluntaryExitType
      extends ContainerType2<SignedVoluntaryExit, VoluntaryExit, VectorViewRead<ByteView>> {

    public SignedVoluntaryExitType() {
      super(VoluntaryExit.TYPE,
          new VectorViewType<>(BasicViewTypes.BYTE_TYPE, 96));
    }

    @Override
    public SignedVoluntaryExit createFromBackingNode(TreeNode node) {
      return new SignedVoluntaryExit(this, node);
    }
  }

  @SszTypeDescriptor public static final SignedVoluntaryExitType TYPE = new SignedVoluntaryExitType();

  private BLSSignature signatureCache;

  private SignedVoluntaryExit(
      SignedVoluntaryExitType type,
      TreeNode backingNode) {
    super(type, backingNode);
  }

  public SignedVoluntaryExit(final VoluntaryExit message, final BLSSignature signature) {
    super(TYPE, message, ViewUtils.createVectorFromBytes(signature.toBytesCompressed()));
    this.signatureCache = signature;
  }

  public VoluntaryExit getMessage() {
    return getField0();
  }

  public BLSSignature getSignature() {
    if (signatureCache == null) {
      signatureCache = BLSSignature.fromBytesCompressed(ViewUtils.getAllBytes(getField1()));
    }
    return signatureCache;
  }

  @Override
  public Bytes32 hash_tree_root() {
    return hashTreeRoot();
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("message", getMessage())
        .add("signature", getSignature())
        .toString();
  }
}
