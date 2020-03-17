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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.datastructures.operations.Deposit;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.BeaconStateImpl;
import tech.pegasys.teku.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.teku.ssz.SSZTypes.SSZContainer;
import tech.pegasys.teku.ssz.sos.SimpleOffsetSerializable;

public class DepositFuzzInput implements SimpleOffsetSerializable, SSZContainer {

  private BeaconStateImpl state;
  private Deposit deposit;

  public DepositFuzzInput(final BeaconStateImpl state, final Deposit deposit) {
    this.state = state;
    this.deposit = deposit;
  }

  // NOTE: empty constructor is needed for reflection/introspection
  public DepositFuzzInput() {
    this(new BeaconStateImpl(), new Deposit());
  }

  @Override
  public int getSSZFieldCount() {
    return 2;
  }

  @Override
  public List<Bytes> get_fixed_parts() {
    List<Bytes> fixedPartsList = new ArrayList<>();
    fixedPartsList.add(Bytes.EMPTY);
    fixedPartsList.add(SimpleOffsetSerializer.serialize(deposit));
    return fixedPartsList;
  }

  @Override
  public List<Bytes> get_variable_parts() {
    return List.of(SimpleOffsetSerializer.serialize(state), Bytes.EMPTY);
  }

  /** ******************* * GETTERS & SETTERS * * ******************* */
  public Deposit getDeposit() {
    return deposit;
  }

  public BeaconState getState() {
    return state;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof DepositFuzzInput)) {
      return false;
    }
    final DepositFuzzInput that = (DepositFuzzInput) o;
    return Objects.equals(getState(), that.getState())
        && Objects.equals(getDeposit(), that.getDeposit());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getState(), getDeposit());
  }
}
