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

package tech.pegasys.teku.datastructures.operations;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.ssz.SSZ;
import tech.pegasys.teku.datastructures.util.HashTreeUtil;
import tech.pegasys.teku.datastructures.util.HashTreeUtil.SSZTypes;
import tech.pegasys.teku.datastructures.util.Merkleizable;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.SSZTypes.SSZContainer;
import tech.pegasys.teku.ssz.sos.SimpleOffsetSerializable;

public class VoluntaryExit implements Merkleizable, SimpleOffsetSerializable, SSZContainer {

  // The number of SimpleSerialize basic types in this SSZ Container/POJO.
  public static final int SSZ_FIELD_COUNT = 2;

  private final UInt64 epoch;
  private final UInt64 validator_index;

  public VoluntaryExit(UInt64 epoch, UInt64 validator_index) {
    this.epoch = epoch;
    this.validator_index = validator_index;
  }

  @Override
  public int getSSZFieldCount() {
    return SSZ_FIELD_COUNT;
  }

  @Override
  public List<Bytes> get_fixed_parts() {
    return List.of(
        SSZ.encodeUInt64(epoch.longValue()), SSZ.encodeUInt64(validator_index.longValue()));
  }

  @Override
  public int hashCode() {
    return Objects.hash(epoch, validator_index);
  }

  @Override
  public boolean equals(Object obj) {
    if (Objects.isNull(obj)) {
      return false;
    }

    if (this == obj) {
      return true;
    }

    if (!(obj instanceof VoluntaryExit)) {
      return false;
    }

    VoluntaryExit other = (VoluntaryExit) obj;
    return Objects.equals(this.getEpoch(), other.getEpoch())
        && Objects.equals(this.getValidator_index(), other.getValidator_index());
  }

  /** ******************* * GETTERS & SETTERS * * ******************* */
  public UInt64 getEpoch() {
    return epoch;
  }

  public UInt64 getValidator_index() {
    return validator_index;
  }

  @Override
  public Bytes32 hash_tree_root() {
    return HashTreeUtil.merkleize(
        Arrays.asList(
            HashTreeUtil.hash_tree_root(SSZTypes.BASIC, SSZ.encodeUInt64(epoch.longValue())),
            HashTreeUtil.hash_tree_root(
                SSZTypes.BASIC, SSZ.encodeUInt64(validator_index.longValue()))));
  }
}
