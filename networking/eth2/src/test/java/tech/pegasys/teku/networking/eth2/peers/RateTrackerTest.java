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
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;

public class RateTrackerTest {

  StubTimeProvider timeProvider = StubTimeProvider.withTimeInSeconds(1000);
  private final AtomicInteger requestId = new AtomicInteger(0);

  @BeforeEach
  public void setUp() {
    requestId.set(0);
  }

  @Test
  public void shouldAllowAddingItemsWithinLimit() {
    final RateTracker tracker = RateTracker.create(1, 1, timeProvider);
    final long objectsCount = 1;
    final Optional<RequestApproval> objectRequests = tracker.approveObjectsRequest(objectsCount);
    assertRequestsAllowed(objectRequests, objectsCount, timeProvider);
  }

  @Test
  public void shouldNotUnderflowWhenTimeWindowGreaterThanCurrentTime() {
    final RateTracker tracker = RateTracker.create(1, 15000, timeProvider);
    final long objectsCount = 1;
    final Optional<RequestApproval> objectRequests = tracker.approveObjectsRequest(objectsCount);
    assertRequestsAllowed(objectRequests, objectsCount, timeProvider);
  }

  @Test
  public void shouldAllowAddingItemsAfterTimeoutPasses() {
    final RateTracker tracker = RateTracker.create(1, 1, timeProvider);
    long objectsCount = 1;

    Optional<RequestApproval> objectRequests = tracker.approveObjectsRequest(objectsCount);
    assertRequestsAllowed(objectRequests, objectsCount, timeProvider);

    timeProvider.advanceTimeBySeconds(2L);

    objectRequests = tracker.approveObjectsRequest(objectsCount);
    assertRequestsAllowed(objectRequests, objectsCount, timeProvider);
  }

  @Test
  public void shouldReturnFalseIfCacheFull() {
    final RateTracker tracker = RateTracker.create(1, 1, timeProvider);
    final long objectsCount = 1;
    Optional<RequestApproval> objectRequests = tracker.approveObjectsRequest(objectsCount);
    assertRequestsAllowed(objectRequests, objectsCount, timeProvider);

    objectRequests = tracker.approveObjectsRequest(objectsCount);
    assertThat(objectRequests).isEmpty();
  }

  @Test
  public void shouldAddMultipleValuesToCache() throws InterruptedException {
    final RateTracker tracker = RateTracker.create(10, 1, timeProvider);
    long objectsCount = 10;
    Optional<RequestApproval> objectRequests = tracker.approveObjectsRequest(objectsCount);
    assertRequestsAllowed(objectRequests, objectsCount, timeProvider);

    objectRequests = tracker.approveObjectsRequest(objectsCount);
    assertThat(objectRequests).isEmpty();

    timeProvider.advanceTimeBySeconds(2L);

    objectsCount = 1;
    objectRequests = tracker.approveObjectsRequest(objectsCount);
    assertRequestsAllowed(objectRequests, objectsCount, timeProvider);
  }

  @Test
  public void shouldMaintainCounterOverTime() {
    final RateTracker tracker = RateTracker.create(10, 2, timeProvider);

    long objectsCount = 9;
    Optional<RequestApproval> objectRequests = tracker.approveObjectsRequest(objectsCount);
    assertRequestsAllowed(objectRequests, objectsCount, timeProvider);
    // time:1000 count:9

    timeProvider.advanceTimeBySeconds(1L);

    objectsCount = 5;
    objectRequests = tracker.approveObjectsRequest(objectsCount);
    assertRequestsAllowed(objectRequests, objectsCount, timeProvider);
    // time:1001 count:14

    timeProvider.advanceTimeBySeconds(1L);
    // time:1002 count:14 - reject.
    objectsCount = 5;
    objectRequests = tracker.approveObjectsRequest(objectsCount);
    assertThat(objectRequests).isEmpty();

    timeProvider.advanceTimeBySeconds(1L);
    // time:1003 count:5

    objectsCount = 5;
    objectRequests = tracker.approveObjectsRequest(objectsCount);
    assertRequestsAllowed(objectRequests, objectsCount, timeProvider);
    // time:1003 count:10 - reject

    objectRequests = tracker.approveObjectsRequest(objectsCount);
    assertThat(objectRequests).isEmpty();

    timeProvider.advanceTimeBySeconds(3L);
    // time:1006 count:0
    objectsCount = 9;
    objectRequests = tracker.approveObjectsRequest(objectsCount);
    assertRequestsAllowed(objectRequests, objectsCount, timeProvider);

    timeProvider.advanceTimeBySeconds(1L);
    // time:1007 count:9
    objectsCount = 5;
    objectRequests = tracker.approveObjectsRequest(objectsCount);
    assertRequestsAllowed(objectRequests, objectsCount, timeProvider);
    // time:1007 count:14 - reject

    objectsCount = 1;
    objectRequests = tracker.approveObjectsRequest(objectsCount);
    assertThat(objectRequests).isEmpty();

    timeProvider.advanceTimeBySeconds(2L);
    // time:1009 count:5
    objectsCount = 4;
    objectRequests = tracker.approveObjectsRequest(objectsCount);
    assertRequestsAllowed(objectRequests, objectsCount, timeProvider);
    // time:1009 count:9
    objectsCount = 1;
    objectRequests = tracker.approveObjectsRequest(objectsCount);
    assertRequestsAllowed(objectRequests, objectsCount, timeProvider);
    // time:1009 count:10 - reject
    objectsCount = 1;
    objectRequests = tracker.approveObjectsRequest(objectsCount);
    assertThat(objectRequests).isEmpty();
  }

  @Test
  public void shouldAdjustObjectsCount() {
    final RateTracker tracker = RateTracker.create(10, 2, timeProvider);

    // time: 1000, tracker count: 0, limit: 10, remaining: 10
    Optional<RequestApproval> objectRequests = tracker.approveObjectsRequest(10);
    assertRequestsAllowed(objectRequests, 10, timeProvider);

    // time: 1000, tracker count: 10, limit: 10, remaining: 0
    Optional<RequestApproval> anotherObjectRequests = tracker.approveObjectsRequest(5);
    assertThat(anotherObjectRequests).isEmpty();

    tracker.adjustObjectsRequest(objectRequests.get(), 3);
    // time: 1000, tracker count: 3, limit: 10, remaining: 7
    objectRequests = tracker.approveObjectsRequest(7);
    assertRequestsAllowed(objectRequests, 7, timeProvider);
    // time: 1000, tracker count: 10, limit: 10, remaining: 0
    tracker.adjustObjectsRequest(objectRequests.get(), 5);
    // time: 1000, tracker count: 8, limit: 10, remaining: 2
    objectRequests = tracker.approveObjectsRequest(3);
    assertRequestsAllowed(objectRequests, 3, timeProvider);
    // respond as long as the remaining capacity is > 0
    // time: 1000, tracker count: 11, limit: 10, remaining: 0
    tracker.adjustObjectsRequest(objectRequests.get(), 3);
    // time: 1000, tracker count: 11, limit: 10, remaining: 0
    objectRequests = tracker.approveObjectsRequest(2);
    assertThat(objectRequests).isEmpty();
  }

  private void assertRequestsAllowed(
      final Optional<RequestApproval> requestApproval,
      final long objectsCount,
      final StubTimeProvider timeProvider) {
    assertThat(requestApproval).isPresent();
    assertThat(requestApproval.get().getObjectsCount()).isEqualTo(objectsCount);
    assertThat(requestApproval.get().getRequestKey())
        .isEqualTo(new RequestsKey(timeProvider.getTimeInSeconds(), requestId.getAndIncrement()));
  }
}
