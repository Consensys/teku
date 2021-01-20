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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.Bytes48;
import org.apache.tuweni.ssz.SSZ;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlockHeader;
import tech.pegasys.teku.datastructures.util.HashTreeUtil;
import tech.pegasys.teku.datastructures.util.HashTreeUtil.SSZTypes;
import tech.pegasys.teku.datastructures.util.Merkleizable;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.SSZTypes.SSZContainer;
import tech.pegasys.teku.ssz.backing.ListViewRead;
import tech.pegasys.teku.ssz.backing.VectorViewRead;
import tech.pegasys.teku.ssz.backing.containers.Container3;
import tech.pegasys.teku.ssz.backing.containers.ContainerType2;
import tech.pegasys.teku.ssz.backing.containers.ContainerType3;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.type.BasicViewTypes;
import tech.pegasys.teku.ssz.backing.type.VectorViewType;
import tech.pegasys.teku.ssz.backing.view.BasicViews.ByteView;
import tech.pegasys.teku.ssz.backing.view.BasicViews.Bytes32View;
import tech.pegasys.teku.ssz.backing.view.BasicViews.UInt64View;
import tech.pegasys.teku.ssz.backing.view.ViewUtils;
import tech.pegasys.teku.ssz.sos.SimpleOffsetSerializable;
import tech.pegasys.teku.ssz.sos.SszTypeDescriptor;

public class DepositMessage extends
    Container3<DepositMessage, VectorViewRead<ByteView>, Bytes32View, UInt64View> implements
    SimpleOffsetSerializable, SSZContainer, Merkleizable {

  @SszTypeDescriptor
  public static final ContainerType3<DepositMessage, VectorViewRead<ByteView>, Bytes32View, UInt64View> TYPE =
      ContainerType3.create(
          new VectorViewType<ByteView>(BasicViewTypes.BYTE_TYPE, 48),
          BasicViewTypes.BYTES32_TYPE, BasicViewTypes.UINT64_TYPE,
          DepositMessage::new);

  // The number of SimpleSerialize basic types in this SSZ Container/POJO.
  private static final int SSZ_FIELD_COUNT = 2;

  private BLSPublicKey pubkey;
  private Bytes32 withdrawal_credentials;
  private UInt64 amount;

  private DepositMessage(
      ContainerType3<DepositMessage, VectorViewRead<ByteView>, Bytes32View, UInt64View> type,
      TreeNode backingNode) {
    super(type, backingNode);
  }

  public DepositMessage(
      final BLSPublicKey pubkey, final Bytes32 withdrawal_credentials, final UInt64 amount) {
    super(TYPE, ViewUtils.createVectorFromBytes(pubkey.toBytesCompressed()),
        new Bytes32View(withdrawal_credentials), new UInt64View(amount));
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
  public int getSSZFieldCount() {
    return pubkey.getSSZFieldCount() + SSZ_FIELD_COUNT;
  }

  @Override
  public List<Bytes> get_fixed_parts() {
    List<Bytes> fixedPartsList = new ArrayList<>();
    fixedPartsList.addAll(pubkey.get_fixed_parts());
    fixedPartsList.addAll(
        List.of(
            SSZ.encode(writer -> writer.writeFixedBytes(withdrawal_credentials)),
            SSZ.encodeUInt64(amount.longValue())));
    return fixedPartsList;
  }

  @Override
  public Bytes32 hash_tree_root() {
    return hashTreeRoot();
  }
}
