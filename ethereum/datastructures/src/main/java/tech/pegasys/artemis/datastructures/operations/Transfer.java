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

import java.util.Objects;
import net.consensys.cava.bytes.Bytes;
import net.consensys.cava.ssz.SSZ;
import tech.pegasys.artemis.util.bls.BLSPublicKey;
import tech.pegasys.artemis.util.bls.BLSSignature;

public class Transfer {
  private long from;
  private long to;
  private long amount;
  private long fee;
  private long slot;
  private BLSPublicKey pubkey;
  private BLSSignature signature;

  public Transfer(
      long from,
      long to,
      long amount,
      long fee,
      long slot,
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
                reader.readUInt64(),
                reader.readUInt64(),
                reader.readUInt64(),
                reader.readUInt64(),
                reader.readUInt64(),
                BLSPublicKey.fromBytes(reader.readBytes()),
                BLSSignature.fromBytes(reader.readBytes())));
  }

  public Bytes toBytes() {
    return SSZ.encode(
        writer -> {
          writer.writeUInt64(from);
          writer.writeUInt64(to);
          writer.writeUInt64(amount);
          writer.writeUInt64(fee);
          writer.writeUInt64(slot);
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
  public long getFrom() {
    return from;
  }

  public void setFrom(long from) {
    this.from = from;
  }

  public long getTo() {
    return to;
  }

  public void setTo(long to) {
    this.to = to;
  }

  public long getAmount() {
    return amount;
  }

  public void setAmount(long amount) {
    this.amount = amount;
  }

  public long getFee() {
    return fee;
  }

  public void setFee(long fee) {
    this.fee = fee;
  }

  public long getSlot() {
    return slot;
  }

  public void setSlot(long slot) {
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
