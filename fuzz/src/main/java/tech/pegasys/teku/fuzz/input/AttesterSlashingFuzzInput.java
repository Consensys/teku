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

import tech.pegasys.teku.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.BeaconStateImpl;
import tech.pegasys.teku.ssz.backing.containers.Container2;
import tech.pegasys.teku.ssz.backing.containers.ContainerType2;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.sos.SszTypeDescriptor;

public class AttesterSlashingFuzzInput
    extends Container2<AttesterSlashingFuzzInput, BeaconState, AttesterSlashing> {

  @SszTypeDescriptor
  public static ContainerType2<AttesterSlashingFuzzInput, BeaconState, AttesterSlashing>
      createType() {
    return ContainerType2.create(
        BeaconState.getSszType(), AttesterSlashing.TYPE, AttesterSlashingFuzzInput::new);
  }

  private AttesterSlashingFuzzInput(
      ContainerType2<AttesterSlashingFuzzInput, BeaconState, AttesterSlashing> type,
      TreeNode backingNode) {
    super(type, backingNode);
  }

  public AttesterSlashingFuzzInput(
      final BeaconState state, final AttesterSlashing attester_slashing) {
    super(createType(), state, attester_slashing);
  }

  public AttesterSlashing getAttester_slashing() {
    return getField1();
  }

  public BeaconState getState() {
    return getField0();
  }
}
