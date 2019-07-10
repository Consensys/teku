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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.ssz.SSZ;
import tech.pegasys.artemis.util.bls.BLSPublicKey;
import tech.pegasys.artemis.util.bls.BLSSignature;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil.SSZTypes;
import tech.pegasys.artemis.util.hashtree.Merkleizable;
import tech.pegasys.artemis.util.sos.SimpleOffsetSerializable;

public class Transfer implements Merkleizable, SimpleOffsetSerializable {

  // The number of SimpleSerialize basic types in this SSZ Container/POJO.
  public static final int SSZ_FIELD_COUNT = 5;

  private UnsignedLong sender;
  private UnsignedLong recipient;
  private UnsignedLong amount;
  private UnsignedLong fee;
  private UnsignedLong slot;
  private BLSPublicKey pubkey;
  private BLSSignature signature;

  public Transfer(
      UnsignedLong sender,
      UnsignedLong recipient,
      UnsignedLong amount,
      UnsignedLong fee,
      UnsignedLong slot,
      BLSPublicKey pubkey,
      BLSSignature signature) {
    this.setSender(sender);
    this.setRecipient(recipient);
    this.setAmount(amount);
    this.setFee(fee);
    this.setSlot(slot);
    this.setPubkey(pubkey);
    this.setSignature(signature);
  }

  @Override
  public int getSSZFieldCount() {
    return SSZ_FIELD_COUNT + pubkey.getSSZFieldCount() + signature.getSSZFieldCount();
  }

  @Override
  public List<Bytes> get_fixed_parts() {
    List<Bytes> fixedPartsList =
        new ArrayList<>(
            List.of(
                SSZ.encodeUInt64(sender.longValue()),
                SSZ.encodeUInt64(recipient.longValue()),
                SSZ.encodeUInt64(amount.longValue()),
                SSZ.encodeUInt64(fee.longValue()),
                SSZ.encodeUInt64(slot.longValue())));
    fixedPartsList.addAll(pubkey.get_fixed_parts());
    fixedPartsList.addAll(signature.get_fixed_parts());
    return fixedPartsList;
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
          writer.writeUInt64(sender.longValue());
          writer.writeUInt64(recipient.longValue());
          writer.writeUInt64(amount.longValue());
          writer.writeUInt64(fee.longValue());
          writer.writeUInt64(slot.longValue());
          writer.writeBytes(pubkey.toBytes());
          writer.writeBytes(signature.toBytes());
        });
  }

  @Override
  public int hashCode() {
    return Objects.hash(sender, recipient, amount, fee, slot, pubkey, signature);
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
    return Objects.equals(this.getSender(), other.getSender())
        && Objects.equals(this.getRecipient(), other.getRecipient())
        && Objects.equals(this.getAmount(), other.getAmount())
        && Objects.equals(this.getFee(), other.getFee())
        && Objects.equals(this.getSlot(), other.getSlot())
        && Objects.equals(this.getPubkey(), other.getPubkey())
        && Objects.equals(this.getSignature(), other.getSignature());
  }

  /** ******************* * GETTERS & SETTERS * * ******************* */
  public UnsignedLong getSender() {
    return sender;
  }

  public void setSender(UnsignedLong sender) {
    this.sender = sender;
  }

  public UnsignedLong getRecipient() {
    return recipient;
  }

  public void setRecipient(UnsignedLong recipient) {
    this.recipient = recipient;
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

  public Bytes32 signing_root(String truncation_param) {
    if (!truncation_param.equals("signature")) {
      throw new UnsupportedOperationException(
          "Only signed_root(BeaconBlockHeader, \"signature\") is currently supported for type BeaconBlockHeader.");
    }

    return Bytes32.rightPad(
        HashTreeUtil.merkleize(
            Arrays.asList(
                HashTreeUtil.hash_tree_root(SSZTypes.BASIC, SSZ.encodeUInt64(sender.longValue())),
                HashTreeUtil.hash_tree_root(
                    SSZTypes.BASIC, SSZ.encodeUInt64(recipient.longValue())),
                HashTreeUtil.hash_tree_root(SSZTypes.BASIC, SSZ.encodeUInt64(amount.longValue())),
                HashTreeUtil.hash_tree_root(SSZTypes.BASIC, SSZ.encodeUInt64(fee.longValue())),
                HashTreeUtil.hash_tree_root(SSZTypes.BASIC, SSZ.encodeUInt64(slot.longValue())),
                HashTreeUtil.hash_tree_root(SSZTypes.TUPLE_OF_BASIC, pubkey.toBytes()))));
  }

  @Override
  public Bytes32 hash_tree_root() {
    return HashTreeUtil.merkleize(
        Arrays.asList(
            HashTreeUtil.hash_tree_root(SSZTypes.BASIC, SSZ.encodeUInt64(sender.longValue())),
            HashTreeUtil.hash_tree_root(SSZTypes.BASIC, SSZ.encodeUInt64(recipient.longValue())),
            HashTreeUtil.hash_tree_root(SSZTypes.BASIC, SSZ.encodeUInt64(amount.longValue())),
            HashTreeUtil.hash_tree_root(SSZTypes.BASIC, SSZ.encodeUInt64(fee.longValue())),
            HashTreeUtil.hash_tree_root(SSZTypes.BASIC, SSZ.encodeUInt64(slot.longValue())),
            HashTreeUtil.hash_tree_root(SSZTypes.TUPLE_OF_BASIC, pubkey.toBytes()),
            HashTreeUtil.hash_tree_root(SSZTypes.TUPLE_OF_BASIC, signature.toBytes())));
  }
}
