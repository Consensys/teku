/*
 * Copyright 2019 ConsenSys AG.
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
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.backing.VectorViewRead;
import tech.pegasys.teku.ssz.backing.containers.Container4;
import tech.pegasys.teku.ssz.backing.containers.ContainerType4;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.type.BasicViewTypes;
import tech.pegasys.teku.ssz.backing.type.ComplexViewTypes;
import tech.pegasys.teku.ssz.backing.view.BasicViews.ByteView;
import tech.pegasys.teku.ssz.backing.view.BasicViews.Bytes32View;
import tech.pegasys.teku.ssz.backing.view.BasicViews.UInt64View;
import tech.pegasys.teku.ssz.backing.view.ViewUtils;

public class DepositData
    extends Container4<
        DepositData, VectorViewRead<ByteView>, Bytes32View, UInt64View, VectorViewRead<ByteView>> {

  public static class DepositDataType
      extends ContainerType4<
          DepositData,
          VectorViewRead<ByteView>,
          Bytes32View,
          UInt64View,
          VectorViewRead<ByteView>> {

    public DepositDataType() {
      super(
          "DepositData",
          namedType("pubkey", ComplexViewTypes.BYTES_48_TYPE),
          namedType("withdrawal_credentials", BasicViewTypes.BYTES32_TYPE),
          namedType("amount", BasicViewTypes.UINT64_TYPE),
          namedType("signature", ComplexViewTypes.BYTES_96_TYPE));
    }

    @Override
    public DepositData createFromBackingNode(TreeNode node) {
      return new DepositData(this, node);
    }
  }

  public static final DepositDataType TYPE = new DepositDataType();

  private BLSSignature signatureCache;
  private BLSPublicKey pubkeyCache;

  private DepositData(DepositDataType type, TreeNode backingNode) {
    super(type, backingNode);
  }

  public DepositData(
      BLSPublicKey pubkey, Bytes32 withdrawal_credentials, UInt64 amount, BLSSignature signature) {
    super(
        TYPE,
        ViewUtils.createVectorFromBytes(pubkey.toBytesCompressed()),
        new Bytes32View(withdrawal_credentials),
        new UInt64View(amount),
        ViewUtils.createVectorFromBytes(signature.toBytesCompressed()));
    this.pubkeyCache = pubkey;
    this.signatureCache = signature;
  }

  public DepositData(final DepositMessage depositMessage, final BLSSignature signature) {
    this(
        depositMessage.getPubkey(),
        depositMessage.getWithdrawal_credentials(),
        depositMessage.getAmount(),
        signature);
  }

  public DepositData() {
    super(TYPE);
  }

  public BLSPublicKey getPubkey() {
    if (pubkeyCache == null) {
      pubkeyCache =
          BLSPublicKey.fromBytesCompressed(Bytes48.wrap(ViewUtils.getAllBytes(getField0())));
    }
    return pubkeyCache;
  }

  public Bytes32 getWithdrawal_credentials() {
    return getField1().get();
  }

  public UInt64 getAmount() {
    return getField2().get();
  }

  public BLSSignature getSignature() {
    if (signatureCache == null) {
      signatureCache = BLSSignature.fromBytesCompressed(ViewUtils.getAllBytes(getField3()));
    }
    return signatureCache;
  }
}
