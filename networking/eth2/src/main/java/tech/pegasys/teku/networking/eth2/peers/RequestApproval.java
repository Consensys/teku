/*
 * Copyright ConsenSys Software Inc., 2023
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

import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class RequestApproval {

  private final int requestId;
  private final UInt64 timeSeconds;
  private final long objectsCount;

  private RequestApproval(int requestId, UInt64 timeSeconds, long objectsCount) {
    this.requestId = requestId;
    this.timeSeconds = timeSeconds;
    this.objectsCount = objectsCount;
  }

  public int getRequestId() {
    return requestId;
  }

  public UInt64 getTimeSeconds() {
    return timeSeconds;
  }

  public long getObjectsCount() {
    return objectsCount;
  }

  public static final class RequestApprovalBuilder {
    private int requestId;
    private UInt64 timeSeconds;
    private long objectsCount;

    public RequestApprovalBuilder requestId(final int requestId) {
      this.requestId = requestId;
      return this;
    }

    public RequestApprovalBuilder timeSeconds(final UInt64 timeSeconds) {
      this.timeSeconds = timeSeconds;
      return this;
    }

    public RequestApprovalBuilder objectCount(final long objectsCount) {
      this.objectsCount = objectsCount;
      return this;
    }

    public RequestApproval build() {
      return new RequestApproval(this.requestId, this.timeSeconds, this.objectsCount);
    }
  }
}
