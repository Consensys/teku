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

import java.util.Optional;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;

public class RateTrackerTest {

  StubTimeProvider timeProvider = StubTimeProvider.withTimeInSeconds(1000);

  @Test
  public void shouldAllowAddingItemsWithinLimit() {
    final RateTracker tracker = new RateTracker(1, 1, timeProvider);
    final long objectCount = 1;
    final Optional<RequestApproval> objectRequests = tracker.popObjectRequests(objectCount);
    assertRequestsAllowed(objectRequests, objectCount, timeProvider);
  }

  @Test
  public void shouldNotUnderflowWhenTimeWindowGreaterThanCurrentTime() {
    final RateTracker tracker = new RateTracker(1, 15000, timeProvider);
    final long objectCount = 1;
    final Optional<RequestApproval> objectRequests = tracker.popObjectRequests(objectCount);
    assertRequestsAllowed(objectRequests, objectCount, timeProvider);
  }

  @Test
  public void shouldAllowAddingItemsAfterTimeoutPasses() {
    final RateTracker tracker = new RateTracker(1, 1, timeProvider);
    long objectCount = 1;

    Optional<RequestApproval> objectRequests = tracker.popObjectRequests(objectCount);
    assertRequestsAllowed(objectRequests, objectCount, timeProvider);

    timeProvider.advanceTimeBySeconds(2L);

    objectRequests = tracker.popObjectRequests(objectCount);
    assertRequestsAllowed(objectRequests, objectCount, timeProvider);
  }

  @Test
  public void shouldReturnFalseIfCacheFull() {
    final RateTracker tracker = new RateTracker(1, 1, timeProvider);
    final long objectCount = 1;
    Optional<RequestApproval> objectRequests = tracker.popObjectRequests(objectCount);
    assertRequestsAllowed(objectRequests, objectCount, timeProvider);

    objectRequests = tracker.popObjectRequests(objectCount);
    assertThat(objectRequests).isEmpty();
  }

  @Test
  public void shouldAddMultipleValuesToCache() throws InterruptedException {
    final RateTracker tracker = new RateTracker(10, 1, timeProvider);
    long objectCount = 10;
    Optional<RequestApproval> objectRequests = tracker.popObjectRequests(objectCount);
    assertRequestsAllowed(objectRequests, objectCount, timeProvider);

    objectRequests = tracker.popObjectRequests(objectCount);
    assertThat(objectRequests).isEmpty();

    timeProvider.advanceTimeBySeconds(2L);

    objectCount = 1;
    objectRequests = tracker.popObjectRequests(objectCount);
    assertRequestsAllowed(objectRequests, objectCount, timeProvider);
  }

  @Test
  public void shouldMaintainCounterOverTime() {
    final RateTracker tracker = new RateTracker(10, 2, timeProvider);

    long objectCount = 9;
    Optional<RequestApproval> objectRequests = tracker.popObjectRequests(objectCount);
    assertRequestsAllowed(objectRequests, objectCount, timeProvider);
    // time:1000 count:9

    timeProvider.advanceTimeBySeconds(1L);

    objectCount = 5;
    objectRequests = tracker.popObjectRequests(objectCount);
    assertRequestsAllowed(objectRequests, objectCount, timeProvider);
    // time:1001 count:14

    timeProvider.advanceTimeBySeconds(1L);
    // time:1002 count:14 - reject.
    objectCount = 5;
    objectRequests = tracker.popObjectRequests(objectCount);
    assertThat(objectRequests).isEmpty();

    timeProvider.advanceTimeBySeconds(1L);
    // time:1003 count:5

    objectCount = 5;
    objectRequests = tracker.popObjectRequests(objectCount);
    assertRequestsAllowed(objectRequests, objectCount, timeProvider);
    // time:1003 count:10 - reject

    objectRequests = tracker.popObjectRequests(objectCount);
    assertThat(objectRequests).isEmpty();

    timeProvider.advanceTimeBySeconds(3L);
    // time:1006 count:0
    objectCount = 9;
    objectRequests = tracker.popObjectRequests(objectCount);
    assertRequestsAllowed(objectRequests, objectCount, timeProvider);

    timeProvider.advanceTimeBySeconds(1L);
    // time:1007 count:9
    objectCount = 5;
    objectRequests = tracker.popObjectRequests(objectCount);
    assertRequestsAllowed(objectRequests, objectCount, timeProvider);
    // time:1007 count:14 - reject

    objectCount = 1;
    objectRequests = tracker.popObjectRequests(objectCount);
    assertThat(objectRequests).isEmpty();

    timeProvider.advanceTimeBySeconds(2L);
    // time:1009 count:5
    objectCount = 4;
    objectRequests = tracker.popObjectRequests(objectCount);
    assertRequestsAllowed(objectRequests, objectCount, timeProvider);
    // time:1009 count:9
    objectCount = 1;
    objectRequests = tracker.popObjectRequests(objectCount);
    assertRequestsAllowed(objectRequests, objectCount, timeProvider);
    // time:1009 count:10 - reject
    objectCount = 1;
    objectRequests = tracker.popObjectRequests(objectCount);
    assertThat(objectRequests).isEmpty();
  }

  @Test
  public void shouldAdjustObjectsCount() {
    final RateTracker tracker = new RateTracker(10, 2, timeProvider);

    // time: 1000, tracker count: 0, limit: 10, remaining: 10
    Optional<RequestApproval> objectRequests = tracker.popObjectRequests(10);
    assertRequestsAllowed(objectRequests, 10, timeProvider);

    // time: 1000, tracker count: 10, limit: 10, remaining: 0
    Optional<RequestApproval> anotherObjectRequests = tracker.popObjectRequests(5);
    assertThat(anotherObjectRequests).isEmpty();

    tracker.adjustObjectRequests(objectRequests.get(), 3);
    // time: 1000, tracker count: 3, limit: 10, remaining: 7
    objectRequests = tracker.popObjectRequests(7);
    assertRequestsAllowed(objectRequests, 7, timeProvider);
    // time: 1000, tracker count: 10, limit: 10, remaining: 0
    tracker.adjustObjectRequests(objectRequests.get(), 5);
    // time: 1000, tracker count: 8, limit: 10, remaining: 2
    objectRequests = tracker.popObjectRequests(3);
    assertRequestsAllowed(objectRequests, 3, timeProvider);
    // respond as long as the remaining capacity is > 0
    // time: 1000, tracker count: 11, limit: 10, remaining: 0
    tracker.adjustObjectRequests(objectRequests.get(), 3);
    // time: 1000, tracker count: 11, limit: 10, remaining: 0
    objectRequests = tracker.popObjectRequests(2);
    assertThat(objectRequests).isEmpty();
  }

  private void assertRequestsAllowed(
      Optional<RequestApproval> objectRequests, long objectCount, StubTimeProvider timeProvider) {
    assertThat(objectRequests).isPresent();
    assertThat(objectRequests.get().getObjectsCount()).isEqualTo(objectCount);
    assertThat(objectRequests.get().getRequestKey().getTimeSeconds())
        .isEqualTo(timeProvider.getTimeInSeconds());
  }
}
