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

package tech.pegasys.teku.spec.logic.common.statetransition.epoch;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.SszMutableList;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.ssz.collections.SszMutableUInt64List;
import tech.pegasys.teku.infrastructure.ssz.collections.SszUInt64List;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.BlockCheckpoints;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.HistoricalBatch;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateCache;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.TransitionCaches;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateAccessors;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateMutators;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.RewardAndPenaltyDeltas.RewardAndPenalty;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.status.ProgressiveTotalBalancesUpdates;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.status.TotalBalances;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.status.ValidatorStatus;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.status.ValidatorStatusFactory;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.status.ValidatorStatuses;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.EpochProcessingException;
import tech.pegasys.teku.spec.logic.common.util.BeaconStateUtil;
import tech.pegasys.teku.spec.logic.common.util.ValidatorsUtil;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;

public abstract class AbstractEpochProcessor implements EpochProcessor {
  protected final SpecConfig specConfig;
  protected final MiscHelpers miscHelpers;
  protected final ValidatorsUtil validatorsUtil;
  protected final BeaconStateUtil beaconStateUtil;
  protected final ValidatorStatusFactory validatorStatusFactory;
  private final SchemaDefinitions schemaDefinitions;
  protected final BeaconStateAccessors beaconStateAccessors;
  protected final BeaconStateMutators beaconStateMutators;

  protected AbstractEpochProcessor(
      final SpecConfig specConfig,
      final MiscHelpers miscHelpers,
      final BeaconStateAccessors beaconStateAccessors,
      final BeaconStateMutators beaconStateMutators,
      final ValidatorsUtil validatorsUtil,
      final BeaconStateUtil beaconStateUtil,
      final ValidatorStatusFactory validatorStatusFactory,
      final SchemaDefinitions schemaDefinitions) {
    this.specConfig = specConfig;
    this.miscHelpers = miscHelpers;
    this.beaconStateAccessors = beaconStateAccessors;
    this.beaconStateMutators = beaconStateMutators;
    this.validatorsUtil = validatorsUtil;
    this.beaconStateUtil = beaconStateUtil;
    this.validatorStatusFactory = validatorStatusFactory;
    this.schemaDefinitions = schemaDefinitions;
  }

  /**
   * Processes epoch
   *
   * @param preState state prior to epoch transition
   * @throws EpochProcessingException if processing fails
   */
  @Override
  public BeaconState processEpoch(final BeaconState preState) throws EpochProcessingException {
    return preState.updated(mutableState -> processEpoch(preState, mutableState));
  }

  protected void processEpoch(final BeaconState preState, final MutableBeaconState state)
      throws EpochProcessingException {
    final ValidatorStatuses validatorStatuses =
        validatorStatusFactory.createValidatorStatuses(preState);

    final UInt64 currentEpoch = beaconStateAccessors.getCurrentEpoch(state);
    final TotalBalances totalBalances = validatorStatuses.getTotalBalances();

    updateTransitionCaches(state, currentEpoch, totalBalances);

    final TransitionCaches transitionCaches = BeaconStateCache.getTransitionCaches(state);
    final ProgressiveTotalBalancesUpdates progressiveTotalBalances =
        transitionCaches.getProgressiveTotalBalances();
    progressiveTotalBalances.checkResult(specConfig, state.getSlot(), totalBalances);
    progressiveTotalBalances.onEpochTransition(validatorStatuses.getStatuses());
    processJustificationAndFinalization(state, validatorStatuses.getTotalBalances());
    processInactivityUpdates(state, validatorStatuses);
    processRewardsAndPenalties(state, validatorStatuses);
    processRegistryUpdates(state, validatorStatuses.getStatuses());
    processSlashings(state, validatorStatuses);
    processEth1DataReset(state);
    processEffectiveBalanceUpdates(state, validatorStatuses.getStatuses());
    processSlashingsReset(state);
    processRandaoMixesReset(state);
    processHistoricalRootsUpdate(state);
    processHistoricalSummariesUpdate(state);
    processParticipationUpdates(state);
    processSyncCommitteeUpdates(state);

    if (specConfig.getProgressiveBalancesMode().isUsed()) {
      progressiveTotalBalances
          .getTotalBalances(specConfig)
          .ifPresent(
              newTotalBalances ->
                  BeaconStateCache.getTransitionCaches(state)
                      .getTotalActiveBalance()
                      .get(
                          currentEpoch.plus(1),
                          __ -> newTotalBalances.getCurrentEpochActiveValidators()));
    }
  }

