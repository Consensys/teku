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

package tech.pegasys.artemis.datastructures.state;

import com.google.common.base.MoreObjects;
import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.ssz.SSZ;
import tech.pegasys.artemis.datastructures.Copyable;
import tech.pegasys.artemis.util.backing.ContainerViewWrite;
import tech.pegasys.artemis.util.backing.VectorViewRead;
import tech.pegasys.artemis.util.backing.ViewRead;
import tech.pegasys.artemis.util.backing.tree.TreeNode;
import tech.pegasys.artemis.util.backing.type.BasicViewTypes;
import tech.pegasys.artemis.util.backing.type.ContainerViewType;
import tech.pegasys.artemis.util.backing.type.VectorViewType;
import tech.pegasys.artemis.util.backing.view.BasicViews.BitView;
import tech.pegasys.artemis.util.backing.view.BasicViews.ByteView;
import tech.pegasys.artemis.util.backing.view.BasicViews.Bytes32View;
import tech.pegasys.artemis.util.backing.view.BasicViews.UInt64View;
import tech.pegasys.artemis.util.backing.view.ContainerViewImpl;
import tech.pegasys.artemis.util.backing.view.ViewUtils;
import tech.pegasys.artemis.util.bls.BLSPublicKey;

public class Validator extends ContainerViewImpl<Validator>
    implements ValidatorWrite,
        Copyable<Validator> {
  // The number of SimpleSerialize basic types in this SSZ Container/POJO.
  public static final int SSZ_FIELD_COUNT = 8;
  public static final ContainerViewType<Validator> TYPE =
      new ContainerViewType<>(
          List.of(
              new VectorViewType<ByteView>(BasicViewTypes.BYTE_TYPE, 48),
              BasicViewTypes.BYTES32_TYPE,
              BasicViewTypes.UINT64_TYPE,
              BasicViewTypes.BIT_TYPE,
              BasicViewTypes.UINT64_TYPE,
              BasicViewTypes.UINT64_TYPE,
              BasicViewTypes.UINT64_TYPE,
              BasicViewTypes.UINT64_TYPE),
          Validator::new);

  // BLS public key
  @SuppressWarnings("unused")
  private final BLSPublicKey pubkey = null;

  // Withdrawal credentials
  @SuppressWarnings("unused")
  private final Bytes32 withdrawal_credentials = null;

  // Effective balance
  @SuppressWarnings("unused")
  private final UnsignedLong effective_balance = null;

  // Was the validator slashed
  @SuppressWarnings("unused")
  private final boolean slashed = false;

  // Epoch when became eligible for activation
  @SuppressWarnings("unused")
  private final UnsignedLong activation_eligibility_epoch = null;

  // Epoch when validator activated
  @SuppressWarnings("unused")
  private final UnsignedLong activation_epoch = null;

  // Epoch when validator exited
  @SuppressWarnings("unused")
  private final UnsignedLong exit_epoch = null;

  // Epoch when validator withdrew
  @SuppressWarnings("unused")
  private final UnsignedLong withdrawable_epoch = null;

  private Validator(
      ContainerViewType<? extends ContainerViewWrite<ViewRead>> type, TreeNode backingNode) {
    super(type, backingNode);
  }

  public Validator(
      BLSPublicKey pubkey,
      Bytes32 withdrawal_credentials,
      UnsignedLong effective_balance,
      boolean slashed,
      UnsignedLong activation_eligibility_epoch,
      UnsignedLong activation_epoch,
      UnsignedLong exit_epoch,
      UnsignedLong withdrawable_epoch) {
    super(
        TYPE,
        ViewUtils.createVectorFromBytes(pubkey.toBytes()),
        new Bytes32View(withdrawal_credentials),
        new UInt64View(effective_balance),
        new BitView(slashed),
        new UInt64View(activation_eligibility_epoch),
        new UInt64View(activation_epoch),
        new UInt64View(exit_epoch),
        new UInt64View(withdrawable_epoch));
  }

  public Validator(Validator validator) {
    super(TYPE, validator.getBackingNode());
  }

  public Validator() {
    super(TYPE);
  }

  @Override
  public Validator copy() {
    return new Validator(this);
  }

  @Override
  public int getSSZFieldCount() {
    return SSZ_FIELD_COUNT;
  }

  @Override
  public List<Bytes> get_fixed_parts() {
    List<Bytes> fixedPartsList = new ArrayList<>();
    fixedPartsList.addAll(getPubkey().get_fixed_parts());
    fixedPartsList.addAll(
        List.of(
            SSZ.encode(writer -> writer.writeFixedBytes(getWithdrawal_credentials())),
            SSZ.encodeUInt64(getEffective_balance().longValue()),
            SSZ.encodeBoolean(isSlashed()),
            SSZ.encodeUInt64(getActivation_eligibility_epoch().longValue()),
            SSZ.encodeUInt64(getActivation_epoch().longValue()),
            SSZ.encodeUInt64(getExit_epoch().longValue()),
            SSZ.encodeUInt64(getWithdrawable_epoch().longValue())));
    return fixedPartsList;
  }

  public static Validator fromBytes(Bytes bytes) {
    return SSZ.decode(
        bytes,
        reader ->
            new Validator(
                BLSPublicKey.fromBytes(reader.readFixedBytes(48)),
                Bytes32.wrap(reader.readFixedBytes(32)),
                UnsignedLong.fromLongBits(reader.readUInt64()),
                reader.readBoolean(),
                UnsignedLong.fromLongBits(reader.readUInt64()),
                UnsignedLong.fromLongBits(reader.readUInt64()),
                UnsignedLong.fromLongBits(reader.readUInt64()),
                UnsignedLong.fromLongBits(reader.readUInt64())));
  }

  public Bytes toBytes() {
    return SSZ.encode(
        writer -> {
          writer.writeFixedBytes(getPubkey().toBytes());
          writer.writeFixedBytes(getWithdrawal_credentials());
          writer.writeUInt64(getEffective_balance().longValue());
          writer.writeBoolean(isSlashed());
          writer.writeUInt64(getActivation_eligibility_epoch().longValue());
          writer.writeUInt64(getActivation_epoch().longValue());
          writer.writeUInt64(getExit_epoch().longValue());
          writer.writeUInt64(getWithdrawable_epoch().longValue());
        });
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        getPubkey(),
        getWithdrawal_credentials(),
        getEffective_balance(),
        isSlashed(),
        getActivation_eligibility_epoch(),
        getActivation_epoch(),
        getExit_epoch(),
        getWithdrawable_epoch());
  }

  @Override
  public boolean equals(Object obj) {
    if (Objects.isNull(obj)) {
      return false;
    }

    if (this == obj) {
      return true;
    }

    if (!(obj instanceof Validator)) {
      return false;
    }

    Validator other = (Validator) obj;
    return Objects.equals(this.getPubkey(), other.getPubkey())
        && Objects.equals(this.getWithdrawal_credentials(), other.getWithdrawal_credentials())
        && Objects.equals(this.getEffective_balance(), other.getEffective_balance())
        && Objects.equals(this.isSlashed(), other.isSlashed())
        && Objects.equals(
            this.getActivation_eligibility_epoch(), other.getActivation_eligibility_epoch())
        && Objects.equals(this.getActivation_epoch(), other.getActivation_epoch())
        && Objects.equals(this.getExit_epoch(), other.getExit_epoch())
        && Objects.equals(this.getWithdrawable_epoch(), other.getWithdrawable_epoch());
  }

  @Override
  public BLSPublicKey getPubkey() {
    @SuppressWarnings("unchecked")
    VectorViewRead<ByteView> bytesView = (VectorViewRead<ByteView>) get(0);
    return BLSPublicKey.fromBytes(ViewUtils.getAllBytes(bytesView));
  }

  @Override
  public void setPubkey(BLSPublicKey pubkey) {
    set(0, ViewUtils.createVectorFromBytes(pubkey.toBytes()));
  }

  @Override
  public Bytes32 getWithdrawal_credentials() {
    return ((Bytes32View) get(1)).get();
  }

  @Override
  public UnsignedLong getEffective_balance() {
    return ((UInt64View) get(2)).get();
  }

  @Override
  public void setEffective_balance(UnsignedLong effective_balance) {
    set(2, new UInt64View(effective_balance));
  }

  @Override
  public boolean isSlashed() {
    return ((BitView) get(3)).get();
  }

  @Override
  public void setSlashed(boolean slashed) {
    set(3, new BitView(slashed));
  }

  @Override
  public UnsignedLong getActivation_eligibility_epoch() {
    return ((UInt64View) get(4)).get();
  }

  @Override
  public void setActivation_eligibility_epoch(UnsignedLong activation_eligibility_epoch) {
    set(4, new UInt64View(activation_eligibility_epoch));
  }

  @Override
  public UnsignedLong getActivation_epoch() {
    return ((UInt64View) get(5)).get();
  }

  @Override
  public void setActivation_epoch(UnsignedLong activation_epoch) {
    set(5, new UInt64View(activation_epoch));
  }

  @Override
  public UnsignedLong getExit_epoch() {
    return ((UInt64View) get(6)).get();
  }

  @Override
  public void setExit_epoch(UnsignedLong exit_epoch) {
    set(6, new UInt64View(exit_epoch));
  }

  @Override
  public UnsignedLong getWithdrawable_epoch() {
    return ((UInt64View) get(7)).get();
  }

  @Override
  public void setWithdrawable_epoch(UnsignedLong withdrawable_epoch) {
    set(7, new UInt64View(withdrawable_epoch));
  }

  @Override
  public Bytes32 hash_tree_root() {
    return hashTreeRoot();
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("pubkey", getPubkey())
        .add("withdrawal_credentials", getWithdrawal_credentials())
        .add("effective_balance", getEffective_balance())
        .add("slashed", isSlashed())
        .add("activation_eligibility_epoch", getActivation_eligibility_epoch())
        .add("activation_epoch", getActivation_epoch())
        .add("exit_epoch", getExit_epoch())
        .add("withdrawable_epoch", getWithdrawable_epoch())
        .toString();
  }
}
