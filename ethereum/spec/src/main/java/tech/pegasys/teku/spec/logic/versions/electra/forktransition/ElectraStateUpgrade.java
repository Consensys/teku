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

package tech.pegasys.teku.spec.logic.versions.electra.forktransition;

import static tech.pegasys.teku.spec.config.SpecConfig.FAR_FUTURE_EPOCH;

import java.util.Comparator;
import java.util.stream.IntStream;
import tech.pegasys.teku.infrastructure.ssz.SszMutableList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfigElectra;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.deneb.BeaconStateDeneb;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.BeaconStateElectra;
import tech.pegasys.teku.spec.logic.common.forktransition.StateUpgrade;
import tech.pegasys.teku.spec.logic.versions.electra.helpers.BeaconStateAccessorsElectra;
import tech.pegasys.teku.spec.logic.versions.electra.helpers.BeaconStateMutatorsElectra;
import tech.pegasys.teku.spec.logic.versions.electra.helpers.MiscHelpersElectra;
import tech.pegasys.teku.spec.logic.versions.electra.helpers.PredicatesElectra;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsElectra;

public class ElectraStateUpgrade implements StateUpgrade<BeaconStateDeneb> {

  private final SpecConfigElectra specConfig;
  private final SchemaDefinitionsElectra schemaDefinitions;
  private final BeaconStateAccessorsElectra beaconStateAccessors;
  private final BeaconStateMutatorsElectra beaconStateMutators;

  public ElectraStateUpgrade(
      final SpecConfigElectra specConfig,
      final SchemaDefinitionsElectra schemaDefinitions,
      final BeaconStateAccessorsElectra beaconStateAccessors,
      final BeaconStateMutatorsElectra beaconStateMutators) {
    this.specConfig = specConfig;
    this.schemaDefinitions = schemaDefinitions;
    this.beaconStateAccessors = beaconStateAccessors;
    this.beaconStateMutators = beaconStateMutators;
  }

