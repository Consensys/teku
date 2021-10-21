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

package tech.pegasys.teku.spec.logic.common.operations.validation;

import static java.lang.Math.toIntExact;
import static tech.pegasys.teku.spec.config.SpecConfig.FAR_FUTURE_EPOCH;
import static tech.pegasys.teku.spec.logic.common.operations.validation.OperationInvalidReason.check;
import static tech.pegasys.teku.spec.logic.common.operations.validation.OperationInvalidReason.firstOf;

import java.util.Optional;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.operations.VoluntaryExit;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateAccessors;
import tech.pegasys.teku.spec.logic.common.helpers.Predicates;

public class VoluntaryExitValidator
    implements OperationStateTransitionValidator<SignedVoluntaryExit> {

  private final SpecConfig specConfig;
  private final Predicates predicates;
  private final BeaconStateAccessors beaconStateAccessors;

  VoluntaryExitValidator(
      final SpecConfig specConfig,
      final Predicates predicates,
      final BeaconStateAccessors beaconStateAccessors) {
    this.specConfig = specConfig;
    this.predicates = predicates;
    this.beaconStateAccessors = beaconStateAccessors;
  }

  @Override
  public Optional<OperationInvalidReason> validate(
      final Fork fork, final BeaconState state, final SignedVoluntaryExit signedExit) {
    VoluntaryExit exit = signedExit.getMessage();
    return firstOf(
        () ->
            check(
                UInt64.valueOf(state.getValidators().size()).compareTo(exit.getValidatorIndex())
                    > 0,
                ExitInvalidReason.INVALID_VALIDATOR_INDEX),
        () ->
            check(
                predicates.isActiveValidator(
                    getValidator(state, exit), beaconStateAccessors.getCurrentEpoch(state)),
                ExitInvalidReason.VALIDATOR_INACTIVE),
        () ->
            check(
                getValidator(state, exit).getExit_epoch().compareTo(FAR_FUTURE_EPOCH) == 0,
                ExitInvalidReason.EXIT_INITIATED),
        () ->
            check(
                beaconStateAccessors.getCurrentEpoch(state).compareTo(exit.getEpoch()) >= 0,
                ExitInvalidReason.SUBMITTED_TOO_EARLY),
        () ->
            check(
                beaconStateAccessors
                        .getCurrentEpoch(state)
                        .compareTo(
                            getValidator(state, exit)
                                .getActivation_epoch()
                                .plus(specConfig.getShardCommitteePeriod()))
                    >= 0,
                ExitInvalidReason.VALIDATOR_TOO_YOUNG));
  }

  private Validator getValidator(BeaconState state, VoluntaryExit exit) {
    return state.getValidators().get(toIntExact(exit.getValidatorIndex().longValue()));
  }

  public enum ExitInvalidReason implements OperationInvalidReason {
    INVALID_VALIDATOR_INDEX("Invalid validator index"),
    VALIDATOR_INACTIVE("Validator is not active"),
    EXIT_INITIATED("Validator has already initiated exit"),
    SUBMITTED_TOO_EARLY("Specified exit epoch is still in the future"),
    VALIDATOR_TOO_YOUNG("Validator has not been active long enough"),
    INVALID_SIGNATURE("Signature is invalid");

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
