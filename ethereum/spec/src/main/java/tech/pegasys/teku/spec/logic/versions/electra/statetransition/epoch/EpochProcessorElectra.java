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

package tech.pegasys.teku.spec.logic.versions.electra.statetransition.epoch;

import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;
import static tech.pegasys.teku.spec.config.SpecConfig.FAR_FUTURE_EPOCH;
import static tech.pegasys.teku.spec.config.SpecConfig.GENESIS_SLOT;
import static tech.pegasys.teku.spec.logic.common.block.AbstractBlockProcessor.depositSignatureVerifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.impl.BlsException;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.SszMutableList;
import tech.pegasys.teku.infrastructure.ssz.collections.SszUInt64List;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszByte;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigElectra;
import tech.pegasys.teku.spec.constants.Domain;
import tech.pegasys.teku.spec.datastructures.operations.DepositMessage;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateCache;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.MutableBeaconStateElectra;
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingConsolidation;
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingDeposit;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateMutators;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.status.ValidatorStatus;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.status.ValidatorStatusFactory;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.status.ValidatorStatuses;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.EpochProcessingException;
import tech.pegasys.teku.spec.logic.common.util.BeaconStateUtil;
import tech.pegasys.teku.spec.logic.common.util.ValidatorsUtil;
import tech.pegasys.teku.spec.logic.versions.altair.helpers.BeaconStateAccessorsAltair;
import tech.pegasys.teku.spec.logic.versions.capella.statetransition.epoch.EpochProcessorCapella;
import tech.pegasys.teku.spec.logic.versions.electra.helpers.BeaconStateAccessorsElectra;
import tech.pegasys.teku.spec.logic.versions.electra.helpers.MiscHelpersElectra;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsElectra;

public class EpochProcessorElectra extends EpochProcessorCapella {

  private final UInt64 minActivationBalance;
  private final BeaconStateAccessorsElectra stateAccessorsElectra;
  private final SchemaDefinitionsElectra schemaDefinitionsElectra;

  public EpochProcessorElectra(
      final SpecConfigElectra specConfig,
      final MiscHelpersElectra miscHelpers,
      final BeaconStateAccessorsAltair beaconStateAccessors,
      final BeaconStateMutators beaconStateMutators,
      final ValidatorsUtil validatorsUtil,
      final BeaconStateUtil beaconStateUtil,
      final ValidatorStatusFactory validatorStatusFactory,
      final SchemaDefinitions schemaDefinitions,
      final TimeProvider timeProvider) {
    super(
        specConfig,
        miscHelpers,
        beaconStateAccessors,
        beaconStateMutators,
        validatorsUtil,
        beaconStateUtil,
        validatorStatusFactory,
        schemaDefinitions,
        timeProvider);
    this.minActivationBalance =
        specConfig.toVersionElectra().orElseThrow().getMinActivationBalance();
    this.stateAccessorsElectra = BeaconStateAccessorsElectra.required(beaconStateAccessors);
    this.schemaDefinitionsElectra = SchemaDefinitionsElectra.required(schemaDefinitions);
  }

  /** process_registry_updates */
  @Override
  public void processRegistryUpdates(
      final MutableBeaconState state, final List<ValidatorStatus> statuses)
      throws EpochProcessingException {
    try {

      // Process activation eligibility and ejections
      final SszMutableList<Validator> validators = state.getValidators();
      final UInt64 currentEpoch = stateAccessorsElectra.getCurrentEpoch(state);
      final UInt64 finalizedEpoch = state.getFinalizedCheckpoint().getEpoch();
      final UInt64 ejectionBalance = specConfig.getEjectionBalance();
      final Supplier<BeaconStateMutators.ValidatorExitContext> validatorExitContextSupplier =
          beaconStateMutators.createValidatorExitContextSupplier(state);

      final UInt64 activationEpoch =
          miscHelpers.computeActivationExitEpoch(miscHelpers.computeEpochAtSlot(state.getSlot()));

      for (int index = 0; index < validators.size(); index++) {
        final ValidatorStatus status = statuses.get(index);

        if (isEligibleForActivationQueue(status)) {
          final Validator validator = validators.get(index);
          if (validator.getActivationEligibilityEpoch().equals(SpecConfig.FAR_FUTURE_EPOCH)) {
            state
                .getValidators()
                .update(
                    index, v -> v.withActivationEligibilityEpoch(currentEpoch.plus(UInt64.ONE)));
          }
        } else if (status.isActiveInCurrentEpoch()
            && status.getCurrentEpochEffectiveBalance().isLessThanOrEqualTo(ejectionBalance)) {
          beaconStateMutators.initiateValidatorExit(state, index, validatorExitContextSupplier);
        }
        // activate all eligible validators
        final Validator validator = validators.get(index);
        if (isEligibleForActivation(finalizedEpoch, validator)) {
          state.getValidators().update(index, v -> v.withActivationEpoch(activationEpoch));
        }
      }
    } catch (IllegalArgumentException e) {
      throw new EpochProcessingException(e);
    }
  }

