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

import com.google.common.base.Preconditions;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import tech.pegasys.teku.infrastructure.time.TimeProvider;

public class NewRateTrackerImpl implements RateTracker {

  private final RateTrackerRequests requests;
  private final int peerRateLimit;
  private final String name;

  private final AtomicInteger newRequestId = new AtomicInteger(0);

  public NewRateTrackerImpl(
      final int peerRateLimit,
      final long timeoutSeconds,
      final TimeProvider timeProvider,
      final String name) {
    Preconditions.checkArgument(
        peerRateLimit > 0,
        "peerRateLimit should be a positive number but it was %s",
        peerRateLimit);
    this.peerRateLimit = peerRateLimit;
    this.name = name;
    requests = new RateTrackerRequests(timeProvider, timeoutSeconds);
  }

  // boundary: if a request comes in and remaining capacity is at least 1, then
  // they can have the objects they request otherwise they get none.
  @Override
  public Optional<ApprovedRequest> approveObjectsRequest(final long objectsCount) {
    newRequestId.compareAndSet(Integer.MAX_VALUE, 0);
    // as long as there's space for at least 1 object within the rate limit, we'll allow it
    return requests.getObjectsWithinWindow() >= peerRateLimit
        ? Optional.empty()
        : Optional.of(requests.addRequest(newRequestId.getAndIncrement(), objectsCount));
  }

  @Override
  public long getAvailableObjectCount() {
    return Math.max(peerRateLimit - requests.getObjectsWithinWindow(), 0L);
  }

  @Override
  public void adjustObjectsRequest(
      final ApprovedRequest approvedRequest, final long updatedRequestSize) {
    requests.adjustRequestRemainingObjects(approvedRequest.getRequestKey(), updatedRequestSize);
  }

  @Override
  public String toString() {
    return "NewRateTrackerImpl{"
        + "peerRateLimit="
        + peerRateLimit
        + ", objectsWithinWindow="
        + getAvailableObjectCount()
        + ", name='"
        + name
        + '\''
        + '}';
  }
}
