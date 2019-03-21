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

public class Proposal {

  private UnsignedLong slot;
  private UnsignedLong shard;
  private Bytes32 block_root;
  private BLSSignature signature;

  public Proposal(
      UnsignedLong slot, UnsignedLong shard, Bytes32 block_root, BLSSignature signature) {
    this.slot = slot;
    this.shard = shard;
    this.block_root = block_root;
    this.signature = signature;
  }

  public static Proposal fromBytes(Bytes bytes) {
    return SSZ.decode(
        bytes,
        reader ->
            new Proposal(
                UnsignedLong.fromLongBits(reader.readUInt64()),
                UnsignedLong.fromLongBits(reader.readUInt64()),
                Bytes32.wrap(reader.readBytes()),
                BLSSignature.fromBytes(reader.readBytes())));
  }

  public Bytes toBytes() {
    return SSZ.encode(
        writer -> {
          writer.writeUInt64(slot.longValue());
          writer.writeUInt64(shard.longValue());
          writer.writeBytes(block_root);
          writer.writeBytes(signature.toBytes());
        });
  }

  @Override
  public int hashCode() {
    return Objects.hash(slot, shard, block_root, signature);
  }

  @Override
  public boolean equals(Object obj) {
    if (Objects.isNull(obj)) {
      return false;
    }

    if (this == obj) {
      return true;
    }

    if (!(obj instanceof Proposal)) {
      return false;
    }

    Proposal other = (Proposal) obj;
    return Objects.equals(this.getSlot(), other.getSlot())
        && Objects.equals(this.getShard(), other.getShard())
        && Objects.equals(this.getBlock_root(), other.getBlock_root())
        && Objects.equals(this.getSignature(), other.getSignature());
  }

  /** ******************* * GETTERS & SETTERS * * ******************* */
  public UnsignedLong getSlot() {
    return slot;
  }

  public void setSlot(UnsignedLong slot) {
    this.slot = slot;
  }

  public UnsignedLong getShard() {
    return shard;
  }

  public void setShard(UnsignedLong shard) {
    this.shard = shard;
  }

  public Bytes32 getBlock_root() {
    return block_root;
  }

  public void setBlock_root(Bytes32 block_root) {
    this.block_root = block_root;
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
          "Only signed_root(proposal, \"signature\") is currently supported for type Proposal.");
    }

    return Bytes32.rightPad(
        HashTreeUtil.merkleHash(
            Arrays.asList(
                HashTreeUtil.hash_tree_root(
                    SSZ.encode(
                        writer -> {
                          writer.writeUInt64(slot.longValue());
                        })),
                HashTreeUtil.hash_tree_root(
                    SSZ.encode(
                        writer -> {
                          writer.writeUInt64(shard.longValue());
                        })),
                HashTreeUtil.hash_tree_root(block_root))));
  }
}
