/*
 * Copyright Consensys Software Inc., 2025
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

class RateTrackerRequests {
  private final List<ApprovedRequest> requests = Collections.synchronizedList(new ArrayList<>());
  private final TimeProvider timeProvider;
  private final long timeoutSeconds;
  private final AtomicReference<UInt64> lastPrune = new AtomicReference<>(UInt64.ZERO);
  private long requestCounter = 0L;

  RateTrackerRequests(final TimeProvider timeProvider, final long timeoutSeconds) {
    this.timeProvider = timeProvider;
    this.timeoutSeconds = timeoutSeconds;
  }

  synchronized void pruneRequests(final boolean force) {
    final UInt64 oldestTime = timeProvider.getTimeInSeconds().minusMinZero(timeoutSeconds);
    if (force || lastPrune.get().isLessThan(oldestTime)) {
      lastPrune.set(oldestTime);
      requests.removeIf(r -> r.getRequestKey().timeSeconds().isLessThan(oldestTime));
      requestCounter =
          requests.stream().map(ApprovedRequest::getRequestSize).reduce(Long::sum).orElse(0L);
    }
  }

  synchronized ApprovedRequest addRequest(final int requestId, final long requestSize) {
    pruneRequests(true);
    final ApprovedRequest approvedRequest =
        new ApprovedRequest.RequestApprovalBuilder()
            .requestId(requestId)
            .timeSeconds(timeProvider.getTimeInSeconds())
            .requestSize(requestSize)
            .build();
    requests.add(approvedRequest);
    requestCounter += requestSize;
    return approvedRequest;
  }

  long getObjectsWithinWindow() {
    pruneRequests(false);
    return requestCounter;
  }

  synchronized void adjustRequestRemainingObjects(
      final RequestKey requestKey, final long updatedRequestSize) {
    requests.stream()
        .filter(r -> r.getRequestKey().equals(requestKey))
        .forEach(
            r -> {
              final long delta = r.getRequestSize() - updatedRequestSize;
              r.setRequestSize(updatedRequestSize);
              requestCounter -= delta;
            });
  }
}