  protected boolean isEligibleForActivation(
      final UInt64 finalizedEpoch, final Validator validator) {
    return validator.getActivationEpoch().equals(FAR_FUTURE_EPOCH)
        && validator.getActivationEligibilityEpoch().isLessThanOrEqualTo(finalizedEpoch);
  }

  /**
   * is_eligible_for_activation_queue
   *
   * @param status - Validator status
   */
  @Override
  protected boolean isEligibleForActivationQueue(final ValidatorStatus status) {
    return !status.isActiveInCurrentEpoch()
        && status.getCurrentEpochEffectiveBalance().isGreaterThanOrEqualTo(minActivationBalance);
  }

  @Override
  protected UInt64 getEffectiveBalanceLimitForValidator(final Validator validator) {
    return miscHelpers.getMaxEffectiveBalance(validator);
  }

  // process_effective_balance_updates
  @Override
  public void processEffectiveBalanceUpdates(
      final MutableBeaconState state, final List<ValidatorStatus> statuses) {
    // Update effective balances with hysteresis
    final SszMutableList<Validator> validators = state.getValidators();
    final SszUInt64List balances = state.getBalances();
    final UInt64 hysteresisUpwardMultiplier = specConfig.getHysteresisUpwardMultiplier();
    final UInt64 hysteresisDownwardMultiplier = specConfig.getHysteresisDownwardMultiplier();
    final UInt64 hysteresisQuotient = specConfig.getHysteresisQuotient();
    final UInt64 effectiveBalanceIncrement = specConfig.getEffectiveBalanceIncrement();
    for (int index = 0; index < statuses.size(); index++) {
      final ValidatorStatus status = statuses.get(index);
      final UInt64 balance = balances.getElement(index);

      final UInt64 hysteresisIncrement = effectiveBalanceIncrement.dividedBy(hysteresisQuotient);
      final UInt64 currentEffectiveBalance = status.getCurrentEpochEffectiveBalance();
      final Validator validator = validators.get(index);
      final UInt64 maxEffectiveBalance = getEffectiveBalanceLimitForValidator(validator);
      if (shouldDecreaseEffectiveBalance(
              balance, hysteresisIncrement, currentEffectiveBalance, hysteresisDownwardMultiplier)
          || shouldIncreaseEffectiveBalance(
              balance,
              hysteresisIncrement,
              currentEffectiveBalance,
              hysteresisUpwardMultiplier,
              maxEffectiveBalance)) {
        final UInt64 effectiveBalanceLimit = getEffectiveBalanceLimitForValidator(validator);
        final UInt64 newEffectiveBalance =
            effectiveBalanceLimit.min(
                balance.minus(balance.mod(effectiveBalanceIncrement)).min(maxEffectiveBalance));
        BeaconStateCache.getTransitionCaches(state)
            .getProgressiveTotalBalances()
            .onEffectiveBalanceChange(status, newEffectiveBalance);
        validators.set(index, validator.withEffectiveBalance(newEffectiveBalance));
      }
    }
  }

  /** apply_pending_deposit */
  @Override
  public void applyPendingDeposits(final MutableBeaconState state, final PendingDeposit deposit) {
    validatorsUtil
        .getValidatorIndex(state, deposit.getPublicKey())
        .ifPresentOrElse(
            validatorIndex ->
                beaconStateMutators.increaseBalance(state, validatorIndex, deposit.getAmount()),
            () -> {
              if (isValidDepositSignature(deposit)) {
                addValidatorToRegistry(
                    state,
                    deposit.getPublicKey(),
                    deposit.getWithdrawalCredentials(),
                    deposit.getAmount());
              }
            });
  }

  // TODO-lucas Duplicates method isValidDepositSignature from BlockProcessor
  /** is_valid_deposit_signature */
  public boolean isValidDepositSignature(final PendingDeposit deposit) {
    try {
      return depositSignatureVerifier.verify(
          deposit.getPublicKey(),
          computeDepositSigningRoot(
              deposit.getPublicKey(), deposit.getWithdrawalCredentials(), deposit.getAmount()),
          deposit.getSignature());
    } catch (final BlsException e) {
      return false;
    }
  }

  private Bytes computeDepositSigningRoot(
      final BLSPublicKey pubkey, final Bytes32 withdrawalCredentials, final UInt64 amount) {
    final Bytes32 domain = miscHelpers.computeDomain(Domain.DEPOSIT);
    final DepositMessage depositMessage = new DepositMessage(pubkey, withdrawalCredentials, amount);
    return miscHelpers.computeSigningRoot(depositMessage, domain);
  }

