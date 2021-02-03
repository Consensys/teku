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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.spec.constants.SpecConstants;

public class CommitteeUtilTest {
  final SpecConstants specConstants = mock(SpecConstants.class);
  CommitteeUtil committeeUtil = new CommitteeUtil(specConstants);

  @Test
  void aggregatorModulo_boundaryTests() {
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
}
