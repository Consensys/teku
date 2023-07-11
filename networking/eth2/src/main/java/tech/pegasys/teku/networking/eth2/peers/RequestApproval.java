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

import java.util.Objects;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class RequestApproval {

  private final RequestsKey requestKey;
  private final long objectsCount;

  private RequestApproval(RequestsKey requestKey, long objectsCount) {
    this.requestKey = requestKey;
    this.objectsCount = objectsCount;
  }

  public RequestsKey getRequestKey() {
    return requestKey;
  }

  public long getObjectsCount() {
    return objectsCount;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final RequestApproval that = (RequestApproval) o;
    return objectsCount == that.objectsCount && Objects.equals(requestKey, that.requestKey);
  }

  @Override
  public int hashCode() {
    return Objects.hash(requestKey, objectsCount);
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

    public RequestApprovalBuilder objectsCount(final long objectsCount) {
      this.objectsCount = objectsCount;
      return this;
    }

    public RequestApproval build() {
      return new RequestApproval(
          new RequestsKey(this.timeSeconds, this.requestId), this.objectsCount);
    }
  }
}
