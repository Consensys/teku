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
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.BeaconStateImpl;
import tech.pegasys.teku.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.teku.ssz.SSZTypes.SSZContainer;
import tech.pegasys.teku.ssz.sos.SimpleOffsetSerializable;

/** ******************** Input Classes ********************* */

// TODO common abstract class for all operations that are state + op?
// TODO move to separate package?
// NOTE: not obvious how to have a generic "OperationFuzzInput" class because the get_fixed_parts
// and get_variable_parts
// implementations can be different
public class AttestationFuzzInput implements SimpleOffsetSerializable, SSZContainer {

  // TODO should this be a BeaconState or BeaconStateImpl?
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
    return state.getSSZFieldCount() + attestation.getSSZFieldCount();
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
}
