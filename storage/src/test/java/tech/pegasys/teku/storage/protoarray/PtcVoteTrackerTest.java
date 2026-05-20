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

package tech.pegasys.teku.storage.protoarray;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;

import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

class PtcVoteTrackerTest {

  private static final Bytes32 ROOT_1 = Bytes32.fromHexStringLenient("0x01");
  private static final Bytes32 ROOT_2 = Bytes32.fromHexStringLenient("0x02");

  private final PtcVoteTracker tracker = new PtcVoteTracker();

  @Test
  void recordVote_incrementsPayloadAndDataCounts() {
    assertThat(tracker.getPayloadPresentVoteCount(ROOT_1)).isZero();
    assertThat(tracker.getDataAvailableVoteCount(ROOT_1)).isZero();

    tracker.recordVote(ROOT_1, ZERO, true, false);

    assertThat(tracker.getPayloadPresentVoteCount(ROOT_1)).isEqualTo(1);
    assertThat(tracker.getDataAvailableVoteCount(ROOT_1)).isZero();

    tracker.recordVote(ROOT_1, ONE, true, true);
    tracker.recordVote(ROOT_1, UInt64.valueOf(2), true, true);

    assertThat(tracker.getPayloadPresentVoteCount(ROOT_1)).isEqualTo(3);
    assertThat(tracker.getDataAvailableVoteCount(ROOT_1)).isEqualTo(2);
  }

  @Test
  void recordVote_tracksUniqueValidatorsPerBlock() {
    tracker.recordVote(ROOT_1, ZERO, true, true);
    tracker.recordVote(ROOT_1, ZERO, true, true);
    tracker.recordVote(ROOT_1, ONE, true, false);

    assertThat(tracker.getPayloadPresentVoteCount(ROOT_1)).isEqualTo(2);
    assertThat(tracker.getDataAvailableVoteCount(ROOT_1)).isEqualTo(1);
  }

  @Test
  void recordVote_removesValidatorWhenPayloadOrDataBecomesAbsent() {
    tracker.recordVote(ROOT_1, ZERO, true, true);
    tracker.recordVote(ROOT_1, ONE, true, true);
    tracker.recordVote(ROOT_1, ZERO, false, false);

    assertThat(tracker.getPayloadPresentVoteCount(ROOT_1)).isEqualTo(1);
    assertThat(tracker.getDataAvailableVoteCount(ROOT_1)).isEqualTo(1);
  }

  @Test
  void recordVote_tracksDifferentRootsSeparately() {
    tracker.recordVote(ROOT_1, ZERO, true, true);
    tracker.recordVote(ROOT_1, ONE, true, false);
    tracker.recordVote(ROOT_2, ZERO, true, true);

    assertThat(tracker.getPayloadPresentVoteCount(ROOT_1)).isEqualTo(2);
    assertThat(tracker.getPayloadPresentVoteCount(ROOT_2)).isEqualTo(1);
    assertThat(tracker.getDataAvailableVoteCount(ROOT_1)).isEqualTo(1);
    assertThat(tracker.getDataAvailableVoteCount(ROOT_2)).isEqualTo(1);
  }

  @Test
  void remove_clearsTrackedVotesForRoot() {
    tracker.recordVote(ROOT_1, ZERO, true, true);

    tracker.remove(ROOT_1);

    assertThat(tracker.getPayloadPresentVoteCount(ROOT_1)).isZero();
    assertThat(tracker.getDataAvailableVoteCount(ROOT_1)).isZero();
  }

  @Test
  void removeIf_prunesMatchingRoots() {
    tracker.recordVote(ROOT_1, ZERO, true, true);
    tracker.recordVote(ROOT_2, ONE, true, true);

    tracker.removeIf(ROOT_1::equals);

    assertThat(tracker.getPayloadPresentVoteCount(ROOT_1)).isZero();
    assertThat(tracker.getDataAvailableVoteCount(ROOT_1)).isZero();
    assertThat(tracker.getPayloadPresentVoteCount(ROOT_2)).isEqualTo(1);
    assertThat(tracker.getDataAvailableVoteCount(ROOT_2)).isEqualTo(1);
  }
}
