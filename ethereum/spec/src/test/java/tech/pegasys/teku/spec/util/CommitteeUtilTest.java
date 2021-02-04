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

package tech.pegasys.teku.spec.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.spec.constants.SpecConstants;
import tech.pegasys.teku.spec.util.genesis.CommitteeUtilGenesis;

public class CommitteeUtilTest {
  final SpecConstants specConstants = mock(SpecConstants.class);
  CommitteeUtil committeeUtil = new CommitteeUtilGenesis(specConstants);

  @Test
  void aggregatorModulo_boundaryTest() {
    when(specConstants.getTargetAggregatorsPerCommittee()).thenReturn(100);
    // invalid cases technically
    assertThat(committeeUtil.getAggregatorModulo(Integer.MIN_VALUE)).isEqualTo(1);
    assertThat(committeeUtil.getAggregatorModulo(-1)).isEqualTo(1);

    // practical lower bounds
    assertThat(committeeUtil.getAggregatorModulo(0)).isEqualTo(1);
    assertThat(committeeUtil.getAggregatorModulo(199)).isEqualTo(1);

    // upper bound - valid
    assertThat(committeeUtil.getAggregatorModulo(Integer.MAX_VALUE)).isEqualTo(21474836);
  }

  @Test
  void aggregatorModulo_samples() {
    when(specConstants.getTargetAggregatorsPerCommittee()).thenReturn(100);

    assertThat(committeeUtil.getAggregatorModulo(200)).isEqualTo(2);
    assertThat(committeeUtil.getAggregatorModulo(300)).isEqualTo(3);
    assertThat(committeeUtil.getAggregatorModulo(1000)).isEqualTo(10);
    assertThat(committeeUtil.getAggregatorModulo(100000)).isEqualTo(1000);
  }

  @Test
  void computeShuffledIndex_boundaryTest() {
    assertThatThrownBy(() -> committeeUtil.computeShuffledIndex(2, 1, Bytes32.ZERO))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void computeShuffledIndex_samples() {
    when(specConstants.getShuffleRoundCount()).thenReturn(90);
    assertThat(committeeUtil.computeShuffledIndex(320, 2048, Bytes32.ZERO)).isEqualTo(0);
    assertThat(committeeUtil.computeShuffledIndex(1291, 2048, Bytes32.ZERO)).isEqualTo(1);
    assertThat(committeeUtil.computeShuffledIndex(933, 2048, Bytes32.ZERO)).isEqualTo(2047);
  }

  @Test
  void computeShuffledIndex_testListShuffleAndShuffledIndexCompatibility() {
    when(specConstants.getShuffleRoundCount()).thenReturn(10);
    Bytes32 seed = Bytes32.ZERO;
    int index_count = 3333;
    int[] indexes = IntStream.range(0, index_count).toArray();

    tech.pegasys.teku.datastructures.util.CommitteeUtil.shuffle_list(indexes, seed);
    assertThat(indexes)
        .isEqualTo(
            IntStream.range(0, index_count)
                .map(i -> committeeUtil.computeShuffledIndex(i, indexes.length, seed))
                .toArray());
  }
}
