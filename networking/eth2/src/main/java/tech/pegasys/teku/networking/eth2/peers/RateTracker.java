/*
 * Copyright 2020 ConsenSys AG.
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

import java.util.NavigableMap;
import java.util.TreeMap;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class RateTracker {
  private final NavigableMap<UInt64, Long> requestCount;
  private final int peerRateLimit;
  private final UInt64 timeoutSeconds;
  private long requestsWithinWindow = 0L;
  private final TimeProvider timeProvider;

  public RateTracker(
      final int peerRateLimit, final long timeoutSeconds, final TimeProvider timeProvider) {
    this.timeoutSeconds = UInt64.valueOf(timeoutSeconds);
    requestCount = new TreeMap<>();
    this.peerRateLimit = peerRateLimit;
    this.timeProvider = timeProvider;
  }

  // boundary: if a request comes in and remaining capacity is at least 1, then
  // they can have the objects they request otherwise they get none.
  public synchronized long wantToRequestObjects(final long objectCount) {
    pruneRequests();
    if ((peerRateLimit - requestsWithinWindow) <= 0) {
      return 0L;
    }

    requestsWithinWindow += objectCount;
    requestCount.compute(
        timeProvider.getTimeInSeconds(),
        (key, val) -> val == null ? objectCount : val + objectCount);
    return objectCount;
  }

  void pruneRequests() {
    final UInt64 currentTime = timeProvider.getTimeInSeconds();
    if (currentTime.isLessThan(timeoutSeconds)) {
      return;
    }
    final NavigableMap<UInt64, Long> headMap =
        requestCount.headMap(currentTime.minus(timeoutSeconds), false);
    headMap.values().forEach(value -> requestsWithinWindow -= value);
    headMap.clear();
  }
}
