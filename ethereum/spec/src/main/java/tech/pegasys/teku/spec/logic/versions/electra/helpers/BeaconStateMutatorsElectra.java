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

package tech.pegasys.teku.spec.logic.versions.electra.helpers;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.spec.config.SpecConfig.FAR_FUTURE_EPOCH;
import static tech.pegasys.teku.spec.constants.WithdrawalPrefixes.COMPOUNDING_WITHDRAWAL_BYTE;

import java.util.function.Supplier;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigBellatrix;
import tech.pegasys.teku.spec.config.SpecConfigElectra;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.MutableBeaconStateElectra;
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingDeposit;
import tech.pegasys.teku.spec.datastructures.type.SszPublicKey;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateAccessors;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateMutators;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;
import tech.pegasys.teku.spec.logic.versions.bellatrix.helpers.BeaconStateMutatorsBellatrix;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsElectra;

public class BeaconStateMutatorsElectra extends BeaconStateMutatorsBellatrix {

  private final BeaconStateAccessorsElectra stateAccessorsElectra;
  private final MiscHelpersElectra miscHelpersElectra;

  private final SpecConfigElectra specConfigElectra;
  private final SchemaDefinitionsElectra schemaDefinitionsElectra;

  public static BeaconStateMutatorsElectra required(final BeaconStateMutators beaconStateMutators) {
    checkArgument(
        beaconStateMutators instanceof BeaconStateMutatorsElectra,
        "Expected %s but it was %s",
        BeaconStateMutatorsElectra.class,
        beaconStateMutators.getClass());
    return (BeaconStateMutatorsElectra) beaconStateMutators;
  }

  public BeaconStateMutatorsElectra(
      final SpecConfig specConfig,
      final MiscHelpers miscHelpers,
      final BeaconStateAccessors beaconStateAccessors,
      final SchemaDefinitionsElectra schemaDefinitionsElectra) {
    super(SpecConfigBellatrix.required(specConfig), miscHelpers, beaconStateAccessors);
    this.stateAccessorsElectra = BeaconStateAccessorsElectra.required(beaconStateAccessors);
    this.miscHelpersElectra = MiscHelpersElectra.required(miscHelpers);
    this.specConfigElectra = SpecConfigElectra.required(specConfig);
    this.schemaDefinitionsElectra = schemaDefinitionsElectra;
  }

  /*
   * <spec function="compute_exit_epoch_and_update_churn" fork="electra">
   * def compute_exit_epoch_and_update_churn(state: BeaconState, exit_balance: Gwei) -> Epoch:
   *     earliest_exit_epoch = max(state.earliest_exit_epoch, compute_activation_exit_epoch(get_current_epoch(state)))
   *     per_epoch_churn = get_activation_exit_churn_limit(state)
   *     # New epoch for exits.
   *     if state.earliest_exit_epoch < earliest_exit_epoch:
   *         exit_balance_to_consume = per_epoch_churn
   *     else:
   *         exit_balance_to_consume = state.exit_balance_to_consume
   *
   *     # Exit doesn't fit in the current earliest epoch.
   *     if exit_balance > exit_balance_to_consume:
   *         balance_to_process = exit_balance - exit_balance_to_consume
   *         additional_epochs = (balance_to_process - 1) // per_epoch_churn + 1
   *         earliest_exit_epoch += additional_epochs
   *         exit_balance_to_consume += additional_epochs * per_epoch_churn
   *
   *     # Consume the balance and update state variables.
   *     state.exit_balance_to_consume = exit_balance_to_consume - exit_balance
   *     state.earliest_exit_epoch = earliest_exit_epoch
   *
   *     return state.earliest_exit_epoch
   * </spec>
   */
  public UInt64 computeExitEpochAndUpdateChurn(
      final MutableBeaconStateElectra state, final UInt64 exitBalance) {
    final UInt64 earliestExitEpoch =
        miscHelpers
            .computeActivationExitEpoch(stateAccessorsElectra.getCurrentEpoch(state))
            .max(state.getEarliestExitEpoch());
    final UInt64 perEpochChurn = stateAccessorsElectra.getActivationExitChurnLimit(state);
    final UInt64 exitBalanceToConsume =
        state.getEarliestExitEpoch().isLessThan(earliestExitEpoch)
            ? perEpochChurn
            : state.getExitBalanceToConsume();

    if (exitBalance.isGreaterThan(exitBalanceToConsume)) {
      final UInt64 balanceToProcess = exitBalance.minusMinZero(exitBalanceToConsume);
      final UInt64 additionalEpochs =
          balanceToProcess.minusMinZero(1).dividedBy(perEpochChurn).increment();
      state.setEarliestExitEpoch(earliestExitEpoch.plus(additionalEpochs));
      state.setExitBalanceToConsume(
          exitBalanceToConsume
              .plus(additionalEpochs.times(perEpochChurn))
              .minusMinZero(exitBalance));
    } else {
      state.setExitBalanceToConsume(exitBalanceToConsume.minusMinZero(exitBalance));
      state.setEarliestExitEpoch(earliestExitEpoch);
    }

    return state.getEarliestExitEpoch();
  }

