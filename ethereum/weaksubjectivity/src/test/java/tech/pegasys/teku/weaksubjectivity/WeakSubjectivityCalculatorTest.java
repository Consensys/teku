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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.datastructures.state.CheckpointState;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.util.config.Constants;
import tech.pegasys.teku.weaksubjectivity.config.WeakSubjectivityConfig;

public class WeakSubjectivityCalculatorTest {

  @BeforeAll
  public static void setup() {
    Constants.setConstants("mainnet");
  }

  @AfterAll
  public static void tearDown() {
    Constants.setConstants("minimal");
  }

  @ParameterizedTest(name = "safetyDecay: {0}, avgBalance: {1}, validatorCount: {2}")
  @MethodSource("computeWeakSubjectivityParams")
  public void computeWeakSubjectivity(
      final UInt64 safetyDecay,
      final UInt64 avgActiveValidatorBalance,
      final int validatorCount,
      final int expectedResult) {
    final WeakSubjectivityConfig config =
        WeakSubjectivityConfig.builder().safetyDecay(safetyDecay).build();
    final WeakSubjectivityCalculator calculator = WeakSubjectivityCalculator.create(config);
    UInt64 result =
        calculator.computeWeakSubjectivityPeriod(validatorCount, avgActiveValidatorBalance);
    assertThat(result).isEqualTo(UInt64.valueOf(expectedResult));
  }

  @ParameterizedTest(name = "safetyDecay: {0}, avgBalance: {1}, validatorCount: {2}")
  @MethodSource("computeWeakSubjectivityParams")
  public void isWithinWeakSubjectivityPeriod(
      final UInt64 safetyDecay,
      final UInt64 avgActiveValidatorBalance,
      final int validatorCount,
      final int expectedWeakSubjectivityPeriod) {
    final WeakSubjectivityCalculator calculator =
        new WeakSubjectivityCalculator(
            safetyDecay,
            WeakSubjectivityCalculator.StateCalculator.createStaticCalculator(
                validatorCount, avgActiveValidatorBalance));

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
  // https://github.com/ethereum/eth2.0-specs/blob/77e07adad499f34a8a909acd25d4d69cbc1c87a0/specs/phase0/weak-subjectivity.md#weak-subjectivity-period
  public static Stream<Arguments> computeWeakSubjectivityParams() {
    final UInt64 ethToGwei = UInt64.valueOf(1_000_000_000);
    return Stream.of(
        Arguments.of(UInt64.valueOf(10), ethToGwei.times(24), 8192, 262),
        Arguments.of(UInt64.valueOf(10), ethToGwei.times(24), 16384, 269),
        Arguments.of(UInt64.valueOf(10), ethToGwei.times(24), 32768, 282),
        Arguments.of(UInt64.valueOf(10), ethToGwei.times(28), 8192, 270),
        Arguments.of(UInt64.valueOf(10), ethToGwei.times(28), 16384, 284),
        Arguments.of(UInt64.valueOf(10), ethToGwei.times(28), 32768, 313),
        Arguments.of(UInt64.valueOf(10), ethToGwei.times(32), 8192, 358),
        Arguments.of(UInt64.valueOf(10), ethToGwei.times(32), 16384, 460),
        Arguments.of(UInt64.valueOf(10), ethToGwei.times(32), 32768, 665),
        Arguments.of(UInt64.valueOf(20), ethToGwei.times(24), 16384, 282),
        Arguments.of(UInt64.valueOf(20), ethToGwei.times(28), 16384, 313),
        Arguments.of(UInt64.valueOf(20), ethToGwei.times(32), 16384, 665),
        Arguments.of(UInt64.valueOf(33), ethToGwei.times(24), 16384, 300),
        Arguments.of(UInt64.valueOf(33), ethToGwei.times(28), 16384, 350),
        Arguments.of(UInt64.valueOf(33), ethToGwei.times(32), 16384, 931));
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
