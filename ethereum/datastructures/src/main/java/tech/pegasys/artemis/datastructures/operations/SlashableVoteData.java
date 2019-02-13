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

import java.util.List;
import java.util.Objects;
import net.consensys.cava.bytes.Bytes;
import net.consensys.cava.ssz.SSZ;

public class SlashableVoteData {

  private List<Integer> custody_bit_0_indices;
  private List<Integer> custody_bit_1_indices;
  private AttestationData data;
  private BLSSignature aggregate_signature;

  public SlashableVoteData(
      List<Integer> custody_bit_0_indices,
      List<Integer> custody_bit_1_indices,
      AttestationData data,
      BLSSignature aggregate_signature) {
    this.custody_bit_0_indices = custody_bit_0_indices;
    this.custody_bit_1_indices = custody_bit_1_indices;
    this.data = data;
    this.aggregate_signature = aggregate_signature;
  }

  public static SlashableVoteData fromBytes(Bytes bytes) {
    return SSZ.decode(
        bytes,
        reader ->
            new SlashableVoteData(
                reader.readIntList(24),
                reader.readIntList(24),
                AttestationData.fromBytes(reader.readBytes()),
                BLSSignature.fromBytes(reader.readBytes())));
  }

  public Bytes toBytes() {
    return SSZ.encode(
        writer -> {
          writer.writeIntList(24, custody_bit_0_indices);
          writer.writeIntList(24, custody_bit_1_indices);
          writer.writeBytes(data.toBytes());
          writer.writeBytes(aggregate_signature.toBytes());
        });
  }

  @Override
  public int hashCode() {
    return Objects.hash(custody_bit_0_indices, custody_bit_1_indices, data, aggregate_signature);
  }

  @Override
  public boolean equals(Object obj) {
    if (Objects.isNull(obj)) {
      return false;
    }

    if (this == obj) {
      return true;
    }

    if (!(obj instanceof SlashableVoteData)) {
      return false;
    }

    SlashableVoteData other = (SlashableVoteData) obj;
    return Objects.equals(this.getCustody_bit_0_indices(), other.getCustody_bit_0_indices())
        && Objects.equals(this.getCustody_bit_1_indices(), other.getCustody_bit_1_indices())
        && Objects.equals(this.getData(), other.getData())
        && Objects.equals(this.getAggregate_signature(), other.getAggregate_signature());
  }

  /** ******************* * GETTERS & SETTERS * * ******************* */
  public List<Integer> getCustody_bit_0_indices() {
    return custody_bit_0_indices;
  }

  public void setCustody_bit_0_indices(List<Integer> custody_bit_0_indices) {
    this.custody_bit_0_indices = custody_bit_0_indices;
  }

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

  public List<Integer> getCustody_bit_1_indices() {
    return custody_bit_1_indices;
  }

  public void setCustody_bit_1_indices(List<Integer> custody_bit_1_indices) {
    this.custody_bit_1_indices = custody_bit_1_indices;
  }
}
