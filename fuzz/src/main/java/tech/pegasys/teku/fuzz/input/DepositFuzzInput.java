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

import tech.pegasys.teku.datastructures.operations.Deposit;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.ssz.backing.containers.Container2;
import tech.pegasys.teku.ssz.backing.containers.ContainerSchema2;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;

public class DepositFuzzInput extends Container2<DepositFuzzInput, BeaconState, Deposit> {

  public static ContainerSchema2<DepositFuzzInput, BeaconState, Deposit> createType() {
    return ContainerSchema2.create(BeaconState.getSszType(), Deposit.TYPE, DepositFuzzInput::new);
  }

  public DepositFuzzInput(
      ContainerSchema2<DepositFuzzInput, BeaconState, Deposit> type, TreeNode backingNode) {
    super(type, backingNode);
  }

  public DepositFuzzInput(final BeaconState state, final Deposit deposit) {
    super(createType(), state, deposit);
  }

  // NOTE: empty constructor is needed for reflection/introspection
  public DepositFuzzInput() {
    super(createType());
  }

  public Deposit getDeposit() {
    return getField1();
  }

  public BeaconState getState() {
    return getField0();
  }
}
