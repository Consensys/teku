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

import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.ssz.backing.containers.Container2;
import tech.pegasys.teku.ssz.backing.containers.ContainerSchema2;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;

public class AttestationFuzzInput
    extends Container2<AttestationFuzzInput, BeaconState, Attestation> {

  public static ContainerSchema2<AttestationFuzzInput, BeaconState, Attestation> createSchema() {
    return ContainerSchema2.create(
        BeaconState.getSszSchema(), Attestation.SSZ_SCHEMA, AttestationFuzzInput::new);
  }

  private AttestationFuzzInput(
      ContainerSchema2<AttestationFuzzInput, BeaconState, Attestation> type, TreeNode backingNode) {
    super(type, backingNode);
  }

  public AttestationFuzzInput(final BeaconState state, final Attestation attestation) {
    super(createSchema(), state, attestation);
  }

  // NOTE: empty constructor is needed for reflection/introspection
  public AttestationFuzzInput() {
    super(createSchema());
  }

  public Attestation getAttestation() {
    return getField1();
  }

  public BeaconState getState() {
    return getField0();
  }
}
