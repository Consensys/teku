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

package tech.pegasys.teku.api.response.v1.beacon;

import static tech.pegasys.teku.api.schema.SchemaConstants.EXAMPLE_UINT64;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Objects;
import java.util.Optional;
import tech.pegasys.teku.api.schema.Validator;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

public class ValidatorResponse {

  @JsonProperty("index")
  @Schema(
      type = "string",
      example = EXAMPLE_UINT64,
      description = "Index of validator in validator registry.")
  public final UInt64 index;

  @JsonProperty("balance")
  @Schema(
      type = "string",
      example = EXAMPLE_UINT64,
      description = "Current validator balance in gwei.")
  public final UInt64 balance;

  @JsonProperty("status")
  public final ValidatorStatus status;

  @JsonProperty("validator")
  public final Validator validator;

  @JsonCreator
  public ValidatorResponse(
      @JsonProperty("index") final UInt64 index,
      @JsonProperty("balance") final UInt64 balance,
      @JsonProperty("status") final ValidatorStatus status,
      @JsonProperty("validator") final Validator validator) {
    this.index = index;
    this.balance = balance;
    this.status = status;
    this.validator = validator;
  }

  public static Optional<ValidatorResponse> fromState(
      final BeaconState state,
      final Integer index,
      final UInt64 epoch,
      final UInt64 farFutureEpoch) {
    if (index >= state.getValidators().size()) {
      return Optional.empty();
    }
    tech.pegasys.teku.spec.datastructures.state.Validator validatorInternal =
        state.getValidators().get(index);
    return Optional.of(
        new ValidatorResponse(
            UInt64.valueOf(index),
            state.getBalances().getElement(index),
            getValidatorStatus(epoch, validatorInternal, farFutureEpoch),
            new Validator(validatorInternal)));
  }

  public static ValidatorStatus getValidatorStatus(
      final BeaconState state,
      final Integer validatorIndex,
      final UInt64 epoch,
      final UInt64 farFutureEpoch) {
    return getValidatorStatus(epoch, state.getValidators().get(validatorIndex), farFutureEpoch);
  }

  public static ValidatorStatus getValidatorStatus(
      final UInt64 epoch,
      final tech.pegasys.teku.spec.datastructures.state.Validator validator,
      final UInt64 farFutureEpoch) {
    // pending
    if (validator.getActivation_epoch().isGreaterThan(epoch)) {
      return validator.getActivation_eligibility_epoch().equals(farFutureEpoch)
          ? ValidatorStatus.pending_initialized
          : ValidatorStatus.pending_queued;
    }
    // active
    if (validator.getActivation_epoch().isLessThanOrEqualTo(epoch)
        && epoch.isLessThan(validator.getExit_epoch())) {
      if (validator.getExit_epoch().equals(farFutureEpoch)) {
        return ValidatorStatus.active_ongoing;
      }
      return validator.isSlashed()
          ? ValidatorStatus.active_slashed
          : ValidatorStatus.active_exiting;
    }

    // exited
    if (validator.getExit_epoch().isLessThanOrEqualTo(epoch)
        && epoch.isLessThan(validator.getWithdrawable_epoch())) {
      return validator.isSlashed()
          ? ValidatorStatus.exited_slashed
          : ValidatorStatus.exited_unslashed;
    }

    // withdrawal
    if (validator.getWithdrawable_epoch().isLessThanOrEqualTo(epoch)) {
      return validator.getEffective_balance().isGreaterThan(UInt64.ZERO)
          ? ValidatorStatus.withdrawal_possible
          : ValidatorStatus.withdrawal_done;
    }
    throw new IllegalStateException("Unable to determine validator status");
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final ValidatorResponse that = (ValidatorResponse) o;
    return Objects.equals(index, that.index)
        && Objects.equals(balance, that.balance)
        && status == that.status
        && Objects.equals(validator, that.validator);
  }

  @JsonIgnore
  public Integer getIndex() {
    return index.intValue();
  }

  @JsonIgnore
  public BLSPublicKey getPublicKey() {
    return validator.pubkey.asBLSPublicKey();
  }

  @JsonIgnore
  public ValidatorStatus getStatus() {
    return status;
  }

  @Override
  public int hashCode() {
    return Objects.hash(index, balance, status, validator);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("index", index)
        .add("balance", balance)
        .add("status", status)
        .add("validator", validator)
        .toString();
  }
}
