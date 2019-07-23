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
import java.util.List;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.ssz.SSZ;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil.SSZTypes;
import tech.pegasys.artemis.util.sos.SimpleOffsetSerializable;

public class Checkpoint implements SimpleOffsetSerializable {

  // The number of SimpleSerialize basic types in this SSZ Container/POJO.
  public static final int SSZ_FIELD_COUNT = 2;

  private UnsignedLong epoch;
  private Bytes32 root;

  public Checkpoint(UnsignedLong epoch, Bytes32 root) {
    this.epoch = epoch;
    this.root = root;
  }

  public Checkpoint(Checkpoint checkpoint) {
    this.epoch = checkpoint.getEpoch();
    this.root = checkpoint.getRoot();
  }

  public Checkpoint() {
    this.epoch = UnsignedLong.ZERO;
    this.root = Bytes32.ZERO;
  }

  @Override
  public int getSSZFieldCount() {
    return SSZ_FIELD_COUNT;
  }

  @Override
  public List<Bytes> get_fixed_parts() {
    return List.of(
        SSZ.encodeUInt64(epoch.longValue()), SSZ.encode(writer -> writer.writeFixedBytes(root)));
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
          writer.writeFixedBytes(root);
        });
  }

  @Override
  public int hashCode() {
    return Objects.hash(epoch, root);
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
        && Objects.equals(this.getRoot(), other.getRoot());
  }

  /** ****************** * GETTERS & SETTERS * * ******************* */
  public UnsignedLong getEpoch() {
    return epoch;
  }

  public void setEpoch(UnsignedLong epoch) {
    this.epoch = epoch;
  }

  public Bytes32 getRoot() {
    return root;
  }

  public void setRoot(Bytes32 root) {
    this.root = root;
  }

  public Bytes32 hash_tree_root() {
    return HashTreeUtil.merkleize(
        Arrays.asList(
            HashTreeUtil.hash_tree_root(SSZTypes.BASIC, SSZ.encodeUInt64(epoch.longValue())),
            HashTreeUtil.hash_tree_root(SSZTypes.VECTOR_OF_BASIC, root)));
  }
}
