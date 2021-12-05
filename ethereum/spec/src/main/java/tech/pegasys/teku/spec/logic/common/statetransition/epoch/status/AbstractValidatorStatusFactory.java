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

package tech.pegasys.teku.spec.logic.common.statetransition.epoch.status;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateCache;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateAccessors;
import tech.pegasys.teku.spec.logic.common.helpers.Predicates;
import tech.pegasys.teku.spec.logic.common.util.AttestationUtil;
import tech.pegasys.teku.spec.logic.common.util.BeaconStateUtil;

public abstract class AbstractValidatorStatusFactory implements ValidatorStatusFactory {
  protected final SpecConfig specConfig;
  protected final BeaconStateUtil beaconStateUtil;
  protected final AttestationUtil attestationUtil;
  protected final Predicates predicates;
  protected final BeaconStateAccessors beaconStateAccessors;

  protected AbstractValidatorStatusFactory(
      final SpecConfig specConfig,
      final BeaconStateUtil beaconStateUtil,
      final AttestationUtil attestationUtil,
      final Predicates predicates,
      final BeaconStateAccessors beaconStateAccessors) {
    this.specConfig = specConfig;
    this.beaconStateUtil = beaconStateUtil;
    this.attestationUtil = attestationUtil;
    this.predicates = predicates;
    this.beaconStateAccessors = beaconStateAccessors;
  }

  protected abstract void processParticipation(
      List<ValidatorStatus> statuses,
      BeaconState genericState,
      UInt64 previousEpoch,
      UInt64 currentEpoch);

  @Override
  public ValidatorStatuses createValidatorStatuses(final BeaconState state) {
    final SszList<Validator> validators = state.getValidators();

    final UInt64 currentEpoch = beaconStateAccessors.getCurrentEpoch(state);
    final UInt64 previousEpoch = beaconStateAccessors.getPreviousEpoch(state);

    final List<ValidatorStatus> statuses =
        validators.stream()
            .map(validator -> createValidatorStatus(validator, previousEpoch, currentEpoch))
            .collect(Collectors.toCollection(() -> new ArrayList<>(validators.size())));

    processParticipation(statuses, state, previousEpoch, currentEpoch);

    final TotalBalances totalBalances = createTotalBalances(statuses);
    BeaconStateCache.getTransitionCaches(state).setLatestTotalBalances(totalBalances);
    BeaconStateCache.getTransitionCaches(state)
        .getTotalActiveBalance()
        .get(currentEpoch, __ -> totalBalances.getCurrentEpochActiveValidators());

    return new ValidatorStatuses(statuses, totalBalances);
  }

  @Override
  public ValidatorStatus createValidatorStatus(
      final Validator validator, final UInt64 previousEpoch, final UInt64 currentEpoch) {

    return new ValidatorStatus(
        validator.isSlashed(),
        validator.getWithdrawable_epoch().isLessThanOrEqualTo(currentEpoch),
        validator.getEffective_balance(),
        predicates.isActiveValidator(validator, currentEpoch),
        predicates.isActiveValidator(validator, previousEpoch));
  }

  protected TotalBalances createTotalBalances(final List<ValidatorStatus> statuses) {
    UInt64 currentEpochActiveValidators = UInt64.ZERO;
    UInt64 previousEpochActiveValidators = UInt64.ZERO;
    UInt64 currentEpochSourceAttesters = UInt64.ZERO;
    UInt64 currentEpochTargetAttesters = UInt64.ZERO;
    UInt64 previousEpochSourceAttesters = UInt64.ZERO;
    UInt64 previousEpochTargetAttesters = UInt64.ZERO;
    UInt64 previousEpochHeadAttesters = UInt64.ZERO;

    for (ValidatorStatus status : statuses) {
      final UInt64 balance = status.getCurrentEpochEffectiveBalance();
      if (status.isActiveInCurrentEpoch()) {
        currentEpochActiveValidators = currentEpochActiveValidators.plus(balance);
      }
      if (status.isActiveInPreviousEpoch()) {
        previousEpochActiveValidators = previousEpochActiveValidators.plus(balance);
      }

      if (status.isSlashed()) {
        continue;
      }
      if (status.isCurrentEpochSourceAttester()) {
        currentEpochSourceAttesters = currentEpochSourceAttesters.plus(balance);
      }
      if (status.isCurrentEpochTargetAttester()) {
        currentEpochTargetAttesters = currentEpochTargetAttesters.plus(balance);
      }

      if (status.isPreviousEpochSourceAttester()) {
        previousEpochSourceAttesters = previousEpochSourceAttesters.plus(balance);
      }
      if (status.isPreviousEpochTargetAttester()) {
        previousEpochTargetAttesters = previousEpochTargetAttesters.plus(balance);
      }
      if (status.isPreviousEpochHeadAttester()) {
        previousEpochHeadAttesters = previousEpochHeadAttesters.plus(balance);
      }
    }
    return new TotalBalances(
        specConfig,
        currentEpochActiveValidators,
        previousEpochActiveValidators,
        currentEpochSourceAttesters,
        currentEpochTargetAttesters,
        previousEpochSourceAttesters,
        previousEpochTargetAttesters,
        previousEpochHeadAttesters);
  }

  protected boolean matchesEpochStartBlock(
      final BeaconState state, final UInt64 currentEpoch, final Bytes32 root) {
    return beaconStateAccessors.getBlockRoot(state, currentEpoch).equals(root);
  }
}
