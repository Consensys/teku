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
import net.consensys.cava.ssz.SSZ;

public class AttesterSlashing {

  private SlashableAttestation slashable_attestation_1;
  private SlashableAttestation slashable_attestation_2;

  public AttesterSlashing(
      SlashableAttestation slashable_attestation_1, SlashableAttestation slashable_attestation_2) {
    this.slashable_attestation_1 = slashable_attestation_1;
    this.slashable_attestation_2 = slashable_attestation_2;
  }

  public static AttesterSlashing fromBytes(Bytes bytes) {
    return SSZ.decode(
        bytes,
        reader ->
            new AttesterSlashing(
                SlashableAttestation.fromBytes(reader.readBytes()),
                SlashableAttestation.fromBytes(reader.readBytes())));
  }

  public Bytes toBytes() {
    return SSZ.encode(
        writer -> {
          writer.writeBytes(slashable_attestation_1.toBytes());
          writer.writeBytes(slashable_attestation_2.toBytes());
        });
  }

  @Override
  public int hashCode() {
    return Objects.hash(slashable_attestation_1, slashable_attestation_2);
  }

  @Override
  public boolean equals(Object obj) {
    if (Objects.isNull(obj)) {
      return false;
    }

    if (this == obj) {
      return true;
    }

    if (!(obj instanceof AttesterSlashing)) {
      return false;
    }

    AttesterSlashing other = (AttesterSlashing) obj;
    return Objects.equals(this.getSlashable_attestation_1(), other.getSlashable_attestation_1())
        && Objects.equals(this.getSlashable_attestation_2(), other.getSlashable_attestation_2());
  }

  /** ******************* * GETTERS & SETTERS * * ******************* */
  public SlashableAttestation getSlashable_attestation_1() {
    return slashable_attestation_1;
  }

  public void setSlashable_attestation_1(SlashableAttestation slashable_attestation_1) {
    this.slashable_attestation_1 = slashable_attestation_1;
  }

  public SlashableAttestation getSlashable_attestation_2() {
    return slashable_attestation_2;
  }

  public void setSlashable_attestation_2(SlashableAttestation slashable_attestation_2) {
    this.slashable_attestation_2 = slashable_attestation_2;
  }
}
