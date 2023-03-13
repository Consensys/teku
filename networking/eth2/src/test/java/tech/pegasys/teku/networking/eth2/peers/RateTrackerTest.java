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

package tech.pegasys.teku.networking.eth2.peers;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;

public class RateTrackerTest {

  StubTimeProvider timeProvider = StubTimeProvider.withTimeInSeconds(1000);

  @Test
  public void shouldAllowAddingItemsWithinLimit() {
    final RateTracker tracker = new RateTracker(1, 1, timeProvider);
    assertThat(tracker.popObjectRequests(1)).isEqualTo(1);
  }

  @Test
  public void shouldNotUnderflowWhenTimeWindowGreaterThanCurrentTime() {
    final RateTracker tracker = new RateTracker(1, 15000, timeProvider);
    assertThat(tracker.popObjectRequests(1)).isEqualTo(1);
  }

  @Test
  public void shouldAllowAddingItemsAfterTimeoutPasses() {
    final RateTracker tracker = new RateTracker(1, 1, timeProvider);
    assertThat(tracker.popObjectRequests(1)).isEqualTo(1);
    timeProvider.advanceTimeBySeconds(2L);
    assertThat(tracker.popObjectRequests(1)).isEqualTo(1);
  }

  @Test
  public void shouldReturnFalseIfCacheFull() {
    final RateTracker tracker = new RateTracker(1, 1, timeProvider);
    assertThat(tracker.popObjectRequests(1)).isEqualTo(1);
    assertThat(tracker.popObjectRequests(1)).isEqualTo(0);
  }

  @Test
  public void shouldAddMultipleValuesToCache() throws InterruptedException {
    final RateTracker tracker = new RateTracker(10, 1, timeProvider);
    assertThat(tracker.popObjectRequests(10)).isEqualTo(10);
    assertThat(tracker.popObjectRequests(10)).isEqualTo(0);
    timeProvider.advanceTimeBySeconds(2L);
    assertThat(tracker.popObjectRequests(1)).isEqualTo(1);
  }

  @Test
  public void shouldMaintainCounterOverTime() {
    final RateTracker tracker = new RateTracker(10, 2, timeProvider);
    assertThat(tracker.popObjectRequests(9)).isEqualTo(9);
    // time:1000 count:9

    timeProvider.advanceTimeBySeconds(1L);
    assertThat(tracker.popObjectRequests(5)).isEqualTo(5);
    // time:1001 count:14

    timeProvider.advanceTimeBySeconds(1L);
    // time:1002 count:14 - reject.
    assertThat(tracker.popObjectRequests(5)).isEqualTo(0);

    timeProvider.advanceTimeBySeconds(1L);
    // time:1003 count:5
    assertThat(tracker.popObjectRequests(5)).isEqualTo(5);
    // time:1003 count:10 - reject
    assertThat(tracker.popObjectRequests(5)).isEqualTo(0);

    timeProvider.advanceTimeBySeconds(3L);
    // time:1006 count:0
    assertThat(tracker.popObjectRequests(9)).isEqualTo(9);

    timeProvider.advanceTimeBySeconds(1L);
    // time:1007 count:9
    assertThat(tracker.popObjectRequests(5)).isEqualTo(5);
    // time:1007 count:14 - reject
    assertThat(tracker.popObjectRequests(1)).isEqualTo(0);

    timeProvider.advanceTimeBySeconds(2L);
    // time:1009 count:5
    assertThat(tracker.popObjectRequests(4)).isEqualTo(4);
    // time:1009 count:9
    assertThat(tracker.popObjectRequests(1)).isEqualTo(1);
    // time:1009 count:10 - reject
    assertThat(tracker.popObjectRequests(1)).isEqualTo(0);
  }
}
