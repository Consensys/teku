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

import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.ssz.SSZ;
import tech.pegasys.artemis.datastructures.Constants;
import tech.pegasys.artemis.util.SSZTypes.SSZContainer;
import tech.pegasys.artemis.util.bls.BLSSignature;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil.SSZTypes;
import tech.pegasys.artemis.util.hashtree.Merkleizable;
import tech.pegasys.artemis.util.reflectionInformation.ReflectionInformation;
import tech.pegasys.artemis.util.sos.SimpleOffsetSerializable;

public class IndexedAttestation implements Merkleizable, SimpleOffsetSerializable, SSZContainer {

  // The number of SimpleSerialize basic types in this SSZ Container/POJO.
  public static final int SSZ_FIELD_COUNT = 2;
  public static final ReflectionInformation reflectionInfo =
      new ReflectionInformation(IndexedAttestation.class);

  private List<UnsignedLong> custody_bit_0_indices; // List bounded by MAX_VALIDATORS_PER_COMMITTEE
  private List<UnsignedLong> custody_bit_1_indices; // List bounded by MAX_VALIDATORS_PER_COMMITTEE
  private AttestationData data;
  private BLSSignature signature;

  public IndexedAttestation(
      List<UnsignedLong> custody_bit_0_indices,
      List<UnsignedLong> custody_bit_1_indices,
      AttestationData data,
      BLSSignature signature) {
    this.custody_bit_0_indices = custody_bit_0_indices;
    this.custody_bit_1_indices = custody_bit_1_indices;
    this.data = data;
    this.signature = signature;
  }

  public IndexedAttestation(IndexedAttestation indexedAttestation) {
    this.custody_bit_0_indices =
        indexedAttestation.getCustody_bit_0_indices().stream().collect(Collectors.toList());
    this.custody_bit_1_indices =
        indexedAttestation.getCustody_bit_1_indices().stream().collect(Collectors.toList());
    this.data = new AttestationData(data);
    this.signature = new BLSSignature(indexedAttestation.getSignature().getSignature());
  }

  @Override
  public int getSSZFieldCount() {
    return SSZ_FIELD_COUNT + data.getSSZFieldCount() + signature.getSSZFieldCount();
  }

  @Override
  public List<Bytes> get_fixed_parts() {
    List<Bytes> fixedPartsList = new ArrayList<>();
    fixedPartsList.addAll(List.of(Bytes.EMPTY, Bytes.EMPTY));
    fixedPartsList.addAll(data.get_fixed_parts());
    fixedPartsList.addAll(signature.get_fixed_parts());
    return fixedPartsList;
  }

  @Override
  public List<Bytes> get_variable_parts() {
    List<Bytes> variablePartsList = new ArrayList<>();
    variablePartsList.addAll(
        List.of(
            // TODO The below lines are a hack while Tuweni SSZ/SOS is being upgraded.
            Bytes.fromHexString(
                custody_bit_0_indices.stream()
                    .map(value -> SSZ.encodeUInt64(value.longValue()).toHexString().substring(2))
                    .collect(Collectors.joining())),
            Bytes.fromHexString(
                custody_bit_1_indices.stream()
                    .map(value -> SSZ.encodeUInt64(value.longValue()).toHexString().substring(2))
                    .collect(Collectors.joining()))
            /*SSZ.encodeUInt64List(
                custody_bit_0_indices.stream()
                    .map(value -> value.longValue())
                    .collect(Collectors.toList())),
            SSZ.encodeUInt64List(
                custody_bit_1_indices.stream()
                    .map(value -> value.longValue())
                    .collect(Collectors.toList()))*/ ));
    variablePartsList.addAll(Collections.nCopies(data.getSSZFieldCount(), Bytes.EMPTY));
    variablePartsList.addAll(Collections.nCopies(signature.getSSZFieldCount(), Bytes.EMPTY));
    return variablePartsList;
  }

  public static IndexedAttestation fromBytes(Bytes bytes) {
    return SSZ.decode(
        bytes,
        reader ->
            new IndexedAttestation(
                reader.readUInt64List().stream()
                    .map(UnsignedLong::fromLongBits)
                    .collect(Collectors.toList()),
                reader.readUInt64List().stream()
                    .map(UnsignedLong::fromLongBits)
                    .collect(Collectors.toList()),
                AttestationData.fromBytes(reader.readBytes()),
                BLSSignature.fromBytes(reader.readBytes())));
  }

  public Bytes toBytes() {
    return SSZ.encode(
        writer -> {
          writer.writeULongIntList(
              64,
              custody_bit_0_indices.stream()
                  .map(UnsignedLong::longValue)
                  .collect(Collectors.toList()));
          writer.writeULongIntList(
              64,
              custody_bit_1_indices.stream()
                  .map(UnsignedLong::longValue)
                  .collect(Collectors.toList()));
          writer.writeBytes(data.toBytes());
          writer.writeBytes(signature.toBytes());
        });
  }

  @Override
  public int hashCode() {
    return Objects.hash(custody_bit_0_indices, custody_bit_1_indices, data, signature);
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
    return Objects.equals(this.getCustody_bit_0_indices(), other.getCustody_bit_0_indices())
        && Objects.equals(this.getCustody_bit_1_indices(), other.getCustody_bit_1_indices())
        && Objects.equals(this.getData(), other.getData())
        && Objects.equals(this.getSignature(), other.getSignature());
  }

  /** ******************* * GETTERS & SETTERS * * ******************* */
  public List<UnsignedLong> getCustody_bit_0_indices() {
    return custody_bit_0_indices;
  }

  public void setCustody_bit_0_indices(List<UnsignedLong> custody_bit_0_indices) {
    this.custody_bit_0_indices = custody_bit_0_indices;
  }

  public List<UnsignedLong> getCustody_bit_1_indices() {
    return custody_bit_1_indices;
  }

  public void setCustody_bit_1_indices(List<UnsignedLong> custody_bit_1_indices) {
    this.custody_bit_1_indices = custody_bit_1_indices;
  }

  public AttestationData getData() {
    return data;
  }

  public void setData(AttestationData data) {
    this.data = data;
  }

  public BLSSignature getSignature() {
    return signature;
  }

  public void setSignature(BLSSignature signature) {
    this.signature = signature;
  }

  @Override
  public Bytes32 hash_tree_root() {
    return HashTreeUtil.merkleize(
        Arrays.asList(
            HashTreeUtil.hash_tree_root_list_ul(
                Constants.MAX_VALIDATORS_PER_COMMITTEE,
                custody_bit_0_indices.stream()
                    .map(item -> SSZ.encodeUInt64(item.longValue()))
                    .collect(Collectors.toList())),
            HashTreeUtil.hash_tree_root_list_ul(
                Constants.MAX_VALIDATORS_PER_COMMITTEE,
                custody_bit_1_indices.stream()
                    .map(item -> SSZ.encodeUInt64(item.longValue()))
                    .collect(Collectors.toList())),
            data.hash_tree_root(),
            HashTreeUtil.hash_tree_root(SSZTypes.VECTOR_OF_BASIC, signature.toBytes())));
  }
}
