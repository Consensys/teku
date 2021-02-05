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

import tech.pegasys.teku.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.ssz.backing.containers.Container2;
import tech.pegasys.teku.ssz.backing.containers.ContainerSchema2;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;

public class VoluntaryExitFuzzInput
    extends Container2<VoluntaryExitFuzzInput, BeaconState, SignedVoluntaryExit> {

  public static ContainerSchema2<VoluntaryExitFuzzInput, BeaconState, SignedVoluntaryExit>
      createType() {
    return ContainerSchema2.create(
        BeaconState.getSszType(), SignedVoluntaryExit.SSZ_SCHEMA, VoluntaryExitFuzzInput::new);
  }

  public VoluntaryExitFuzzInput(
      ContainerSchema2<VoluntaryExitFuzzInput, BeaconState, SignedVoluntaryExit> type,
      TreeNode backingNode) {
    super(type, backingNode);
  }

  public VoluntaryExitFuzzInput(final BeaconState state, final SignedVoluntaryExit exit) {
    super(createType(), state, exit);
  }

  // NOTE: empty constructor is needed for reflection/introspection
  public VoluntaryExitFuzzInput() {
    super(createType());
  }

  public SignedVoluntaryExit getExit() {
    return getField1();
  }

  public BeaconState getState() {
    return getField0();
  }
}
