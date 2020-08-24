/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.fuzz.input;

import java.util.List;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.BeaconStateImpl;
import tech.pegasys.teku.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.teku.ssz.SSZTypes.SSZContainer;
import tech.pegasys.teku.ssz.sos.SimpleOffsetSerializable;

public class AttestationFuzzInput implements SimpleOffsetSerializable, SSZContainer {

  private BeaconStateImpl state;
  private Attestation attestation;

  public AttestationFuzzInput(final BeaconStateImpl state, final Attestation attestation) {
    this.state = state;
    this.attestation = attestation;
  }

  // NOTE: empty constructor is needed for reflection/introspection
  public AttestationFuzzInput() {
    this(new BeaconStateImpl(), new Attestation());
  }

  @Override
  public int getSSZFieldCount() {
    return 2;
  }

  @Override
  public List<Bytes> get_variable_parts() {
    // Because we know both fields are variable and registered, we can just serialize.
    return List.of(
        SimpleOffsetSerializer.serialize(state), SimpleOffsetSerializer.serialize(attestation));
  }

  /** ******************* * GETTERS & SETTERS * * ******************* */
  public Attestation getAttestation() {
    return attestation;
  }

  public BeaconState getState() {
    return state;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof AttestationFuzzInput)) {
      return false;
    }
    final AttestationFuzzInput that = (AttestationFuzzInput) o;
    return Objects.equals(getState(), that.getState())
        && Objects.equals(getAttestation(), that.getAttestation());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getState(), getAttestation());
  }
}
