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

import java.util.Arrays;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.ssz.SSZ;
import tech.pegasys.artemis.util.bls.BLSSignature;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil.SSZTypes;

public final class BeaconBlock {

  // Header
  private long slot;
  private Bytes32 previous_block_root;
  private Bytes32 state_root;

  // Body
  private BeaconBlockBody body;

  // Signature
  private BLSSignature signature;

  public BeaconBlock(
      long slot,
      Bytes32 previous_block_root,
      Bytes32 state_root,
      BeaconBlockBody body,
      BLSSignature signature) {
    this.slot = slot;
    this.previous_block_root = previous_block_root;
    this.state_root = state_root;
    this.body = body;
    this.signature = signature;
  }

  public static BeaconBlock fromBytes(Bytes bytes) {
    return SSZ.decode(
        bytes,
        reader ->
            new BeaconBlock(
                reader.readUInt64(),
                Bytes32.wrap(reader.readFixedBytes(32)),
                Bytes32.wrap(reader.readFixedBytes(32)),
                BeaconBlockBody.fromBytes(reader.readBytes()),
                BLSSignature.fromBytes(reader.readBytes())));
  }

  public Bytes toBytes() {
    return SSZ.encode(
        writer -> {
          writer.writeUInt64(slot);
          writer.writeFixedBytes(32, previous_block_root);
          writer.writeFixedBytes(32, state_root);
          writer.writeBytes(body.toBytes());
          writer.writeBytes(signature.toBytes());
        });
  }

  @Override
  public int hashCode() {
    return Objects.hash(slot, previous_block_root, state_root, body, signature);
  }

  @Override
  public boolean equals(Object obj) {
    if (Objects.isNull(obj)) {
      return false;
    }

    if (this == obj) {
      return true;
    }

    if (!(obj instanceof BeaconBlock)) {
      return false;
    }

    BeaconBlock other = (BeaconBlock) obj;
    return slot == other.getSlot()
        && Objects.equals(this.getPrevious_block_root(), other.getPrevious_block_root())
        && Objects.equals(this.getState_root(), other.getState_root())
        && Objects.equals(this.getBody(), other.getBody())
        && Objects.equals(this.getSignature(), other.getSignature());
  }

  /** ******************* * GETTERS & SETTERS * * ******************* */
  public BeaconBlockBody getBody() {
    return body;
  }

  public void setBody(BeaconBlockBody body) {
    this.body = body;
  }

  public BLSSignature getSignature() {
    return signature;
  }

  public void setSignature(BLSSignature signature) {
    this.signature = signature;
  }

  public Bytes32 getState_root() {
    return state_root;
  }

  public void setState_root(Bytes32 state_root) {
    this.state_root = state_root;
  }

  public Bytes32 getPrevious_block_root() {
    return previous_block_root;
  }

  public void setPrevious_block_root(Bytes32 previous_block_root) {
    this.previous_block_root = previous_block_root;
  }

  public long getSlot() {
    return slot;
  }

  public void setSlot(long slot) {
    this.slot = slot;
  }

  public Bytes32 signed_root(String truncation_param) {
    if (!truncation_param.equals("signature")) {
      throw new UnsupportedOperationException(
          "Only signed_root(beaconBlock, \"signature\") is currently supported for type BeaconBlock.");
    }

    return Bytes32.rightPad(
        HashTreeUtil.merkleize(
            Arrays.asList(
                HashTreeUtil.hash_tree_root(SSZTypes.BASIC, SSZ.encodeUInt64(slot)),
                HashTreeUtil.hash_tree_root(SSZTypes.TUPLE_OF_BASIC, previous_block_root),
                HashTreeUtil.hash_tree_root(SSZTypes.TUPLE_OF_BASIC, state_root),
                body.hash_tree_root())));
  }

  public Bytes32 hash_tree_root() {
    return HashTreeUtil.merkleize(
        Arrays.asList(
            HashTreeUtil.hash_tree_root(SSZTypes.BASIC, SSZ.encodeUInt64(slot)),
            HashTreeUtil.hash_tree_root(SSZTypes.TUPLE_OF_BASIC, previous_block_root),
            HashTreeUtil.hash_tree_root(SSZTypes.TUPLE_OF_BASIC, state_root),
            body.hash_tree_root(),
            HashTreeUtil.hash_tree_root(SSZTypes.TUPLE_OF_BASIC, signature.toBytes())));
  }
}