  // TODO-lucas Duplicates method addValidatorToRegistry from BlockProcessor
  /** add_validator_to_registry */
  public void addValidatorToRegistry(
      final MutableBeaconState state,
      final BLSPublicKey pubkey,
      final Bytes32 withdrawalCredentials,
      final UInt64 amount) {
    final Validator validator = getValidatorFromDeposit(pubkey, withdrawalCredentials, amount);

    final MutableBeaconStateElectra stateElectra = MutableBeaconStateElectra.required(state);
    stateElectra.getValidators().append(validator);
    stateElectra.getBalances().appendElement(amount);
    stateElectra.getPreviousEpochParticipation().append(SszByte.ZERO);
    stateElectra.getCurrentEpochParticipation().append(SszByte.ZERO);
    stateElectra.getInactivityScores().append(SszUInt64.ZERO);
  }

  /** get_validator_from_deposit */
  private Validator getValidatorFromDeposit(
      final BLSPublicKey pubkey, final Bytes32 withdrawalCredentials, final UInt64 amount) {
    final Validator validator =
        new Validator(
            pubkey,
            withdrawalCredentials,
            ZERO,
            false,
            FAR_FUTURE_EPOCH,
            FAR_FUTURE_EPOCH,
            FAR_FUTURE_EPOCH,
            FAR_FUTURE_EPOCH);

    final UInt64 maxEffectiveBalance = miscHelpers.getMaxEffectiveBalance(validator);
    final UInt64 validatorEffectiveBalance =
        amount
            .minusMinZero(amount.mod(specConfig.getEffectiveBalanceIncrement()))
            .min(maxEffectiveBalance);

    return validator.withEffectiveBalance(validatorEffectiveBalance);
  }

  /** process_pending_deposits */
  @Override
  public void processPendingDeposits(final MutableBeaconState state) {
    final MutableBeaconStateElectra stateElectra = MutableBeaconStateElectra.required(state);

    final UInt64 nextEpoch = beaconStateAccessors.getCurrentEpoch(state).plus(UInt64.ONE);
    final UInt64 availableForProcessing =
        stateElectra
            .getDepositBalanceToConsume()
            .plus(stateAccessorsElectra.getActivationExitChurnLimit(stateElectra));
    UInt64 processedAmount = UInt64.ZERO;
    int nextDepositIndex = 0;
    final List<PendingDeposit> depositsToPostpone = new ArrayList<>();
    boolean isChurnLimitReached = false;
    final UInt64 finalizedSlot =
        miscHelpers.computeStartSlotAtEpoch(stateElectra.getFinalizedCheckpoint().getEpoch());

    for (final PendingDeposit deposit : stateElectra.getPendingDeposits()) {
      // Do not process deposit requests if Eth1 bridge deposits are not yet applied.
      final boolean isDepositRequest = deposit.getSlot().isGreaterThan(GENESIS_SLOT);
      final boolean hasPendingEth1BridgeDeposits =
          stateElectra
              .getEth1DepositIndex()
              .isLessThan(stateElectra.getDepositRequestsStartIndex());
      if (isDepositRequest && hasPendingEth1BridgeDeposits) {
        break;
      }

      // Check if deposit has been finalized, otherwise stop processing
      if (deposit.getSlot().isGreaterThan(finalizedSlot)) {
        break;
      }

      // Check if number of processed deposits has not reached the limit, otherwise, stop processing
      if (nextDepositIndex
          >= SpecConfigElectra.required(specConfig).getMaxPendingDepositsPerEpoch()) {
        break;
      }

      final Optional<Integer> maybeValidatorIndex =
          validatorsUtil.getValidatorIndex(state, deposit.getPublicKey());
      boolean isValidatorExited = false;
      boolean isValidatorWithdrawn = false;
      if (maybeValidatorIndex.isPresent()) {
        Validator validator = state.getValidators().get(maybeValidatorIndex.get());
        isValidatorExited = validator.getExitEpoch().isLessThan(FAR_FUTURE_EPOCH);
        isValidatorWithdrawn = validator.getWithdrawableEpoch().isLessThan(nextEpoch);
      }

      if (isValidatorWithdrawn) {
        // Deposited balance will never become active. Increase balance but do not consume churn
        applyPendingDeposits(state, deposit);
      } else if (isValidatorExited) {
        // Validator is exiting, postpone the deposit until after withdrawable epoch
        depositsToPostpone.add(deposit);
      } else {
        // Check if deposit fits in the churn, otherwise, do no more deposit processing in this
        // epoch
        isChurnLimitReached =
            processedAmount.plus(deposit.getAmount()).isGreaterThan(availableForProcessing);
        if (isChurnLimitReached) {
          break;
        }
        // Consume churn and apply deposit
        processedAmount = processedAmount.plus(deposit.getAmount());
        applyPendingDeposits(state, deposit);
      }

      // Regardless of how the deposit was handled, we move on in the queue
      nextDepositIndex += 1;
    }

    final SszMutableList<PendingDeposit> pendingDeposits = stateElectra.getPendingDeposits();
    final ArrayList<PendingDeposit> newPendingDeposits = new ArrayList<>();
    IntStream.range(nextDepositIndex, pendingDeposits.size())
        .sorted()
        .forEach(index -> newPendingDeposits.add(pendingDeposits.get(index)));
    newPendingDeposits.addAll(depositsToPostpone);
    stateElectra.setPendingDeposits(
        schemaDefinitionsElectra.getPendingDepositsSchema().createFromElements(newPendingDeposits));

    // Accumulate churn only if the churn limit has been hit
    if (isChurnLimitReached) {
      stateElectra.setDepositBalanceToConsume(availableForProcessing.minusMinZero(processedAmount));
    } else {
      stateElectra.setDepositBalanceToConsume(UInt64.ZERO);
    }
  }

