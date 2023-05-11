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

package tech.pegasys.teku.api;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.spec.constants.IncentivizationWeights.PROPOSER_WEIGHT;
import static tech.pegasys.teku.spec.constants.IncentivizationWeights.WEIGHT_DENOMINATOR;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.api.exceptions.BadRequestException;
import tech.pegasys.teku.api.migrated.SyncCommitteeRewardData;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregate;
import tech.pegasys.teku.spec.datastructures.metadata.BlockAndMetaData;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class RewardCalculatorTest {
  private final Spec spec = TestSpecFactory.createMinimalCapella();
  private final DataStructureUtil data = new DataStructureUtil(spec);

  private final RewardCalculator calculator = new RewardCalculator(spec);

  private final BLSPublicKey publicKey = data.randomPublicKey();

  @Test
  void getCommitteeIndices_withSpecifiedValidators() {
    final BeaconState state = data.randomBeaconState(32);

    final List<BLSPublicKey> stateValidators =
        state.getValidators().stream().map(Validator::getPublicKey).collect(toList());
    final List<BLSPublicKey> committeeKeys =
        List.of(
            stateValidators.get(1),
            stateValidators.get(4),
            stateValidators.get(6),
            stateValidators.get(7),
            stateValidators.get(9),
            stateValidators.get(11));

    final Set<String> validators =
        Set.of(committeeKeys.get(0).toHexString(), committeeKeys.get(2).toHexString(), "4");
    final Map<Integer, Integer> committeeIndices =
        calculator.getCommitteeIndices(committeeKeys, validators, state);

    assertThat(committeeIndices).containsExactlyInAnyOrderEntriesOf(Map.of(0, 1, 1, 4, 2, 6));
  }

  @Test
  void getCommitteeIndices_withNoSpecifiedValidators() {
    final BeaconState state = data.randomBeaconState(32);

    final List<BLSPublicKey> stateValidators =
        state.getValidators().stream().map(Validator::getPublicKey).collect(toList());
    final List<BLSPublicKey> committeeKeys =
        List.of(
            stateValidators.get(1),
            stateValidators.get(4),
            stateValidators.get(6),
            stateValidators.get(7),
            stateValidators.get(9),
            stateValidators.get(11));

    final Map<Integer, Integer> committeeIndices =
        calculator.getCommitteeIndices(committeeKeys, Set.of(), state);

    assertThat(committeeIndices)
        .containsExactlyInAnyOrderEntriesOf(Map.of(0, 1, 1, 4, 2, 6, 3, 7, 4, 9, 5, 11));
  }

  @Test
  void getCommitteeIndices_withSpecifiedValidatorsNotInCommittee() {
    final BeaconState state = data.randomBeaconState(32);

    final List<BLSPublicKey> stateValidators =
        state.getValidators().stream().map(Validator::getPublicKey).collect(toList());
    final List<BLSPublicKey> committeeKeys =
        List.of(
            stateValidators.get(1),
            stateValidators.get(4),
            stateValidators.get(6),
            stateValidators.get(7),
            stateValidators.get(9),
            stateValidators.get(11));

    final Set<String> validators =
        Set.of(
            data.randomPublicKey().toHexString(), // Validator not in committee
            "2", // Validator not in committee
            committeeKeys.get(5).toHexString());

    assertThatThrownBy(() -> calculator.getCommitteeIndices(committeeKeys, validators, state))
        .isInstanceOf(BadRequestException.class)
        .hasMessageMatching("'0x[0-9a-f]+' " + "was not found in the committee");
  }

  @Test
  void calculateAttestationRewards_shouldCalculateRewards() {
    final long result = calculator.calculateAttestationRewards();
    assertThat(result).isEqualTo(0L);
  }

  @Test
  void getBlockRewardData_shouldRejectPreAltair() {
    final RewardCalculator rewardCalculator =
        new RewardCalculator(TestSpecFactory.createMinimalPhase0());
    final DataStructureUtil dataStructureUtil =
        new DataStructureUtil(TestSpecFactory.createMinimalPhase0());
    final BlockAndMetaData blockAndMetaData = mock(BlockAndMetaData.class);
    when(blockAndMetaData.getData()).thenReturn(dataStructureUtil.randomSignedBeaconBlock());
    assertThatThrownBy(
            () -> rewardCalculator.getBlockRewardData(blockAndMetaData, mock(BeaconState.class)))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("is pre altair,");
  }

  @Test
  void getSyncCommitteeRewardsFromBlockId_shouldRejectPreAltair() {
    final RewardCalculator rewardCalculator =
        new RewardCalculator(TestSpecFactory.createMinimalPhase0());
    final DataStructureUtil dataStructureUtil =
        new DataStructureUtil(TestSpecFactory.createMinimalPhase0());
    final BlockAndMetaData blockAndMetaData =
        new BlockAndMetaData(
            dataStructureUtil.randomSignedBeaconBlock(),
            spec.getGenesisSpec().getMilestone(),
            false,
            true,
            false);
    assertThatThrownBy(
            () ->
                rewardCalculator.getSyncCommitteeRewardData(
                    Set.of(), blockAndMetaData, mock(BeaconState.class)))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("is pre altair,");
  }

  @Test
  void test_getProposerReward() {
    final BeaconState state = data.randomBeaconState();
    final long participantReward = spec.getSyncCommitteeParticipantReward(state).longValue();
    final long proposerWeight = PROPOSER_WEIGHT.longValue();
    final long weightDenominator = WEIGHT_DENOMINATOR.longValue();
    final long expected = participantReward * proposerWeight / (weightDenominator - proposerWeight);

    assertThat(calculator.getProposerReward(state)).isEqualTo(expected);
  }

  @Test
  void calculateProposerSlashingsRewards_shouldCalculateRewards() {
    final BeaconBlockAndState blockAndState = data.randomBlockAndStateWithValidatorLogic(16);
    final long result =
        calculator.calculateProposerSlashingsRewards(
            blockAndState.getBlock(), blockAndState.getState());
    assertThat(result).isEqualTo(62500000L);
  }

  @Test
  void calculateAttesterSlashingsRewards_shouldCalculateRewards() {
    final BeaconBlockAndState blockAndState = data.randomBlockAndStateWithValidatorLogic(100);
    final long result =
        calculator.calculateAttesterSlashingsRewards(
            blockAndState.getBlock(), blockAndState.getState());
    assertThat(result).isEqualTo(62500000L);
  }

  @Test
  void calculateProposerSyncAggregateBlockRewards_manySyncAggregateIndices() {
    final long reward = 1234L;
    final int[] participantIndices = new int[] {0, 3, 4, 7, 16, 17, 20, 23, 25, 26, 29, 30};
    final SyncAggregate syncAggregate = data.randomSyncAggregate(participantIndices);

    final long syncAggregateBlockRewards =
        calculator.calculateProposerSyncAggregateBlockRewards(reward, syncAggregate);
    assertThat(syncAggregateBlockRewards).isEqualTo(reward * participantIndices.length);
  }

  @Test
  void getSyncCommitteeRewardsFromBlockId_noSpecifiedValidators() {
    final SignedBlockAndState blockAndState = data.randomSignedBlockAndStateWithValidatorLogic(16);
    final BlockAndMetaData blockAndMetaData =
        new BlockAndMetaData(
            blockAndState.getBlock(), spec.getGenesisSpec().getMilestone(), false, true, false);

    final SyncCommitteeRewardData expectedOutput = new SyncCommitteeRewardData(false, false);

    final SyncCommitteeRewardData rewardData =
        calculator.getSyncCommitteeRewardData(Set.of(), blockAndMetaData, blockAndState.getState());
    assertThat(rewardData).isEqualTo(expectedOutput);
  }

  @ParameterizedTest
  @MethodSource("validatorsListTestCases")
  void checkValidatorsList_shouldAcceptValidInput(
      final String validatorString, final Optional<String> maybeErrorContains) {
    List<BLSPublicKey> committeeKeys =
        List.of(data.randomPublicKey(), data.randomPublicKey(), publicKey);
    if (maybeErrorContains.isPresent()) {
      assertThatThrownBy(
              () -> calculator.checkValidatorsList(committeeKeys, Set.of(validatorString)))
          .isInstanceOf(BadRequestException.class)
          .hasMessageContaining(maybeErrorContains.get());
    } else {
      calculator.checkValidatorsList(committeeKeys, Set.of(validatorString));
    }
  }

  @Test
  void calculateSyncCommitteeRewards_shouldNotChangeValuesWhenAggregateEmpty() {
    final SyncCommitteeRewardData data = mock(SyncCommitteeRewardData.class);
    assertThat(calculator.calculateSyncCommitteeRewards(Map.of(1,1), 1L, Optional.empty(), data)).isEqualTo(data);
    verifyNoMoreInteractions(data);
  }

  @Test
  void calculateSyncCommitteeRewards_shouldAdjustVRewards() {
    final SyncCommitteeRewardData rewardData = new SyncCommitteeRewardData(false,false);
    rewardData.increaseReward(1, 1L);
    rewardData.decreaseReward(2,-1L);
    final SyncAggregate aggregate = data.randomSyncAggregate(1);
    assertThat(calculator.calculateSyncCommitteeRewards(Map.of(1,1, 2,2), 1L, Optional.of(aggregate), rewardData)).isEqualTo(rewardData);
  }

  static Stream<Arguments> validatorsListTestCases() {
    final String missingKey =
        "0x8f9335f7d6b19469d5c8880df50bf41c01f476411d5b69a8b121255347f1c0b8400ba31a63010b229080240589ad2421";
    ArrayList<Arguments> args = new ArrayList<>();
    args.add(Arguments.of("0", Optional.empty()));
    args.add(Arguments.of("1", Optional.empty()));
    args.add(Arguments.of("2", Optional.empty()));
    args.add(
        Arguments.of(
            "0xa4654ac3105a58c7634031b5718c4880c87300f72091cfbc69fe490b71d93a671e00e80a388e1ceb8ea1de112003e976",
            Optional.empty()));
    args.add(Arguments.of("a1", Optional.of("could not be read as a number")));
    args.add(Arguments.of("-1", Optional.of("range 0 - 3")));
    args.add(Arguments.of("4", Optional.of("range 0 - 3")));
    args.add(Arguments.of(missingKey, Optional.of(" was not found in the committee")));

    return args.stream();
  }
}
