/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.spec.logic.versions.gloas.execution;

import tech.pegasys.teku.infrastructure.ssz.SszMutableList;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.spec.config.SpecConfigGloas;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.DepositRequest;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.MutableBeaconStateElectra;
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingDeposit;
import tech.pegasys.teku.spec.datastructures.type.SszPublicKey;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;
import tech.pegasys.teku.spec.logic.common.util.ValidatorsUtil;
import tech.pegasys.teku.spec.logic.versions.electra.execution.ExecutionRequestsProcessorElectra;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.BeaconStateAccessorsGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.BeaconStateMutatorsGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.MiscHelpersGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.PredicatesGloas;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;

public class ExecutionRequestsProcessorGloas extends ExecutionRequestsProcessorElectra {

  private final PredicatesGloas predicatesGloas;
  private final BeaconStateMutatorsGloas beaconStateMutatorsGloas;
  private final BeaconStateAccessorsGloas beaconStateAccessorsGloas;

  public ExecutionRequestsProcessorGloas(
      final SchemaDefinitionsGloas schemaDefinitions,
      final MiscHelpersGloas miscHelpers,
      final SpecConfigGloas specConfig,
      final PredicatesGloas predicates,
      final ValidatorsUtil validatorsUtil,
      final BeaconStateMutatorsGloas beaconStateMutators,
      final BeaconStateAccessorsGloas beaconStateAccessors) {
    super(
        schemaDefinitions,
        miscHelpers,
        specConfig,
        predicates,
        validatorsUtil,
        beaconStateMutators,
        beaconStateAccessors);
    this.predicatesGloas = predicates;
    this.beaconStateMutatorsGloas = beaconStateMutators;
    this.beaconStateAccessorsGloas = beaconStateAccessors;
  }

  @Override
  protected void processDepositRequest(
      final MutableBeaconStateElectra state, final DepositRequest depositRequest) {
    // Regardless of the withdrawal credentials prefix, if a builder/validator already exists with
    // this pubkey, apply the deposit to their balance
    final boolean isBuilder =
        beaconStateAccessorsGloas.getBuilderIndex(state, depositRequest.getPubkey()).isPresent();
    final boolean isValidator =
        validatorsUtil.getValidatorIndex(state, depositRequest.getPubkey()).isPresent();
    final boolean isBuilderPrefix =
        predicatesGloas.isBuilderWithdrawalCredential(depositRequest.getWithdrawalCredentials());
    if (isBuilder || (isBuilderPrefix && !isValidator)) {
      // Apply builder deposits immediately
      beaconStateMutatorsGloas.applyDepositForBuilder(
          state,
          depositRequest.getPubkey(),
          depositRequest.getWithdrawalCredentials(),
          depositRequest.getAmount(),
          depositRequest.getSignature());
      return;
    }
    // Add validator deposits to the queue
    final SszMutableList<PendingDeposit> pendingDeposits = state.getPendingDeposits();
    final PendingDeposit deposit =
        schemaDefinitions
            .getPendingDepositSchema()
            .create(
                new SszPublicKey(depositRequest.getPubkey()),
                SszBytes32.of(depositRequest.getWithdrawalCredentials()),
                SszUInt64.of(depositRequest.getAmount()),
                new SszSignature(depositRequest.getSignature()),
                SszUInt64.of(state.getSlot()));
    pendingDeposits.append(deposit);
  }
}
