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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.artemis.util.SSZTypes.Bitlist;
import tech.pegasys.artemis.util.SSZTypes.SSZContainer;
import tech.pegasys.artemis.util.bls.BLSSignature;
import tech.pegasys.artemis.util.config.Constants;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil.SSZTypes;
import tech.pegasys.artemis.util.hashtree.Merkleizable;
import tech.pegasys.artemis.util.hashtree.SigningRoot;
import tech.pegasys.artemis.util.sos.SimpleOffsetSerializable;

public class Attestation
    implements Merkleizable, SigningRoot, SimpleOffsetSerializable, SSZContainer {

  // The number of SimpleSerialize basic types in this SSZ Container/POJO.
  public static final int SSZ_FIELD_COUNT = 2;

  private Bitlist aggregation_bits; // Bitlist bounded by MAX_VALIDATORS_PER_COMMITTEE
  private AttestationData data;
  private Bitlist custody_bitfield; // Bitlist bounded by MAX_VALIDATORS_PER_COMMITTEE
  private BLSSignature signature;

  public Attestation(
      Bitlist aggregation_bits,
      AttestationData data,
      Bitlist custody_bitfield,
      BLSSignature signature) {
    this.aggregation_bits = aggregation_bits;
    this.data = data;
    this.custody_bitfield = custody_bitfield;
    this.signature = signature;
  }

  public Attestation(Attestation attestation) {
    this.aggregation_bits = attestation.getAggregation_bits().copy();
    this.data = new AttestationData(attestation.getData());
    this.custody_bitfield = attestation.getCustody_bitfield().copy();
    this.signature = BLSSignature.fromBytes(attestation.getAggregate_signature().toBytes());
  }

  public Attestation() {
    this.aggregation_bits =
        new Bitlist(Constants.MAX_VALIDATORS_PER_COMMITTEE, Constants.MAX_VALIDATORS_PER_COMMITTEE);
    this.custody_bitfield =
        new Bitlist(Constants.MAX_VALIDATORS_PER_COMMITTEE, Constants.MAX_VALIDATORS_PER_COMMITTEE);
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
    fixedPartsList.addAll(List.of(Bytes.EMPTY));
    fixedPartsList.addAll(signature.get_fixed_parts());
    return fixedPartsList;
  }

  @Override
  public List<Bytes> get_variable_parts() {
    List<Bytes> variablePartsList = new ArrayList<>();
    // TODO The below lines are a hack while Tuweni SSZ/SOS is being upgraded. To be uncommented
    // once we shift from Bitlist to a real bitlist type.
    // Bitlist serialized_aggregation_bits =
    // Bitlist.fromHexString("0x01").shiftLeft(aggregation_bits.bitLength()).or(aggregation_bits);
    // variablePartsList.addAll(List.of(serialized_aggregation_bits));
    variablePartsList.addAll(List.of(aggregation_bits.serialize()));
    variablePartsList.addAll(Collections.nCopies(data.getSSZFieldCount(), Bytes.EMPTY));
    // TODO The below lines are a hack while Tuweni SSZ/SOS is being upgraded. To be uncommented
    // once we shift from Bitlist to a real bitlist type.
    // Bitlist serialized_custody_bitfield =
    // Bitlist.fromHexString("0x01").shiftLeft(aggregation_bits.bitLength()).or(custody_bitfield);
    // variablePartsList.addAll(List.of(serialized_custody_bitfield));
    variablePartsList.addAll(List.of(custody_bitfield.serialize()));
    variablePartsList.addAll(Collections.nCopies(signature.getSSZFieldCount(), Bytes.EMPTY));
    return variablePartsList;
  }

  @Override
  public int hashCode() {
    return Objects.hash(aggregation_bits, data, custody_bitfield, signature);
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
    return Objects.equals(this.getAggregation_bits(), other.getAggregation_bits())
        && Objects.equals(this.getData(), other.getData())
        && Objects.equals(this.getCustody_bitfield(), other.getCustody_bitfield())
        && Objects.equals(this.getAggregate_signature(), other.getAggregate_signature());
  }

  /** ******************* * GETTERS & SETTERS * * ******************* */
  public Bitlist getAggregation_bits() {
    return aggregation_bits;
  }

  public void setAggregation_bits(Bitlist aggregation_bits) {
    this.aggregation_bits = aggregation_bits;
  }

  public AttestationData getData() {
    return data;
  }

  public void setData(AttestationData data) {
    this.data = data;
  }

  public Bitlist getCustody_bitfield() {
    return custody_bitfield;
  }

  public void setCustody_bitfield(Bitlist custody_bitfield) {
    this.custody_bitfield = custody_bitfield;
  }

  public BLSSignature getAggregate_signature() {
    return signature;
  }

  public void setAggregate_signature(BLSSignature aggregate_signature) {
    this.signature = aggregate_signature;
  }

  @Override
  public Bytes32 signing_root(String truncation_param) {
    return HashTreeUtil.merkleize(
        Arrays.asList(
            HashTreeUtil.hash_tree_root_bitlist(aggregation_bits),
            data.hash_tree_root(),
            HashTreeUtil.hash_tree_root_bitlist(custody_bitfield)));
  }

  @Override
  public Bytes32 hash_tree_root() {
    return HashTreeUtil.merkleize(
        Arrays.asList(
            HashTreeUtil.hash_tree_root_bitlist(aggregation_bits),
            data.hash_tree_root(),
            HashTreeUtil.hash_tree_root_bitlist(custody_bitfield),
            HashTreeUtil.hash_tree_root(
                SSZTypes.VECTOR_OF_BASIC, SimpleOffsetSerializer.serialize(signature))));
  }
}
