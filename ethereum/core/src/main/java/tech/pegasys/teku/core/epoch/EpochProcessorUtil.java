/*
 * Copyright 2019 ConsenSys AG.
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

package tech.pegasys.teku.core.epoch;

import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.all;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_activation_exit_epoch;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.get_block_root;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.get_current_epoch;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.get_previous_epoch;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.get_randao_mix;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.get_validator_churn_limit;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.initiate_validator_exit;
import static tech.pegasys.teku.datastructures.util.ValidatorsUtil.decrease_balance;
import static tech.pegasys.teku.datastructures.util.ValidatorsUtil.is_eligible_for_activation;
import static tech.pegasys.teku.util.config.Constants.EFFECTIVE_BALANCE_INCREMENT;
import static tech.pegasys.teku.util.config.Constants.EJECTION_BALANCE;
import static tech.pegasys.teku.util.config.Constants.EPOCHS_PER_ETH1_VOTING_PERIOD;
import static tech.pegasys.teku.util.config.Constants.EPOCHS_PER_HISTORICAL_VECTOR;
import static tech.pegasys.teku.util.config.Constants.EPOCHS_PER_SLASHINGS_VECTOR;
import static tech.pegasys.teku.util.config.Constants.FAR_FUTURE_EPOCH;
import static tech.pegasys.teku.util.config.Constants.GENESIS_EPOCH;
import static tech.pegasys.teku.util.config.Constants.HYSTERESIS_DOWNWARD_MULTIPLIER;
import static tech.pegasys.teku.util.config.Constants.HYSTERESIS_QUOTIENT;
import static tech.pegasys.teku.util.config.Constants.HYSTERESIS_UPWARD_MULTIPLIER;
import static tech.pegasys.teku.util.config.Constants.MAX_ATTESTATIONS;
import static tech.pegasys.teku.util.config.Constants.MAX_EFFECTIVE_BALANCE;
import static tech.pegasys.teku.util.config.Constants.SLOTS_PER_EPOCH;
import static tech.pegasys.teku.util.config.Constants.SLOTS_PER_HISTORICAL_ROOT;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import tech.pegasys.teku.core.Deltas;
import tech.pegasys.teku.core.Deltas.Delta;
import tech.pegasys.teku.core.epoch.status.ValidatorStatus;
import tech.pegasys.teku.core.epoch.status.ValidatorStatuses;
import tech.pegasys.teku.core.exceptions.EpochProcessingException;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.datastructures.state.HistoricalBatch;
import tech.pegasys.teku.datastructures.state.MutableBeaconState;
import tech.pegasys.teku.datastructures.state.PendingAttestation;
import tech.pegasys.teku.datastructures.state.Validator;
import tech.pegasys.teku.independent.TotalBalances;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.SSZTypes.Bitvector;
import tech.pegasys.teku.ssz.SSZTypes.SSZList;
import tech.pegasys.teku.ssz.SSZTypes.SSZMutableList;
import tech.pegasys.teku.util.config.Constants;

public final class EpochProcessorUtil {

  // State Transition Helper Functions

  /**
   * Processes justification and finalization
   *
   * @param state
   * @param totalBalances
   * @throws EpochProcessingException
   */
  public static void process_justification_and_finalization(
      MutableBeaconState state, TotalBalances totalBalances) throws EpochProcessingException {
    try {
      UInt64 current_epoch = get_current_epoch(state);
      if (current_epoch.isLessThanOrEqualTo(UInt64.valueOf(GENESIS_EPOCH + 1))) {
        return;
      }

      Checkpoint old_previous_justified_checkpoint = state.getPrevious_justified_checkpoint();
      Checkpoint old_current_justified_checkpoint = state.getCurrent_justified_checkpoint();

      // Process justifications
      state.setPrevious_justified_checkpoint(state.getCurrent_justified_checkpoint());
      Bitvector justificationBits = state.getJustification_bits().rightShift(1);

      if (totalBalances
          .getPreviousEpochTargetAttesters()
          .times(3)
          .isGreaterThanOrEqualTo(totalBalances.getCurrentEpoch().times(2))) {
        UInt64 previous_epoch = get_previous_epoch(state);
        Checkpoint newCheckpoint =
            new Checkpoint(previous_epoch, get_block_root(state, previous_epoch));
        state.setCurrent_justified_checkpoint(newCheckpoint);
        justificationBits.setBit(1);
      }

      if (totalBalances
          .getCurrentEpochTargetAttesters()
          .times(3)
          .isGreaterThanOrEqualTo(totalBalances.getCurrentEpoch().times(2))) {
        Checkpoint newCheckpoint =
            new Checkpoint(current_epoch, get_block_root(state, current_epoch));
        state.setCurrent_justified_checkpoint(newCheckpoint);
        justificationBits.setBit(0);
      }

      state.setJustification_bits(justificationBits);

      // Process finalizations

      // The 2nd/3rd/4th most recent epochs are justified, the 2nd using the 4th as source
      if (all(justificationBits, 1, 4)
          && old_previous_justified_checkpoint.getEpoch().plus(3).equals(current_epoch)) {
        state.setFinalized_checkpoint(old_previous_justified_checkpoint);
      }
      // The 2nd/3rd most recent epochs are justified, the 2nd using the 3rd as source
      if (all(justificationBits, 1, 3)
          && old_previous_justified_checkpoint.getEpoch().plus(2).equals(current_epoch)) {
        state.setFinalized_checkpoint(old_previous_justified_checkpoint);
      }
      // The 1st/2nd/3rd most recent epochs are justified, the 1st using the 3rd as source
      if (all(justificationBits, 0, 3)
          && old_current_justified_checkpoint.getEpoch().plus(2).equals(current_epoch)) {
        state.setFinalized_checkpoint(old_current_justified_checkpoint);
      }
      // The 1st/2nd most recent epochs are justified, the 1st using the 2nd as source
      if (all(justificationBits, 0, 2)
          && old_current_justified_checkpoint.getEpoch().plus(1).equals(current_epoch)) {
        state.setFinalized_checkpoint(old_current_justified_checkpoint);
      }

    } catch (IllegalArgumentException e) {
      throw new EpochProcessingException(e);
    }
  }

  /** Processes rewards and penalties */
  public static void process_rewards_and_penalties(
      MutableBeaconState state, ValidatorStatuses validatorStatuses)
      throws EpochProcessingException {
    try {
      if (get_current_epoch(state).equals(UInt64.valueOf(GENESIS_EPOCH))) {
        return;
      }

      Deltas attestation_deltas =
          new RewardsAndPenaltiesCalculator(state, validatorStatuses).getAttestationDeltas();

      applyDeltas(state, attestation_deltas);
    } catch (IllegalArgumentException e) {
      throw new EpochProcessingException(e);
    }
  }

  private static void applyDeltas(final MutableBeaconState state, final Deltas attestation_deltas) {
    final SSZMutableList<UInt64> balances = state.getBalances();
    for (int i = 0; i < state.getValidators().size(); i++) {
      final Delta delta = attestation_deltas.getDelta(i);
      balances.set(i, balances.get(i).plus(delta.getReward()).minusMinZero(delta.getPenalty()));
    }
  }

  /**
   * Processes validator registry updates
   *
   * @param state
   * @throws EpochProcessingException
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#registry-updates</a>
   */
  public static void process_registry_updates(
      MutableBeaconState state, List<ValidatorStatus> statuses) throws EpochProcessingException {
    try {

      // Process activation eligibility and ejections
      SSZMutableList<Validator> validators = state.getValidators();
      final UInt64 currentEpoch = get_current_epoch(state);
      for (int index = 0; index < validators.size(); index++) {
        final ValidatorStatus status = statuses.get(index);

        // Slightly optimised form of is_eligible_for_activation_queue to avoid accessing the
        // state for the majority of validators.  Can't be eligible for activation if already active
        // or if effective balance is too low.  Only get the validator if both those checks pass to
        // confirm it isn't already in the queue.
        if (!status.isActiveInCurrentEpoch()
            && status.getCurrentEpochEffectiveBalance().equals(MAX_EFFECTIVE_BALANCE)) {
          final Validator validator = validators.get(index);
          if (validator.getActivation_eligibility_epoch().equals(FAR_FUTURE_EPOCH)) {
            validators.set(
                index, validator.withActivation_eligibility_epoch(currentEpoch.plus(UInt64.ONE)));
          }
        }

        if (status.isActiveInCurrentEpoch()
            && status.getCurrentEpochEffectiveBalance().isLessThanOrEqualTo(EJECTION_BALANCE)) {
          initiate_validator_exit(state, index);
        }
      }

      // Queue validators eligible for activation and not yet dequeued for activation
      List<Integer> activation_queue =
          IntStream.range(0, state.getValidators().size())
              // Cheap filter first before accessing state
              .filter(index -> !statuses.get(index).isActiveInCurrentEpoch())
              .filter(
                  index -> {
                    Validator validator = state.getValidators().get(index);
                    return is_eligible_for_activation(state, validator);
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
      int churn_limit = get_validator_churn_limit(state).intValue();
      int sublist_size = Math.min(churn_limit, activation_queue.size());
      for (Integer index : activation_queue.subList(0, sublist_size)) {
        state
            .getValidators()
            .update(
                index,
                validator ->
                    validator.withActivation_epoch(compute_activation_exit_epoch(currentEpoch)));
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
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#slashings</a>
   */
  public static void process_slashings(MutableBeaconState state, final UInt64 total_balance) {
    UInt64 epoch = get_current_epoch(state);
    UInt64 adjusted_total_slashing_balance =
        state.getSlashings().stream()
            .reduce(UInt64.ZERO, UInt64::plus)
            .times(Constants.PROPORTIONAL_SLASHING_MULTIPLIER)
            .min(total_balance);

    SSZList<Validator> validators = state.getValidators();
    for (int index = 0; index < validators.size(); index++) {
      Validator validator = validators.get(index);
      if (validator.isSlashed()
          && epoch
              .plus(EPOCHS_PER_SLASHINGS_VECTOR / 2)
              .equals(validator.getWithdrawable_epoch())) {
        UInt64 increment = EFFECTIVE_BALANCE_INCREMENT;
        UInt64 penalty_numerator =
            validator
                .getEffective_balance()
                .dividedBy(increment)
                .times(adjusted_total_slashing_balance);
        UInt64 penalty = penalty_numerator.dividedBy(total_balance).times(increment);
        decrease_balance(state, index, penalty);
      }
    }
  }

  /**
   * Processes final updates
   *
   * @param state
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#final-updates</a>
   */
  public static void process_final_updates(MutableBeaconState state) {
    UInt64 current_epoch = get_current_epoch(state);
    UInt64 next_epoch = current_epoch.plus(UInt64.ONE);

    // Reset eth1 data votes
    if (next_epoch.mod(EPOCHS_PER_ETH1_VOTING_PERIOD).equals(UInt64.ZERO)) {
      state.getEth1_data_votes().clear();
    }

    // Update effective balances with hysteresis
    SSZMutableList<Validator> validators = state.getValidators();
    SSZList<UInt64> balances = state.getBalances();
    for (int index = 0; index < validators.size(); index++) {
      Validator validator = validators.get(index);
      UInt64 balance = balances.get(index);

      final UInt64 hysteresis_increment =
          EFFECTIVE_BALANCE_INCREMENT.dividedBy(HYSTERESIS_QUOTIENT);
      final UInt64 downward_threshold = hysteresis_increment.times(HYSTERESIS_DOWNWARD_MULTIPLIER);
      final UInt64 upward_threshold = hysteresis_increment.times(HYSTERESIS_UPWARD_MULTIPLIER);
      if (balance.plus(downward_threshold).isLessThan(validator.getEffective_balance())
          || validator.getEffective_balance().plus(upward_threshold).isLessThan(balance)) {
        state
            .getValidators()
            .set(
                index,
                validator.withEffective_balance(
                    balance
                        .minus(balance.mod(EFFECTIVE_BALANCE_INCREMENT))
                        .min(MAX_EFFECTIVE_BALANCE)));
      }
    }

    // Reset slashings
    int index = next_epoch.mod(EPOCHS_PER_SLASHINGS_VECTOR).intValue();
    state.getSlashings().set(index, UInt64.ZERO);

    // Set randao mix
    final int randaoIndex = next_epoch.mod(EPOCHS_PER_HISTORICAL_VECTOR).intValue();
    state.getRandao_mixes().set(randaoIndex, get_randao_mix(state, current_epoch));

    // Set historical root accumulator
    if (next_epoch.mod(SLOTS_PER_HISTORICAL_ROOT / SLOTS_PER_EPOCH).equals(UInt64.ZERO)) {
      HistoricalBatch historical_batch =
          new HistoricalBatch(state.getBlock_roots(), state.getState_roots());
      state.getHistorical_roots().add(historical_batch.hash_tree_root());
    }

    // Rotate current/previous epoch attestations
    state.getPrevious_epoch_attestations().setAll(state.getCurrent_epoch_attestations());
    state
        .getCurrent_epoch_attestations()
        .setAll(
            SSZList.createMutable(PendingAttestation.class, MAX_ATTESTATIONS * SLOTS_PER_EPOCH));
  }
}
