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
import net.consensys.cava.bytes.Bytes;
import net.consensys.cava.bytes.Bytes32;
import net.consensys.cava.bytes.Bytes48;
import net.consensys.cava.ssz.SSZ;

public class Attestation {

  private AttestationData data;
  private Bytes32 participation_bitfield;
  private Bytes32 custody_bitfield;
  private List<Bytes48> aggregate_signature;

  public Attestation(
      AttestationData data,
      Bytes32 participation_bitfield,
      Bytes32 custody_bitfield,
      List<Bytes48> aggregate_signature) {
    this.data = data;
    this.participation_bitfield = participation_bitfield;
    this.custody_bitfield = custody_bitfield;
    this.aggregate_signature = aggregate_signature;
  }

  public Bytes toBytes() {
    return SSZ.encode(
        writer -> {
          writer.writeBytes(data.toBytes());
          writer.writeBytes(participation_bitfield);
          writer.writeBytes(custody_bitfield);
          writer.writeBytesList(aggregate_signature.toArray(new Bytes48[0]));
        });
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

  public List<Bytes48> getAggregate_signature() {
    return aggregate_signature;
  }

  public void setAggregate_signature(List<Bytes48> aggregate_signature) {
    this.aggregate_signature = aggregate_signature;
  }
}
