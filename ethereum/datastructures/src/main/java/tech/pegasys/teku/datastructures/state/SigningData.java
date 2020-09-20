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

package tech.pegasys.teku.datastructures.state;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.ssz.SSZ;
import tech.pegasys.teku.datastructures.util.HashTreeUtil;
import tech.pegasys.teku.datastructures.util.HashTreeUtil.SSZTypes;
import tech.pegasys.teku.datastructures.util.Merkleizable;
import tech.pegasys.teku.ssz.SSZTypes.SSZContainer;
import tech.pegasys.teku.ssz.sos.SimpleOffsetSerializable;

public class SigningData implements Merkleizable, SimpleOffsetSerializable, SSZContainer {

  // The number of SimpleSerialize basic types in this SSZ Container/POJO.
  public static final int SSZ_FIELD_COUNT = 2;

  private final Bytes32 object_root;
  private final Bytes32 domain;

  public SigningData(Bytes32 object_root, Bytes32 domain) {
    this.object_root = object_root;
    this.domain = domain;
  }

  @Override
  public int getSSZFieldCount() {
    return SSZ_FIELD_COUNT;
  }

  @Override
  public List<Bytes> get_fixed_parts() {
    return List.of(
        SSZ.encode(writer -> writer.writeFixedBytes(object_root)),
        SSZ.encode(writer -> writer.writeFixedBytes(domain)));
  }

  @Override
  public int hashCode() {
    return Objects.hash(object_root, domain);
  }

  @Override
  public boolean equals(Object obj) {
    if (Objects.isNull(obj)) {
      return false;
    }

    if (this == obj) {
      return true;
    }

    if (!(obj instanceof SigningData)) {
      return false;
    }

    SigningData other = (SigningData) obj;
    return Objects.equals(this.getObjectRoot(), other.getObjectRoot())
        && Objects.equals(this.getDomain(), other.getDomain());
  }

  /** ******************* * GETTERS & SETTERS * * ******************* */
  public Bytes32 getObjectRoot() {
    return object_root;
  }

  public Bytes getDomain() {
    return domain;
  }

  @Override
  public Bytes32 hash_tree_root() {
    return HashTreeUtil.merkleize(
        Arrays.asList(
            HashTreeUtil.hash_tree_root(SSZTypes.VECTOR_OF_BASIC, object_root),
            HashTreeUtil.hash_tree_root(SSZTypes.VECTOR_OF_BASIC, domain)));
  }
}
