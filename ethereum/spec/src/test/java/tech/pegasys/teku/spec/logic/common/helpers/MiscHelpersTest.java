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

package tech.pegasys.teku.spec.logic.common.helpers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import it.unimi.dsi.fastutil.ints.IntList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigLoader;
import tech.pegasys.teku.spec.networks.Eth2Network;

class MiscHelpersTest {
  private final SpecConfig specConfig =
      SpecConfigLoader.loadConfig(Eth2Network.MINIMAL.configName());
  private final MiscHelpers miscHelpers = new MiscHelpers(specConfig);

  @Test
  void computeShuffledIndex_boundaryTest() {
    assertThatThrownBy(() -> miscHelpers.computeShuffledIndex(2, 1, Bytes32.ZERO))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void computeShuffledIndex_samples() {
    final SpecConfig specConfig = mock(SpecConfig.class);
    final MiscHelpers miscHelpers = new MiscHelpers(specConfig);

    when(specConfig.getShuffleRoundCount()).thenReturn(90);
    assertThat(miscHelpers.computeShuffledIndex(320, 2048, Bytes32.ZERO)).isEqualTo(0);
    assertThat(miscHelpers.computeShuffledIndex(1291, 2048, Bytes32.ZERO)).isEqualTo(1);
    assertThat(miscHelpers.computeShuffledIndex(933, 2048, Bytes32.ZERO)).isEqualTo(2047);
  }

  @Test
  void testListShuffleAndShuffledIndexCompatibility() {
    final SpecConfig specConfig = mock(SpecConfig.class);
    final MiscHelpers miscHelpers = new MiscHelpers(specConfig);

    when(specConfig.getShuffleRoundCount()).thenReturn(10);
    Bytes32 seed = Bytes32.ZERO;
    int indexCount = 3333;
    int[] indices = IntStream.range(0, indexCount).toArray();

    miscHelpers.shuffleList(indices, seed);
    assertThat(indices)
        .isEqualTo(
            IntStream.range(0, indexCount)
                .map(i -> miscHelpers.computeShuffledIndex(i, indices.length, seed))
                .toArray());
  }

  @Test
  void shuffleList_compareListAndArrayVersions() {
    final SpecConfig specConfig = mock(SpecConfig.class);
    final MiscHelpers miscHelpers = new MiscHelpers(specConfig);

    when(specConfig.getShuffleRoundCount()).thenReturn(10);
    Bytes32 seed = Bytes32.ZERO;
    int indexCount = 3333;

    int[] indices = IntStream.range(0, indexCount).toArray();
    miscHelpers.shuffleList(indices, seed);

    IntList indexList = IntList.of(IntStream.range(0, indexCount).toArray());
    final List<Integer> result = miscHelpers.shuffleList(indexList, seed);

    assertThat(result)
        .containsExactlyElementsOf(Arrays.stream(indices).boxed().collect(Collectors.toList()));
  }

  @ParameterizedTest(name = "n={0}")
  @MethodSource("getNValues")
  void isSlotAtNthEpochBoundary_withSkippedBlock(final int n) {
    final int nthStartSlot = miscHelpers.computeStartSlotAtEpoch(UInt64.valueOf(n)).intValue();

    final UInt64 genesisSlot = UInt64.ZERO;
    final UInt64 block1Slot = UInt64.valueOf(nthStartSlot + 1);
    final UInt64 block2Slot = block1Slot.plus(1);
    assertThat(miscHelpers.isSlotAtNthEpochBoundary(block1Slot, genesisSlot, n)).isTrue();
    assertThat(miscHelpers.isSlotAtNthEpochBoundary(block2Slot, block1Slot, n)).isFalse();
  }

  @ParameterizedTest(name = "n={0}")
  @MethodSource("getNValues")
  public void isSlotAtNthEpochBoundary_withSkippedEpochs_oneEpochAndSlotSkipped(final int n) {
    final int nthStartSlot = miscHelpers.computeStartSlotAtEpoch(UInt64.valueOf(n)).intValue();

    final UInt64 genesisSlot = UInt64.ZERO;
    final UInt64 block1Slot = UInt64.valueOf(nthStartSlot + specConfig.getSlotsPerEpoch() + 1);
    final UInt64 block2Slot = block1Slot.plus(1);

    assertThat(miscHelpers.isSlotAtNthEpochBoundary(block1Slot, genesisSlot, n)).isTrue();
    assertThat(miscHelpers.isSlotAtNthEpochBoundary(block2Slot, block1Slot, n)).isFalse();
  }

  @ParameterizedTest(name = "n={0}")
  @MethodSource("getNValues")
  public void isSlotAtNthEpochBoundary_withSkippedEpochs_nearlyNEpochsSkipped(final int n) {
    final int startSlotAt2N =
        miscHelpers.computeStartSlotAtEpoch(UInt64.valueOf(n * 2L)).intValue();

    final UInt64 genesisSlot = UInt64.ZERO;
    final UInt64 block1Slot = UInt64.valueOf(startSlotAt2N - 1);
    final UInt64 block2Slot = block1Slot.plus(1);
    final UInt64 block3Slot = block2Slot.plus(1);

    assertThat(miscHelpers.isSlotAtNthEpochBoundary(block1Slot, genesisSlot, n)).isTrue();
    assertThat(miscHelpers.isSlotAtNthEpochBoundary(block2Slot, block1Slot, n)).isTrue();
    assertThat(miscHelpers.isSlotAtNthEpochBoundary(block3Slot, block2Slot, n)).isFalse();
  }

  @ParameterizedTest(name = "n={0}")
  @MethodSource("getNValues")
  public void isSlotAtNthEpochBoundary_allSlotsFilled(final int n) {
    final UInt64 epochs = UInt64.valueOf(n * 3L);
    final UInt64 slots = epochs.times(specConfig.getSlotsPerEpoch());

    for (int i = 1; i <= slots.intValue(); i++) {
      final boolean expected = i % (n * specConfig.getSlotsPerEpoch()) == 0 && i != 0;

      final UInt64 blockSlot = UInt64.valueOf(i);
      assertThat(miscHelpers.isSlotAtNthEpochBoundary(blockSlot, blockSlot.minus(1), n))
          .describedAs("Block at %d should %sbe at epoch boundary", i, expected ? "" : "not ")
          .isEqualTo(expected);
    }
  }

  @Test
  public void isSlotAtNthEpochBoundary_invalidNParameter_zero() {
    assertThatThrownBy(() -> miscHelpers.isSlotAtNthEpochBoundary(UInt64.ONE, UInt64.ZERO, 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Parameter n must be greater than 0");
  }

  @Test
  public void isSlotAtNthEpochBoundary_invalidNParameter_negative() {
    assertThatThrownBy(() -> miscHelpers.isSlotAtNthEpochBoundary(UInt64.ONE, UInt64.ZERO, -1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Parameter n must be greater than 0");
  }

  @ParameterizedTest
  @MethodSource("getComputesSlotAtTimeArguments")
  public void computesSlotAtTime(final long currentTime, final UInt64 expectedSlot) {
    final UInt64 actualSlot =
        miscHelpers.computeSlotAtTime(UInt64.ZERO, UInt64.valueOf(currentTime));
    assertThat(actualSlot).isEqualTo(expectedSlot);
  }

  @ParameterizedTest
  @MethodSource("getComputesTimeAtSlotArguments")
  public void computesTimeAtSlot(final UInt64 slot, final long expectedTime) {
    final UInt64 actualTime = miscHelpers.computeTimeAtSlot(UInt64.ZERO, slot);
    assertThat(actualTime).isEqualTo(UInt64.valueOf(expectedTime));
  }

  public static Stream<Arguments> getComputesSlotAtTimeArguments() {
    // 6 seconds per slot
    return Stream.of(
        Arguments.of(6 * 10, UInt64.valueOf(10)),
        Arguments.of(6 * 4, UInt64.valueOf(4)),
        Arguments.of(0, UInt64.ZERO),
        Arguments.of(60253, UInt64.valueOf(10042)));
  }

  public static Stream<Arguments> getComputesTimeAtSlotArguments() {
    // 6 seconds per slot
    return Stream.of(
        Arguments.of(UInt64.valueOf(10), 6 * 10),
        Arguments.of(UInt64.valueOf(4), 6 * 4),
        Arguments.of(UInt64.ZERO, 0),
        Arguments.of(UInt64.valueOf(10042), 60252));
  }

  public static Stream<Arguments> getNValues() {
    return Stream.of(
        Arguments.of(1), Arguments.of(2), Arguments.of(3), Arguments.of(4), Arguments.of(5));
  }
}
