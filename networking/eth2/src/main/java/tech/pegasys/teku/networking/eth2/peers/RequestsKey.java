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

import java.util.Comparator;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class RequestsKey implements Comparable<RequestsKey> {
  private final UInt64 timeSeconds;
  private final int requestId;

  public RequestsKey(final UInt64 timeSeconds, final int requestId) {
    this.timeSeconds = timeSeconds;
    this.requestId = requestId;
  }

  public UInt64 getTimeSeconds() {
    return timeSeconds;
  }

  public int getRequestId() {
    return requestId;
  }

  @Override
  public int hashCode() {
    return Objects.hash(timeSeconds, requestId);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final RequestsKey that = (RequestsKey) o;
    return Objects.equals(this.timeSeconds, that.timeSeconds) && this.requestId == that.requestId;
  }

  @Override
  public int compareTo(@NotNull RequestsKey other) {
    return Comparator.comparing(RequestsKey::getTimeSeconds)
        .thenComparingInt(RequestsKey::getRequestId)
        .compare(this, other);
  }
}
