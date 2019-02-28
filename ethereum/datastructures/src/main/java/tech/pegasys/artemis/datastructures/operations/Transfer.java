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

package tech.pegasys.artemis.datastructures.operations;

import com.google.common.primitives.UnsignedLong;
import java.util.Objects;
import net.consensys.cava.bytes.Bytes;
import net.consensys.cava.ssz.SSZ;
import tech.pegasys.artemis.util.bls.BLSPublicKey;
import tech.pegasys.artemis.util.bls.BLSSignature;

public class Transfer {
  private UnsignedLong from;
  private UnsignedLong to;
  private UnsignedLong amount;
  private UnsignedLong fee;
  private UnsignedLong slot;
  private BLSPublicKey pubkey;
  private BLSSignature signature;

  public Transfer(
      UnsignedLong from,
      UnsignedLong to,
      UnsignedLong amount,
      UnsignedLong fee,
      UnsignedLong slot,
      BLSPublicKey pubkey,
      BLSSignature signature) {
    this.setFrom(from);
    this.setTo(to);
    this.setAmount(amount);
    this.setFee(fee);
    this.setSlot(slot);
    this.setPubkey(pubkey);
    this.setSignature(signature);
  }

  public static Transfer fromBytes(Bytes bytes) {
    return SSZ.decode(
        bytes,
        reader ->
            new Transfer(
                UnsignedLong.fromLongBits(reader.readUInt64()),
                UnsignedLong.fromLongBits(reader.readUInt64()),
                UnsignedLong.fromLongBits(reader.readUInt64()),
                UnsignedLong.fromLongBits(reader.readUInt64()),
                UnsignedLong.fromLongBits(reader.readUInt64()),
                BLSPublicKey.fromBytes(reader.readBytes()),
                BLSSignature.fromBytes(reader.readBytes())));
  }

  public Bytes toBytes() {
    return SSZ.encode(
        writer -> {
          writer.writeUInt64(from.longValue());
          writer.writeUInt64(to.longValue());
          writer.writeUInt64(amount.longValue());
          writer.writeUInt64(fee.longValue());
          writer.writeUInt64(slot.longValue());
          writer.writeBytes(pubkey.toBytes());
          writer.writeBytes(signature.toBytes());
        });
  }

  @Override
  public int hashCode() {
    return Objects.hash(from, to, amount, fee, slot, pubkey, signature);
  }

  @Override
  public boolean equals(Object obj) {
    if (Objects.isNull(obj)) {
      return false;
    }

    if (this == obj) {
      return true;
    }

    if (!(obj instanceof Transfer)) {
      return false;
    }

    Transfer other = (Transfer) obj;
    return Objects.equals(this.getFrom(), other.getFrom())
        && Objects.equals(this.getTo(), other.getTo())
        && Objects.equals(this.getAmount(), other.getAmount())
        && Objects.equals(this.getFee(), other.getFee())
        && Objects.equals(this.getSlot(), other.getSlot())
        && Objects.equals(this.getPubkey(), other.getPubkey())
        && Objects.equals(this.getSignature(), other.getSignature());
  }

  /** ******************* * GETTERS & SETTERS * * ******************* */
  public UnsignedLong getFrom() {
    return from;
  }

  public void setFrom(UnsignedLong from) {
    this.from = from;
  }

  public UnsignedLong getTo() {
    return to;
  }

  public void setTo(UnsignedLong to) {
    this.to = to;
  }

  public UnsignedLong getAmount() {
    return amount;
  }

  public void setAmount(UnsignedLong amount) {
    this.amount = amount;
  }

  public UnsignedLong getFee() {
    return fee;
  }

  public void setFee(UnsignedLong fee) {
    this.fee = fee;
  }

  public UnsignedLong getSlot() {
    return slot;
  }

  public void setSlot(UnsignedLong slot) {
    this.slot = slot;
  }

  public BLSPublicKey getPubkey() {
    return pubkey;
  }

  public void setPubkey(BLSPublicKey pubkey) {
    this.pubkey = pubkey;
  }

  public BLSSignature getSignature() {
    return signature;
  }

  public void setSignature(BLSSignature signature) {
    this.signature = signature;
  }
}
