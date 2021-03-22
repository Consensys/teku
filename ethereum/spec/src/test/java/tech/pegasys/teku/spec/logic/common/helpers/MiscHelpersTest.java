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

package tech.pegasys.teku.spec.logic.common.helpers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.spec.config.SpecConfig;

class MiscHelpersTest {
  private final SpecConfig specConfig = mock(SpecConfig.class);
  private final MiscHelpers miscHelpers = new MiscHelpers(specConfig);

  @Test
  void computeShuffledIndex_boundaryTest() {
    assertThatThrownBy(() -> miscHelpers.computeShuffledIndex(2, 1, Bytes32.ZERO))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void computeShuffledIndex_samples() {
    when(specConfig.getShuffleRoundCount()).thenReturn(90);
    assertThat(miscHelpers.computeShuffledIndex(320, 2048, Bytes32.ZERO)).isEqualTo(0);
    assertThat(miscHelpers.computeShuffledIndex(1291, 2048, Bytes32.ZERO)).isEqualTo(1);
    assertThat(miscHelpers.computeShuffledIndex(933, 2048, Bytes32.ZERO)).isEqualTo(2047);
  }

  @Test
  void computeShuffledIndex_testListShuffleAndShuffledIndexCompatibility() {
    when(specConfig.getShuffleRoundCount()).thenReturn(10);
    Bytes32 seed = Bytes32.ZERO;
    int index_count = 3333;
    int[] indexes = IntStream.range(0, index_count).toArray();

    tech.pegasys.teku.spec.datastructures.util.CommitteeUtil.shuffle_list(indexes, seed);
    assertThat(indexes)
        .isEqualTo(
            IntStream.range(0, index_count)
                .map(i -> miscHelpers.computeShuffledIndex(i, indexes.length, seed))
                .toArray());
  }
}
