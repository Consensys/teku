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

package tech.pegasys.teku.spec.logic.common.operations.validation;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import tech.pegasys.teku.infrastructure.ssz.SszContainer;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateAccessors;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;
import tech.pegasys.teku.spec.logic.common.helpers.Predicates;
import tech.pegasys.teku.spec.logic.common.util.AttestationUtil;

public class OperationValidators {

  private final Map<Class<? extends SszContainer>, OperationStateTransitionValidator<?>>
      validatorsMap = new HashMap<>();

  public static OperationValidators create(
      final SpecConfig specConfig,
      final Predicates predicates,
      final MiscHelpers miscHelpers,
      final BeaconStateAccessors beaconStateAccessors,
      final AttestationUtil attestationUtil) {
    final AttestationDataValidator attestationDataValidator =
        new AttestationDataValidator(specConfig, miscHelpers, beaconStateAccessors);
    final AttesterSlashingValidator attesterSlashingValidator =
        new AttesterSlashingValidator(predicates, beaconStateAccessors, attestationUtil);
    final ProposerSlashingValidator proposerSlashingValidator =
        new ProposerSlashingValidator(predicates, beaconStateAccessors);
    final VoluntaryExitValidator voluntaryExitValidator =
        new VoluntaryExitValidator(specConfig, predicates, beaconStateAccessors);

    final OperationValidators operationValidator = new OperationValidators();

    operationValidator.validatorsMap.put(AttestationData.class, attestationDataValidator);
    operationValidator.validatorsMap.put(AttesterSlashing.class, attesterSlashingValidator);
    operationValidator.validatorsMap.put(ProposerSlashing.class, proposerSlashingValidator);
    operationValidator.validatorsMap.put(SignedVoluntaryExit.class, voluntaryExitValidator);

    return operationValidator;
  }

  public <T> Optional<OperationInvalidReason> validate(
      final Fork fork, final BeaconState beaconState, final T operation) {
    return getValidatorForOperation(operation).validate(fork, beaconState, operation);
  }

  @SuppressWarnings("unchecked")
  public <T extends OperationStateTransitionValidator<?>> T getValidatorOfType(Class<T> type) {
    return (T)
        validatorsMap.values().stream()
            .filter(v -> type.equals(v.getClass()))
            .findFirst()
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "No OperationStateTransitionValidator of type "
                            + type.getSimpleName()
                            + " found. Did you register a validator of this type on "
                            + getClass().getSimpleName()
                            + "?"));
  }

  @SuppressWarnings({"unchecked", "TypeParameterUnusedInFormals"})
  private <T extends OperationStateTransitionValidator<O>, O> T getValidatorForOperation(
      O operation) {
    final OperationStateTransitionValidator<?> validator = validatorsMap.get(operation.getClass());

    if (validator == null) {
      throw new IllegalStateException(
          "No OperationStateTransitionValidator for operation "
              + operation.getClass().getSimpleName()
              + ". Did you register a validator for this operation on "
              + getClass().getSimpleName()
              + "?");
    }

    return (T) validator;
  }

  public static void main(String[] args) {
    System.out.println("Lucas");
  }
}
