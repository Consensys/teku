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
import tech.pegasys.teku.ssz.backing.containers.Container2;
import tech.pegasys.teku.ssz.backing.containers.ContainerType2;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.sos.SimpleOffsetSerializable;
import tech.pegasys.teku.ssz.sos.SszTypeDescriptor;

public class AttestationFuzzInput extends
    Container2<AttestationFuzzInput, BeaconState, Attestation> implements SimpleOffsetSerializable,
    SSZContainer {

  @SszTypeDescriptor
  public static final ContainerType2<AttestationFuzzInput, BeaconState, Attestation> TYPE = ContainerType2
      .create(
          BeaconState.getSSZType(),
          Attestation.TYPE, AttestationFuzzInput::new);


  private AttestationFuzzInput(
      ContainerType2<AttestationFuzzInput, BeaconState, Attestation> type,
      TreeNode backingNode) {
    super(type, backingNode);
  }

  public AttestationFuzzInput(final BeaconStateImpl state, final Attestation attestation) {
    super(TYPE, state, attestation);
  }

  // NOTE: empty constructor is needed for reflection/introspection
  public AttestationFuzzInput() {
    super(TYPE);
  }

  public Attestation getAttestation() {
    return getField1();
  }

  public BeaconState getState() {
    return getField0();
  }
}
