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
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.ssz.SSZ;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.datastructures.util.HashTreeUtil;
import tech.pegasys.teku.datastructures.util.HashTreeUtil.SSZTypes;
import tech.pegasys.teku.datastructures.util.Merkleizable;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.SSZTypes.SSZContainer;
import tech.pegasys.teku.ssz.SSZTypes.SSZList;
import tech.pegasys.teku.ssz.sos.SimpleOffsetSerializable;
import tech.pegasys.teku.util.config.Constants;

public class IndexedAttestation implements Merkleizable, SimpleOffsetSerializable, SSZContainer {

  // The number of SimpleSerialize basic types in this SSZ Container/POJO.
  public static final int SSZ_FIELD_COUNT = 2;

  private final SSZList<UInt64> attesting_indices; // List bounded by MAX_VALIDATORS_PER_COMMITTEE
  private final AttestationData data;
  private final BLSSignature signature;

  public IndexedAttestation(
      SSZList<UInt64> attesting_indices, AttestationData data, BLSSignature signature) {
    this.attesting_indices = attesting_indices;
    this.data = data;
    this.signature = signature;
  }

  // Required by SSZ reflection
  public IndexedAttestation() {
    this.attesting_indices =
        SSZList.createMutable(UInt64.class, Constants.MAX_VALIDATORS_PER_COMMITTEE);
    data = null;
    signature = null;
  }

  public IndexedAttestation(IndexedAttestation indexedAttestation) {
    this.attesting_indices = SSZList.createMutable(indexedAttestation.getAttesting_indices());
    this.data = indexedAttestation.getData();
    this.signature = indexedAttestation.getSignature();
  }

  @Override
  public int getSSZFieldCount() {
    return SSZ_FIELD_COUNT + data.getSSZFieldCount() + signature.getSSZFieldCount();
  }

  @Override
  public List<Bytes> get_fixed_parts() {
    List<Bytes> fixedPartsList = new ArrayList<>();
    fixedPartsList.addAll(List.of(Bytes.EMPTY));
    fixedPartsList.addAll(data.get_fixed_parts());
    fixedPartsList.addAll(signature.get_fixed_parts());
    return fixedPartsList;
  }

  @Override
  public List<Bytes> get_variable_parts() {
    List<Bytes> variablePartsList = new ArrayList<>();
    variablePartsList.add(
        SSZ.encode(
            writer ->
                attesting_indices.stream()
                    .forEach(value -> writer.writeUInt64(value.longValue()))));
    variablePartsList.addAll(Collections.nCopies(data.getSSZFieldCount(), Bytes.EMPTY));
    variablePartsList.addAll(Collections.nCopies(signature.getSSZFieldCount(), Bytes.EMPTY));
    return variablePartsList;
  }

  @Override
  public int hashCode() {
    return Objects.hash(attesting_indices, data, signature);
  }

  @Override
  public boolean equals(Object obj) {
    if (Objects.isNull(obj)) {
      return false;
    }

    if (this == obj) {
      return true;
    }

    if (!(obj instanceof IndexedAttestation)) {
      return false;
    }

    IndexedAttestation other = (IndexedAttestation) obj;
    return Objects.equals(this.getAttesting_indices(), other.getAttesting_indices())
        && Objects.equals(this.getData(), other.getData())
        && Objects.equals(this.getSignature(), other.getSignature());
  }

  /** ******************* * GETTERS & SETTERS * * ******************* */
  public SSZList<UInt64> getAttesting_indices() {
    return attesting_indices;
  }

  public AttestationData getData() {
    return data;
  }

  public BLSSignature getSignature() {
    return signature;
  }

  @Override
  public Bytes32 hash_tree_root() {
    return HashTreeUtil.merkleize(
        Arrays.asList(
            HashTreeUtil.hash_tree_root_list_ul(
                attesting_indices.map(Bytes.class, item -> SSZ.encodeUInt64(item.longValue()))),
            data.hash_tree_root(),
            HashTreeUtil.hash_tree_root(SSZTypes.VECTOR_OF_BASIC, signature.toSSZBytes())));
  }
}
