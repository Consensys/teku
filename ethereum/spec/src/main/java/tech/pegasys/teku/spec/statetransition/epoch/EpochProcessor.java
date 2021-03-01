/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.spec.statetransition.epoch;

import static tech.pegasys.teku.spec.datastructures.util.ValidatorsUtil.decrease_balance;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import tech.pegasys.teku.independent.TotalBalances;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.constants.SpecConstants;
import tech.pegasys.teku.spec.datastructures.state.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.HistoricalBatch;
import tech.pegasys.teku.spec.datastructures.state.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.statetransition.epoch.status.ValidatorStatus;
import tech.pegasys.teku.spec.statetransition.epoch.status.ValidatorStatuses;
import tech.pegasys.teku.spec.statetransition.exceptions.EpochProcessingException;
import tech.pegasys.teku.spec.util.BeaconStateUtil;
import tech.pegasys.teku.spec.util.ValidatorsUtil;
import tech.pegasys.teku.ssz.SSZTypes.SSZList;
import tech.pegasys.teku.ssz.SSZTypes.SSZMutableList;
import tech.pegasys.teku.ssz.backing.SszList;
import tech.pegasys.teku.ssz.backing.SszMutableList;
import tech.pegasys.teku.ssz.backing.collections.SszBitvector;

public class EpochProcessor {
  private final SpecConstants specConstants;
  private final ValidatorsUtil validatorsUtil;
  private final BeaconStateUtil beaconStateUtil;

  public EpochProcessor(
      final SpecConstants specConstants,
      final ValidatorsUtil validatorsUtil,
      final BeaconStateUtil beaconStateUtil) {
    this.specConstants = specConstants;
    this.validatorsUtil = validatorsUtil;
    this.beaconStateUtil = beaconStateUtil;
  }

  /**
   * Processes epoch
   *
   * @param preState state prior to epoch transition
   * @throws EpochProcessingException if processing fails
   */
  public BeaconState processEpoch(final BeaconState preState) throws EpochProcessingException {
    final ValidatorStatuses validatorStatuses = ValidatorStatuses.create(preState);
    return preState.updated(
        state -> {
          processJustificationAndFinalization(state, validatorStatuses.getTotalBalances());
          processRewardsAndPenalties(state, validatorStatuses);
          processRegistryUpdates(state, validatorStatuses.getStatuses());
          processSlashings(state, validatorStatuses.getTotalBalances().getCurrentEpoch());
          processFinalUpdates(state);
        });
  }

  /**
   * Processes justification and finalization
   *
   * @param state
   * @param totalBalances
   * @throws EpochProcessingException
   */
  public void processJustificationAndFinalization(
      MutableBeaconState state, TotalBalances totalBalances) throws EpochProcessingException {
    try {
      UInt64 currentEpoch = beaconStateUtil.getCurrentEpoch(state);
      if (currentEpoch.isLessThanOrEqualTo(SpecConstants.GENESIS_EPOCH.plus(1))) {
        return;
      }

      Checkpoint oldPreviousJustifiedCheckpoint = state.getPrevious_justified_checkpoint();
      Checkpoint oldCurrentJustifiedCheckpoint = state.getCurrent_justified_checkpoint();

      // Process justifications
      state.setPrevious_justified_checkpoint(state.getCurrent_justified_checkpoint());
      SszBitvector justificationBits = state.getJustification_bits().rightShift(1);

      if (totalBalances
          .getPreviousEpochTargetAttesters()
          .times(3)
          .isGreaterThanOrEqualTo(totalBalances.getCurrentEpoch().times(2))) {
        UInt64 previousEpoch = beaconStateUtil.getPreviousEpoch(state);
        Checkpoint newCheckpoint =
            new Checkpoint(previousEpoch, beaconStateUtil.getBlockRoot(state, previousEpoch));
        state.setCurrent_justified_checkpoint(newCheckpoint);
        justificationBits = justificationBits.withBit(1);
      }

      if (totalBalances
          .getCurrentEpochTargetAttesters()
          .times(3)
          .isGreaterThanOrEqualTo(totalBalances.getCurrentEpoch().times(2))) {
        Checkpoint newCheckpoint =
            new Checkpoint(currentEpoch, beaconStateUtil.getBlockRoot(state, currentEpoch));
        state.setCurrent_justified_checkpoint(newCheckpoint);
        justificationBits = justificationBits.withBit(0);
      }

      state.setJustification_bits(justificationBits);

      // Process finalizations

      // The 2nd/3rd/4th most recent epochs are justified, the 2nd using the 4th as source
      if (beaconStateUtil.all(justificationBits, 1, 4)
          && oldPreviousJustifiedCheckpoint.getEpoch().plus(3).equals(currentEpoch)) {
        state.setFinalized_checkpoint(oldPreviousJustifiedCheckpoint);
      }
      // The 2nd/3rd most recent epochs are justified, the 2nd using the 3rd as source
      if (beaconStateUtil.all(justificationBits, 1, 3)
          && oldPreviousJustifiedCheckpoint.getEpoch().plus(2).equals(currentEpoch)) {
        state.setFinalized_checkpoint(oldPreviousJustifiedCheckpoint);
      }
      // The 1st/2nd/3rd most recent epochs are justified, the 1st using the 3rd as source
      if (beaconStateUtil.all(justificationBits, 0, 3)
          && oldCurrentJustifiedCheckpoint.getEpoch().plus(2).equals(currentEpoch)) {
        state.setFinalized_checkpoint(oldCurrentJustifiedCheckpoint);
      }
      // The 1st/2nd most recent epochs are justified, the 1st using the 2nd as source
      if (beaconStateUtil.all(justificationBits, 0, 2)
          && oldCurrentJustifiedCheckpoint.getEpoch().plus(1).equals(currentEpoch)) {
        state.setFinalized_checkpoint(oldCurrentJustifiedCheckpoint);
      }

    } catch (IllegalArgumentException e) {
      throw new EpochProcessingException(e);
    }
  }

