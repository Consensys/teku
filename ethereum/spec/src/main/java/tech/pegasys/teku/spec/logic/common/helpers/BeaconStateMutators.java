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

package tech.pegasys.teku.spec.logic.common.helpers;

import static java.util.stream.Collectors.toList;
import static tech.pegasys.teku.spec.config.SpecConfig.FAR_FUTURE_EPOCH;

import java.util.Collections;
import java.util.List;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;

public class BeaconStateMutators {
  private final SpecConfig specConfig;
  private final MiscHelpers miscHelpers;
  private final BeaconStateAccessors beaconStateAccessors;

  public BeaconStateMutators(
      final SpecConfig specConfig,
      final MiscHelpers miscHelpers,
      final BeaconStateAccessors beaconStateAccessors) {
    this.specConfig = specConfig;
    this.miscHelpers = miscHelpers;
    this.beaconStateAccessors = beaconStateAccessors;
  }

  /**
   * Increase validator balance by ``delta``.
   *
   * @param state
   * @param index
   * @param delta
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#increase_balance</a>
   */
  public void increaseBalance(MutableBeaconState state, int index, UInt64 delta) {
    state.getBalances().setElement(index, state.getBalances().getElement(index).plus(delta));
  }

  /**
   * Decrease validator balance by ``delta`` with underflow protection.
   *
   * @param state
   * @param index
   * @param delta
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#decrease_balance</a>
   */
  public void decreaseBalance(MutableBeaconState state, int index, UInt64 delta) {
    state
        .getBalances()
        .setElement(index, state.getBalances().getElement(index).minusMinZero(delta));
  }

  public void initiateValidatorExit(MutableBeaconState state, int index) {
    Validator validator = state.getValidators().get(index);
    // Return if validator already initiated exit
    if (!validator.getExit_epoch().equals(FAR_FUTURE_EPOCH)) {
      return;
    }

    // Compute exit queue epoch
    List<UInt64> exit_epochs =
        state.getValidators().stream()
            .map(Validator::getExit_epoch)
            .filter(exitEpoch -> !exitEpoch.equals(FAR_FUTURE_EPOCH))
            .collect(toList());
    exit_epochs.add(
        miscHelpers.computeActivationExitEpoch(beaconStateAccessors.getCurrentEpoch(state)));
    UInt64 exit_queue_epoch = Collections.max(exit_epochs);
    final UInt64 final_exit_queue_epoch = exit_queue_epoch;
    UInt64 exit_queue_churn =
        UInt64.valueOf(
            state.getValidators().stream()
                .filter(v -> v.getExit_epoch().equals(final_exit_queue_epoch))
                .count());

    if (exit_queue_churn.compareTo(beaconStateAccessors.getValidatorChurnLimit(state)) >= 0) {
      exit_queue_epoch = exit_queue_epoch.plus(UInt64.ONE);
    }

    // Set validator exit epoch and withdrawable epoch
    state
        .getValidators()
        .set(
            index,
            validator
                .withExit_epoch(exit_queue_epoch)
                .withWithdrawable_epoch(
                    exit_queue_epoch.plus(specConfig.getMinValidatorWithdrawabilityDelay())));
  }

  public void slashValidator(MutableBeaconState state, int slashed_index) {
    slashValidator(state, slashed_index, -1);
  }

  private void slashValidator(MutableBeaconState state, int slashedIndex, int whistleblowerIndex) {
    UInt64 epoch = beaconStateAccessors.getCurrentEpoch(state);
    initiateValidatorExit(state, slashedIndex);

    Validator validator = state.getValidators().get(slashedIndex);

    state
        .getValidators()
        .set(
            slashedIndex,
            validator
                .withSlashed(true)
                .withWithdrawable_epoch(
                    validator
                        .getWithdrawable_epoch()
                        .max(epoch.plus(specConfig.getEpochsPerSlashingsVector()))));

    int index = epoch.mod(specConfig.getEpochsPerSlashingsVector()).intValue();
    state
        .getSlashings()
        .setElement(
            index, state.getSlashings().getElement(index).plus(validator.getEffective_balance()));
    decreaseBalance(
        state,
        slashedIndex,
        validator.getEffective_balance().dividedBy(getMinSlashingPenaltyQuotient()));

    // Apply proposer and whistleblower rewards
    int proposer_index = beaconStateAccessors.getBeaconProposerIndex(state);
    if (whistleblowerIndex == -1) {
      whistleblowerIndex = proposer_index;
    }

    UInt64 whistleblower_reward =
        validator.getEffective_balance().dividedBy(specConfig.getWhistleblowerRewardQuotient());
    UInt64 proposer_reward = calculateProposerReward(whistleblower_reward);
    increaseBalance(state, proposer_index, proposer_reward);
    increaseBalance(state, whistleblowerIndex, whistleblower_reward.minus(proposer_reward));
  }

  protected UInt64 calculateProposerReward(final UInt64 whistleblower_reward) {
    return whistleblower_reward.dividedBy(specConfig.getProposerRewardQuotient());
  }

  protected int getMinSlashingPenaltyQuotient() {
    return specConfig.getMinSlashingPenaltyQuotient();
  }
}
