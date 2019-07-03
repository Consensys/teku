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

package tech.pegasys.artemis.datastructures.blocks;

import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.ssz.SSZ;
import tech.pegasys.artemis.util.bls.BLSSignature;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil.SSZTypes;
import tech.pegasys.artemis.util.sos.SimpleOffsetSerializable;

public class BeaconBlockHeader implements SimpleOffsetSerializable {

  // The number of SimpleSerialize basic types in this SSZ Container/POJO.
  public static final int SSZ_FIELD_COUNT = 4;

  private UnsignedLong slot;
  private Bytes32 parent_root;
  private Bytes32 state_root;
  private Bytes32 body_root;
  private BLSSignature signature;

  public BeaconBlockHeader(
      UnsignedLong slot,
      Bytes32 parent_root,
      Bytes32 state_root,
      Bytes32 body_root,
      BLSSignature signature) {
    this.slot = slot;
    this.parent_root = parent_root;
    this.state_root = state_root;
    this.body_root = body_root;
    this.signature = signature;
  }

  public BeaconBlockHeader(Bytes32 body_root) {
    this.slot = UnsignedLong.ZERO;
    this.parent_root = Bytes32.ZERO;
    this.state_root = Bytes32.ZERO;
    this.body_root = body_root;
    this.signature = BLSSignature.empty();
  }

  @Override
  public int getSSZFieldCount() {
    return SSZ_FIELD_COUNT + signature.getSSZFieldCount();
  }

  @Override
  public List<Bytes> get_fixed_parts() {
    List<Bytes> fixedPartsList =
        new ArrayList<>(
            List.of(
                SSZ.encodeUInt64(slot.longValue()),
                SSZ.encode(writer -> writer.writeFixedBytes(32, parent_root)),
                SSZ.encode(writer -> writer.writeFixedBytes(32, state_root)),
                SSZ.encode(writer -> writer.writeFixedBytes(32, body_root))));
    fixedPartsList.addAll(signature.get_fixed_parts());
    return fixedPartsList;
  }

  public static BeaconBlockHeader fromBytes(Bytes bytes) {
    return SSZ.decode(
        bytes,
        reader ->
            new BeaconBlockHeader(
                UnsignedLong.fromLongBits(reader.readUInt64()),
                Bytes32.wrap(reader.readFixedBytes(32)),
                Bytes32.wrap(reader.readFixedBytes(32)),
                Bytes32.wrap(reader.readFixedBytes(32)),
                BLSSignature.fromBytes(reader.readBytes())));
  }

  public Bytes toBytes() {
    return SSZ.encode(
        writer -> {
          writer.writeUInt64(slot.longValue());
          writer.writeFixedBytes(parent_root);
          writer.writeFixedBytes(state_root);
          writer.writeFixedBytes(body_root);
          writer.writeBytes(signature.toBytes());
        });
  }

  @Override
  public int hashCode() {
    return Objects.hash(slot, parent_root, state_root, body_root, signature);
  }

  @Override
  public boolean equals(Object obj) {
    if (Objects.isNull(obj)) {
      return false;
    }

    if (this == obj) {
      return true;
    }

    if (!(obj instanceof BeaconBlockHeader)) {
      return false;
    }

    BeaconBlockHeader other = (BeaconBlockHeader) obj;
    return Objects.equals(this.getSlot(), other.getSlot())
        && Objects.equals(this.getParent_root(), other.getParent_root())
        && Objects.equals(this.getState_root(), other.getState_root())
        && Objects.equals(this.getBody_root(), other.getBody_root())
        && Objects.equals(this.getSignature(), other.getSignature());
  }

  /** ******************* * GETTERS & SETTERS * * ******************* */
  public UnsignedLong getSlot() {
    return slot;
  }

  public void setSlot(UnsignedLong slot) {
    this.slot = slot;
  }

  public Bytes32 getParent_root() {
    return parent_root;
  }

  public void setParent_root(Bytes32 parent_root) {
    this.parent_root = parent_root;
  }

  public Bytes32 getState_root() {
    return state_root;
  }

  public void setState_root(Bytes32 state_root) {
    this.state_root = state_root;
  }

  public Bytes32 getBody_root() {
    return body_root;
  }

  public void setBody_root(Bytes32 body_root) {
    this.body_root = body_root;
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
                HashTreeUtil.hash_tree_root(SSZTypes.BASIC, SSZ.encodeUInt64(slot.longValue())),
                HashTreeUtil.hash_tree_root(SSZTypes.TUPLE_OF_BASIC, parent_root),
                HashTreeUtil.hash_tree_root(SSZTypes.TUPLE_OF_BASIC, state_root),
                HashTreeUtil.hash_tree_root(SSZTypes.TUPLE_OF_BASIC, body_root))));
  }

  public Bytes32 hash_tree_root() {
    return HashTreeUtil.merkleize(
        Arrays.asList(
            HashTreeUtil.hash_tree_root(SSZTypes.BASIC, SSZ.encodeUInt64(slot.longValue())),
            HashTreeUtil.hash_tree_root(SSZTypes.TUPLE_OF_BASIC, parent_root),
            HashTreeUtil.hash_tree_root(SSZTypes.TUPLE_OF_BASIC, state_root),
            HashTreeUtil.hash_tree_root(SSZTypes.TUPLE_OF_BASIC, body_root),
            HashTreeUtil.hash_tree_root(SSZTypes.TUPLE_OF_BASIC, signature.toBytes())));
  }
}
