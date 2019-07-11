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

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.ssz.SSZ;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil.SSZTypes;

public class Checkpoint {

  private UnsignedLong epoch;
  private Bytes32 hash;

  public Checkpoint(UnsignedLong epoch, Bytes32 hash) {
    this.epoch = epoch;
    this.hash = hash;
  }

  public Checkpoint(Checkpoint checkpoint) {
    this.epoch = checkpoint.getEpoch();
    this.hash = checkpoint.getHash();
  }

  public Checkpoint() {
    this.epoch = UnsignedLong.ZERO;
    this.hash = Bytes32.ZERO;
  }

  public static Checkpoint fromBytes(Bytes bytes) {
    return SSZ.decode(
            bytes,
            reader ->
                    new Checkpoint(
                            UnsignedLong.fromLongBits(reader.readUInt64()),
                            Bytes32.wrap(reader.readFixedBytes(32))));
  }

  public Bytes toBytes() {
    return SSZ.encode(
            writer -> {
              writer.writeUInt64(epoch.longValue());
              writer.writeFixedBytes(hash);
            });
  }

  @Override
  public int hashCode() {
    return Objects.hash(epoch, hash);
  }

  @Override
  public boolean equals(Object obj) {
    if (Objects.isNull(obj)) {
      return false;
    }

    if (this == obj) {
      return true;
    }

    if (!(obj instanceof Checkpoint)) {
      return false;
    }

    Checkpoint other = (Checkpoint) obj;
    return Objects.equals(this.getEpoch(), other.getEpoch())
            && Objects.equals(this.getHash(), other.getHash());
  }

  /**
   * ****************** * GETTERS & SETTERS * * *******************
   */

  public UnsignedLong getEpoch() {
    return epoch;
  }

  public void setEpoch(UnsignedLong epoch) {
    this.epoch = epoch;
  }

  public Bytes32 getHash() {
    return hash;
  }

  public void setHash(Bytes32 hash) {
    this.hash = hash;
  }

  public Bytes32 hash_tree_root() {
    return HashTreeUtil.merkleize(
            Arrays.asList(
                    HashTreeUtil.hash_tree_root(SSZTypes.BASIC, SSZ.encodeUInt64(epoch.longValue())),
                    HashTreeUtil.hash_tree_root(SSZTypes.TUPLE_OF_BASIC, hash)));
  }
}
