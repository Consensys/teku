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

package tech.pegasys.teku.pow.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.primitives.UnsignedLong;
import org.junit.jupiter.api.Test;

class Eth1DataCachePeriodCalculatorTest {
  @Test
  void shouldCalculateCachePeriodForMinimalConstantsFromFollowDistance() {
    assertThat(Eth1DataCachePeriodCalculator.calculateEth1DataCacheDurationPriorToFollowDistance())
        .isEqualTo(UnsignedLong.valueOf(374));
  }

  @Test
  void shouldCalculateCachePeriodForMinimalConstantsFromCurrentTime() {
    assertThat(Eth1DataCachePeriodCalculator.calculateEth1DataCacheDurationPriorToCurrentTime())
        .isEqualTo(UnsignedLong.valueOf(598));
  }
}