  /*
   * <spec function="initiate_validator_exit" fork="electra">
   * def initiate_validator_exit(state: BeaconState, index: ValidatorIndex) -> None:
   *     """
   *     Initiate the exit of the validator with index ``index``.
   *     """
   *     # Return if validator already initiated exit
   *     validator = state.validators[index]
   *     if validator.exit_epoch != FAR_FUTURE_EPOCH:
   *         return
   *
   *     # Compute exit queue epoch [Modified in Electra:EIP7251]
   *     exit_queue_epoch = compute_exit_epoch_and_update_churn(state, validator.effective_balance)
   *
   *     # Set validator exit epoch and withdrawable epoch
   *     validator.exit_epoch = exit_queue_epoch
   *     validator.withdrawable_epoch = Epoch(validator.exit_epoch + MIN_VALIDATOR_WITHDRAWABILITY_DELAY)
   * </spec>
   */
  @Override
  public void initiateValidatorExit(
      final MutableBeaconState state,
      final int index,
      final Supplier<ValidatorExitContext> validatorExitContextSupplier) {
    final Validator validator = state.getValidators().get(index);
    // Return if validator already initiated exit
    if (!validator.getExitEpoch().equals(FAR_FUTURE_EPOCH)) {
      return;
    }

    final MutableBeaconStateElectra stateElectra = MutableBeaconStateElectra.required(state);

    final ValidatorExitContext validatorExitContext = validatorExitContextSupplier.get();

    if (validatorExitContext.getExitQueueChurn().compareTo(validatorExitContext.getChurnLimit())
        >= 0) {
      validatorExitContext.setExitQueueEpoch(validatorExitContext.getExitQueueEpoch().increment());
      validatorExitContext.setExitQueueChurn(UInt64.ONE);
    } else {
      validatorExitContext.setExitQueueChurn(validatorExitContext.getExitQueueChurn().increment());
    }

    final UInt64 exitQueueEpoch =
        computeExitEpochAndUpdateChurn(stateElectra, validator.getEffectiveBalance());

    // Set validator exit epoch and withdrawable epoch
    stateElectra
        .getValidators()
        .set(
            index,
            validator
                .withExitEpoch(exitQueueEpoch)
                .withWithdrawableEpoch(
                    exitQueueEpoch.plus(specConfig.getMinValidatorWithdrawabilityDelay())));
  }

  /** compute_consolidation_epoch_and_update_churn */
  /*
   * <spec function="compute_consolidation_epoch_and_update_churn" fork="electra">
   * def compute_consolidation_epoch_and_update_churn(state: BeaconState, consolidation_balance: Gwei) -> Epoch:
   *     earliest_consolidation_epoch = max(
   *         state.earliest_consolidation_epoch, compute_activation_exit_epoch(get_current_epoch(state)))
   *     per_epoch_consolidation_churn = get_consolidation_churn_limit(state)
   *     # New epoch for consolidations.
   *     if state.earliest_consolidation_epoch < earliest_consolidation_epoch:
   *         consolidation_balance_to_consume = per_epoch_consolidation_churn
   *     else:
   *         consolidation_balance_to_consume = state.consolidation_balance_to_consume
   *
   *     # Consolidation doesn't fit in the current earliest epoch.
   *     if consolidation_balance > consolidation_balance_to_consume:
   *         balance_to_process = consolidation_balance - consolidation_balance_to_consume
   *         additional_epochs = (balance_to_process - 1) // per_epoch_consolidation_churn + 1
   *         earliest_consolidation_epoch += additional_epochs
   *         consolidation_balance_to_consume += additional_epochs * per_epoch_consolidation_churn
   *
   *     # Consume the balance and update state variables.
   *     state.consolidation_balance_to_consume = consolidation_balance_to_consume - consolidation_balance
   *     state.earliest_consolidation_epoch = earliest_consolidation_epoch
   *
   *     return state.earliest_consolidation_epoch
   * </spec>
   */
  public UInt64 computeConsolidationEpochAndUpdateChurn(
      final MutableBeaconState state, final UInt64 consolidationBalance) {
    final MutableBeaconStateElectra stateElectra = MutableBeaconStateElectra.required(state);
    final UInt64 epoch = miscHelpers.computeEpochAtSlot(state.getSlot());
    final UInt64 computedActivationExitEpoch = miscHelpersElectra.computeActivationExitEpoch(epoch);
    final UInt64 perEpochConsolidationChurn =
        stateAccessorsElectra.getConsolidationChurnLimit(stateElectra);

    UInt64 earliestConsolidationEpoch =
        stateElectra.getEarliestConsolidationEpoch().max(computedActivationExitEpoch);
    // New epoch for consolidations.
    UInt64 consolidationBalanceToConsume =
        stateElectra.getEarliestConsolidationEpoch().isLessThan(earliestConsolidationEpoch)
            ? perEpochConsolidationChurn
            : stateElectra.getConsolidationBalanceToConsume();

    // Consolidation doesn't fit in the current earliest epoch.
    if (consolidationBalance.isGreaterThan(consolidationBalanceToConsume)) {
      final UInt64 balanceToProcess =
          consolidationBalance.minusMinZero(consolidationBalanceToConsume);
      final UInt64 additionalEpochs =
          balanceToProcess.decrement().dividedBy(perEpochConsolidationChurn).increment();
      earliestConsolidationEpoch = earliestConsolidationEpoch.plus(additionalEpochs);
      consolidationBalanceToConsume =
          consolidationBalanceToConsume.plus(additionalEpochs.times(perEpochConsolidationChurn));
    }

    // Consume the balance and update state variables.
    stateElectra.setConsolidationBalanceToConsume(
        consolidationBalanceToConsume.minusMinZero(consolidationBalance));
    stateElectra.setEarliestConsolidationEpoch(earliestConsolidationEpoch);

    return stateElectra.getEarliestConsolidationEpoch();
  }

