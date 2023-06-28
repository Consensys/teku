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

import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class RateTracker {
  private final NavigableMap<ObjectRequestsKey, Long> requestCount;
  private final int peerRateLimit;
  private final UInt64 timeoutSeconds;
  private long objectsWithinWindow = 0L;
  private final TimeProvider timeProvider;

  private final AtomicInteger newRequestId = new AtomicInteger(0);

  public RateTracker(
      final int peerRateLimit, final long timeoutSeconds, final TimeProvider timeProvider) {
    this.timeoutSeconds = UInt64.valueOf(timeoutSeconds);
    requestCount = new TreeMap<>();
    this.peerRateLimit = peerRateLimit;
    this.timeProvider = timeProvider;
  }

  // boundary: if a request comes in and remaining capacity is at least 1, then
  // they can have the objects they request otherwise they get none.
  public synchronized Optional<RequestApproval> popObjectRequests(final long objectCount) {
    pruneRequests();
    final UInt64 currentTime = timeProvider.getTimeInSeconds();
    if ((peerRateLimit - objectsWithinWindow) <= 0) {
      return Optional.empty();
    }
    objectsWithinWindow += objectCount;
    resetRequestId(currentTime);
    final RequestApproval requestApproval =
        new RequestApproval.RequestApprovalBuilder()
            .requestId(newRequestId.getAndIncrement())
            .timeSeconds(currentTime)
            .objectCount(objectCount)
            .build();
    requestCount.put(requestApproval.getRequestKey(), objectCount);
    return Optional.of(requestApproval);
  }

  private void resetRequestId(UInt64 currentTime) {
    if (requestCount.keySet().stream()
        .noneMatch(objectRequestsKey -> objectRequestsKey.getTimeSeconds().equals(currentTime))) {
      this.newRequestId.set(0);
    }
  }

  public synchronized void adjustObjectRequests(
      final RequestApproval requestApproval, final long returnedObjectsCount) {
    pruneRequests();
    if (requestCount.containsKey(requestApproval.getRequestKey())) {
      final long initialObjectsCount = requestCount.get(requestApproval.getRequestKey());
      requestCount.put(requestApproval.getRequestKey(), returnedObjectsCount);
      objectsWithinWindow = objectsWithinWindow - initialObjectsCount + returnedObjectsCount;
    }
  }

  void pruneRequests() {
    final UInt64 currentTime = timeProvider.getTimeInSeconds();
    if (currentTime.isLessThan(timeoutSeconds)) {
      return;
    }
    final NavigableMap<ObjectRequestsKey, Long> headMap =
        requestCount.headMap(new ObjectRequestsKey(currentTime.minus(timeoutSeconds), 0), false);
    headMap.values().forEach(value -> objectsWithinWindow -= value);
    headMap.clear();
  }
}
