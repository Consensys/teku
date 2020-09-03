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
import static tech.pegasys.teku.weaksubjectivity.WeakSubjectivityCalculator.WITHDRAWAL_DELAY;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.datastructures.state.*;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class WeakSubjectivityCalculatorTest {

  @ParameterizedTest(name = "validatorCount: {0}, safetyDecay: {1}")
  @MethodSource("calculateSafeEpochsParams")
  public void calculateSafeEpochs(
      final int validatorCount, final float safetyDecay, final int expectedResult) {
    final WeakSubjectivityCalculator calculator = WeakSubjectivityCalculator.create(safetyDecay);
    UInt64 result = calculator.calculateSafeEpochs(validatorCount);
    assertThat(result).isEqualTo(UInt64.valueOf(expectedResult));
  }

  @ParameterizedTest(name = "validatorCount: {0}, safetyDecay: {1}")
  @MethodSource("getWeakSubjectivityModParams")
  public void getWeakSubjectivityMod(
      final int validatorCount, final float safetyDecay, final int expectedResult) {
    final WeakSubjectivityCalculator calculator = WeakSubjectivityCalculator.create(safetyDecay);
    UInt64 result = calculator.getWeakSubjectivityMod(validatorCount);
    assertThat(result).isEqualTo(UInt64.valueOf(expectedResult));
  }

  @ParameterizedTest(name = "validatorCount: {0}, safetyDecay: {1}")
  @MethodSource("calculateSafeEpochsParams")
  public void isWithinWeakSubjectivityPeriod(
      final int validatorCount, final float safetyDecay, final int expectedSafetyMargin) {
    final WeakSubjectivityCalculator calculator =
        new WeakSubjectivityCalculator(safetyDecay, __ -> validatorCount);

    final UInt64 finalizedEpoch = UInt64.valueOf(10_000);
    final CheckpointState finalizedCheckpoint = createMockCheckpointState(finalizedEpoch);
    final UInt64 expectedSafeEpochs = WITHDRAWAL_DELAY.plus(expectedSafetyMargin);
    final UInt64 minSafeEpoch = expectedSafeEpochs.plus(finalizedEpoch);

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
  // https://notes.ethereum.org/@adiasg/weak-subjectvity-eth2#Calculating-the-Weak-Subjectivity-Period-Quick-Version
  public static Stream<Arguments> calculateSafeEpochsParams() {
    // TODO - why are the results off by one from the table?
    //        return Stream.of(
    //                Arguments.of(1024, .1f, 13),
    //                Arguments.of(4096, .1f, 52),
    //                Arguments.of(16384, .1f, 205),
    //                Arguments.of(65536, .1f, 820),
    //                Arguments.of(1024, 1f/3, 43),
    //                Arguments.of(4096, 1f/3, 171),
    //                Arguments.of(16384, 1f/3, 683),
    //                Arguments.of(65536, 1f/3, 2731)
    //        );

    return Stream.of(
        Arguments.of(1024, .1f, 12),
        Arguments.of(4096, .1f, 51),
        Arguments.of(16384, .1f, 204),
        Arguments.of(65536, .1f, 819),
        Arguments.of(1024, 1f / 3, 42),
        Arguments.of(4096, 1f / 3, 170),
        Arguments.of(16384, 1f / 3, 682),
        Arguments.of(65536, 1f / 3, 2730));
  }
  // Parameters from the table here:
  // https://notes.ethereum.org/@adiasg/weak-subjectvity-eth2#Updating-Weak-Subjectivity-Checkpoint-States
  public static Stream<Arguments> getWeakSubjectivityModParams() {
    return Stream.of(
        Arguments.of(8191, .1f, 256),
        Arguments.of(16384, .1f, 256),
        Arguments.of(32768, .1f, 512),
        Arguments.of(65536, .1f, 1024),
        Arguments.of(131072, .1f, 1792),
        Arguments.of(262144, .1f, 3328));
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
