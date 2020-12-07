/*
 * Copyright 2020 ConsenSys AG.
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
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.datastructures.state.CheckpointState;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.weaksubjectivity.config.WeakSubjectivityConfig;

public class WeakSubjectivityCalculatorTest {

  @ParameterizedTest(name = "validatorCount: {0}, safetyDecay: {1}")
  @MethodSource("computeWeakSubjectivityParams")
  public void computeWeakSubjectivity(
      final int validatorCount, final UInt64 safetyDecay, final int expectedResult) {
    final WeakSubjectivityConfig config =
        WeakSubjectivityConfig.builder().safetyDecay(safetyDecay).build();
    final WeakSubjectivityCalculator calculator = WeakSubjectivityCalculator.create(config);
    UInt64 result = calculator.computeWeakSubjectivityPeriod(validatorCount);
    assertThat(result).isEqualTo(UInt64.valueOf(expectedResult));
  }

  @ParameterizedTest(name = "validatorCount: {0}, safetyDecay: {1}")
  @MethodSource("computeWeakSubjectivityParams")
  public void isWithinWeakSubjectivityPeriod(
      final int validatorCount,
      final UInt64 safetyDecay,
      final int expectedWeakSubjectivityPeriod) {
    final WeakSubjectivityCalculator calculator =
        new WeakSubjectivityCalculator(safetyDecay, __ -> validatorCount);

    final UInt64 finalizedEpoch = UInt64.valueOf(10_000);
    final CheckpointState finalizedCheckpoint = createMockCheckpointState(finalizedEpoch);
    final UInt64 minSafeEpoch = finalizedEpoch.plus(expectedWeakSubjectivityPeriod);

    // Check the minimum safe epoch
    final UInt64 minEpochStartSlot = compute_start_slot_at_epoch(minSafeEpoch);
    assertThat(calculator.isWithinWeakSubjectivityPeriod(finalizedCheckpoint, minEpochStartSlot))
        .isTrue();
    final UInt64 minEpochLastSlot = compute_start_slot_at_epoch(minSafeEpoch.plus(1)).minus(1);
    assertThat(calculator.isWithinWeakSubjectivityPeriod(finalizedCheckpoint, minEpochLastSlot))
        .isTrue();

    // Check first unsafe epoch
    final UInt64 minUnsafeSlot = compute_start_slot_at_epoch(minSafeEpoch.plus(1));
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
    final UInt64 earlierEpochSlot = compute_start_slot_at_epoch(minSafeEpoch).minus(1);
    assertThat(calculator.isWithinWeakSubjectivityPeriod(finalizedCheckpoint, earlierEpochSlot))
        .isTrue();
    assertThat(
            calculator.isWithinWeakSubjectivityPeriod(
                finalizedCheckpoint, earlierEpochSlot.minus(100)))
        .isTrue();

    // Check finalized epoch
    final UInt64 finalizedEpochSlot = compute_start_slot_at_epoch(finalizedEpoch);
    assertThat(calculator.isWithinWeakSubjectivityPeriod(finalizedCheckpoint, finalizedEpochSlot))
        .isTrue();
  }

  // Parameters from the table here:
  // https://github.com/ethereum/eth2.0-specs/blob/weak-subjectivity-guide/specs/phase0/weak-subjectivity.md#calculating-the-weak-subjectivity-period
  public static Stream<Arguments> computeWeakSubjectivityParams() {
    return Stream.of(
        Arguments.of(1024, UInt64.valueOf(10), 268),
        Arguments.of(2048, UInt64.valueOf(10), 281),
        Arguments.of(4096, UInt64.valueOf(10), 307),
        Arguments.of(8192, UInt64.valueOf(10), 358),
        Arguments.of(16384, UInt64.valueOf(10), 460),
        Arguments.of(32768, UInt64.valueOf(10), 665),
        Arguments.of(65536, UInt64.valueOf(10), 1075),
        Arguments.of(131072, UInt64.valueOf(10), 1894),
        Arguments.of(262144, UInt64.valueOf(10), 3532),
        Arguments.of(524288, UInt64.valueOf(10), 3532));
  }

  private CheckpointState createMockCheckpointState(final UInt64 finalizedEpoch) {
    final CheckpointState checkpointState = mock(CheckpointState.class);
    final Checkpoint checkpoint = mock(Checkpoint.class);
    final BeaconState state = mock(BeaconState.class);

    when(checkpointState.getState()).thenReturn(state);
    when(checkpointState.getCheckpoint()).thenReturn(checkpoint);

    when(checkpoint.getEpoch()).thenReturn(finalizedEpoch);

    return checkpointState;
  }
}
