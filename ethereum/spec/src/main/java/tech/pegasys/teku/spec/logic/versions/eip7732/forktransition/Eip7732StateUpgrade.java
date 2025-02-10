/*
 * Copyright Consensys Software Inc., 2024
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

package tech.pegasys.teku.spec.logic.versions.eip7732.forktransition;

import static tech.pegasys.teku.spec.config.SpecConfig.FAR_FUTURE_EPOCH;

import java.util.Comparator;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.SszMutableList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfigEip7732;
import tech.pegasys.teku.spec.config.SpecConfigElectra;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.eip7732.BeaconStateEip7732;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.BeaconStateElectra;
import tech.pegasys.teku.spec.logic.common.forktransition.StateUpgrade;
import tech.pegasys.teku.spec.logic.versions.eip7732.helpers.BeaconStateAccessorsEip7732;
import tech.pegasys.teku.spec.logic.versions.eip7732.helpers.MiscHelpersEip7732;
import tech.pegasys.teku.spec.logic.versions.eip7732.helpers.PredicatesEip7732;
import tech.pegasys.teku.spec.logic.versions.electra.helpers.BeaconStateMutatorsElectra;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsEip7732;

public class Eip7732StateUpgrade implements StateUpgrade<BeaconStateElectra> {

  private final SpecConfigEip7732 specConfig;
  private final SchemaDefinitionsEip7732 schemaDefinitions;
  private final BeaconStateAccessorsEip7732 beaconStateAccessors;
  private final BeaconStateMutatorsElectra beaconStateMutators;

  public Eip7732StateUpgrade(
      final SpecConfigEip7732 specConfig,
      final SchemaDefinitionsEip7732 schemaDefinitions,
      final BeaconStateAccessorsEip7732 beaconStateAccessors,
      final BeaconStateMutatorsElectra beaconStateMutators) {
    this.specConfig = specConfig;
    this.schemaDefinitions = schemaDefinitions;
    this.beaconStateAccessors = beaconStateAccessors;
    this.beaconStateMutators = beaconStateMutators;
  }

  @Override
  public BeaconStateEip7732 upgrade(final BeaconState preState) {
    final UInt64 epoch = beaconStateAccessors.getCurrentEpoch(preState);
    final BeaconStateElectra preStateElectra = BeaconStateElectra.required(preState);
    final PredicatesEip7732 predicatesEip7732 = new PredicatesEip7732(specConfig);
    final MiscHelpersEip7732 miscHelpersEip7732 =
        new MiscHelpersEip7732(specConfig, predicatesEip7732, schemaDefinitions);
    final UInt64 activationExitEpoch = miscHelpersEip7732.computeActivationExitEpoch(epoch);
    return BeaconStateEip7732.required(schemaDefinitions.getBeaconStateSchema().createEmpty())
        .updatedEip7732(
            state -> {
              BeaconStateFields.copyCommonFieldsFromSource(state, preState);

              state.setCurrentEpochParticipation(preStateElectra.getCurrentEpochParticipation());
              state.setPreviousEpochParticipation(preStateElectra.getPreviousEpochParticipation());
              state.setCurrentSyncCommittee(preStateElectra.getCurrentSyncCommittee());
              state.setNextSyncCommittee(preStateElectra.getNextSyncCommittee());
              state.setInactivityScores(preStateElectra.getInactivityScores());

              state.setFork(
                  new Fork(
                      preState.getFork().getCurrentVersion(),
                      specConfig.getEip7732ForkVersion(),
                      epoch));

              final ExecutionPayloadHeader upgradedExecutionPayloadHeader =
                  schemaDefinitions
                      .getExecutionPayloadHeaderSchema()
                      .createExecutionPayloadHeader(
                          builder ->
                              builder
                                  .parentBlockHash(() -> Bytes32.ZERO)
                                  .parentBlockRoot(() -> Bytes32.ZERO)
                                  .blockHash(
                                      preStateElectra
                                          .getLatestExecutionPayloadHeader()
                                          .getBlockHash())
                                  .gasLimit(UInt64.ZERO)
                                  .builderIndex(() -> UInt64.ZERO)
                                  .slot(() -> UInt64.ZERO)
                                  .value(() -> UInt64.ZERO)
                                  .blobKzgCommitmentsRoot(() -> Bytes32.ZERO));

              state.setLatestExecutionPayloadHeader(upgradedExecutionPayloadHeader);

              state.setNextWithdrawalValidatorIndex(
                  preStateElectra.getNextWithdrawalValidatorIndex());
              state.setNextWithdrawalIndex(preStateElectra.getNextWithdrawalIndex());
              state.setHistoricalSummaries(preStateElectra.getHistoricalSummaries());
              state.setDepositRequestsStartIndex(
                  SpecConfigElectra.UNSET_DEPOSIT_REQUESTS_START_INDEX);
              state.setDepositBalanceToConsume(UInt64.ZERO);
              state.setExitBalanceToConsume(
                  beaconStateAccessors.getActivationExitChurnLimit(state));
              state.setEarliestExitEpoch(findEarliestExitEpoch(state, activationExitEpoch));
              state.setConsolidationBalanceToConsume(
                  beaconStateAccessors.getConsolidationChurnLimit(state));
              state.setEarliestConsolidationEpoch(activationExitEpoch);

              final SszMutableList<Validator> validators = state.getValidators();

              // Add validators that are not yet active to pending balance deposits
              IntStream.range(0, validators.size())
                  .filter(
                      index -> validators.get(index).getActivationEpoch().equals(FAR_FUTURE_EPOCH))
                  .boxed()
                  .sorted(
                      Comparator.comparing(
                              (Integer index) ->
                                  validators.get(index).getActivationEligibilityEpoch())
                          .thenComparing(index -> index))
                  .forEach(
                      index ->
                          beaconStateMutators.queueEntireBalanceAndResetValidator(state, index));

              // Ensure early adopters of compounding credentials go through the activation churn
              IntStream.range(0, validators.size())
                  .forEach(
                      index -> {
                        if (predicatesEip7732.hasCompoundingWithdrawalCredential(
                            validators.get(index))) {
                          beaconStateMutators.queueExcessActiveBalance(state, index);
                        }
                      });

              // ePBS
              state.setLatestBlockHash(
                  preStateElectra.getLatestExecutionPayloadHeader().getBlockHash());
              state.setLatestFullSlot(preState.getSlot());
              state.setLatestWithdrawalsRoot(Bytes32.ZERO);
            });
  }

  private UInt64 findEarliestExitEpoch(final BeaconState state, final UInt64 activationExitEpoch) {
    final UInt64 maxExitEpochFromValidatorSet =
        state.getValidators().stream()
            .map(Validator::getExitEpoch)
            .filter(exitEpoch -> !exitEpoch.equals(FAR_FUTURE_EPOCH))
            .max(UInt64::compareTo)
            .orElse(UInt64.ZERO);
    return maxExitEpochFromValidatorSet.max(activationExitEpoch).increment();
  }
}
