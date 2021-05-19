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

package tech.pegasys.teku.cli.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;

public class SlashingProtectionCommandUtilsTest {
  final Spec spec = TestSpecFactory.createMinimalPhase0();

  final UInt64 genesis = UInt64.valueOf(1234);

  @Test
  void getComputedSlot_shouldHandlePreGenesis() {
    assertThat(SlashingProtectionCommandUtils.getComputedSlot(genesis, genesis.decrement(), spec))
        .isEqualTo(UInt64.ZERO);
  }

  @Test
  void getComputedSlot_shouldHandleGenesis() {
    assertThat(SlashingProtectionCommandUtils.getComputedSlot(genesis, genesis, spec))
        .isEqualTo(UInt64.ZERO);
  }

  @Test
  void getComputedSlot_shouldHandleAfterGenesis() {
    final UInt64 oneThousandSlotSeconds =
        UInt64.valueOf(spec.getGenesisSpec().getConfig().getSecondsPerSlot()).times(1000);
    assertThat(
            SlashingProtectionCommandUtils.getComputedSlot(
                genesis, genesis.plus(oneThousandSlotSeconds), spec))
        .isEqualTo(UInt64.valueOf(1000));
  }
}