  /** process_pending_consolidations */
  @Override
  public void processPendingConsolidations(final MutableBeaconState state) {
    final MutableBeaconStateElectra stateElectra = MutableBeaconStateElectra.required(state);
    int nextPendingBalanceConsolidation = 0;
    final SszList<PendingConsolidation> pendingConsolidations =
        stateElectra.getPendingConsolidations();
    final UInt64 nextEpoch = stateAccessorsElectra.getCurrentEpoch(state).plus(1L);

    for (final PendingConsolidation pendingConsolidation : pendingConsolidations) {
      final Validator sourceValidator =
          state.getValidators().get(pendingConsolidation.getSourceIndex());
      if (sourceValidator.isSlashed()) {
        nextPendingBalanceConsolidation++;
        continue;
      }
      if (sourceValidator.getWithdrawableEpoch().isGreaterThan(nextEpoch)) {
        break;
      }

      final UInt64 activeBalance =
          stateAccessorsElectra.getActiveBalance(state, pendingConsolidation.getSourceIndex());
      beaconStateMutators.decreaseBalance(
          state, pendingConsolidation.getSourceIndex(), activeBalance);
      beaconStateMutators.increaseBalance(
          state, pendingConsolidation.getTargetIndex(), activeBalance);

      nextPendingBalanceConsolidation++;
    }
    if (pendingConsolidations.size() <= nextPendingBalanceConsolidation) {
      stateElectra.setPendingConsolidations(
          schemaDefinitionsElectra.getPendingConsolidationsSchema().createFromElements(List.of()));
    } else {
      final List<PendingConsolidation> newList =
          pendingConsolidations
              .asList()
              .subList(nextPendingBalanceConsolidation, pendingConsolidations.size());
      stateElectra.setPendingConsolidations(
          schemaDefinitionsElectra.getPendingConsolidationsSchema().createFromElements(newList));
    }
  }

  /** Processes slashings */
  @Override
  public void processSlashings(
      final MutableBeaconState state, final ValidatorStatuses validatorStatuses) {
    final UInt64 totalBalance =
        validatorStatuses.getTotalBalances().getCurrentEpochActiveValidators();
    final UInt64 epoch = beaconStateAccessors.getCurrentEpoch(state);
    final UInt64 adjustedTotalSlashingBalance =
        state
            .getSlashings()
            .streamUnboxed()
            .reduce(ZERO, UInt64::plus)
            .times(getProportionalSlashingMultiplier())
            .min(totalBalance);

    final List<ValidatorStatus> validatorStatusList = validatorStatuses.getStatuses();
    final int halfEpochsPerSlashingsVector = specConfig.getEpochsPerSlashingsVector() / 2;

    final UInt64 increment = specConfig.getEffectiveBalanceIncrement();
    final UInt64 penaltyPerEffectiveBalanceIncrement =
        adjustedTotalSlashingBalance.dividedBy(totalBalance.dividedBy(increment));
    for (int index = 0; index < validatorStatusList.size(); index++) {
      final ValidatorStatus status = validatorStatusList.get(index);
      if (status.isSlashed()
          && epoch.plus(halfEpochsPerSlashingsVector).equals(status.getWithdrawableEpoch())) {

        // EIP-7251
        final UInt64 effectiveBalanceIncrements =
            status.getCurrentEpochEffectiveBalance().dividedBy(increment);
        final UInt64 penalty =
            penaltyPerEffectiveBalanceIncrement.times(effectiveBalanceIncrements);
        beaconStateMutators.decreaseBalance(state, index, penalty);
      }
    }
  }
}
