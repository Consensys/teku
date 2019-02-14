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
import java.util.Objects;
import net.consensys.cava.bytes.Bytes;
import net.consensys.cava.bytes.Bytes32;
import net.consensys.cava.bytes.Bytes48;
import net.consensys.cava.ssz.SSZ;

public class Validator {

  // BLS public key
  private Bytes48 pubkey;
  // Withdrawal credentials
  private Bytes32 withdrawal_credentials;
  // Epoch when validator activated
  private UnsignedLong activation_epoch;
  // Epoch when validator exited
  private UnsignedLong exit_epoch;
  // Epoch when validator withdrew
  private UnsignedLong withdrawal_epoch;
  // Epoch when validator was penalized
  private UnsignedLong penalized_epoch;
  // Status flags
  private UnsignedLong status_flags;

  public Validator(
      Bytes48 pubkey,
      Bytes32 withdrawal_credentials,
      UnsignedLong activation_epoch,
      UnsignedLong exit_epoch,
      UnsignedLong withdrawal_epoch,
      UnsignedLong penalized_epoch,
      UnsignedLong status_flags) {
    this.pubkey = pubkey;
    this.withdrawal_credentials = withdrawal_credentials;
    this.activation_epoch = activation_epoch;
    this.exit_epoch = exit_epoch;
    this.withdrawal_epoch = withdrawal_epoch;
    this.penalized_epoch = penalized_epoch;
    this.status_flags = status_flags;
  }

  public static Validator fromBytes(Bytes bytes) {
    return SSZ.decode(
        bytes,
        reader ->
            new Validator(
                Bytes48.wrap(reader.readBytes()),
                Bytes32.wrap(reader.readBytes()),
                UnsignedLong.fromLongBits(reader.readUInt64()),
                UnsignedLong.fromLongBits(reader.readUInt64()),
                UnsignedLong.fromLongBits(reader.readUInt64()),
                UnsignedLong.fromLongBits(reader.readUInt64()),
                UnsignedLong.fromLongBits(reader.readUInt64())));
  }

  public Bytes toBytes() {
    return SSZ.encode(
        writer -> {
          writer.writeBytes(pubkey);
          writer.writeBytes(withdrawal_credentials);
          writer.writeUInt64(activation_epoch.longValue());
          writer.writeUInt64(exit_epoch.longValue());
          writer.writeUInt64(withdrawal_epoch.longValue());
          writer.writeUInt64(penalized_epoch.longValue());
          writer.writeUInt64(status_flags.longValue());
        });
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        pubkey,
        withdrawal_credentials,
        activation_epoch,
        exit_epoch,
        withdrawal_epoch,
        penalized_epoch,
        status_flags);
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
        && Objects.equals(this.getWithdrawal_epoch(), other.getWithdrawal_epoch())
        && Objects.equals(this.getPenalized_epoch(), other.getPenalized_epoch())
        && Objects.equals(this.getStatus_flags(), other.getStatus_flags());
  }

  public Bytes48 getPubkey() {
    return pubkey;
  }

  public void setPubkey(Bytes48 pubkey) {
    this.pubkey = pubkey;
  }

  public Bytes32 getWithdrawal_credentials() {
    return withdrawal_credentials;
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

  public UnsignedLong getWithdrawal_epoch() {
    return withdrawal_epoch;
  }

  public void setWithdrawal_epoch(UnsignedLong withdrawal_epoch) {
    this.withdrawal_epoch = withdrawal_epoch;
  }

  public UnsignedLong getPenalized_epoch() {
    return penalized_epoch;
  }

  public void setPenalized_epoch(UnsignedLong penalized_epoch) {
    this.penalized_epoch = penalized_epoch;
  }

  public UnsignedLong getStatus_flags() {
    return status_flags;
  }

  public void setStatus_flags(UnsignedLong status_flags) {
    this.status_flags = status_flags;
  }

  public boolean is_active_validator(UnsignedLong epoch) {
    // checks validator status against the validator status constants for whether the validator is
    // active
    return activation_epoch.compareTo(epoch) <= 0 && epoch.compareTo(exit_epoch) < 0;
  }
}
