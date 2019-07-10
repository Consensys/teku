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
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.ssz.SSZ;
import tech.pegasys.artemis.util.bls.BLSSignature;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil.SSZTypes;
import tech.pegasys.artemis.util.hashtree.Merkleizable;
import tech.pegasys.artemis.util.sos.SimpleOffsetSerializable;

public class Attestation implements Merkleizable, SimpleOffsetSerializable {

  // The number of SimpleSerialize basic types in this SSZ Container/POJO.
  public static final int SSZ_FIELD_COUNT = 2;

  private Bytes aggregation_bitfield;
  private AttestationData data;
  private Bytes custody_bitfield;
  private BLSSignature aggregate_signature;

  public Attestation(
      Bytes aggregation_bitfield,
      AttestationData data,
      Bytes custody_bitfield,
      BLSSignature aggregate_signature) {
    this.aggregation_bitfield = aggregation_bitfield;
    this.data = data;
    this.custody_bitfield = custody_bitfield;
    this.aggregate_signature = aggregate_signature;
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

  public static Attestation fromBytes(Bytes bytes) {
    return SSZ.decode(
        bytes,
        reader ->
            new Attestation(
                Bytes.wrap(reader.readBytes()),
                AttestationData.fromBytes(reader.readBytes()),
                Bytes.wrap(reader.readBytes()),
                BLSSignature.fromBytes(reader.readBytes())));
  }

  public Bytes toBytes() {
    return SSZ.encode(
        writer -> {
          writer.writeBytes(aggregation_bitfield);
          writer.writeBytes(data.toBytes());
          writer.writeBytes(custody_bitfield);
          writer.writeBytes(aggregate_signature.toBytes());
        });
  }

  @Override
  public int hashCode() {
    return Objects.hash(aggregation_bitfield, data, custody_bitfield, aggregate_signature);
  }

  @Override
  public boolean equals(Object obj) {
    if (Objects.isNull(obj)) {
      return false;
    }

    if (this == obj) {
      return true;
    }

    if (!(obj instanceof Attestation)) {
      return false;
    }

    Attestation other = (Attestation) obj;
    return Objects.equals(this.getAggregation_bitfield(), other.getAggregation_bitfield())
        && Objects.equals(this.getData(), other.getData())
        && Objects.equals(this.getCustody_bitfield(), other.getCustody_bitfield())
        && Objects.equals(this.getAggregate_signature(), other.getAggregate_signature());
  }

  /** ******************* * GETTERS & SETTERS * * ******************* */
  public Bytes getAggregation_bitfield() {
    return aggregation_bitfield;
  }

  public void setAggregation_bitfield(Bytes aggregation_bitfield) {
    this.aggregation_bitfield = aggregation_bitfield;
  }

  public AttestationData getData() {
    return data;
  }

  public void setData(AttestationData data) {
    this.data = data;
  }

  public Bytes getCustody_bitfield() {
    return custody_bitfield;
  }

  public void setCustody_bitfield(Bytes custody_bitfield) {
    this.custody_bitfield = custody_bitfield;
  }

  public BLSSignature getAggregate_signature() {
    return aggregate_signature;
  }

  public void setAggregate_signature(BLSSignature aggregate_signature) {
    this.aggregate_signature = aggregate_signature;
  }

  public UnsignedLong getTarget_epoch() {
    return this.data.getTarget_epoch();
  }

  @Override
  public Bytes32 hash_tree_root() {
    return HashTreeUtil.merkleize(
        Arrays.asList(
            HashTreeUtil.hash_tree_root(SSZTypes.LIST_OF_BASIC, aggregation_bitfield),
            data.hash_tree_root(),
            HashTreeUtil.hash_tree_root(SSZTypes.LIST_OF_BASIC, custody_bitfield),
            HashTreeUtil.hash_tree_root(SSZTypes.TUPLE_OF_BASIC, aggregate_signature.toBytes())));
  }
}
