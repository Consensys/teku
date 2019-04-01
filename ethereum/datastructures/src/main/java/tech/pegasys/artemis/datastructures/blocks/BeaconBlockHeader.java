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
import java.util.Arrays;
import java.util.Objects;
import net.consensys.cava.bytes.Bytes;
import net.consensys.cava.bytes.Bytes32;
import net.consensys.cava.ssz.SSZ;
import tech.pegasys.artemis.util.bls.BLSSignature;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil;

public class BeaconBlockHeader {

  private UnsignedLong slot;
  private Bytes32 previous_block_root;
  private Bytes32 state_root;
  private Bytes32 block_body_root;
  private BLSSignature signature;

  public BeaconBlockHeader(
      UnsignedLong slot,
      Bytes32 previous_block_root,
      Bytes32 state_root,
      Bytes32 block_body_root,
      BLSSignature signature) {
    this.slot = slot;
    this.previous_block_root = previous_block_root;
    this.state_root = state_root;
    this.block_body_root = block_body_root;
    this.signature = signature;
  }

  public static BeaconBlockHeader fromBytes(Bytes bytes) {
    return SSZ.decode(
        bytes,
        reader ->
            new BeaconBlockHeader(
                UnsignedLong.fromLongBits(reader.readUInt64()),
                Bytes32.wrap(reader.readBytes()),
                Bytes32.wrap(reader.readBytes()),
                Bytes32.wrap(reader.readBytes()),
                BLSSignature.fromBytes(reader.readBytes())));
  }

  public Bytes toBytes() {
    return SSZ.encode(
        writer -> {
          writer.writeUInt64(slot.longValue());
          writer.writeBytes(previous_block_root);
          writer.writeBytes(state_root);
          writer.writeBytes(block_body_root);
          writer.writeBytes(signature.toBytes());
        });
  }

  @Override
  public int hashCode() {
    return Objects.hash(slot, previous_block_root, state_root, block_body_root, signature);
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
        && Objects.equals(this.getPrevious_block_root(), other.getPrevious_block_root())
        && Objects.equals(this.getState_root(), other.getState_root())
        && Objects.equals(this.getBlock_body_root(), other.getBlock_body_root())
        && Objects.equals(this.getSignature(), other.getSignature());
  }

  /** ******************* * GETTERS & SETTERS * * ******************* */
  public UnsignedLong getSlot() {
    return slot;
  }

  public void setSlot(UnsignedLong slot) {
    this.slot = slot;
  }

  public Bytes32 getPrevious_block_root() {
    return previous_block_root;
  }

  public void setPrevious_block_root(Bytes32 previous_block_root) {
    this.previous_block_root = previous_block_root;
  }

  public Bytes32 getState_root() {
    return state_root;
  }

  public void setState_root(Bytes32 state_root) {
    this.state_root = state_root;
  }

  public Bytes32 getBlock_body_root() {
    return block_body_root;
  }

  public void setBlock_block_root(Bytes32 block_bodyjj_root) {
    this.block_body_root = block_body_root;
  }

  public BLSSignature getSignature() {
    return signature;
  }

  public void setSignature(BLSSignature signature) {
    this.signature = signature;
  }

  public Bytes32 signedRoot(String truncationParam) {
    if (!truncationParam.equals("signature")) {
      throw new UnsupportedOperationException(
          "Only signed_root(BeaconBlockHeader, \"signature\") is currently supported for type BeaconBlockHeader.");
    }

    return Bytes32.rightPad(
        HashTreeUtil.merkleHash(
            Arrays.asList(
                HashTreeUtil.hash_tree_root(
                    SSZ.encode(
                        writer -> {
                          writer.writeUInt64(slot.longValue());
                        })),
                HashTreeUtil.hash_tree_root(previous_block_root),
                HashTreeUtil.hash_tree_root(state_root),
                HashTreeUtil.hash_tree_root(block_body_root))));
  }
}
