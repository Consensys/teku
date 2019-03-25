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
import java.util.Objects;
import net.consensys.cava.bytes.Bytes;
import net.consensys.cava.ssz.SSZ;
import tech.pegasys.artemis.datastructures.Copyable;
import tech.pegasys.artemis.datastructures.operations.AttestationData;

public class PendingAttestation implements Copyable<PendingAttestation> {

  private Bytes aggregation_bitfield;
  private AttestationData data;
  private Bytes custody_bitfield;
  private UnsignedLong inclusion_slot;

  public PendingAttestation(
      Bytes aggregation_bitfield,
      AttestationData data,
      Bytes custody_bitfield,
      UnsignedLong inclusion_slot) {
    this.aggregation_bitfield = aggregation_bitfield;
    this.data = data;
    this.custody_bitfield = custody_bitfield;
    this.inclusion_slot = inclusion_slot;
  }

  public PendingAttestation(PendingAttestation pendingAttestation) {
    this.aggregation_bitfield = pendingAttestation.getAggregation_bitfield().copy();
    this.data = new AttestationData(pendingAttestation.getData());
    this.custody_bitfield = pendingAttestation.getCustody_bitfield().copy();
    this.inclusion_slot = pendingAttestation.getInclusionSlot();
  }

  @Override
  public PendingAttestation copy() {
    return new PendingAttestation(this);
  }

  public static PendingAttestation fromBytes(Bytes bytes) {
    return SSZ.decode(
        bytes,
        reader ->
            new PendingAttestation(
                Bytes.wrap(reader.readBytes()),
                AttestationData.fromBytes(reader.readBytes()),
                Bytes.wrap(reader.readBytes()),
                UnsignedLong.fromLongBits(reader.readUInt64())));
  }

  public Bytes toBytes() {
    return SSZ.encode(
        writer -> {
          writer.writeBytes(aggregation_bitfield);
          writer.writeBytes(data.toBytes());
          writer.writeBytes(custody_bitfield);
          writer.writeUInt64(inclusion_slot.longValue());
        });
  }

  @Override
  public int hashCode() {
    return Objects.hash(aggregation_bitfield, data, custody_bitfield, inclusion_slot);
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
        && Objects.equals(this.getCustody_bitfield(), other.getCustody_bitfield())
        && Objects.equals(this.getInclusionSlot(), other.getInclusionSlot());
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

  public UnsignedLong getInclusionSlot() {
    return inclusion_slot;
  }

  public void setInclusionSlot(UnsignedLong inclusion_slot) {
    this.inclusion_slot = inclusion_slot;
  }
}
