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

package tech.pegasys.teku.spec.logic.common.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.spec.constants.IncentivizationWeights.PROPOSER_WEIGHT;
import static tech.pegasys.teku.spec.constants.IncentivizationWeights.WEIGHT_DENOMINATOR;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.BlockContainer;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregate;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.block.AbstractBlockProcessor;
import tech.pegasys.teku.spec.logic.common.util.BlockRewardCalculatorUtil.BlockRewardData;
import tech.pegasys.teku.spec.logic.versions.altair.block.BlockProcessorAltair;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class BlockRewardCalculatorUtilTest {
  private final Spec spec = TestSpecFactory.createMinimalCapella();
  private final DataStructureUtil data = new DataStructureUtil(spec);

  private final BlockRewardCalculatorUtil calculator = new BlockRewardCalculatorUtil(spec);

  private final BlockProcessorAltair blockProcessorAltair = mock(BlockProcessorAltair.class);

  private static final long SINGLE_SLASHING_REWARD = 62500000L;

  @Test
  void getBlockRewardData_shouldOutputAttesterSlashings() {
    final BeaconState preState = data.randomBeaconState();
    final BeaconBlock block =
        data.blockBuilder(preState.getSlot().increment().longValue())
            .attesterSlashings(data.randomAttesterSlashings(1, 100))
            .build()
            .getImmediately();
    final BlockRewardData reward =
        calculator.calculateBlockRewards(block, blockProcessorAltair, preState);
    assertThat(reward.attesterSlashings()).isEqualTo(SINGLE_SLASHING_REWARD);
  }

  @Test
  void getBlockRewardData_shouldOutputProposerSlashings() {
    final BeaconState preState = data.randomBeaconState();
    final BeaconBlock block =
        data.blockBuilder(preState.getSlot().increment().longValue())
            .proposerSlashings(data.randomProposerSlashings(1, 100))
            .build()
            .getImmediately();
    final BlockRewardData reward =
        calculator.calculateBlockRewards(block, blockProcessorAltair, preState);
    assertThat(reward.proposerSlashings()).isEqualTo(SINGLE_SLASHING_REWARD);
  }

  @Test
  void getBlockRewardData_shouldOutputSyncAggregate() {
    final BeaconState preState = data.randomBeaconState();
    final BeaconBlock block =
        data.blockBuilder(preState.getSlot().increment().longValue())
            .syncAggregate(data.randomSyncAggregate(1, 2, 3, 4))
            .build()
            .getImmediately();
    final BlockRewardData reward =
        calculator.calculateBlockRewards(block, blockProcessorAltair, preState);
    assertThat(reward.syncAggregate()).isEqualTo(140L);
  }

  @Test
  void getBlockRewardData_shouldOutputAttestations() {
    // these values are invalid, but basically count each attestation as 1, so the attestation
    // reward equals the number of attestations
    when(blockProcessorAltair.createIndexedAttestationProvider(any(), any()))
        .thenReturn(mock(AbstractBlockProcessor.IndexedAttestationProvider.class));
    when(blockProcessorAltair.processAttestationProposerReward(any(), any(), any()))
        .thenReturn(Optional.of(UInt64.ONE));
    final BeaconState preState = data.randomBeaconState();
    final BeaconBlock block =
        data.blockBuilder(preState.getSlot().increment().longValue())
            .attestations(data.randomAttestations(10, preState.getSlot().decrement()))
            .build()
            .getImmediately();
    final BlockRewardData reward =
        calculator.calculateBlockRewards(block, blockProcessorAltair, preState);
    assertThat(reward.attestations()).isEqualTo(10L);
    verify(blockProcessorAltair, times(10)).processAttestationProposerReward(any(), any(), any());
  }

  @Test
  void getBlockRewardData_shouldOutputRewardData() {
    when(blockProcessorAltair.createIndexedAttestationProvider(any(), any()))
        .thenReturn(mock(AbstractBlockProcessor.IndexedAttestationProvider.class));
    when(blockProcessorAltair.processAttestationProposerReward(any(), any(), any()))
        .thenReturn(Optional.of(UInt64.ONE));
    final BeaconState preState = data.randomBeaconState();
    final BeaconBlock block =
        data.blockBuilder(preState.getSlot().increment().longValue())
            .executionPayload(data.randomExecutionPayload())
            .proposerSlashings(data.randomProposerSlashings(3, 100))
            .attesterSlashings(data.randomAttesterSlashings(2, 100))
            .attestations(data.randomAttestations(10, preState.getSlot().decrement()))
            .syncAggregate(data.randomSyncAggregate(1, 2, 3, 4))
            .build()
            .getImmediately();
    final BlockRewardData reward =
        calculator.calculateBlockRewards(block, blockProcessorAltair, preState);
    assertThat(reward)
        .isEqualTo(
            new BlockRewardData(
                block.getProposerIndex(),
                10L,
                140L,
                3 * SINGLE_SLASHING_REWARD,
                2 * SINGLE_SLASHING_REWARD));
    verify(blockProcessorAltair, times(10)).processAttestationProposerReward(any(), any(), any());
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
    assertThat(result).isEqualTo(SINGLE_SLASHING_REWARD);
  }

  @Test
  void calculateAttesterSlashingsRewards_shouldCalculateRewards() {
    final BeaconBlockAndState blockAndState = data.randomBlockAndStateWithValidatorLogic(100);
    final long result =
        calculator.calculateAttesterSlashingsRewards(
            blockAndState.getBlock(), blockAndState.getState());
    assertThat(result).isEqualTo(SINGLE_SLASHING_REWARD);
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
  void getBlockRewardData_shouldRejectPreAltair() {
    final BlockRewardCalculatorUtil rewardCalculator =
        new BlockRewardCalculatorUtil(TestSpecFactory.createMinimalPhase0());
    final DataStructureUtil dataStructureUtil =
        new DataStructureUtil(TestSpecFactory.createMinimalPhase0());
    final BlockContainer blockContainer = mock(BlockContainer.class);
    when(blockContainer.getBlock()).thenReturn(dataStructureUtil.randomBeaconBlock());
    assertThatThrownBy(
            () -> rewardCalculator.getBlockRewardData(blockContainer, mock(BeaconState.class)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("is pre altair,");
  }
}