  private void updateTransitionCaches(
      final MutableBeaconState state,
      final UInt64 currentEpoch,
      final TotalBalances totalBalances) {
    final TransitionCaches transitionCaches = BeaconStateCache.getTransitionCaches(state);
    transitionCaches.setLatestTotalBalances(totalBalances);
    transitionCaches
        .getTotalActiveBalance()
        .get(currentEpoch, __ -> totalBalances.getCurrentEpochActiveValidators());
    initProgressiveTotalBalancesIfRequired(state, totalBalances);
  }

  @Override
  public BlockCheckpoints calculateBlockCheckpoints(final BeaconState preState) {
    final UInt64 currentEpoch = beaconStateAccessors.getCurrentEpoch(preState);
    final Checkpoint currentJustifiedCheckpoint = preState.getCurrentJustifiedCheckpoint();
    final Checkpoint currentFinalizedCheckpoint = preState.getFinalizedCheckpoint();
    if (currentEpoch.isLessThanOrEqualTo(SpecConfig.GENESIS_EPOCH.plus(1))
        || !specConfig.getProgressiveBalancesMode().isUsed()) {
      return new BlockCheckpoints(
          currentJustifiedCheckpoint,
          currentFinalizedCheckpoint,
          currentJustifiedCheckpoint,
          currentFinalizedCheckpoint);
    }

    final TotalBalances totalBalances =
        BeaconStateCache.getTransitionCaches(preState)
            .getProgressiveTotalBalances()
            .getTotalBalances(specConfig)
            .orElseGet(
                () -> validatorStatusFactory.createValidatorStatuses(preState).getTotalBalances());
    return calculateBlockCheckpoints(preState, currentEpoch, totalBalances, __ -> {});
  }

  /** Processes justification and finalization */
  @Override
  public void processJustificationAndFinalization(
      MutableBeaconState state, TotalBalances totalBalances) throws EpochProcessingException {
    try {
      UInt64 currentEpoch = beaconStateAccessors.getCurrentEpoch(state);
      if (currentEpoch.isLessThanOrEqualTo(SpecConfig.GENESIS_EPOCH.plus(1))) {
        return;
      }
      weighJustificationAndFinalization(state, currentEpoch, totalBalances);

    } catch (IllegalArgumentException e) {
      throw new EpochProcessingException(e);
    }
  }

  private BlockCheckpoints calculateBlockCheckpoints(
      final BeaconState state,
      final UInt64 currentEpoch,
      final TotalBalances totalBalances,
      final Consumer<SszBitvector> modifiedJustificationBitsHandler) {
    final UInt64 totalActiveBalance = totalBalances.getCurrentEpochActiveValidators();
    final UInt64 previousEpochTargetBalance = totalBalances.getPreviousEpochTargetAttesters();
    final UInt64 currentEpochTargetBalance = totalBalances.getCurrentEpochTargetAttesters();

    final Checkpoint oldPreviousJustifiedCheckpoint = state.getPreviousJustifiedCheckpoint();
    final Checkpoint oldCurrentJustifiedCheckpoint = state.getCurrentJustifiedCheckpoint();
    final Checkpoint oldFinalizedCheckpoint = state.getFinalizedCheckpoint();
    Checkpoint unrealizedJustifiedCheckpoint = oldCurrentJustifiedCheckpoint;
    Checkpoint unrealizedFinalizedCheckpoint = oldFinalizedCheckpoint;

    SszBitvector justificationBits = state.getJustificationBits().rightShift(1);

    // Process justifications
    if (previousEpochTargetBalance.times(3).isGreaterThanOrEqualTo(totalActiveBalance.times(2))) {
      UInt64 previousEpoch = beaconStateAccessors.getPreviousEpoch(state);
      unrealizedJustifiedCheckpoint =
          new Checkpoint(previousEpoch, getBlockRootInEffect(state, previousEpoch));
      justificationBits = justificationBits.withBit(1);
    }

    if (currentEpochTargetBalance.times(3).isGreaterThanOrEqualTo(totalActiveBalance.times(2))) {
      unrealizedJustifiedCheckpoint =
          new Checkpoint(currentEpoch, getBlockRootInEffect(state, currentEpoch));
      justificationBits = justificationBits.withBit(0);
    }

    modifiedJustificationBitsHandler.accept(justificationBits);

    // The 2nd/3rd/4th most recent epochs are justified, the 2nd using the 4th as source
    if (beaconStateUtil.all(justificationBits, 1, 4)
        && oldPreviousJustifiedCheckpoint.getEpoch().plus(3).equals(currentEpoch)) {
      unrealizedFinalizedCheckpoint = oldPreviousJustifiedCheckpoint;
    }
    // The 2nd/3rd most recent epochs are justified, the 2nd using the 3rd as source
    if (beaconStateUtil.all(justificationBits, 1, 3)
        && oldPreviousJustifiedCheckpoint.getEpoch().plus(2).equals(currentEpoch)) {
      unrealizedFinalizedCheckpoint = oldPreviousJustifiedCheckpoint;
    }
    // The 1st/2nd/3rd most recent epochs are justified, the 1st using the 3rd as source
    if (beaconStateUtil.all(justificationBits, 0, 3)
        && oldCurrentJustifiedCheckpoint.getEpoch().plus(2).equals(currentEpoch)) {
      unrealizedFinalizedCheckpoint = oldCurrentJustifiedCheckpoint;
    }
    // The 1st/2nd most recent epochs are justified, the 1st using the 2nd as source
    if (beaconStateUtil.all(justificationBits, 0, 2)
        && oldCurrentJustifiedCheckpoint.getEpoch().plus(1).equals(currentEpoch)) {
      unrealizedFinalizedCheckpoint = oldCurrentJustifiedCheckpoint;
    }
    return new BlockCheckpoints(
        oldCurrentJustifiedCheckpoint,
        oldFinalizedCheckpoint,
        unrealizedJustifiedCheckpoint,
        unrealizedFinalizedCheckpoint);
  }

