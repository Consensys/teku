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

package tech.pegasys.teku.core.operationvalidators;

import static java.lang.Math.toIntExact;
import static tech.pegasys.teku.core.operationvalidators.OperationInvalidReason.check;
import static tech.pegasys.teku.core.operationvalidators.OperationInvalidReason.firstOf;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.get_current_epoch;
import static tech.pegasys.teku.datastructures.util.ValidatorsUtil.is_active_validator;
import static tech.pegasys.teku.util.config.Constants.FAR_FUTURE_EPOCH;
import static tech.pegasys.teku.util.config.Constants.SHARD_COMMITTEE_PERIOD;

import java.util.Optional;
import tech.pegasys.teku.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.datastructures.operations.VoluntaryExit;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Validator;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class VoluntaryExitStateTransitionValidator
    implements OperationStateTransitionValidator<SignedVoluntaryExit> {

  @Override
  public Optional<OperationInvalidReason> validate(
      final BeaconState state, final SignedVoluntaryExit signedExit) {
    VoluntaryExit exit = signedExit.getMessage();
    return firstOf(
        () ->
            check(
                UInt64.valueOf(state.getValidators().size()).compareTo(exit.getValidator_index())
                    > 0,
                ExitInvalidReason.INVALID_VALIDATOR_INDEX),
        () ->
            check(
                is_active_validator(getValidator(state, exit), get_current_epoch(state)),
                ExitInvalidReason.VALIDATOR_INACTIVE),
        () ->
            check(
                getValidator(state, exit).getExit_epoch().compareTo(FAR_FUTURE_EPOCH) == 0,
                ExitInvalidReason.EXIT_INITIATED),
        () ->
            check(
                get_current_epoch(state).compareTo(exit.getEpoch()) >= 0,
                ExitInvalidReason.SUBMITTED_TOO_EARLY),
        () ->
            check(
                get_current_epoch(state)
                        .compareTo(
                            getValidator(state, exit)
                                .getActivation_epoch()
                                .plus(SHARD_COMMITTEE_PERIOD))
                    >= 0,
                ExitInvalidReason.VALIDATOR_TOO_YOUNG));
  }

  private Validator getValidator(BeaconState state, VoluntaryExit exit) {
    return state.getValidators().get(toIntExact(exit.getValidator_index().longValue()));
  }

  public enum ExitInvalidReason implements OperationInvalidReason {
    INVALID_VALIDATOR_INDEX("Invalid validator index"),
    VALIDATOR_INACTIVE("Validator is not active"),
    EXIT_INITIATED("Validator has already initiated exit"),
    SUBMITTED_TOO_EARLY("Specified exit epoch is still in the future"),
    VALIDATOR_TOO_YOUNG("Validator has not been active long enough");

    private final String description;

    ExitInvalidReason(final String description) {
      this.description = description;
    }

    @Override
    public String describe() {
      return description;
    }
  }
}
