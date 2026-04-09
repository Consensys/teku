/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.storage.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class BlockTimelinessTrackerTest {
  private final Spec spec = TestSpecFactory.createDefault();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final int millisPerSlot = spec.getGenesisSpecConfig().getSlotDurationMillis();
  private final UInt64 slot = UInt64.ONE;

  private SignedBlockAndState signedBlockAndState;
  private UInt64 genesisTimeMillis;
  private BlockTimelinessTracker tracker;

  @BeforeEach
  void setup() {
    signedBlockAndState = dataStructureUtil.randomSignedBlockAndState(slot);
    genesisTimeMillis = signedBlockAndState.getState().getGenesisTime().times(1000);
    tracker = new BlockTimelinessTracker(spec, () -> genesisTimeMillis, new HashMap<>());
  }

  @Test
  void shouldReportTimelinessIfSet() {
    tracker.setBlockTimelinessFromArrivalTime(
        signedBlockAndState.getBlock(), computeTime(slot, 500));

    assertThat(tracker.getBlockTimeliness(signedBlockAndState.getRoot()))
        .isPresent()
        .hasValueSatisfying(timeliness -> assertThat(timeliness.isTimelyAttestation()).isTrue());
    assertThat(tracker.isBlockLate(signedBlockAndState.getRoot())).isFalse();
  }

  @Test
  void shouldKeepFirstTimelyObservation() {
    tracker.setBlockTimelinessFromArrivalTime(
        signedBlockAndState.getBlock(), computeTime(slot, 500));
    tracker.setBlockTimelinessFromArrivalTime(
        signedBlockAndState.getBlock(), computeTime(slot, 3000));

    assertThat(tracker.getBlockTimeliness(signedBlockAndState.getRoot()))
        .isPresent()
        .hasValueSatisfying(timeliness -> assertThat(timeliness.isTimelyAttestation()).isTrue());
  }

  @Test
  void shouldKeepFirstLateObservation() {
    tracker.setBlockTimelinessFromArrivalTime(
        signedBlockAndState.getBlock(), computeTime(slot, 2100));
    tracker.setBlockTimelinessFromArrivalTime(
        signedBlockAndState.getBlock(), computeTime(slot, 500));

    assertThat(tracker.getBlockTimeliness(signedBlockAndState.getRoot()))
        .isPresent()
        .hasValueSatisfying(timeliness -> assertThat(timeliness.isTimelyAttestation()).isFalse());
  }

  @Test
  void shouldReportLateBlock() {
    tracker.setBlockTimelinessFromArrivalTime(
        signedBlockAndState.getBlock(), computeTime(slot, 2100));

    assertThat(tracker.getBlockTimeliness(signedBlockAndState.getRoot()))
        .isPresent()
        .hasValueSatisfying(timeliness -> assertThat(timeliness.isTimelyAttestation()).isFalse());
    assertThat(tracker.isBlockLate(signedBlockAndState.getRoot())).isTrue();
  }

  @Test
  void shouldReturnEmptyForUnknownBlock() {
    assertThat(tracker.getBlockTimeliness(signedBlockAndState.getRoot())).isEmpty();
    assertThat(tracker.isBlockLate(signedBlockAndState.getRoot())).isFalse();
  }

  private UInt64 computeTime(final UInt64 slot, final long timeIntoSlot) {
    return genesisTimeMillis.plus(slot.times(millisPerSlot)).plus(timeIntoSlot);
  }
}