  /*
   * <spec function="switch_to_compounding_validator" fork="electra">
   * def switch_to_compounding_validator(state: BeaconState, index: ValidatorIndex) -> None:
   *     validator = state.validators[index]
   *     validator.withdrawal_credentials = COMPOUNDING_WITHDRAWAL_PREFIX + validator.withdrawal_credentials[1:]
   *     queue_excess_active_balance(state, index)
   * </spec>
   */
  public void switchToCompoundingValidator(final MutableBeaconStateElectra state, final int index) {
    final byte[] withdrawalCredentialsUpdated =
        state.getValidators().get(index).getWithdrawalCredentials().toArray();
    withdrawalCredentialsUpdated[0] = COMPOUNDING_WITHDRAWAL_BYTE;
    state
        .getValidators()
        .update(
            index,
            validator ->
                validator.withWithdrawalCredentials(Bytes32.wrap(withdrawalCredentialsUpdated)));
    queueExcessActiveBalance(state, index);
  }

  /*
   * <spec function="queue_excess_active_balance" fork="electra">
   * def queue_excess_active_balance(state: BeaconState, index: ValidatorIndex) -> None:
   *     balance = state.balances[index]
   *     if balance > MIN_ACTIVATION_BALANCE:
   *         excess_balance = balance - MIN_ACTIVATION_BALANCE
   *         state.balances[index] = MIN_ACTIVATION_BALANCE
   *         validator = state.validators[index]
   *         # Use bls.G2_POINT_AT_INFINITY as a signature field placeholder
   *         # and GENESIS_SLOT to distinguish from a pending deposit request
   *         state.pending_deposits.append(PendingDeposit(
   *             pubkey=validator.pubkey,
   *             withdrawal_credentials=validator.withdrawal_credentials,
   *             amount=excess_balance,
   *             signature=bls.G2_POINT_AT_INFINITY,
   *             slot=GENESIS_SLOT,
   *         ))
   * </spec>
   */
  public void queueExcessActiveBalance(
      final MutableBeaconStateElectra state, final int validatorIndex) {
    final UInt64 balance = state.getBalances().get(validatorIndex).get();
    final UInt64 minActivationBalance = specConfigElectra.getMinActivationBalance();

    if (balance.isGreaterThan(minActivationBalance)) {
      final UInt64 excessBalance = balance.minusMinZero(minActivationBalance);
      state.getBalances().set(validatorIndex, SszUInt64.of(minActivationBalance));

      final Validator validator = state.getValidators().get(validatorIndex);
      final PendingDeposit deposit =
          schemaDefinitionsElectra
              .getPendingDepositSchema()
              .create(
                  new SszPublicKey(validator.getPublicKey()),
                  SszBytes32.of(validator.getWithdrawalCredentials()),
                  SszUInt64.of(excessBalance),
                  new SszSignature(BLSSignature.infinity()),
                  SszUInt64.of(SpecConfig.GENESIS_SLOT));

      state.getPendingDeposits().append(deposit);
    }
  }

  // TODO: Join with queueExcessActiveBalance.
  public void queueEntireBalanceAndResetValidator(
      final MutableBeaconStateElectra state, final int validatorIndex) {
    final UInt64 balance = state.getBalances().getElement(validatorIndex);
    state.getBalances().set(validatorIndex, SszUInt64.ZERO);
    state
        .getValidators()
        .update(
            validatorIndex,
            validator ->
                validator
                    .withEffectiveBalance(UInt64.ZERO)
                    .withActivationEligibilityEpoch(FAR_FUTURE_EPOCH));

    final Validator validator = state.getValidators().get(validatorIndex);
    final PendingDeposit deposit =
        schemaDefinitionsElectra
            .getPendingDepositSchema()
            .create(
                new SszPublicKey(validator.getPublicKey()),
                SszBytes32.of(validator.getWithdrawalCredentials()),
                SszUInt64.of(balance),
                new SszSignature(BLSSignature.infinity()),
                SszUInt64.of(SpecConfig.GENESIS_SLOT));

    state.getPendingDeposits().append(deposit);
  }

  @Override
  protected int getWhistleblowerRewardQuotient() {
    return specConfigElectra.getWhistleblowerRewardQuotientElectra();
  }

  @Override
  protected int getMinSlashingPenaltyQuotient() {
    return specConfigElectra.getMinSlashingPenaltyQuotientElectra();
  }
}
