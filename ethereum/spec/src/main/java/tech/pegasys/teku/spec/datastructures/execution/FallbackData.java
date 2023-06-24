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

package tech.pegasys.teku.spec.datastructures.execution;

import com.google.common.base.MoreObjects;
import java.util.Objects;
import java.util.Optional;

public class FallbackData {

  private final ExecutionPayload executionPayload;
  private final Optional<BlobsBundle> blobsBundle;
  private final FallbackReason reason;

  public FallbackData(final ExecutionPayload executionPayload, final FallbackReason reason) {
    this.executionPayload = executionPayload;
    this.blobsBundle = Optional.empty();
    this.reason = reason;
  }

  public FallbackData(
      final ExecutionPayload executionPayload,
      final Optional<BlobsBundle> blobsBundle,
      final FallbackReason reason) {
    this.executionPayload = executionPayload;
    this.blobsBundle = blobsBundle;
    this.reason = reason;
  }

  public ExecutionPayload getExecutionPayload() {
    return executionPayload;
  }

  public Optional<BlobsBundle> getBlobsBundle() {
    return blobsBundle;
  }

  public FallbackReason getReason() {
    return reason;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof FallbackData)) {
      return false;
    }
    final FallbackData that = (FallbackData) o;
    return Objects.equals(executionPayload, that.executionPayload)
        && Objects.equals(blobsBundle, that.blobsBundle)
        && reason == that.reason;
  }

  @Override
  public int hashCode() {
    return Objects.hash(executionPayload, blobsBundle, reason);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("executionPayload", executionPayload)
        .add("blobsBundle", blobsBundle)
        .add("reason", reason)
        .toString();
  }
}
