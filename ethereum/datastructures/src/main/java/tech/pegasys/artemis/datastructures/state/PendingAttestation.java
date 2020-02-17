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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.ssz.SSZ;
import tech.pegasys.artemis.datastructures.Copyable;
import tech.pegasys.artemis.datastructures.operations.AttestationData;
import tech.pegasys.artemis.util.SSZTypes.Bitlist;
import tech.pegasys.artemis.util.SSZTypes.SSZContainer;
import tech.pegasys.artemis.util.config.Constants;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil.SSZTypes;
import tech.pegasys.artemis.util.hashtree.Merkleizable;
import tech.pegasys.artemis.util.sos.SimpleOffsetSerializable;

public class PendingAttestation
    implements Copyable<PendingAttestation>, Merkleizable, SimpleOffsetSerializable, SSZContainer {

  // The number of SimpleSerialize basic types in this SSZ Container/POJO.
  public static final int SSZ_FIELD_COUNT = 3;

  private final Bitlist aggregation_bits; // bitlist bounded by MAX_VALIDATORS_PER_COMMITTEE
  private final AttestationData data;
  private final UnsignedLong inclusion_delay;
  private final UnsignedLong proposer_index;

  public PendingAttestation(
      Bitlist aggregation_bitfield,
      AttestationData data,
      UnsignedLong inclusion_delay,
      UnsignedLong proposer_index) {
    this.aggregation_bits = aggregation_bitfield;
    this.data = data;
    this.inclusion_delay = inclusion_delay;
    this.proposer_index = proposer_index;
  }

  public PendingAttestation() {
    this.aggregation_bits =
        new Bitlist(Constants.MAX_VALIDATORS_PER_COMMITTEE, Constants.MAX_VALIDATORS_PER_COMMITTEE);
    this.data = null;
    this.inclusion_delay = null;
    this.proposer_index = null;
  }

  public PendingAttestation(PendingAttestation pendingAttestation) {
    this.aggregation_bits = pendingAttestation.getAggregation_bits().copy();
    this.data = pendingAttestation.getData();
    this.inclusion_delay = pendingAttestation.getInclusion_delay();
    this.proposer_index = pendingAttestation.getProposer_index();
  }

  @Override
  public PendingAttestation copy() {
    return new PendingAttestation(this);
  }

  @Override
  public int getSSZFieldCount() {
    return data.getSSZFieldCount() + SSZ_FIELD_COUNT;
  }

  @Override
  public List<Bytes> get_fixed_parts() {
    List<Bytes> fixedPartsList = new ArrayList<>();
    fixedPartsList.addAll(List.of(Bytes.EMPTY));
    fixedPartsList.addAll(data.get_fixed_parts());
    fixedPartsList.addAll(
        List.of(
            SSZ.encodeUInt64(inclusion_delay.longValue()),
            SSZ.encodeUInt64(proposer_index.longValue())));
    return fixedPartsList;
  }

  @Override
  public List<Bytes> get_variable_parts() {
    List<Bytes> variablePartsList = new ArrayList<>();
    // TODO The below lines are a hack while Tuweni SSZ/SOS is being upgraded. To be uncommented
    // once we shift from Bytes to a real bitlist type.
    // Bytes serialized_aggregation_bits =
    // Bytes.fromHexString("0x01").shiftLeft(aggregation_bits.bitLength()).or(aggregation_bits);
    // variablePartsList.addAll(List.of(serialized_aggregation_bits));
    variablePartsList.addAll(List.of(aggregation_bits.serialize()));
    variablePartsList.addAll(Collections.nCopies(data.getSSZFieldCount(), Bytes.EMPTY));
    variablePartsList.addAll(List.of(Bytes.EMPTY, Bytes.EMPTY));
    return variablePartsList;
  }

  @Override
  public int hashCode() {
    return Objects.hash(aggregation_bits, data, inclusion_delay, proposer_index);
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
    return Objects.equals(this.getAggregation_bits(), other.getAggregation_bits())
        && Objects.equals(this.getData(), other.getData())
        && Objects.equals(this.getInclusion_delay(), other.getInclusion_delay())
        && Objects.equals(this.getProposer_index(), other.getProposer_index());
  }

  /** ******************* * GETTERS & SETTERS * * ******************* */
  public Bitlist getAggregation_bits() {
    return aggregation_bits;
  }

  public AttestationData getData() {
    return data;
  }

  public UnsignedLong getInclusion_delay() {
    return inclusion_delay;
  }

  public UnsignedLong getProposer_index() {
    return proposer_index;
  }

  @Override
  public Bytes32 hash_tree_root() {
    return HashTreeUtil.merkleize(
        Arrays.asList(
            // HashTreeUtil.hash_tree_root(
            //   SSZTypes.BITLIST, Constants.MAX_VALIDATORS_PER_COMMITTEE, aggregation_bits),
            HashTreeUtil.hash_tree_root_bitlist(aggregation_bits),
            data.hash_tree_root(),
            HashTreeUtil.hash_tree_root(
                SSZTypes.BASIC, SSZ.encodeUInt64(inclusion_delay.longValue())),
            HashTreeUtil.hash_tree_root(
                SSZTypes.BASIC, SSZ.encodeUInt64(proposer_index.longValue()))));
  }

  @Override
  public String toString() {
    return "PendingAttestation{" +
        "aggregation_bits=" + aggregation_bits +
        ", data=" + data +
        ", inclusion_delay=" + inclusion_delay +
        ", proposer_index=" + proposer_index +
        '}';
  }
}
