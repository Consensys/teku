/*
 * Copyright Consensys Software Inc., 2022
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

package tech.pegasys.teku.ethereum.json.types.beacon;

import static tech.pegasys.teku.infrastructure.http.RestApiConstants.EXECUTION_OPTIMISTIC;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.FINALIZED;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BOOLEAN_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.UINT64_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition.listOf;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import tech.pegasys.teku.api.response.v1.beacon.ValidatorResponse;
import tech.pegasys.teku.api.response.v1.beacon.ValidatorStatus;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.metadata.ObjectAndMetaData;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

public class StateValidatorData {
  private static final DeserializableTypeDefinition<ValidatorStatus> STATUS_TYPE =
      DeserializableTypeDefinition.enumOf(ValidatorStatus.class);

  public static final SerializableTypeDefinition<StateValidatorData> STATE_VALIDATOR_DATA_TYPE =
      SerializableTypeDefinition.object(StateValidatorData.class)
          .withField("index", UINT64_TYPE, StateValidatorData::getIndex)
          .withField("balance", UINT64_TYPE, StateValidatorData::getBalance)
          .withField("status", STATUS_TYPE, StateValidatorData::getStatus)
          .withField(
              "validator",
              Validator.SSZ_SCHEMA.getJsonTypeDefinition(),
              StateValidatorData::getValidator)
          .build();

  public static final SerializableTypeDefinition<ObjectAndMetaData<List<StateValidatorData>>>
      STATE_VALIDATORS_RESPONSE_TYPE =
          SerializableTypeDefinition.<ObjectAndMetaData<List<StateValidatorData>>>object()
              .name("GetStateValidatorsResponse")
              .withField(
                  EXECUTION_OPTIMISTIC, BOOLEAN_TYPE, ObjectAndMetaData::isExecutionOptimistic)
              .withField(FINALIZED, BOOLEAN_TYPE, ObjectAndMetaData::isFinalized)
              .withField("data", listOf(STATE_VALIDATOR_DATA_TYPE), ObjectAndMetaData::getData)
              .build();

  private final UInt64 index;
  private final UInt64 balance;
  private final ValidatorStatus status;
  private final Validator validator;

  public static Optional<StateValidatorData> fromState(
      final BeaconState state,
      final Integer index,
      final UInt64 epoch,
      final UInt64 farFutureEpoch) {
    if (index >= state.getValidators().size()) {
      return Optional.empty();
    }

    Validator validatorInternal = state.getValidators().get(index);

    final StateValidatorData data =
        new StateValidatorData(
            UInt64.valueOf(index),
            state.getBalances().getElement(index),
            ValidatorResponse.getValidatorStatus(epoch, validatorInternal, farFutureEpoch),
            validatorInternal);
    return Optional.of(data);
  }

  public StateValidatorData(
      final UInt64 index,
      final UInt64 balance,
      final ValidatorStatus status,
      final Validator validator) {
    this.index = index;
    this.balance = balance;
    this.status = status;
    this.validator = validator;
  }

  public UInt64 getIndex() {
    return index;
  }

  public UInt64 getBalance() {
    return balance;
  }

  public ValidatorStatus getStatus() {
    return status;
  }

  public Validator getValidator() {
    return validator;
  }

  public BLSPublicKey getPublicKey() {
    return validator.getPublicKey();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    StateValidatorData data = (StateValidatorData) o;
    return Objects.equals(index, data.index)
        && Objects.equals(balance, data.balance)
        && status == data.status
        && Objects.equals(validator, data.validator);
  }

  @Override
  public int hashCode() {
    return Objects.hash(index, balance, status, validator);
  }

  @Override
  public String toString() {
    return "StateValidatorData{"
        + "index="
        + index
        + ", balance="
        + balance
        + ", status="
        + status
        + ", validator="
        + validator
        + '}';
  }
}