  private Bytes32 getBlockRootInEffect(final BeaconState state, final UInt64 epoch) {
    final UInt64 slot = miscHelpers.computeStartSlotAtEpoch(epoch);
    if (state.getSlot().isGreaterThan(slot)) {
      return beaconStateAccessors.getBlockRootAtSlot(state, slot);
    } else {
      return BeaconBlockHeader.fromState(state).hashTreeRoot();
    }
  }

  private void weighJustificationAndFinalization(
      final MutableBeaconState state,
      final UInt64 currentEpoch,
      final TotalBalances totalBalances) {
    final BlockCheckpoints unrealisedCheckpoints =
        calculateBlockCheckpoints(state, currentEpoch, totalBalances, state::setJustificationBits);
    state.setPreviousJustifiedCheckpoint(state.getCurrentJustifiedCheckpoint());
    state.setCurrentJustifiedCheckpoint(unrealisedCheckpoints.getUnrealizedJustifiedCheckpoint());
    state.setFinalizedCheckpoint(unrealisedCheckpoints.getUnrealizedFinalizedCheckpoint());
  }

  @Override
  public void processInactivityUpdates(
      final MutableBeaconState state, final ValidatorStatuses validatorStatuses) {
    // Do nothing by default.
  }

  @Override
  public void processRewardsAndPenalties(
      MutableBeaconState state, ValidatorStatuses validatorStatuses)
      throws EpochProcessingException {
    try {
      if (beaconStateAccessors.getCurrentEpoch(state).equals(SpecConfig.GENESIS_EPOCH)) {
        return;
      }

      RewardAndPenaltyDeltas attestationDeltas =
          getRewardAndPenaltyDeltas(state, validatorStatuses);

      applyDeltas(state, attestationDeltas);
    } catch (IllegalArgumentException e) {
      throw new EpochProcessingException(e);
    }
  }

  protected void applyDeltas(
      final MutableBeaconState state, final RewardAndPenaltyDeltas attestationDeltas) {
    final SszMutableUInt64List balances = state.getBalances();
    // To optimize performance, calculate validator size once outside of the loop
    int validatorsCount = state.getValidators().size();
    for (int i = 0; i < validatorsCount; i++) {
      final RewardAndPenalty delta = attestationDeltas.getDelta(i);
      balances.setElement(
          i, balances.getElement(i).plus(delta.getReward()).minusMinZero(delta.getPenalty()));
    }
  }

