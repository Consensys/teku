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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlockHeader;
import tech.pegasys.teku.datastructures.util.HashTreeUtil;
import tech.pegasys.teku.datastructures.util.Merkleizable;
import tech.pegasys.teku.ssz.SSZTypes.SSZContainer;
import tech.pegasys.teku.ssz.sos.SimpleOffsetSerializable;

public class ProposerSlashing implements Merkleizable, SimpleOffsetSerializable, SSZContainer {

  // The number of SimpleSerialize basic types in this SSZ Container/POJO.
  public static final int SSZ_FIELD_COUNT = 0;

  private final SignedBeaconBlockHeader header_1;
  private final SignedBeaconBlockHeader header_2;

  public ProposerSlashing(SignedBeaconBlockHeader header_1, SignedBeaconBlockHeader header_2) {
    this.header_1 = header_1;
    this.header_2 = header_2;
  }

  @Override
  public int getSSZFieldCount() {
    return SSZ_FIELD_COUNT + header_1.getSSZFieldCount() + header_2.getSSZFieldCount();
  }

  @Override
  public List<Bytes> get_fixed_parts() {
    List<Bytes> fixedPartsList = new ArrayList<>();
    fixedPartsList.addAll(header_1.get_fixed_parts());
    fixedPartsList.addAll(header_2.get_fixed_parts());
    return fixedPartsList;
  }

  @Override
  public int hashCode() {
    return Objects.hash(header_1, header_2);
  }

  @Override
  public boolean equals(Object obj) {
    if (Objects.isNull(obj)) {
      return false;
    }

    if (this == obj) {
      return true;
    }

    if (!(obj instanceof ProposerSlashing)) {
      return false;
    }

    ProposerSlashing other = (ProposerSlashing) obj;
    return Objects.equals(this.getHeader_1(), other.getHeader_1())
        && Objects.equals(this.getHeader_2(), other.getHeader_2());
  }

  /** ******************* * GETTERS & SETTERS * * ******************* */
  public SignedBeaconBlockHeader getHeader_1() {
    return header_1;
  }

  public SignedBeaconBlockHeader getHeader_2() {
    return header_2;
  }

  @Override
  public Bytes32 hash_tree_root() {
    return HashTreeUtil.merkleize(
        Arrays.asList(header_1.hash_tree_root(), header_2.hash_tree_root()));
  }
}
