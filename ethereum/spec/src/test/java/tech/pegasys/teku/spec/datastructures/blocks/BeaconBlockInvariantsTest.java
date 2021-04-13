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

package tech.pegasys.teku.spec.datastructures.blocks;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class BeaconBlockInvariantsTest {
  private final DataStructureUtil dataStructureUtil =
      new DataStructureUtil(TestSpecFactory.createMinimalPhase0());

  @ParameterizedTest
  @MethodSource("slotNumbers")
  void shouldExtractSlotFromSignedBeaconBlock(final UInt64 slot) {
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(slot);
    assertThat(BeaconBlockInvariants.extractSignedBeaconBlockSlot(block.sszSerialize()))
        .isEqualTo(slot);
  }

  @ParameterizedTest
  @MethodSource("slotNumbers")
  void shouldExtractSlotFromBeaconBlock(final UInt64 slot) {
    final BeaconBlock block = dataStructureUtil.randomBeaconBlock(slot);
    assertThat(BeaconBlockInvariants.extractBeaconBlockSlot(block.sszSerialize())).isEqualTo(slot);
  }

  static List<Arguments> slotNumbers() {
    return List.of(
        Arguments.of(UInt64.ZERO),
        Arguments.of(UInt64.ONE),
        Arguments.of(UInt64.MAX_VALUE),
        Arguments.of(UInt64.valueOf(1234582)));
  }
}
