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

package tech.pegasys.artemis.datastructures.state;

import com.google.common.primitives.UnsignedLong;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.ssz.SSZ;
import tech.pegasys.artemis.datastructures.Copyable;
import tech.pegasys.artemis.datastructures.operations.AttestationData;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil.SSZTypes;
import tech.pegasys.artemis.util.hashtree.Merkleizable;
import tech.pegasys.artemis.util.sos.SimpleOffsetSerializable;

public class PendingAttestation
    implements Copyable<PendingAttestation>, Merkleizable, SimpleOffsetSerializable {

  // The number of SimpleSerialize basic types in this SSZ Container/POJO.
  public static final int SSZ_FIELD_COUNT = 3;

  private Bytes aggregation_bitfield;
  private AttestationData data;
  private UnsignedLong inclusion_delay;
  private UnsignedLong proposer_index;

  public PendingAttestation(
      Bytes aggregation_bitfield,
      AttestationData data,
      UnsignedLong inclusion_delay,
      UnsignedLong proposer_index) {
    this.aggregation_bitfield = aggregation_bitfield;
    this.data = data;
    this.inclusion_delay = inclusion_delay;
    this.proposer_index = proposer_index;
  }

  public PendingAttestation(PendingAttestation pendingAttestation) {
    this.aggregation_bitfield = pendingAttestation.getAggregation_bitfield().copy();
    this.data = new AttestationData(pendingAttestation.getData());
    this.inclusion_delay = pendingAttestation.getInclusion_delay();
    this.proposer_index = pendingAttestation.getProposer_index();
  }

  @Override
  public PendingAttestation copy() {
    return new PendingAttestation(this);
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

  public static PendingAttestation fromBytes(Bytes bytes) {
    return SSZ.decode(
        bytes,
        reader ->
            new PendingAttestation(
                Bytes.wrap(reader.readBytes()),
                AttestationData.fromBytes(reader.readBytes()),
                UnsignedLong.fromLongBits(reader.readUInt64()),
                UnsignedLong.fromLongBits(reader.readUInt64())));
  }

  public Bytes toBytes() {
    return SSZ.encode(
        writer -> {
          writer.writeBytes(aggregation_bitfield);
          writer.writeBytes(data.toBytes());
          writer.writeUInt64(inclusion_delay.longValue());
          writer.writeUInt64(proposer_index.longValue());
        });
  }

  @Override
  public int hashCode() {
    return Objects.hash(aggregation_bitfield, data, inclusion_delay, proposer_index);
  }

  @Override
  public boolean equals(Object obj) {
    if (Objects.isNull(obj)) {
      return false;
    }

    if (this == obj) {
      return true;
    }

    if (!(obj instanceof PendingAttestation)) {
      return false;
    }

    PendingAttestation other = (PendingAttestation) obj;
    return Objects.equals(this.getAggregation_bitfield(), other.getAggregation_bitfield())
        && Objects.equals(this.getData(), other.getData())
        && Objects.equals(this.getInclusion_delay(), other.getInclusion_delay())
        && Objects.equals(this.getProposer_index(), other.getProposer_index());
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

  public UnsignedLong getInclusion_delay() {
    return inclusion_delay;
  }

  public void setInclusion_delay(UnsignedLong inclusion_delay) {
    this.inclusion_delay = inclusion_delay;
  }

  public UnsignedLong getProposer_index() {
    return proposer_index;
  }

  public void setProposer_index(UnsignedLong proposer_index) {
    this.proposer_index = proposer_index;
  }

  @Override
  public Bytes32 hash_tree_root() {
    return HashTreeUtil.merkleize(
        Arrays.asList(
            HashTreeUtil.hash_tree_root(SSZTypes.LIST_OF_BASIC, aggregation_bitfield),
            data.hash_tree_root(),
            HashTreeUtil.hash_tree_root(
                SSZTypes.BASIC, SSZ.encodeUInt64(inclusion_delay.longValue())),
            HashTreeUtil.hash_tree_root(
                SSZTypes.BASIC, SSZ.encodeUInt64(proposer_index.longValue()))));
  }
}
