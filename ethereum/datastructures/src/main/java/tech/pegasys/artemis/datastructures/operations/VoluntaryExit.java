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
import java.util.List;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.ssz.SSZ;
import tech.pegasys.artemis.util.SSZTypes.SSZContainer;
import tech.pegasys.artemis.util.bls.BLSSignature;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil.SSZTypes;
import tech.pegasys.artemis.util.hashtree.Merkleizable;
import tech.pegasys.artemis.util.sos.SimpleOffsetSerializable;

public class VoluntaryExit implements Merkleizable, SimpleOffsetSerializable, SSZContainer {

  // The number of SimpleSerialize basic types in this SSZ Container/POJO.
  public static final int SSZ_FIELD_COUNT = 2;

  private UnsignedLong epoch;
  private UnsignedLong validator_index;
  private BLSSignature signature;

  public VoluntaryExit(UnsignedLong epoch, UnsignedLong validator_index, BLSSignature signature) {
    this.epoch = epoch;
    this.validator_index = validator_index;
    this.signature = signature;
  }

  @Override
  public int getSSZFieldCount() {
    return SSZ_FIELD_COUNT + signature.getSSZFieldCount();
  }

  @Override
  public List<Bytes> get_fixed_parts() {
    List<Bytes> fixedPartsList =
        new ArrayList<>(
            List.of(
                SSZ.encodeUInt64(epoch.longValue()),
                SSZ.encodeUInt64(validator_index.longValue())));
    fixedPartsList.addAll(signature.get_fixed_parts());
    return fixedPartsList;
  }

  public static VoluntaryExit fromBytes(Bytes bytes) {
    return SSZ.decode(
        bytes,
        reader ->
            new VoluntaryExit(
                UnsignedLong.fromLongBits(reader.readUInt64()),
                UnsignedLong.fromLongBits(reader.readUInt64()),
                BLSSignature.fromBytes(reader.readBytes())));
  }

  public Bytes toBytes() {
    return SSZ.encode(
        writer -> {
          writer.writeUInt64(epoch.longValue());
          writer.writeUInt64(validator_index.longValue());
          writer.writeBytes(signature.toBytes());
        });
  }

  @Override
  public int hashCode() {
    return Objects.hash(epoch, validator_index, signature);
  }

  @Override
  public boolean equals(Object obj) {
    if (Objects.isNull(obj)) {
      return false;
    }

    if (this == obj) {
      return true;
    }

    if (!(obj instanceof VoluntaryExit)) {
      return false;
    }

    VoluntaryExit other = (VoluntaryExit) obj;
    return Objects.equals(this.getEpoch(), other.getEpoch())
        && Objects.equals(this.getValidator_index(), other.getValidator_index())
        && Objects.equals(this.getSignature(), other.getSignature());
  }

  /** ******************* * GETTERS & SETTERS * * ******************* */
  public UnsignedLong getEpoch() {
    return epoch;
  }

  public void setEpoch(UnsignedLong epoch) {
    this.epoch = epoch;
  }

  public UnsignedLong getValidator_index() {
    return validator_index;
  }

  public void setValidator_index(UnsignedLong validator_index) {
    this.validator_index = validator_index;
  }

  public BLSSignature getSignature() {
    return signature;
  }

  public void setSignature(BLSSignature signature) {
    this.signature = signature;
  }

  public Bytes32 signing_root(String truncation_param) {
    if (!truncation_param.equals("signature")) {
      throw new UnsupportedOperationException(
          "Only signed_root(proposal, \"signature\") is currently supported for type Proposal.");
    }

    return Bytes32.rightPad(
        HashTreeUtil.merkleize(
            Arrays.asList(
                HashTreeUtil.hash_tree_root(SSZTypes.BASIC, SSZ.encodeUInt64(epoch.longValue())),
                HashTreeUtil.hash_tree_root(
                    SSZTypes.BASIC, SSZ.encodeUInt64(validator_index.longValue())))));
  }

  @Override
  public Bytes32 hash_tree_root() {
    return HashTreeUtil.merkleize(
        Arrays.asList(
            HashTreeUtil.hash_tree_root(SSZTypes.BASIC, SSZ.encodeUInt64(epoch.longValue())),
            HashTreeUtil.hash_tree_root(
                SSZTypes.BASIC, SSZ.encodeUInt64(validator_index.longValue())),
            HashTreeUtil.hash_tree_root(SSZTypes.VECTOR_OF_BASIC, signature.toBytes())));
  }
}
