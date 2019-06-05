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
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.ssz.SSZ;
import tech.pegasys.artemis.util.bls.BLSSignature;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil.SSZTypes;

public class SlashableAttestation {

  private List<UnsignedLong> validator_indices;
  private AttestationData data;
  private Bytes custody_bitfield;
  private BLSSignature aggregate_signature;

  public SlashableAttestation(
      List<UnsignedLong> validator_indices,
      AttestationData data,
      Bytes custody_bitfield,
      BLSSignature aggregate_signature) {
    this.validator_indices = validator_indices;
    this.data = data;
    this.custody_bitfield = custody_bitfield;
    this.aggregate_signature = aggregate_signature;
  }

  public static SlashableAttestation fromBytes(Bytes bytes) {
    return SSZ.decode(
        bytes,
        reader ->
            new SlashableAttestation(
                reader.readUInt64List().stream()
                    .map(UnsignedLong::fromLongBits)
                    .collect(Collectors.toList()),
                AttestationData.fromBytes(reader.readBytes()),
                Bytes.wrap(reader.readBytes()),
                BLSSignature.fromBytes(reader.readBytes())));
  }

  public Bytes toBytes() {
    return SSZ.encode(
        writer -> {
          writer.writeULongIntList(
              64,
              validator_indices.stream().map(UnsignedLong::longValue).collect(Collectors.toList()));
          writer.writeBytes(data.toBytes());
          writer.writeBytes(custody_bitfield);
          writer.writeBytes(aggregate_signature.toBytes());
        });
  }

  @Override
  public int hashCode() {
    return Objects.hash(validator_indices, data, custody_bitfield, aggregate_signature);
  }

  @Override
  public boolean equals(Object obj) {
    if (Objects.isNull(obj)) {
      return false;
    }

    if (this == obj) {
      return true;
    }

    if (!(obj instanceof SlashableAttestation)) {
      return false;
    }

    SlashableAttestation other = (SlashableAttestation) obj;
    return Objects.equals(this.getValidator_indices(), other.getValidator_indices())
        && Objects.equals(this.getData(), other.getData())
        && Objects.equals(this.getCustody_bitfield(), other.getCustody_bitfield())
        && Objects.equals(this.getAggregate_signature(), other.getAggregate_signature());
  }

  /** ******************* * GETTERS & SETTERS * * ******************* */
  public AttestationData getData() {
    return data;
  }

  public void setData(AttestationData data) {
    this.data = data;
  }

  public BLSSignature getAggregate_signature() {
    return aggregate_signature;
  }

  public void setAggregate_signature(BLSSignature aggregate_signature) {
    this.aggregate_signature = aggregate_signature;
  }

  public List<UnsignedLong> getValidator_indices() {
    return validator_indices;
  }

  public void setValidator_indices(List<UnsignedLong> validator_indices) {
    this.validator_indices = validator_indices;
  }

  public Bytes getCustody_bitfield() {
    return custody_bitfield;
  }

  public void setCustody_bitfield(Bytes custody_bitfield) {
    this.custody_bitfield = custody_bitfield;
  }

  public Bytes32 hash_tree_root() {
    return HashTreeUtil.merkleize(
        Arrays.asList(
            HashTreeUtil.hash_tree_root(
                SSZTypes.LIST_OF_BASIC,
                validator_indices.stream()
                    .map(item -> SSZ.encodeUInt64(item.longValue()))
                    .collect(Collectors.toList())),
            data.hash_tree_root(),
            HashTreeUtil.hash_tree_root(SSZTypes.LIST_OF_BASIC, custody_bitfield),
            HashTreeUtil.hash_tree_root(SSZTypes.TUPLE_OF_BASIC, aggregate_signature.toBytes())));
  }
}
