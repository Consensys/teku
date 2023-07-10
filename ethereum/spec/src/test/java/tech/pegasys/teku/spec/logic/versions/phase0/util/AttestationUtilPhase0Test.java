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

package tech.pegasys.teku.spec.logic.versions.phase0.util;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.time.TimeUtilities.secondsToMillis;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.TestSpecFactory;

class AttestationUtilPhase0Test {

  private static final SpecVersion SPEC_VERSION =
      TestSpecFactory.createMinimalPhase0().forMilestone(SpecMilestone.PHASE0);

  private static final int SECONDS_PER_SLOT = SPEC_VERSION.getConfig().getSecondsPerSlot();

  private final AttestationUtilPhase0 attestationUtilPhase0 =
      (AttestationUtilPhase0) SPEC_VERSION.getAttestationUtil();

  @ParameterizedTest
  @MethodSource("provideCurrentTimeAfterAttestationPropagationSlotRangeArguments")
  public void testIsCurrentTimeAfterAttestationPropagationSlotRange(
      final int attestationSlot, final UInt64 currentTimeMillis, final boolean expectedResult) {
    // set genesisTime as 0 for simplification
    final UInt64 genesisTime = UInt64.ZERO;
    final boolean actualResult =
        attestationUtilPhase0.isCurrentTimeAfterAttestationPropagationSlotRange(
            UInt64.valueOf(attestationSlot), genesisTime, currentTimeMillis);
    assertThat(actualResult).isEqualTo(expectedResult);
  }

  @ParameterizedTest
  @MethodSource("provideCurrentTimeBeforeMinimumAttestationBroadcastTimeArguments")
  public void testIsCurrentTimeBeforeMinimumAttestationBroadcastTime(
      final int attestationSlot, final UInt64 currentTimeMillis, final boolean expectedResult) {
    // set genesisTime as 0 for simplification
    final UInt64 genesisTime = UInt64.ZERO;
    final boolean actualResult =
        attestationUtilPhase0.isCurrentTimeBeforeMinimumAttestationBroadcastTime(
            UInt64.valueOf(attestationSlot), genesisTime, currentTimeMillis);
    assertThat(actualResult).isEqualTo(expectedResult);
  }

  // ATTESTATION_PROPAGATION_SLOT_RANGE is 32
  private static Stream<Arguments>
      provideCurrentTimeAfterAttestationPropagationSlotRangeArguments() {
    return Stream.of(
        Arguments.of(0, getTimeForSlotInMillis(34), true),
        Arguments.of(0, getTimeForSlotInMillis(32), false),
        Arguments.of(12, getTimeForSlotInMillis(26), false),
        Arguments.of(12, getTimeForSlotInMillis(46), true),
        // testing MAXIMUM_GOSSIP_CLOCK_DISPARITY
        Arguments.of(12, getTimeForSlotInMillis(45).plus(500), false),
        Arguments.of(12, getTimeForSlotInMillis(45).plus(501), true));
  }

  private static Stream<Arguments>
      provideCurrentTimeBeforeMinimumAttestationBroadcastTimeArguments() {
    return Stream.of(
        Arguments.of(0, UInt64.ZERO, false),
        Arguments.of(8, getTimeForSlotInMillis(7), true),
        Arguments.of(7, getTimeForSlotInMillis(8), false),
        // testing MAXIMUM_GOSSIP_CLOCK_DISPARITY
        Arguments.of(7, getTimeForSlotInMillis(7).minus(501), true),
        Arguments.of(7, getTimeForSlotInMillis(7).minus(500), false));
  }

  private static UInt64 getTimeForSlotInMillis(final int slot) {
    return secondsToMillis(UInt64.valueOf(slot).times(SECONDS_PER_SLOT));
  }
}
