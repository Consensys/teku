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

import com.google.common.base.MoreObjects;
import com.google.common.primitives.UnsignedLong;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.ssz.SSZ;
import tech.pegasys.artemis.util.SSZTypes.Bytes4;
import tech.pegasys.artemis.util.SSZTypes.SSZContainer;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil.SSZTypes;
import tech.pegasys.artemis.util.hashtree.Merkleizable;
import tech.pegasys.artemis.util.sos.SimpleOffsetSerializable;

public class Fork implements Merkleizable, SimpleOffsetSerializable, SSZContainer {

  // The number of SimpleSerialize basic types in this SSZ Container/POJO.
  public static final int SSZ_FIELD_COUNT = 3;

  private final Bytes4 previous_version; // This is a Version type, aliased as a Bytes4
  private final Bytes4 current_version; // This is a Version type, aliased as a Bytes4
  private final UnsignedLong epoch;

  public Fork(Bytes4 previous_version, Bytes4 current_version, UnsignedLong epoch) {
    this.previous_version = previous_version;
    this.current_version = current_version;
    this.epoch = epoch;
  }

  public Fork(Fork fork) {
    this.previous_version = fork.getPrevious_version();
    this.current_version = fork.getCurrent_version();
    this.epoch = fork.getEpoch();
  }

  @Override
  public int getSSZFieldCount() {
    return SSZ_FIELD_COUNT;
  }

  @Override
  public List<Bytes> get_fixed_parts() {
    return List.of(
        SSZ.encode(writer -> writer.writeFixedBytes(previous_version.getWrappedBytes())),
        SSZ.encode(writer -> writer.writeFixedBytes(current_version.getWrappedBytes())),
        SSZ.encodeUInt64(epoch.longValue()));
  }

  @Override
  public int hashCode() {
    return Objects.hash(previous_version, current_version, epoch);
  }

  @Override
  public boolean equals(Object obj) {
    if (Objects.isNull(obj)) {
      return false;
    }

    if (this == obj) {
      return true;
    }

    if (!(obj instanceof Fork)) {
      return false;
    }

    Fork other = (Fork) obj;
    return Objects.equals(
            this.getPrevious_version().getWrappedBytes(),
            other.getPrevious_version().getWrappedBytes())
        && Objects.equals(
            this.getCurrent_version().getWrappedBytes(),
            other.getCurrent_version().getWrappedBytes())
        && Objects.equals(this.getEpoch(), other.getEpoch());
  }

  /** ******************* * GETTERS & SETTERS * * ******************* */
  public Bytes4 getPrevious_version() {
    return previous_version;
  }

  public Bytes4 getCurrent_version() {
    return current_version;
  }

  public UnsignedLong getEpoch() {
    return epoch;
  }

  @Override
  public Bytes32 hash_tree_root() {
    return HashTreeUtil.merkleize(
        Arrays.asList(
            HashTreeUtil.hash_tree_root(
                SSZTypes.VECTOR_OF_BASIC, previous_version.getWrappedBytes()),
            HashTreeUtil.hash_tree_root(
                SSZTypes.VECTOR_OF_BASIC, current_version.getWrappedBytes()),
            HashTreeUtil.hash_tree_root(SSZTypes.BASIC, SSZ.encodeUInt64(epoch.longValue()))));
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("previous_version", previous_version)
        .add("current_version", current_version)
        .add("epoch", epoch)
        .toString();
  }
}
