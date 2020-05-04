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
import tech.pegasys.teku.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.teku.ssz.SSZTypes.SSZContainer;
import tech.pegasys.teku.ssz.sos.SimpleOffsetSerializable;
import tech.pegasys.teku.util.hashtree.HashTreeUtil;
import tech.pegasys.teku.util.hashtree.Merkleizable;

public class AttesterSlashing implements Merkleizable, SimpleOffsetSerializable, SSZContainer {

  // The number of SimpleSerialize basic types in this SSZ Container/POJO.
  public static final int SSZ_FIELD_COUNT = 2;

  private final IndexedAttestation attestation_1;
  private final IndexedAttestation attestation_2;

  public AttesterSlashing(IndexedAttestation attestation_1, IndexedAttestation attestation_2) {
    this.attestation_1 = attestation_1;
    this.attestation_2 = attestation_2;
  }

  @Override
  public int getSSZFieldCount() {
    return SSZ_FIELD_COUNT;
  }

  @Override
  public List<Bytes> get_variable_parts() {
    return List.of(
        SimpleOffsetSerializer.serialize(attestation_1),
        SimpleOffsetSerializer.serialize(attestation_2));
  }

  @Override
  public int hashCode() {
    return Objects.hash(attestation_1, attestation_2);
  }

  @Override
  public boolean equals(Object obj) {
    if (Objects.isNull(obj)) {
      return false;
    }

    if (this == obj) {
      return true;
    }

    if (!(obj instanceof AttesterSlashing)) {
      return false;
    }

    AttesterSlashing other = (AttesterSlashing) obj;
    return Objects.equals(this.getAttestation_1(), other.getAttestation_1())
        && Objects.equals(this.getAttestation_2(), other.getAttestation_2());
  }

  /** ******************* * GETTERS & SETTERS * * ******************* */
  public IndexedAttestation getAttestation_1() {
    return attestation_1;
  }

  public IndexedAttestation getAttestation_2() {
    return attestation_2;
  }

  @Override
  public Bytes32 hash_tree_root() {
    return HashTreeUtil.merkleize(
        Arrays.asList(attestation_1.hash_tree_root(), attestation_2.hash_tree_root()));
  }
}