  /** Processes validator registry updates */
  @Override
  public void processRegistryUpdates(MutableBeaconState state, List<ValidatorStatus> statuses)
      throws EpochProcessingException {
    try {

      // Process activation eligibility and ejections
      SszMutableList<Validator> validators = state.getValidators();
      final UInt64 currentEpoch = beaconStateAccessors.getCurrentEpoch(state);
      final UInt64 finalizedEpoch = state.getFinalizedCheckpoint().getEpoch();
      for (int index = 0; index < validators.size(); index++) {
        final ValidatorStatus status = statuses.get(index);

        // Slightly optimised form of isEligibleForActivationQueue to avoid accessing the
        // state for the majority of validators.  Can't be eligible for activation if already active
        // or if effective balance is too low.  Only get the validator if both those checks pass to
        // confirm it isn't already in the queue.
        if (!status.isActiveInCurrentEpoch()
            && status
                .getCurrentEpochEffectiveBalance()
                .equals(specConfig.getMaxEffectiveBalance())) {
          final Validator validator = validators.get(index);
          if (validator.getActivationEligibilityEpoch().equals(SpecConfig.FAR_FUTURE_EPOCH)) {
            validators.set(
                index, validator.withActivationEligibilityEpoch(currentEpoch.plus(UInt64.ONE)));
          }
        }

        if (status.isActiveInCurrentEpoch()
            && status
                .getCurrentEpochEffectiveBalance()
                .isLessThanOrEqualTo(specConfig.getEjectionBalance())) {
          beaconStateMutators.initiateValidatorExit(state, index);
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
                    return validatorsUtil.isEligibleForActivation(finalizedEpoch, validator);
                  })
              .boxed()
              .sorted(
                  (index1, index2) -> {
                    int comparisonResult =
                        state
                            .getValidators()
                            .get(index1)
                            .getActivationEligibilityEpoch()
                            .compareTo(
                                state.getValidators().get(index2).getActivationEligibilityEpoch());
                    if (comparisonResult == 0) {
                      return index1.compareTo(index2);
                    } else {
                      return comparisonResult;
                    }
                  })
              .collect(Collectors.toList());

      // Dequeued validators for activation up to churn limit (without resetting activation epoch)
      int churnLimit = beaconStateAccessors.getValidatorChurnLimit(state).intValue();
      int sublistSize = Math.min(churnLimit, activationQueue.size());
      for (Integer index : activationQueue.subList(0, sublistSize)) {
        state
            .getValidators()
            .update(
                index,
                validator ->
                    validator.withActivationEpoch(
                        miscHelpers.computeActivationExitEpoch(currentEpoch)));
      }
    } catch (IllegalArgumentException e) {
      throw new EpochProcessingException(e);
    }
  }

  /** Processes slashings */
  @Override
  public void processSlashings(
      MutableBeaconState state, final ValidatorStatuses validatorStatuses) {
    final UInt64 totalBalance =
        validatorStatuses.getTotalBalances().getCurrentEpochActiveValidators();
    final UInt64 epoch = beaconStateAccessors.getCurrentEpoch(state);
    final UInt64 adjustedTotalSlashingBalance =
        state
            .getSlashings()
            .streamUnboxed()
            .reduce(UInt64.ZERO, UInt64::plus)
            .times(getProportionalSlashingMultiplier())
            .min(totalBalance);

    final List<ValidatorStatus> validatorStatusList = validatorStatuses.getStatuses();
    final int halfEpochsPerSlashingsVector = specConfig.getEpochsPerSlashingsVector() / 2;
    for (int index = 0; index < validatorStatusList.size(); index++) {
      final ValidatorStatus status = validatorStatusList.get(index);
      if (status.isSlashed()
          && epoch.plus(halfEpochsPerSlashingsVector).equals(status.getWithdrawableEpoch())) {
        final UInt64 increment = specConfig.getEffectiveBalanceIncrement();
        final UInt64 penaltyNumerator =
            status
                .getCurrentEpochEffectiveBalance()
                .dividedBy(increment)
                .times(adjustedTotalSlashingBalance);
        final UInt64 penalty = penaltyNumerator.dividedBy(totalBalance).times(increment);
        beaconStateMutators.decreaseBalance(state, index, penalty);
      }
    }
  }

  protected int getProportionalSlashingMultiplier() {
    return specConfig.getProportionalSlashingMultiplier();
  }