  /** Processes rewards and penalties */
  public void processRewardsAndPenalties(
      MutableBeaconState state, ValidatorStatuses validatorStatuses)
      throws EpochProcessingException {
    try {
      if (beaconStateUtil.getCurrentEpoch(state).equals(SpecConstants.GENESIS_EPOCH)) {
        return;
      }

      Deltas attestationDeltas =
          createRewardsAndPenaltiesCalculator(state, validatorStatuses).getAttestationDeltas();

      applyDeltas(state, attestationDeltas);
    } catch (IllegalArgumentException e) {
      throw new EpochProcessingException(e);
    }
  }

  public RewardsAndPenaltiesCalculator createRewardsAndPenaltiesCalculator(
      final BeaconState state, final ValidatorStatuses validatorStatuses) {
    return new DefaultRewardsAndPenaltiesCalculator(
        specConstants, beaconStateUtil, state, validatorStatuses);
  }

  private static void applyDeltas(final MutableBeaconState state, final Deltas attestationDeltas) {
    final SSZMutableList<UInt64> balances = state.getBalances();
    for (int i = 0; i < state.getValidators().size(); i++) {
      final Deltas.Delta delta = attestationDeltas.getDelta(i);
      balances.set(i, balances.get(i).plus(delta.getReward()).minusMinZero(delta.getPenalty()));
    }
  }

  /**
   * Processes validator registry updates
   *
   * @param state
   * @throws EpochProcessingException
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0Beacon-chain.md#registry-updates</a>
   */
  public void processRegistryUpdates(MutableBeaconState state, List<ValidatorStatus> statuses)
      throws EpochProcessingException {
    try {

      // Process activation eligibility and ejections
      SszMutableList<Validator> validators = state.getValidators();
      final UInt64 currentEpoch = beaconStateUtil.getCurrentEpoch(state);
      for (int index = 0; index < validators.size(); index++) {
        final ValidatorStatus status = statuses.get(index);

        // Slightly optimised form of isEligibleForActivationQueue to avoid accessing the
        // state for the majority of validators.  Can't be eligible for activation if already active
        // or if effective balance is too low.  Only get the validator if both those checks pass to
        // confirm it isn't already in the queue.
        if (!status.isActiveInCurrentEpoch()
            && status
                .getCurrentEpochEffectiveBalance()
                .equals(specConstants.getMaxEffectiveBalance())) {
          final Validator validator = validators.get(index);
          if (validator.getActivation_eligibility_epoch().equals(SpecConstants.FAR_FUTURE_EPOCH)) {
            validators.set(
                index, validator.withActivation_eligibility_epoch(currentEpoch.plus(UInt64.ONE)));
          }
        }

        if (status.isActiveInCurrentEpoch()
            && status
                .getCurrentEpochEffectiveBalance()
                .isLessThanOrEqualTo(specConstants.getEjectionBalance())) {
          beaconStateUtil.initiateValidatorExit(state, index);
        }
      }

      // Queue validators eligible for activation and not yet dequeued for activation
      List<Integer> activationQueue =
          IntStream.range(0, state.getValidators().size())
              // Cheap filter first before accessing state
              .filter(index -> !statuses.get(index).isActiveInCurrentEpoch())
              .filter(
                  index -> {
                    Validator validator = state.getValidators().get(index);
                    return validatorsUtil.isEligibleForActivation(state, validator);
                  })
              .boxed()
              .sorted(
                  (index1, index2) -> {
                    int comparisonResult =
                        state
                            .getValidators()
                            .get(index1)
                            .getActivation_eligibility_epoch()
                            .compareTo(
                                state
                                    .getValidators()
                                    .get(index2)
                                    .getActivation_eligibility_epoch());
                    if (comparisonResult == 0) {
                      return index1.compareTo(index2);
                    } else {
                      return comparisonResult;
                    }
                  })
              .collect(Collectors.toList());

      // Dequeued validators for activation up to churn limit (without resetting activation epoch)
      int churnLimit = beaconStateUtil.getValidatorChurnLimit(state).intValue();
      int sublistSize = Math.min(churnLimit, activationQueue.size());
      for (Integer index : activationQueue.subList(0, sublistSize)) {
        state
            .getValidators()
            .update(
                index,
                validator ->
                    validator.withActivation_epoch(
                        beaconStateUtil.computeActivationExitEpoch(currentEpoch)));
      }
    } catch (IllegalArgumentException e) {
      throw new EpochProcessingException(e);
    }
  }

