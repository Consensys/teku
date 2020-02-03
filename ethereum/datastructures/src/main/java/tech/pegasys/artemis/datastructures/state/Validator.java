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
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.ssz.SSZ;
import tech.pegasys.artemis.datastructures.Copyable;
import tech.pegasys.artemis.util.SSZTypes.SSZContainer;
import tech.pegasys.artemis.util.bls.BLSPublicKey;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil.SSZTypes;
import tech.pegasys.artemis.util.hashtree.Merkleizable;
import tech.pegasys.artemis.util.sos.SimpleOffsetSerializable;

public class Validator
    implements Copyable<Validator>, Merkleizable, SimpleOffsetSerializable, SSZContainer {

  // The number of SimpleSerialize basic types in this SSZ Container/POJO.
  public static final int SSZ_FIELD_COUNT = 7;

  // BLS public key
  private BLSPublicKey pubkey;
  // Withdrawal credentials
  private Bytes32 withdrawal_credentials;
  // Effective balance
  private UnsignedLong effective_balance;
  // Was the validator slashed
  private boolean slashed;
  // Epoch when became eligible for activation
  private UnsignedLong activation_eligibility_epoch;
  // Epoch when validator activated
  private UnsignedLong activation_epoch;
  // Epoch when validator exited
  private UnsignedLong exit_epoch;
  // Epoch when validator withdrew
  private UnsignedLong withdrawable_epoch;

  public Validator(
      BLSPublicKey pubkey,
      Bytes32 withdrawal_credentials,
      UnsignedLong effective_balance,
      boolean slashed,
      UnsignedLong activation_eligibility_epoch,
      UnsignedLong activation_epoch,
      UnsignedLong exit_epoch,
      UnsignedLong withdrawable_epoch) {
    this.pubkey = pubkey;
    this.withdrawal_credentials = withdrawal_credentials;
    this.slashed = slashed;
    this.effective_balance = effective_balance;
    this.activation_eligibility_epoch = activation_eligibility_epoch;
    this.activation_epoch = activation_epoch;
    this.exit_epoch = exit_epoch;
    this.withdrawable_epoch = withdrawable_epoch;
  }

  public Validator(Validator validator) {
    this.pubkey = new BLSPublicKey(validator.getPubkey().getPublicKey());
    this.withdrawal_credentials = validator.getWithdrawal_credentials();
    this.effective_balance = validator.getEffective_balance();
    this.slashed = validator.isSlashed();
    this.activation_eligibility_epoch = validator.getActivation_eligibility_epoch();
    this.activation_epoch = validator.getActivation_epoch();
    this.exit_epoch = validator.getExit_epoch();
    this.withdrawable_epoch = validator.getWithdrawable_epoch();
  }

  public Validator() {
    this.pubkey = BLSPublicKey.empty();
    this.withdrawal_credentials = Bytes32.ZERO;
    this.effective_balance = UnsignedLong.ZERO;
    this.slashed = false;
    this.activation_eligibility_epoch = UnsignedLong.ZERO;
    this.activation_epoch = UnsignedLong.ZERO;
    this.exit_epoch = UnsignedLong.ZERO;
    this.withdrawable_epoch = UnsignedLong.ZERO;
  }

  @Override
  public Validator copy() {
    return new Validator(this);
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
            SSZ.encodeUInt64(effective_balance.longValue()),
            SSZ.encodeBoolean(slashed),
            SSZ.encodeUInt64(activation_eligibility_epoch.longValue()),
            SSZ.encodeUInt64(activation_epoch.longValue()),
            SSZ.encodeUInt64(exit_epoch.longValue()),
            SSZ.encodeUInt64(withdrawable_epoch.longValue())));
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
          writer.writeFixedBytes(pubkey.toBytes());
          writer.writeFixedBytes(withdrawal_credentials);
          writer.writeUInt64(effective_balance.longValue());
          writer.writeBoolean(slashed);
          writer.writeUInt64(activation_eligibility_epoch.longValue());
          writer.writeUInt64(activation_epoch.longValue());
          writer.writeUInt64(exit_epoch.longValue());
          writer.writeUInt64(withdrawable_epoch.longValue());
        });
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        pubkey,
        withdrawal_credentials,
        effective_balance,
        slashed,
        activation_eligibility_epoch,
        activation_epoch,
        exit_epoch,
        withdrawable_epoch);
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

  public BLSPublicKey getPubkey() {
    return new BLSPublicKey(pubkey.getPublicKey());
  }

  public void setPubkey(BLSPublicKey pubkey) {
    this.pubkey = pubkey;
  }

  public Bytes32 getWithdrawal_credentials() {
    return withdrawal_credentials.copy();
  }

  public UnsignedLong getEffective_balance() {
    return effective_balance;
  }

  public void setEffective_balance(UnsignedLong effective_balance) {
    this.effective_balance = effective_balance;
  }

  public boolean isSlashed() {
    return slashed;
  }

  public void setSlashed(boolean slashed) {
    this.slashed = slashed;
  }

  public UnsignedLong getActivation_eligibility_epoch() {
    return activation_eligibility_epoch;
  }

  public void setActivation_eligibility_epoch(UnsignedLong activation_eligibility_epoch) {
    this.activation_eligibility_epoch = activation_eligibility_epoch;
  }

  public UnsignedLong getActivation_epoch() {
    return activation_epoch;
  }

  public void setActivation_epoch(UnsignedLong activation_epoch) {
    this.activation_epoch = activation_epoch;
  }

  public UnsignedLong getExit_epoch() {
    return exit_epoch;
  }

  public void setExit_epoch(UnsignedLong exit_epoch) {
    this.exit_epoch = exit_epoch;
  }

  public UnsignedLong getWithdrawable_epoch() {
    return withdrawable_epoch;
  }

  public void setWithdrawable_epoch(UnsignedLong withdrawable_epoch) {
    this.withdrawable_epoch = withdrawable_epoch;
  }

  @Override
  public Bytes32 hash_tree_root() {
    return HashTreeUtil.merkleize(
        Arrays.asList(
            HashTreeUtil.hash_tree_root(SSZTypes.VECTOR_OF_BASIC, pubkey.toBytes()),
            HashTreeUtil.hash_tree_root(SSZTypes.VECTOR_OF_BASIC, withdrawal_credentials),
            HashTreeUtil.hash_tree_root(
                SSZTypes.BASIC, SSZ.encodeUInt64(effective_balance.longValue())),
            HashTreeUtil.hash_tree_root(SSZTypes.BASIC, SSZ.encodeBoolean(slashed)),
            HashTreeUtil.hash_tree_root(
                SSZTypes.BASIC, SSZ.encodeUInt64(activation_eligibility_epoch.longValue())),
            HashTreeUtil.hash_tree_root(
                SSZTypes.BASIC, SSZ.encodeUInt64(activation_epoch.longValue())),
            HashTreeUtil.hash_tree_root(SSZTypes.BASIC, SSZ.encodeUInt64(exit_epoch.longValue())),
            HashTreeUtil.hash_tree_root(
                SSZTypes.BASIC, SSZ.encodeUInt64(withdrawable_epoch.longValue()))));
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("pubkey", pubkey)
        .add("withdrawal_credentials", withdrawal_credentials)
        .add("effective_balance", effective_balance)
        .add("slashed", slashed)
        .add("activation_eligibility_epoch", activation_eligibility_epoch)
        .add("activation_epoch", activation_epoch)
        .add("exit_epoch", exit_epoch)
        .add("withdrawable_epoch", withdrawable_epoch)
        .toString();
  }
}
