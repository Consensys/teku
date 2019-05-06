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

import com.google.common.primitives.UnsignedLong;
import java.util.Arrays;
import java.util.Objects;
import net.consensys.cava.bytes.Bytes;
import net.consensys.cava.bytes.Bytes32;
import net.consensys.cava.ssz.SSZ;
import tech.pegasys.artemis.datastructures.Copyable;
import tech.pegasys.artemis.util.bls.BLSPublicKey;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil.SSZTypes;

public final class Validator implements Copyable<Validator> {

  // BLS public key
  private BLSPublicKey pubkey;
  // Withdrawal credentials
  private Bytes32 withdrawal_credentials;
  // Epoch when validator activated
  private UnsignedLong activation_epoch;
  // Epoch when validator exited
  private UnsignedLong exit_epoch;
  // Epoch when validator withdrew
  private UnsignedLong withdrawable_epoch;
  // Did the validator initiate an exit
  private boolean initiated_exit;
  // Was the validator slashed
  private boolean slashed;

  public Validator(
      BLSPublicKey pubkey,
      Bytes32 withdrawal_credentials,
      UnsignedLong activation_epoch,
      UnsignedLong exit_epoch,
      UnsignedLong withdrawal_epoch,
      boolean initiated_exit,
      boolean slashed) {
    this.pubkey = pubkey;
    this.withdrawal_credentials = withdrawal_credentials;
    this.activation_epoch = activation_epoch;
    this.exit_epoch = exit_epoch;
    this.withdrawable_epoch = withdrawal_epoch;
    this.initiated_exit = initiated_exit;
    this.slashed = slashed;
  }

  public Validator(Validator validator) {
    this.pubkey = new BLSPublicKey(validator.getPubkey().getPublicKey());
    this.withdrawal_credentials = validator.getWithdrawal_credentials().copy();
    this.activation_epoch = validator.getActivation_epoch();
    this.exit_epoch = validator.getExit_epoch();
    this.withdrawable_epoch = validator.getWithdrawable_epoch();
    this.initiated_exit = validator.hasInitiatedExit();
    this.slashed = validator.isSlashed();
  }

  @Override
  public Validator copy() {
    return new Validator(this);
  }

  public static Validator fromBytes(Bytes bytes) {
    return SSZ.decode(
        bytes,
        reader ->
            new Validator(
                BLSPublicKey.fromBytes(reader.readBytes()),
                Bytes32.wrap(reader.readFixedBytes(32)),
                UnsignedLong.fromLongBits(reader.readUInt64()),
                UnsignedLong.fromLongBits(reader.readUInt64()),
                UnsignedLong.fromLongBits(reader.readUInt64()),
                reader.readBoolean(),
                reader.readBoolean()));
  }

  public Bytes toBytes() {
    return SSZ.encode(
        writer -> {
          writer.writeBytes(pubkey.toBytes());
          writer.writeFixedBytes(32, withdrawal_credentials);
          writer.writeUInt64(activation_epoch.longValue());
          writer.writeUInt64(exit_epoch.longValue());
          writer.writeUInt64(withdrawable_epoch.longValue());
          writer.writeBoolean(initiated_exit);
          writer.writeBoolean(slashed);
        });
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        pubkey,
        withdrawal_credentials,
        activation_epoch,
        exit_epoch,
        withdrawable_epoch,
        initiated_exit,
        slashed);
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
        && Objects.equals(this.getActivation_epoch(), other.getActivation_epoch())
        && Objects.equals(this.getExit_epoch(), other.getExit_epoch())
        && Objects.equals(this.getWithdrawable_epoch(), other.getWithdrawable_epoch())
        && Objects.equals(this.hasInitiatedExit(), other.hasInitiatedExit())
        && Objects.equals(this.isSlashed(), other.isSlashed());
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

  public void setWithdrawal_credentials(Bytes32 withdrawal_credentials) {
    this.withdrawal_credentials = withdrawal_credentials;
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

  public boolean hasInitiatedExit() {
    return initiated_exit;
  }

  public void setInitiatedExit(boolean initiated_exit) {
    this.initiated_exit = initiated_exit;
  }

  public boolean isSlashed() {
    return slashed;
  }

  public void setSlashed(boolean slashed) {
    this.slashed = slashed;
  }

  public Bytes32 hash_tree_root() {
    return HashTreeUtil.merkleize(
        Arrays.asList(
            HashTreeUtil.hash_tree_root(SSZTypes.TUPLE_OF_BASIC, pubkey.toBytes()),
            HashTreeUtil.hash_tree_root(SSZTypes.TUPLE_OF_BASIC, withdrawal_credentials),
            HashTreeUtil.hash_tree_root(
                SSZTypes.BASIC, SSZ.encodeUInt64(activation_epoch.longValue())),
            HashTreeUtil.hash_tree_root(SSZTypes.BASIC, SSZ.encodeUInt64(exit_epoch.longValue())),
            HashTreeUtil.hash_tree_root(
                SSZTypes.BASIC, SSZ.encodeUInt64(withdrawable_epoch.longValue())),
            HashTreeUtil.hash_tree_root(SSZTypes.BASIC, SSZ.encodeBoolean(initiated_exit)),
            HashTreeUtil.hash_tree_root(SSZTypes.BASIC, SSZ.encodeBoolean(slashed))));
  }
}