  /**
   * Processes slashings
   *
   * @param state
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0Beacon-chain.md#slashings</a>
   */
  public void processSlashings(MutableBeaconState state, final UInt64 totalBalance) {
    UInt64 epoch = beaconStateUtil.getCurrentEpoch(state);
    UInt64 adjustedTotalSlashingBalance =
        state
            .getSlashings()
            .streamUnboxed()
            .reduce(UInt64.ZERO, UInt64::plus)
            .times(specConstants.getProportionalSlashingMultiplier())
            .min(totalBalance);

    SszList<Validator> validators = state.getValidators();
    for (int index = 0; index < validators.size(); index++) {
      Validator validator = validators.get(index);
      if (validator.isSlashed()
          && epoch
              .plus(specConstants.getEpochsPerSlashingsVector() / 2)
              .equals(validator.getWithdrawable_epoch())) {
        UInt64 increment = specConstants.getEffectiveBalanceIncrement();
        UInt64 penaltyNumerator =
            validator
                .getEffective_balance()
                .dividedBy(increment)
                .times(adjustedTotalSlashingBalance);
        UInt64 penalty = penaltyNumerator.dividedBy(totalBalance).times(increment);
        decrease_balance(state, index, penalty);
      }
    }
  }

  /**
   * Processes final updates
   *
   * @param state
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0Beacon-chain.md#final-updates</a>
   */
  public void processFinalUpdates(MutableBeaconState state) {
    UInt64 currentEpoch = beaconStateUtil.getCurrentEpoch(state);
    UInt64 nextEpoch = currentEpoch.plus(UInt64.ONE);

    // Reset eth1 data votes
    if (nextEpoch.mod(specConstants.getEpochsPerEth1VotingPeriod()).equals(UInt64.ZERO)) {
      state.getEth1_data_votes().clear();
    }

    // Update effective balances with hysteresis
    SszMutableList<Validator> validators = state.getValidators();
    SSZList<UInt64> balances = state.getBalances();
    for (int index = 0; index < validators.size(); index++) {
      Validator validator = validators.get(index);
      UInt64 balance = balances.get(index);

      final UInt64 hysteresisIncrement =
          specConstants
              .getEffectiveBalanceIncrement()
              .dividedBy(specConstants.getHysteresisQuotient());
      final UInt64 downwardThreshold =
          hysteresisIncrement.times(specConstants.getHysteresisDownwardMultiplier());
      final UInt64 upwardThreshold =
          hysteresisIncrement.times(specConstants.getHysteresisUpwardMultiplier());
      if (balance.plus(downwardThreshold).isLessThan(validator.getEffective_balance())
          || validator.getEffective_balance().plus(upwardThreshold).isLessThan(balance)) {
        state
            .getValidators()
            .set(
                index,
                validator.withEffective_balance(
                    balance
                        .minus(balance.mod(specConstants.getEffectiveBalanceIncrement()))
                        .min(specConstants.getMaxEffectiveBalance())));
      }
    }

    // Reset slashings
    int index = nextEpoch.mod(specConstants.getEpochsPerSlashingsVector()).intValue();
    state.getSlashings().setElement(index, UInt64.ZERO);

    // Set randao mix
    final int randaoIndex = nextEpoch.mod(specConstants.getEpochsPerHistoricalVector()).intValue();
    state
        .getRandao_mixes()
        .setElement(randaoIndex, beaconStateUtil.getRandaoMix(state, currentEpoch));

    // Set historical root accumulator
    if (nextEpoch
        .mod(specConstants.getSlotsPerHistoricalRoot() / specConstants.getSlotsPerEpoch())
        .equals(UInt64.ZERO)) {
      HistoricalBatch historicalBatch =
          new HistoricalBatch(state.getBlock_roots(), state.getState_roots());
      state.getHistorical_roots().add(historicalBatch.hashTreeRoot());
    }

    // Rotate current/previous epoch attestations
    state.setPrevious_epoch_attestations(state.getCurrent_epoch_attestations());
    state.setCurrent_epoch_attestations(
        BeaconState.CURRENT_EPOCH_ATTESTATIONS_FIELD_SCHEMA.get().getDefault());
  }
}
