/*
 * Copyright Consensys Software Inc., 2025
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

package tech.pegasys.teku.spec.logic.versions.gloas.operations.validation;

import static tech.pegasys.teku.spec.logic.common.operations.validation.OperationInvalidReason.check;
import static tech.pegasys.teku.spec.logic.common.operations.validation.OperationInvalidReason.firstOf;

import java.util.Optional;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfigGloas;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.operations.validation.OperationInvalidReason;
import tech.pegasys.teku.spec.logic.versions.electra.operations.validation.VoluntaryExitValidatorElectra;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.BeaconStateAccessorsGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.MiscHelpersGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.PredicatesGloas;

public class VoluntaryExitValidatorGloas extends VoluntaryExitValidatorElectra {

  private final PredicatesGloas predicates;
  private final BeaconStateAccessorsGloas beaconStateAccessors;
  private final MiscHelpersGloas miscHelpers;

  public VoluntaryExitValidatorGloas(
      final SpecConfigGloas specConfig,
      final PredicatesGloas predicates,
      final BeaconStateAccessorsGloas beaconStateAccessors,
      final MiscHelpersGloas miscHelpers) {
    super(specConfig, predicates, beaconStateAccessors);
    this.predicates = predicates;
    this.beaconStateAccessors = beaconStateAccessors;
    this.miscHelpers = miscHelpers;
  }

  @Override
  public Optional<OperationInvalidReason> validate(
      final Fork fork, final BeaconState state, final SignedVoluntaryExit signedExit) {
    if (predicates.isBuilderIndex(signedExit.getMessage().getValidatorIndex())) {
      final UInt64 builderIndex =
          miscHelpers.convertValidatorIndexToBuilderIndex(
              signedExit.getMessage().getValidatorIndex());
      return validateBuilderExit(state, builderIndex);
    }
    return super.validate(fork, state, signedExit);
  }

  protected Optional<OperationInvalidReason> validateBuilderExit(
      final BeaconState state, final UInt64 builderIndex) {
    return firstOf(
        () ->
            check(
                predicates.isActiveBuilder(state, builderIndex),
                ExitInvalidReason.builderInactive()),
        () ->
            check(
                beaconStateAccessors
                    .getPendingBalanceToWithdrawForBuilder(state, builderIndex)
                    .equals(UInt64.ZERO),
                ExitInvalidReason.pendingWithdrawalsInQueueForBuilder()));
  }
}
