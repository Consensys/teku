/*
 * Copyright ConsenSys Software Inc., 2023
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

package tech.pegasys.teku.validator.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;

class VoluntaryExitDataProviderTest {
  private final Spec spec = TestSpecFactory.createMinimal(SpecMilestone.CAPELLA);
  private final StubTimeProvider timeProvider = StubTimeProvider.withTimeInSeconds(100000);
  private VoluntaryExitDataProvider provider;

  @BeforeEach
  void setUp() {
    provider = new VoluntaryExitDataProvider(spec, timeProvider);
  }

  @Test
  void calculateCurrentEpoch_shouldReturnEpoch() {
    final UInt64 epoch = provider.calculateCurrentEpoch(UInt64.ZERO);
    final UInt64 expectedEpoch = UInt64.valueOf(2083);
    assertThat(epoch).isEqualTo(expectedEpoch);
  }
}
