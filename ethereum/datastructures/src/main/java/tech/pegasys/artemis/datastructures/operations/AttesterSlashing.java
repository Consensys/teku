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

package tech.pegasys.artemis.datastructures.operations;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.ssz.SSZ;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil;
import tech.pegasys.artemis.util.hashtree.Merkleizable;
import tech.pegasys.artemis.util.sos.SimpleOffsetSerializable;

public class AttesterSlashing implements Merkleizable, SimpleOffsetSerializable {

  // The number of SimpleSerialize basic types in this SSZ Container/POJO.
  public static final int SSZ_FIELD_COUNT = 0;

  private IndexedAttestation attestation_1;
  private IndexedAttestation attestation_2;

  public AttesterSlashing(IndexedAttestation attestation_1, IndexedAttestation attestation_2) {
    this.attestation_1 = attestation_1;
    this.attestation_2 = attestation_2;
  }

  @Override
  public int getSSZFieldCount() {
    // TODO Finish this stub.
    return SSZ_FIELD_COUNT;
  }

  @Override
  public List<Bytes> get_fixed_parts() {
    // TODO Implement this stub.
    return Collections.nCopies(getSSZFieldCount(), Bytes.EMPTY);
  }

  @Override
  public List<Bytes> get_variable_parts() {
    // TODO Implement this stub.
    return Collections.nCopies(getSSZFieldCount(), Bytes.EMPTY);
  }

  public static AttesterSlashing fromBytes(Bytes bytes) {
    return SSZ.decode(
        bytes,
        reader ->
            new AttesterSlashing(
                IndexedAttestation.fromBytes(reader.readBytes()),
                IndexedAttestation.fromBytes(reader.readBytes())));
  }

  public Bytes toBytes() {
    return SSZ.encode(
        writer -> {
          writer.writeBytes(attestation_1.toBytes());
          writer.writeBytes(attestation_2.toBytes());
        });
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

  public void setAttestation_1(IndexedAttestation attestation_1) {
    this.attestation_1 = attestation_1;
  }

  public IndexedAttestation getAttestation_2() {
    return attestation_2;
  }

  public void setAttestation_2(IndexedAttestation attestation_2) {
    this.attestation_2 = attestation_2;
  }

  @Override
  public Bytes32 hash_tree_root() {
    return HashTreeUtil.merkleize(
        Arrays.asList(attestation_1.hash_tree_root(), attestation_2.hash_tree_root()));
  }
}
