/*
 * Copyright ConsenSys Software Inc., 2023
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

package tech.pegasys.teku.api.rewards;

import static java.util.stream.Collectors.toList;

import java.util.List;
import java.util.stream.IntStream;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.api.migrated.AttestationRewardsData;
import tech.pegasys.teku.api.migrated.IdealAttestationReward;
import tech.pegasys.teku.api.migrated.TotalAttestationReward;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.EpochProcessor;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.RewardAndPenaltyDeltas;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.RewardsAndPenaltiesCalculator;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.status.ValidatorStatuses;

public class EpochAttestationRewardsCalculator {

  private static final Logger LOG = LogManager.getLogger();

  private final BeaconState state;
  private final EpochProcessor epochProcessor;
  private final ValidatorStatuses validatorStatuses;
  private final List<Integer> validatorIndexes;

  public EpochAttestationRewardsCalculator(
      final SpecVersion specVersion,
      final BeaconState state,
      final List<String> validatorPublicKeys) {
    this.state = state;
    this.epochProcessor = specVersion.getEpochProcessor();
    this.validatorStatuses = specVersion.getValidatorStatusFactory().createValidatorStatuses(state);
    this.validatorIndexes = getValidatorIndexes(state, validatorPublicKeys);
  }

  private List<Integer> getValidatorIndexes(
      final BeaconState state, final List<String> validatorPublicKeys) {
    final SszList<Validator> allValidators = state.getValidators();
    return IntStream.range(0, allValidators.size())
        .filter(
            i ->
                validatorPublicKeys.isEmpty()
                    || validatorPublicKeys.contains(
                    allValidators.get(i).getPublicKey().toHexString()))
        .filter(i -> validatorStatuses.getStatuses().get(i).isEligibleValidator())
        .boxed()
        .collect(toList());
  }

  public AttestationRewardsData calculate() {
    try {
      final List<IdealAttestationReward> idealAttestationRewards = idealAttestationRewards();
      final List<TotalAttestationReward> totalAttestationRewards = totalAttestationRewards();
      return new AttestationRewardsData(idealAttestationRewards, totalAttestationRewards);
    } catch (RuntimeException ex) {
      LOG.error("Error calculating detailed rewards and penalties", ex);
      throw ex;
    }
  }

  public List<IdealAttestationReward> idealAttestationRewards() {
    return List.of();
  }

  private List<TotalAttestationReward> totalAttestationRewards() {
    final RewardAndPenaltyDeltas totalRewardAndPenaltyDeltas =
        epochProcessor.getRewardAndPenaltyDeltas(state, validatorStatuses,
            RewardsAndPenaltiesCalculator::getDetailedDeltas);

    return validatorIndexes.stream()
        .map(i -> new ImmutablePair<>(i, totalRewardAndPenaltyDeltas.getDelta(i)))
        .map(p -> new TotalAttestationReward(p.left, p.right))
        .collect(toList());
  }
}