  @Override
  public void processEth1DataReset(final MutableBeaconState state) {
    final UInt64 nextEpoch = beaconStateAccessors.getCurrentEpoch(state).plus(1);
    // Reset eth1 data votes
    if (nextEpoch.mod(specConfig.getEpochsPerEth1VotingPeriod()).equals(UInt64.ZERO)) {
      state.getEth1DataVotes().clear();
    }
  }

  @Override
  public void processEffectiveBalanceUpdates(
      final MutableBeaconState state, final List<ValidatorStatus> statuses) {
    // Update effective balances with hysteresis
    SszMutableList<Validator> validators = state.getValidators();
    SszUInt64List balances = state.getBalances();
    for (int index = 0; index < validators.size(); index++) {
      ValidatorStatus status = statuses.get(index);
      UInt64 balance = balances.getElement(index);

      final UInt64 hysteresisIncrement =
          specConfig.getEffectiveBalanceIncrement().dividedBy(specConfig.getHysteresisQuotient());
      final UInt64 currentEffectiveBalance = status.getCurrentEpochEffectiveBalance();
      if (shouldDecreaseEffectiveBalance(balance, hysteresisIncrement, currentEffectiveBalance)
          || shouldIncreaseEffectiveBalance(
              balance, hysteresisIncrement, currentEffectiveBalance)) {
        Validator validator = validators.get(index);
        final UInt64 newEffectiveBalance =
            balance
                .minus(balance.mod(specConfig.getEffectiveBalanceIncrement()))
                .min(specConfig.getMaxEffectiveBalance());
        BeaconStateCache.getTransitionCaches(state)
            .getProgressiveTotalBalances()
            .onEffectiveBalanceChange(status, newEffectiveBalance);
        validators.set(index, validator.withEffectiveBalance(newEffectiveBalance));
      }
    }
  }

  private boolean shouldIncreaseEffectiveBalance(
      final UInt64 balance,
      final UInt64 hysteresisIncrement,
      final UInt64 currentEffectiveBalance) {
    final UInt64 upwardThreshold =
        hysteresisIncrement.times(specConfig.getHysteresisUpwardMultiplier());
    // This condition doesn't match the spec but is an optimisation to avoid creating a new
    // validator with the same effective balance when it's already at the maximum.
    return !currentEffectiveBalance.equals(specConfig.getMaxEffectiveBalance())
        && currentEffectiveBalance.plus(upwardThreshold).isLessThan(balance);
  }

  private boolean shouldDecreaseEffectiveBalance(
      final UInt64 balance,
      final UInt64 hysteresisIncrement,
      final UInt64 currentEffectiveBalance) {
    final UInt64 downwardThreshold =
        hysteresisIncrement.times(specConfig.getHysteresisDownwardMultiplier());
    return balance.plus(downwardThreshold).isLessThan(currentEffectiveBalance);
  }

  @Override
  public void processSlashingsReset(final MutableBeaconState state) {
    final UInt64 nextEpoch = beaconStateAccessors.getCurrentEpoch(state).plus(1);
    // Reset slashings
    int index = nextEpoch.mod(specConfig.getEpochsPerSlashingsVector()).intValue();
    state.getSlashings().setElement(index, UInt64.ZERO);
  }

  @Override
  public void processRandaoMixesReset(final MutableBeaconState state) {
    final UInt64 currentEpoch = beaconStateAccessors.getCurrentEpoch(state);
    final UInt64 nextEpoch = currentEpoch.plus(1);
    // Set randao mix
    final int randaoIndex = nextEpoch.mod(specConfig.getEpochsPerHistoricalVector()).intValue();
    state
        .getRandaoMixes()
        .setElement(randaoIndex, beaconStateAccessors.getRandaoMix(state, currentEpoch));
  }

  @Override
  public void processHistoricalRootsUpdate(final MutableBeaconState state) {
    final UInt64 nextEpoch = beaconStateAccessors.getCurrentEpoch(state).plus(1);
    // Set historical root accumulator
    if (nextEpoch
        .mod(specConfig.getSlotsPerHistoricalRoot() / specConfig.getSlotsPerEpoch())
        .equals(UInt64.ZERO)) {
      HistoricalBatch historicalBatch =
          schemaDefinitions
              .getHistoricalBatchSchema()
              .create(state.getBlockRoots(), state.getStateRoots());
      state.getHistoricalRoots().appendElement(historicalBatch.hashTreeRoot());
    }
  }
}
