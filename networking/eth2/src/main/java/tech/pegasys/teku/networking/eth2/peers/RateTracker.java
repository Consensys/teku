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

import java.util.Optional;
import tech.pegasys.teku.infrastructure.time.TimeProvider;

public interface RateTracker {
  RateTracker NOOP =
      new RateTracker() {
        @Override
        public Optional<RequestApproval> approveObjectsRequest(long objectsCount) {
          return Optional.empty();
        }

        @Override
        public void adjustObjectsRequest(
            RequestApproval requestApproval, long returnedObjectsCount) {}

        @Override
        public long getAvailableObjectCount() {
          return 0;
        }

        @Override
        public void pruneRequests() {}
      };

  // boundary: if a request comes in and remaining capacity is at least 1, then
  // they can have the objects they request otherwise they get none.
  Optional<RequestApproval> approveObjectsRequest(long objectsCount);

  long getAvailableObjectCount();

  void adjustObjectsRequest(RequestApproval requestApproval, long returnedObjectsCount);

  void pruneRequests();

  static RateTracker create(
      final int peerRateLimit,
      final long timeoutSeconds,
      final TimeProvider timeProvider,
      final String name) {
    return new RateTrackerImpl(peerRateLimit, timeoutSeconds, timeProvider, name);
  }
}
