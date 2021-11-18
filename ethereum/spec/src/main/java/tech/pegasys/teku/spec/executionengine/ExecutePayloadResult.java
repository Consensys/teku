/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.spec.executionengine;

import com.google.common.base.MoreObjects;
import java.util.Objects;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;

public class ExecutePayloadResult {
  public static final ExecutePayloadResult SYNCING =
      new ExecutePayloadResult(ExecutionPayloadStatus.SYNCING, Optional.empty(), Optional.empty());
  private final ExecutionPayloadStatus status;
  private final Optional<Bytes32> latestValidHash;
  private final Optional<String> message;

  public ExecutePayloadResult(
      ExecutionPayloadStatus status, Optional<Bytes32> latestValidHash, Optional<String> message) {
    this.status = status;
    this.latestValidHash = latestValidHash;
    this.message = message;
  }

  public ExecutionPayloadStatus getStatus() {
    return status;
  }

  public Optional<Bytes32> getLatestValidHash() {
    return latestValidHash;
  }

  public Optional<String> getMessage() {
    return message;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final ExecutePayloadResult that = (ExecutePayloadResult) o;
    return Objects.equals(status, that.status)
        && Objects.equals(latestValidHash, that.latestValidHash)
        && Objects.equals(message, that.message);
  }

  @Override
  public int hashCode() {
    return Objects.hash(status, latestValidHash, message);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("status", status)
        .add("latestValidHash", latestValidHash)
        .add("message", message)
        .toString();
  }
}
