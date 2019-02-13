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

import java.util.Objects;
import net.consensys.cava.bytes.Bytes;
import net.consensys.cava.bytes.Bytes32;
import net.consensys.cava.ssz.SSZ;

public class Attestation {

  private AttestationData data;
  private Bytes32 participation_bitfield;
  private Bytes32 custody_bitfield;
  private BLSSignature aggregate_signature;

  public Attestation(
      AttestationData data,
      Bytes32 participation_bitfield,
      Bytes32 custody_bitfield,
      BLSSignature aggregate_signature) {
    this.data = data;
    this.participation_bitfield = participation_bitfield;
    this.custody_bitfield = custody_bitfield;
    this.aggregate_signature = aggregate_signature;
  }

  public static Attestation fromBytes(Bytes bytes) {
    return SSZ.decode(
        bytes,
        reader ->
            new Attestation(
                AttestationData.fromBytes(reader.readBytes()),
                Bytes32.wrap(reader.readBytes()),
                Bytes32.wrap(reader.readBytes()),
                BLSSignature.fromBytes(reader.readBytes())));
  }

  public Bytes toBytes() {
    return SSZ.encode(
        writer -> {
          writer.writeBytes(data.toBytes());
          writer.writeBytes(participation_bitfield);
          writer.writeBytes(custody_bitfield);
          writer.writeBytes(aggregate_signature.toBytes());
        });
  }

  @Override
  public int hashCode() {
    return Objects.hash(data, participation_bitfield, custody_bitfield, aggregate_signature);
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
    return Objects.equals(this.getData(), other.getData())
        && Objects.equals(this.getParticipation_bitfield(), other.getParticipation_bitfield())
        && Objects.equals(this.getCustody_bitfield(), other.getCustody_bitfield())
        && Objects.equals(this.getAggregate_signature(), other.getAggregate_signature());
  }

  /** ******************* * GETTERS & SETTERS * * ******************* */
  public Bytes32 getParticipation_bitfield() {
    return participation_bitfield;
  }

  public void setParticipation_bitfield(Bytes32 participation_bitfield) {
    this.participation_bitfield = participation_bitfield;
  }

  public AttestationData getData() {
    return data;
  }

  public void setData(AttestationData data) {
    this.data = data;
  }

  public Bytes32 getCustody_bitfield() {
    return custody_bitfield;
  }

  public void setCustody_bitfield(Bytes32 custody_bitfield) {
    this.custody_bitfield = custody_bitfield;
  }

  public BLSSignature getAggregate_signature() {
    return aggregate_signature;
  }

  public void setAggregate_signature(BLSSignature aggregate_signature) {
    this.aggregate_signature = aggregate_signature;
  }
}
