/*
 * Copyright 2022 ConsenSys AG.
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

import java.util.Objects;
import java.util.Optional;
import tech.pegasys.teku.api.response.v1.beacon.ValidatorStatus;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

public class StateValidatorData {
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

    tech.pegasys.teku.spec.datastructures.state.Validator validatorInternal =
        state.getValidators().get(index);

    final StateValidatorData data =
        new StateValidatorData(
            UInt64.valueOf(index),
            state.getBalances().getElement(index),
            getValidatorStatus(epoch, validatorInternal, farFutureEpoch),
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

  public static ValidatorStatus getValidatorStatus(
      final UInt64 epoch,
      final tech.pegasys.teku.spec.datastructures.state.Validator validator,
      final UInt64 farFutureEpoch) {
    // pending
    if (validator.getActivationEpoch().isGreaterThan(epoch)) {
      return validator.getActivationEligibilityEpoch().equals(farFutureEpoch)
          ? ValidatorStatus.pending_initialized
          : ValidatorStatus.pending_queued;
    }
    // active
    if (validator.getActivationEpoch().isLessThanOrEqualTo(epoch)
        && epoch.isLessThan(validator.getExitEpoch())) {
      if (validator.getExitEpoch().equals(farFutureEpoch)) {
        return ValidatorStatus.active_ongoing;
      }
      return validator.isSlashed()
          ? ValidatorStatus.active_slashed
          : ValidatorStatus.active_exiting;
    }

    // exited
    if (validator.getExitEpoch().isLessThanOrEqualTo(epoch)
        && epoch.isLessThan(validator.getWithdrawableEpoch())) {
      return validator.isSlashed()
          ? ValidatorStatus.exited_slashed
          : ValidatorStatus.exited_unslashed;
    }

    // withdrawal
    if (validator.getWithdrawableEpoch().isLessThanOrEqualTo(epoch)) {
      return validator.getEffectiveBalance().isGreaterThan(UInt64.ZERO)
          ? ValidatorStatus.withdrawal_possible
          : ValidatorStatus.withdrawal_done;
    }
    throw new IllegalStateException("Unable to determine validator status");
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
