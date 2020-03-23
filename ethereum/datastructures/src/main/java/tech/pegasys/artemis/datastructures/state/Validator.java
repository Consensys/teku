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

package tech.pegasys.artemis.datastructures.state;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.ssz.SSZ;
import tech.pegasys.artemis.util.SSZTypes.SSZContainer;
import tech.pegasys.artemis.util.backing.ContainerViewRead;
import tech.pegasys.artemis.util.backing.tree.TreeNode;
import tech.pegasys.artemis.util.backing.type.BasicViewTypes;
import tech.pegasys.artemis.util.backing.type.ContainerViewType;
import tech.pegasys.artemis.util.backing.type.VectorViewType;
import tech.pegasys.artemis.util.backing.view.AbstractImmutableContainer;
import tech.pegasys.artemis.util.backing.view.BasicViews.BitView;
import tech.pegasys.artemis.util.backing.view.BasicViews.ByteView;
import tech.pegasys.artemis.util.backing.view.BasicViews.Bytes32View;
import tech.pegasys.artemis.util.backing.view.BasicViews.UInt64View;
import tech.pegasys.artemis.util.backing.view.ViewUtils;
import tech.pegasys.artemis.util.bls.BLSPublicKey;
import tech.pegasys.artemis.util.hashtree.Merkleizable;
import tech.pegasys.artemis.util.sos.SimpleOffsetSerializable;

@JsonAutoDetect(getterVisibility = Visibility.NONE)
public class Validator extends AbstractImmutableContainer
    implements ContainerViewRead, SimpleOffsetSerializable, Merkleizable, SSZContainer {

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

  private Validator(ContainerViewType<Validator> type, TreeNode backingNode) {
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

  @Override
  public int hashCode() {
    return hashTreeRoot().slice(0, 4).toInt();
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
    return hashTreeRoot().equals(other.hashTreeRoot());
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

  public static Validator create(
      BLSPublicKey pubkey,
      Bytes32 withdrawal_credentials,
      UnsignedLong effective_balance,
      boolean slashed,
      UnsignedLong activation_eligibility_epoch,
      UnsignedLong activation_epoch,
      UnsignedLong exit_epoch,
      UnsignedLong withdrawable_epoch) {
    return new Validator(
        pubkey,
        withdrawal_credentials,
        effective_balance,
        slashed,
        activation_eligibility_epoch,
        activation_epoch,
        exit_epoch,
        withdrawable_epoch);
  }

  @JsonProperty
  public BLSPublicKey getPubkey() {
    return BLSPublicKey.fromBytes(ViewUtils.getAllBytes(getAny(0)));
  }

  @JsonProperty
  public Bytes32 getWithdrawal_credentials() {
    return ((Bytes32View) get(1)).get();
  }

  @JsonProperty
  public UnsignedLong getEffective_balance() {
    return ((UInt64View) get(2)).get();
  }

  @JsonProperty
  public boolean isSlashed() {
    return ((BitView) get(3)).get();
  }

  @JsonProperty
  public UnsignedLong getActivation_eligibility_epoch() {
    return ((UInt64View) get(4)).get();
  }

  @JsonProperty
  public UnsignedLong getActivation_epoch() {
    return ((UInt64View) get(5)).get();
  }

  @JsonProperty
  public UnsignedLong getExit_epoch() {
    return ((UInt64View) get(6)).get();
  }

  @JsonProperty
  public UnsignedLong getWithdrawable_epoch() {
    return ((UInt64View) get(7)).get();
  }

  public Validator withEffective_balance(UnsignedLong effective_balance) {
    return create(
        getPubkey(),
        getWithdrawal_credentials(),
        effective_balance,
        isSlashed(),
        getActivation_eligibility_epoch(),
        getActivation_epoch(),
        getExit_epoch(),
        getWithdrawable_epoch());
  }

  public Validator withSlashed(boolean slashed) {
    return create(
        getPubkey(),
        getWithdrawal_credentials(),
        getEffective_balance(),
        slashed,
        getActivation_eligibility_epoch(),
        getActivation_epoch(),
        getExit_epoch(),
        getWithdrawable_epoch());
  }

  public Validator withActivation_eligibility_epoch(UnsignedLong activation_eligibility_epoch) {
    return create(
        getPubkey(),
        getWithdrawal_credentials(),
        getEffective_balance(),
        isSlashed(),
        activation_eligibility_epoch,
        getActivation_epoch(),
        getExit_epoch(),
        getWithdrawable_epoch());
  }

  public Validator withActivation_epoch(UnsignedLong activation_epoch) {
    return create(
        getPubkey(),
        getWithdrawal_credentials(),
        getEffective_balance(),
        isSlashed(),
        getActivation_eligibility_epoch(),
        activation_epoch,
        getExit_epoch(),
        getWithdrawable_epoch());
  }

  public Validator withExit_epoch(UnsignedLong exit_epoch) {
    return create(
        getPubkey(),
        getWithdrawal_credentials(),
        getEffective_balance(),
        isSlashed(),
        getActivation_eligibility_epoch(),
        getActivation_epoch(),
        exit_epoch,
        getWithdrawable_epoch());
  }

  public Validator withWithdrawable_epoch(UnsignedLong withdrawable_epoch) {
    return create(
        getPubkey(),
        getWithdrawal_credentials(),
        getEffective_balance(),
        isSlashed(),
        getActivation_eligibility_epoch(),
        getActivation_epoch(),
        getExit_epoch(),
        withdrawable_epoch);
  }

  @Override
  public Bytes32 hash_tree_root() {
    return hashTreeRoot();
  }
}
