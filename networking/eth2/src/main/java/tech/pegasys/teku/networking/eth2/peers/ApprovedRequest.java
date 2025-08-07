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

import java.util.Objects;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class ApprovedRequest {

  private final RequestKey requestKey;
  private long requestSize;

  private ApprovedRequest(final RequestKey requestKey, final long requestSize) {
    this.requestKey = requestKey;
    this.requestSize = requestSize;
  }

  public RequestKey getRequestKey() {
    return requestKey;
  }

  void setRequestSize(final long requestSize) {
    this.requestSize = requestSize;
  }

  public long getRequestSize() {
    return requestSize;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ApprovedRequest that = (ApprovedRequest) o;
    return requestSize == that.requestSize && Objects.equals(requestKey, that.requestKey);
  }

  @Override
  public int hashCode() {
    return Objects.hash(requestKey, requestSize);
  }

  public static final class RequestApprovalBuilder {
    private int requestId;
    private UInt64 timeSeconds;
    private long requestSize;

    public RequestApprovalBuilder requestId(final int requestId) {
      this.requestId = requestId;
      return this;
    }

    public RequestApprovalBuilder timeSeconds(final UInt64 timeSeconds) {
      this.timeSeconds = timeSeconds;
      return this;
    }

    public RequestApprovalBuilder requestSize(final long requestSize) {
      this.requestSize = requestSize;
      return this;
    }

    public ApprovedRequest build() {
      return new ApprovedRequest(
          new RequestKey(this.timeSeconds, this.requestId), this.requestSize);
    }
  }
}