  /*
   * <spec function="upgrade_to_electra" fork="electra">
   * def upgrade_to_electra(pre: deneb.BeaconState) -> BeaconState:
   *     epoch = deneb.get_current_epoch(pre)
   *
   *     earliest_exit_epoch = compute_activation_exit_epoch(get_current_epoch(pre))
   *     for validator in pre.validators:
   *         if validator.exit_epoch != FAR_FUTURE_EPOCH:
   *             if validator.exit_epoch > earliest_exit_epoch:
   *                 earliest_exit_epoch = validator.exit_epoch
   *     earliest_exit_epoch += Epoch(1)
   *
   *     post = BeaconState(
   *         # Versioning
   *         genesis_time=pre.genesis_time,
   *         genesis_validators_root=pre.genesis_validators_root,
   *         slot=pre.slot,
   *         fork=Fork(
   *             previous_version=pre.fork.current_version,
   *             current_version=ELECTRA_FORK_VERSION,  # [Modified in Electra:EIP6110]
   *             epoch=epoch,
   *         ),
   *         # History
   *         latest_block_header=pre.latest_block_header,
   *         block_roots=pre.block_roots,
   *         state_roots=pre.state_roots,
   *         historical_roots=pre.historical_roots,
   *         # Eth1
   *         eth1_data=pre.eth1_data,
   *         eth1_data_votes=pre.eth1_data_votes,
   *         eth1_deposit_index=pre.eth1_deposit_index,
   *         # Registry
   *         validators=pre.validators,
   *         balances=pre.balances,
   *         # Randomness
   *         randao_mixes=pre.randao_mixes,
   *         # Slashings
   *         slashings=pre.slashings,
   *         # Participation
   *         previous_epoch_participation=pre.previous_epoch_participation,
   *         current_epoch_participation=pre.current_epoch_participation,
   *         # Finality
   *         justification_bits=pre.justification_bits,
   *         previous_justified_checkpoint=pre.previous_justified_checkpoint,
   *         current_justified_checkpoint=pre.current_justified_checkpoint,
   *         finalized_checkpoint=pre.finalized_checkpoint,
   *         # Inactivity
   *         inactivity_scores=pre.inactivity_scores,
   *         # Sync
   *         current_sync_committee=pre.current_sync_committee,
   *         next_sync_committee=pre.next_sync_committee,
   *         # Execution-layer
   *         latest_execution_payload_header=pre.latest_execution_payload_header,
   *         # Withdrawals
   *         next_withdrawal_index=pre.next_withdrawal_index,
   *         next_withdrawal_validator_index=pre.next_withdrawal_validator_index,
   *         # Deep history valid from Capella onwards
   *         historical_summaries=pre.historical_summaries,
   *         # [New in Electra:EIP6110]
   *         deposit_requests_start_index=UNSET_DEPOSIT_REQUESTS_START_INDEX,
   *         # [New in Electra:EIP7251]
   *         deposit_balance_to_consume=0,
   *         exit_balance_to_consume=0,
   *         earliest_exit_epoch=earliest_exit_epoch,
   *         consolidation_balance_to_consume=0,
   *         earliest_consolidation_epoch=compute_activation_exit_epoch(get_current_epoch(pre)),
   *         pending_deposits=[],
   *         pending_partial_withdrawals=[],
   *         pending_consolidations=[],
   *     )
   *
   *     post.exit_balance_to_consume = get_activation_exit_churn_limit(post)
   *     post.consolidation_balance_to_consume = get_consolidation_churn_limit(post)
   *
   *     # [New in Electra:EIP7251]
   *     # add validators that are not yet active to pending balance deposits
   *     pre_activation = sorted([
   *         index for index, validator in enumerate(post.validators)
   *         if validator.activation_epoch == FAR_FUTURE_EPOCH
   *     ], key=lambda index: (
   *         post.validators[index].activation_eligibility_epoch,
   *         index
   *     ))
   *
   *     for index in pre_activation:
   *         balance = post.balances[index]
   *         post.balances[index] = 0
   *         validator = post.validators[index]
   *         validator.effective_balance = 0
   *         validator.activation_eligibility_epoch = FAR_FUTURE_EPOCH
   *         # Use bls.G2_POINT_AT_INFINITY as a signature field placeholder
   *         # and GENESIS_SLOT to distinguish from a pending deposit request
   *         post.pending_deposits.append(PendingDeposit(
   *             pubkey=validator.pubkey,
   *             withdrawal_credentials=validator.withdrawal_credentials,
   *             amount=balance,
   *             signature=bls.G2_POINT_AT_INFINITY,
   *             slot=GENESIS_SLOT,
   *         ))
   *
   *     # Ensure early adopters of compounding credentials go through the activation churn
   *     for index, validator in enumerate(post.validators):
   *         if has_compounding_withdrawal_credential(validator):
   *             queue_excess_active_balance(post, ValidatorIndex(index))
   *
   *     return post
   * </spec>
   */
  @Override
  public BeaconStateElectra upgrade(final BeaconState preState) {
    final UInt64 epoch = beaconStateAccessors.getCurrentEpoch(preState);
    final BeaconStateDeneb preStateDeneb = BeaconStateDeneb.required(preState);
    final PredicatesElectra predicatesElectra = new PredicatesElectra(specConfig);
    final MiscHelpersElectra miscHelpersElectra =
        new MiscHelpersElectra(specConfig, predicatesElectra, schemaDefinitions);
    final UInt64 activationExitEpoch = miscHelpersElectra.computeActivationExitEpoch(epoch);
    return BeaconStateElectra.required(schemaDefinitions.getBeaconStateSchema().createEmpty())
        .updatedElectra(
            state -> {
              BeaconStateFields.copyCommonFieldsFromSource(state, preState);

              state.setCurrentEpochParticipation(preStateDeneb.getCurrentEpochParticipation());
              state.setPreviousEpochParticipation(preStateDeneb.getPreviousEpochParticipation());
              state.setCurrentSyncCommittee(preStateDeneb.getCurrentSyncCommittee());
              state.setNextSyncCommittee(preStateDeneb.getNextSyncCommittee());
              state.setInactivityScores(preStateDeneb.getInactivityScores());

              state.setFork(
                  new Fork(
                      preState.getFork().getCurrentVersion(),
                      specConfig.getElectraForkVersion(),
                      epoch));

              state.setLatestExecutionPayloadHeader(
                  preStateDeneb.getLatestExecutionPayloadHeader());
              state.setNextWithdrawalValidatorIndex(
                  preStateDeneb.getNextWithdrawalValidatorIndex());
              state.setNextWithdrawalIndex(preStateDeneb.getNextWithdrawalIndex());
              state.setHistoricalSummaries(preStateDeneb.getHistoricalSummaries());
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
                        if (predicatesElectra.hasCompoundingWithdrawalCredential(
                            validators.get(index))) {
                          beaconStateMutators.queueExcessActiveBalance(state, index);
                        }
                      });
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
