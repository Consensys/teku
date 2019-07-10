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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.ssz.SSZ;
import tech.pegasys.artemis.util.bls.BLSSignature;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil.SSZTypes;
import tech.pegasys.artemis.util.hashtree.Merkleizable;
import tech.pegasys.artemis.util.sos.SimpleOffsetSerializable;

public class IndexedAttestation implements Merkleizable, SimpleOffsetSerializable {

  // The number of SimpleSerialize basic types in this SSZ Container/POJO.
  public static final int SSZ_FIELD_COUNT = 0;

  private List<Integer> custody_bit_0_indices;
  private List<Integer> custody_bit_1_indices;
  private AttestationData data;
  private BLSSignature signature;

  public IndexedAttestation(
      List<Integer> custody_bit_0_indices,
      List<Integer> custody_bit_1_indices,
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

  // TODO Indexed Attestation should be converted to unsigned longs before serialization
  public static IndexedAttestation fromBytes(Bytes bytes) {
    return SSZ.decode(
        bytes,
        reader ->
            new IndexedAttestation(
                reader.readUInt64List().stream()
                    .map(UnsignedLong::fromLongBits)
                    .map(UnsignedLong::intValue)
                    .collect(Collectors.toList()),
                reader.readUInt64List().stream()
                    .map(UnsignedLong::fromLongBits)
                    .map(UnsignedLong::intValue)
                    .collect(Collectors.toList()),
                AttestationData.fromBytes(reader.readBytes()),
                BLSSignature.fromBytes(reader.readBytes())));
  }

  // TODO Indexed Attestation should be converted to unsigned longs before serialization
  public Bytes toBytes() {
    return SSZ.encode(
        writer -> {
          writer.writeULongIntList(
              64,
              custody_bit_0_indices.stream()
                  .map(UnsignedLong::valueOf)
                  .map(UnsignedLong::longValue)
                  .collect(Collectors.toList()));
          writer.writeULongIntList(
              64,
              custody_bit_1_indices.stream()
                  .map(UnsignedLong::valueOf)
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
  public List<Integer> getCustody_bit_0_indices() {
    return custody_bit_0_indices;
  }

  public void setCustody_bit_0_indices(List<Integer> custody_bit_0_indices) {
    this.custody_bit_0_indices = custody_bit_0_indices;
  }

  public List<Integer> getCustody_bit_1_indices() {
    return custody_bit_1_indices;
  }

  public void setCustody_bit_1_indices(List<Integer> custody_bit_1_indices) {
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
            HashTreeUtil.hash_tree_root(
                SSZTypes.LIST_OF_BASIC,
                custody_bit_0_indices.stream()
                    .map(item -> SSZ.encodeUInt64(item.longValue()))
                    .collect(Collectors.toList())),
            HashTreeUtil.hash_tree_root(
                SSZTypes.LIST_OF_BASIC,
                custody_bit_1_indices.stream()
                    .map(item -> SSZ.encodeUInt64(item.longValue()))
                    .collect(Collectors.toList())),
            data.hash_tree_root(),
            HashTreeUtil.hash_tree_root(SSZTypes.TUPLE_OF_BASIC, signature.toBytes())));
  }
}
