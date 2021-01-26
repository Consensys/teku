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
import tech.pegasys.teku.datastructures.state.BeaconStateImpl;
import tech.pegasys.teku.ssz.SSZTypes.SSZContainer;
import tech.pegasys.teku.ssz.backing.containers.Container2;
import tech.pegasys.teku.ssz.backing.containers.ContainerType2;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.sos.SimpleOffsetSerializable;
import tech.pegasys.teku.ssz.sos.SszTypeDescriptor;

public class DepositFuzzInput extends Container2<DepositFuzzInput, BeaconState, Deposit>
    implements SimpleOffsetSerializable, SSZContainer {

  @SszTypeDescriptor
  public static final ContainerType2<DepositFuzzInput, BeaconState, Deposit> TYPE =
      ContainerType2.create(BeaconState.getSSZType(), Deposit.TYPE, DepositFuzzInput::new);

  public DepositFuzzInput(
      ContainerType2<DepositFuzzInput, BeaconState, Deposit> type, TreeNode backingNode) {
    super(type, backingNode);
  }

  public DepositFuzzInput(final BeaconStateImpl state, final Deposit deposit) {
    super(TYPE, state, deposit);
  }

  // NOTE: empty constructor is needed for reflection/introspection
  public DepositFuzzInput() {
    super(TYPE);
  }

  public Deposit getDeposit() {
    return getField1();
  }

  public BeaconState getState() {
    return getField0();
  }
}
