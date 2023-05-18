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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.migrated.AttestationRewardsData;
import tech.pegasys.teku.api.migrated.TotalAttestationReward;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.EpochProcessor;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.RewardAndPenalty.RewardComponent;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.RewardAndPenaltyDeltas;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.status.TotalBalances;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.status.ValidatorStatus;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.status.ValidatorStatusFactory;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.status.ValidatorStatuses;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class EpochAttestationRewardsCalculatorTest {

  private final Spec spec = spy(TestSpecFactory.createMainnetCapella());
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final SpecVersion specVersion = mock(SpecVersion.class);
  private final EpochProcessor epochProcessor = mock(EpochProcessor.class);
  private final ValidatorStatusFactory validatorStatusFactory = mock(ValidatorStatusFactory.class);
  private EpochAttestationRewardsCalculator calculator;

  @BeforeEach
  public void setUp() {
    when(specVersion.getEpochProcessor()).thenReturn(epochProcessor);
    when(specVersion.getValidatorStatusFactory()).thenReturn(validatorStatusFactory);
  }

  @Test
  public void totalAttestationRewardShouldMapEpochProcessorResultCorrectly() {
    final BeaconState beaconState = dataStructureUtil.randomBeaconState();
    final SszList<Validator> validators = beaconState.getValidators();
    final List<String> validatorPubKeys =
        List.of(
            validators.get(0).getPublicKey().toHexString(),
            validators.get(1).getPublicKey().toHexString());

    final ValidatorStatus validatorStatus = mock(ValidatorStatus.class);
    when(validatorStatus.isEligibleValidator()).thenReturn(true);
    final ValidatorStatuses validatorStatuses =
        new ValidatorStatuses(List.of(validatorStatus, validatorStatus), mock(TotalBalances.class));
    when(validatorStatusFactory.createValidatorStatuses(any())).thenReturn(validatorStatuses);

    final RewardAndPenaltyDeltas expectedTotalRewards =
        RewardAndPenaltyDeltas.detailed(validatorPubKeys.size());
    for (int i = 0; i < validatorPubKeys.size(); i++) {
      expectedTotalRewards.getDelta(i).reward(RewardComponent.HEAD, UInt64.ONE);
      expectedTotalRewards.getDelta(i).reward(RewardComponent.SOURCE, UInt64.ONE);
      expectedTotalRewards.getDelta(i).reward(RewardComponent.TARGET, UInt64.ONE);
    }

    when(epochProcessor.getRewardAndPenaltyDeltas(any(), any(), any()))
        .thenReturn(expectedTotalRewards);

    calculator = new EpochAttestationRewardsCalculator(specVersion, beaconState, validatorPubKeys);
    final AttestationRewardsData rewards = calculator.calculate();

    final List<TotalAttestationReward> totalAttestationRewards =
        rewards.getTotalAttestationRewards();
    assertThat(totalAttestationRewards).hasSize(validatorPubKeys.size());

    for (int i = 0; i < validatorPubKeys.size(); i++) {
      final TotalAttestationReward totalAttestationReward = totalAttestationRewards.get(i);
      assertThat(totalAttestationReward.getValidatorIndex()).isEqualTo(i);
      assertThat(totalAttestationReward.getHead()).isEqualTo(1);
      assertThat(totalAttestationReward.getSource()).isEqualTo(1);
      assertThat(totalAttestationReward.getTarget()).isEqualTo(1);
    }
  }
}
