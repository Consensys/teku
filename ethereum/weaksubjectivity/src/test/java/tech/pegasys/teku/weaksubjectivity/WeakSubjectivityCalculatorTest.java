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

package tech.pegasys.teku.weaksubjectivity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.spec.constants.EthConstants.ETH_TO_GWEI;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.CheckpointState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.weaksubjectivity.config.WeakSubjectivityConfig;

public class WeakSubjectivityCalculatorTest {
  private static final Spec SPEC = TestSpecFactory.createMainnetPhase0();

  @ParameterizedTest(name = "safetyDecay: {0}, avgBalance: {1}, validatorCount: {2}")
  @MethodSource("computeWeakSubjectivityParams")
  public void computeWeakSubjectivity(
      final UInt64 safetyDecay,
      final UInt64 avgActiveValidatorBalance,
      final int validatorCount,
      final int expectedResult) {
    final UInt64 totalActiveValidatorBalance = avgActiveValidatorBalance.times(validatorCount);
    final WeakSubjectivityConfig config =
        WeakSubjectivityConfig.builder().specProvider(SPEC).safetyDecay(safetyDecay).build();
    final WeakSubjectivityCalculator calculator = WeakSubjectivityCalculator.create(config);
    UInt64 result =
        calculator.computeWeakSubjectivityPeriod(
            SPEC.getGenesisSpec(), validatorCount, totalActiveValidatorBalance);
    assertThat(result).isEqualTo(UInt64.valueOf(expectedResult));
  }

  @ParameterizedTest(name = "safetyDecay: {0}, avgBalance: {1}, validatorCount: {2}")
  @MethodSource("computeWeakSubjectivityParams")
  public void isWithinWeakSubjectivityPeriod(
      final UInt64 safetyDecay,
      final UInt64 avgActiveValidatorBalance,
      final int validatorCount,
      final int expectedWeakSubjectivityPeriod) {
    final UInt64 totalActiveValidatorBalance = avgActiveValidatorBalance.times(validatorCount);
    final WeakSubjectivityCalculator calculator =
        new WeakSubjectivityCalculator(
            SPEC,
            safetyDecay,
            WeakSubjectivityCalculator.StateCalculator.createStaticCalculator(
                validatorCount, totalActiveValidatorBalance));

    final UInt64 finalizedEpoch = UInt64.valueOf(10_000);
    final CheckpointState finalizedCheckpoint = createMockCheckpointState(finalizedEpoch);
    final UInt64 minSafeEpoch = finalizedEpoch.plus(expectedWeakSubjectivityPeriod);

    // Check the minimum safe epoch
    final UInt64 minEpochStartSlot = SPEC.computeStartSlotAtEpoch(minSafeEpoch);
    assertThat(calculator.isWithinWeakSubjectivityPeriod(finalizedCheckpoint, minEpochStartSlot))
        .isTrue();
    final UInt64 minEpochLastSlot = SPEC.computeStartSlotAtEpoch(minSafeEpoch.plus(1)).minus(1);
    assertThat(calculator.isWithinWeakSubjectivityPeriod(finalizedCheckpoint, minEpochLastSlot))
        .isTrue();

    // Check first unsafe epoch
    final UInt64 minUnsafeSlot = SPEC.computeStartSlotAtEpoch(minSafeEpoch.plus(1));
    assertThat(calculator.isWithinWeakSubjectivityPeriod(finalizedCheckpoint, minUnsafeSlot))
        .isFalse();
    assertThat(
            calculator.isWithinWeakSubjectivityPeriod(finalizedCheckpoint, minUnsafeSlot.plus(1)))
        .isFalse();

    // Check subsequent epochs
    assertThat(
            calculator.isWithinWeakSubjectivityPeriod(finalizedCheckpoint, minUnsafeSlot.plus(100)))
        .isFalse();
    assertThat(
            calculator.isWithinWeakSubjectivityPeriod(finalizedCheckpoint, minUnsafeSlot.plus(999)))
        .isFalse();

    // Check earlier safe epochs
    final UInt64 earlierEpochSlot = SPEC.computeStartSlotAtEpoch(minSafeEpoch).minus(1);
    assertThat(calculator.isWithinWeakSubjectivityPeriod(finalizedCheckpoint, earlierEpochSlot))
        .isTrue();
    assertThat(
            calculator.isWithinWeakSubjectivityPeriod(
                finalizedCheckpoint, earlierEpochSlot.minus(100)))
        .isTrue();

    // Check finalized epoch
    final UInt64 finalizedEpochSlot = SPEC.computeStartSlotAtEpoch(finalizedEpoch);
    assertThat(calculator.isWithinWeakSubjectivityPeriod(finalizedCheckpoint, finalizedEpochSlot))
        .isTrue();
  }

  // Parameters from the table here:
  // https://github.com/ethereum/consensus-specs/blob/dev/specs/phase0/weak-subjectivity.md#compute_weak_subjectivity_period
  public static Stream<Arguments> computeWeakSubjectivityParams() {
    return Stream.of(
        Arguments.of(UInt64.valueOf(10), ETH_TO_GWEI.times(28), 32768, 504),
        Arguments.of(UInt64.valueOf(10), ETH_TO_GWEI.times(28), 65536, 752),
        Arguments.of(UInt64.valueOf(10), ETH_TO_GWEI.times(28), 131072, 1248),
        Arguments.of(UInt64.valueOf(10), ETH_TO_GWEI.times(28), 262144, 2241),
        Arguments.of(UInt64.valueOf(10), ETH_TO_GWEI.times(28), 524288, 2241),
        Arguments.of(UInt64.valueOf(10), ETH_TO_GWEI.times(28), 1048576, 2241),
        Arguments.of(UInt64.valueOf(10), ETH_TO_GWEI.times(32), 32768, 665),
        Arguments.of(UInt64.valueOf(10), ETH_TO_GWEI.times(32), 65536, 1075),
        Arguments.of(UInt64.valueOf(10), ETH_TO_GWEI.times(32), 131072, 1894),
        Arguments.of(UInt64.valueOf(10), ETH_TO_GWEI.times(32), 262144, 3532),
        Arguments.of(UInt64.valueOf(10), ETH_TO_GWEI.times(32), 524288, 3532),
        Arguments.of(UInt64.valueOf(10), ETH_TO_GWEI.times(32), 1048576, 3532));
  }

  private CheckpointState createMockCheckpointState(final UInt64 finalizedEpoch) {
    final CheckpointState checkpointState = mock(CheckpointState.class);
    final Checkpoint checkpoint = mock(Checkpoint.class);
    final BeaconState state = mock(BeaconState.class);

    when(checkpointState.getState()).thenReturn(state);
    when(checkpointState.getCheckpoint()).thenReturn(checkpoint);
    when(checkpointState.getEpoch()).thenReturn(finalizedEpoch);

    when(checkpoint.getEpoch()).thenReturn(finalizedEpoch);

    return checkpointState;
  }
}
