/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.api.migrated;

import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.UINT64_TYPE;

import java.util.Objects;
import java.util.Optional;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

public class StateValidatorBalanceData {
  private final UInt64 index;
  private final UInt64 balance;

  private static final SerializableTypeDefinition<StateValidatorBalanceData> DATA_TYPE =
      SerializableTypeDefinition.object(StateValidatorBalanceData.class)
          .withField("index", UINT64_TYPE, StateValidatorBalanceData::getIndex)
          .withField("balance", UINT64_TYPE, StateValidatorBalanceData::getBalance)
          .build();

  public StateValidatorBalanceData(final UInt64 index, final UInt64 balance) {
    this.index = index;
    this.balance = balance;
  }

  public static Optional<StateValidatorBalanceData> fromState(
      final BeaconState state, final Integer index) {
    if (index >= state.getValidators().size()) {
      return Optional.empty();
    }
    return Optional.of(
        new StateValidatorBalanceData(
            UInt64.valueOf(index), state.getBalances().getElement(index)));
  }

  public UInt64 getIndex() {
    return index;
  }

  public UInt64 getBalance() {
    return balance;
  }

  public static SerializableTypeDefinition<StateValidatorBalanceData> getJsonTypeDefinition() {
    return DATA_TYPE;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    StateValidatorBalanceData that = (StateValidatorBalanceData) o;
    return Objects.equals(index, that.index) && Objects.equals(balance, that.balance);
  }

  @Override
  public int hashCode() {
    return Objects.hash(index, balance);
  }
}
