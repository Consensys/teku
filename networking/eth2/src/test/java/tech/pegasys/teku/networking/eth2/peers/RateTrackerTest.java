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

import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class RateTrackerTest {

  StubTimeProvider timeProvider = StubTimeProvider.withTimeInSeconds(1000);
  final long zero = 0;

  @Test
  public void shouldAllowAddingItemsWithinLimit() {
    final RateTracker tracker = new RateTracker(1, 1, timeProvider);
    final long objectCount = 1;
    final Pair<UInt64, Long> trackerResponse = tracker.popObjectRequests(objectCount);
    assertThat(trackerResponse).isEqualTo(Pair.of(timeProvider.getTimeInSeconds(), objectCount));
  }

  @Test
  public void shouldNotUnderflowWhenTimeWindowGreaterThanCurrentTime() {
    final RateTracker tracker = new RateTracker(1, 15000, timeProvider);
    final long objectCount = 1;
    final Pair<UInt64, Long> trackerResponse = tracker.popObjectRequests(objectCount);
    assertThat(trackerResponse).isEqualTo(Pair.of(timeProvider.getTimeInSeconds(), objectCount));
  }

  @Test
  public void shouldAllowAddingItemsAfterTimeoutPasses() {
    final RateTracker tracker = new RateTracker(1, 1, timeProvider);
    long objectCount = 1;

    Pair<UInt64, Long> trackerResponse = tracker.popObjectRequests(objectCount);
    assertThat(trackerResponse).isEqualTo(Pair.of(timeProvider.getTimeInSeconds(), objectCount));

    timeProvider.advanceTimeBySeconds(2L);

    trackerResponse = tracker.popObjectRequests(objectCount);
    assertThat(trackerResponse).isEqualTo(Pair.of(timeProvider.getTimeInSeconds(), objectCount));
  }

  @Test
  public void shouldReturnFalseIfCacheFull() {
    final RateTracker tracker = new RateTracker(1, 1, timeProvider);
    final long objectCount = 1;
    Pair<UInt64, Long> trackerResponse = tracker.popObjectRequests(objectCount);
    assertThat(trackerResponse).isEqualTo(Pair.of(timeProvider.getTimeInSeconds(), objectCount));

    trackerResponse = tracker.popObjectRequests(objectCount);
    assertThat(trackerResponse).isEqualTo(Pair.of(timeProvider.getTimeInSeconds(), zero));
  }

  @Test
  public void shouldAddMultipleValuesToCache() throws InterruptedException {
    final RateTracker tracker = new RateTracker(10, 1, timeProvider);
    long objectCount = 10;
    Pair<UInt64, Long> trackerResponse = tracker.popObjectRequests(objectCount);
    assertThat(trackerResponse).isEqualTo(Pair.of(timeProvider.getTimeInSeconds(), objectCount));

    trackerResponse = tracker.popObjectRequests(objectCount);
    assertThat(trackerResponse).isEqualTo(Pair.of(timeProvider.getTimeInSeconds(), zero));

    timeProvider.advanceTimeBySeconds(2L);

    objectCount = 1;
    trackerResponse = tracker.popObjectRequests(objectCount);
    assertThat(trackerResponse).isEqualTo(Pair.of(timeProvider.getTimeInSeconds(), objectCount));
  }

  @Test
  public void shouldMaintainCounterOverTime() {
    final RateTracker tracker = new RateTracker(10, 2, timeProvider);

    long objectCount = 9;
    Pair<UInt64, Long> trackerResponse = tracker.popObjectRequests(objectCount);
    assertThat(trackerResponse).isEqualTo(Pair.of(timeProvider.getTimeInSeconds(), objectCount));
    // time:1000 count:9

    timeProvider.advanceTimeBySeconds(1L);

    objectCount = 5;
    trackerResponse = tracker.popObjectRequests(objectCount);
    assertThat(trackerResponse).isEqualTo(Pair.of(timeProvider.getTimeInSeconds(), objectCount));
    // time:1001 count:14

    timeProvider.advanceTimeBySeconds(1L);
    // time:1002 count:14 - reject.
    objectCount = 5;
    trackerResponse = tracker.popObjectRequests(objectCount);
    assertThat(trackerResponse).isEqualTo(Pair.of(timeProvider.getTimeInSeconds(), zero));

    timeProvider.advanceTimeBySeconds(1L);
    // time:1003 count:5

    objectCount = 5;
    trackerResponse = tracker.popObjectRequests(objectCount);
    assertThat(trackerResponse).isEqualTo(Pair.of(timeProvider.getTimeInSeconds(), objectCount));
    // time:1003 count:10 - reject

    trackerResponse = tracker.popObjectRequests(objectCount);
    assertThat(trackerResponse).isEqualTo(Pair.of(timeProvider.getTimeInSeconds(), zero));

    timeProvider.advanceTimeBySeconds(3L);
    // time:1006 count:0
    objectCount = 9;
    trackerResponse = tracker.popObjectRequests(objectCount);
    assertThat(trackerResponse).isEqualTo(Pair.of(timeProvider.getTimeInSeconds(), objectCount));

    timeProvider.advanceTimeBySeconds(1L);
    // time:1007 count:9
    objectCount = 5;
    trackerResponse = tracker.popObjectRequests(objectCount);
    assertThat(trackerResponse).isEqualTo(Pair.of(timeProvider.getTimeInSeconds(), objectCount));
    // time:1007 count:14 - reject

    objectCount = 1;
    trackerResponse = tracker.popObjectRequests(objectCount);
    assertThat(trackerResponse).isEqualTo(Pair.of(timeProvider.getTimeInSeconds(), zero));

    timeProvider.advanceTimeBySeconds(2L);
    // time:1009 count:5
    objectCount = 4;
    trackerResponse = tracker.popObjectRequests(objectCount);
    assertThat(trackerResponse).isEqualTo(Pair.of(timeProvider.getTimeInSeconds(), objectCount));
    // time:1009 count:9
    objectCount = 1;
    trackerResponse = tracker.popObjectRequests(objectCount);
    assertThat(trackerResponse).isEqualTo(Pair.of(timeProvider.getTimeInSeconds(), objectCount));
    // time:1009 count:10 - reject
    objectCount = 1;
    trackerResponse = tracker.popObjectRequests(objectCount);
    assertThat(trackerResponse).isEqualTo(Pair.of(timeProvider.getTimeInSeconds(), zero));
  }

  @Test
  public void shouldAdjustObjectsCount() {
    final RateTracker tracker = new RateTracker(10, 2, timeProvider);

    Pair<UInt64, Long> trackerResponse = tracker.popObjectRequests(10);
    assertThat(trackerResponse).isEqualTo(Pair.of(timeProvider.getTimeInSeconds(), 10L));

    trackerResponse = tracker.popObjectRequests(5);
    assertThat(trackerResponse).isEqualTo(Pair.of(timeProvider.getTimeInSeconds(), zero));

    tracker.adjustRequestObjects(3, 10, timeProvider.getTimeInSeconds());

    trackerResponse = tracker.popObjectRequests(7);
    assertThat(trackerResponse).isEqualTo(Pair.of(timeProvider.getTimeInSeconds(), 7L));

    tracker.adjustRequestObjects(5, 7, timeProvider.getTimeInSeconds());

    trackerResponse = tracker.popObjectRequests(3);
    assertThat(trackerResponse).isEqualTo(Pair.of(timeProvider.getTimeInSeconds(), 3L));
  }
}
