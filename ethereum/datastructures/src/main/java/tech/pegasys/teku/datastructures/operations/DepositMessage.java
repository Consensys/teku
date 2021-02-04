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

import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.Bytes48;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.datastructures.util.Merkleizable;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.SSZTypes.SSZContainer;
import tech.pegasys.teku.ssz.backing.VectorViewRead;
import tech.pegasys.teku.ssz.backing.containers.Container3;
import tech.pegasys.teku.ssz.backing.containers.ContainerType3;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.type.BasicViewTypes;
import tech.pegasys.teku.ssz.backing.type.ComplexViewTypes;
import tech.pegasys.teku.ssz.backing.view.BasicViews.ByteView;
import tech.pegasys.teku.ssz.backing.view.BasicViews.Bytes32View;
import tech.pegasys.teku.ssz.backing.view.BasicViews.UInt64View;
import tech.pegasys.teku.ssz.backing.view.ViewUtils;
import tech.pegasys.teku.ssz.sos.SimpleOffsetSerializable;
import tech.pegasys.teku.ssz.sos.SszTypeDescriptor;

public class DepositMessage
    extends Container3<DepositMessage, VectorViewRead<ByteView>, Bytes32View, UInt64View>
    implements SimpleOffsetSerializable, SSZContainer, Merkleizable {

  static class DepositMessageType
      extends ContainerType3<DepositMessage, VectorViewRead<ByteView>, Bytes32View, UInt64View> {

    public DepositMessageType() {
      super(
          "DepositMessage",
          namedType("pubkey", ComplexViewTypes.BYTES_48_TYPE),
          namedType("withdrawal_credentials", BasicViewTypes.BYTES32_TYPE),
          namedType("amount", BasicViewTypes.UINT64_TYPE));
    }

    @Override
    public DepositMessage createFromBackingNode(TreeNode node) {
      return new DepositMessage(this, node);
    }
  }

  @SszTypeDescriptor public static final DepositMessageType TYPE = new DepositMessageType();

  private DepositMessage(DepositMessageType type, TreeNode backingNode) {
    super(type, backingNode);
  }

  public DepositMessage(
      final BLSPublicKey pubkey, final Bytes32 withdrawal_credentials, final UInt64 amount) {
    super(
        TYPE,
        ViewUtils.createVectorFromBytes(pubkey.toBytesCompressed()),
        new Bytes32View(withdrawal_credentials),
        new UInt64View(amount));
  }

  public BLSPublicKey getPubkey() {
    return BLSPublicKey.fromBytesCompressed(Bytes48.wrap(ViewUtils.getAllBytes(getField0())));
  }

  public Bytes32 getWithdrawal_credentials() {
    return getField1().get();
  }

  public UInt64 getAmount() {
    return getField2().get();
  }

  @Override
  public Bytes32 hash_tree_root() {
    return hashTreeRoot();
  }
}
