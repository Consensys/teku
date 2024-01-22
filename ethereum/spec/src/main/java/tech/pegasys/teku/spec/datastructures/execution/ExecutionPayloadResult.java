/*
 * Copyright Consensys Software Inc., 2022
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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.MoreObjects;
import java.util.Objects;
import java.util.Optional;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.infrastructure.async.SafeFuture;

public class ExecutionPayloadResult {

  private final ExecutionPayloadContext executionPayloadContext;
  private final Optional<SafeFuture<ExecutionPayload>> executionPayloadFuture;
  private final Optional<SafeFuture<Optional<BlobsBundle>>> blobsBundleFuture;
  private final Optional<SafeFuture<HeaderWithFallbackData>> headerWithFallbackDataFuture;
  private final Optional<SafeFuture<UInt256>> executionPayloadValueFuture;

  public ExecutionPayloadResult(
      final ExecutionPayloadContext executionPayloadContext,
      final Optional<SafeFuture<ExecutionPayload>> executionPayloadFuture,
      final Optional<SafeFuture<Optional<BlobsBundle>>> blobsBundleFuture,
      final Optional<SafeFuture<HeaderWithFallbackData>> headerWithFallbackDataFuture,
      final Optional<SafeFuture<UInt256>> executionPayloadValueFuture) {
    checkArgument(
        executionPayloadFuture.isPresent() != headerWithFallbackDataFuture.isPresent(),
        "Either executionPayloadFuture or headerWithFallbackDataFuture must be present");
    this.executionPayloadContext = executionPayloadContext;
    this.executionPayloadFuture = executionPayloadFuture;
    this.blobsBundleFuture = blobsBundleFuture;
    this.headerWithFallbackDataFuture = headerWithFallbackDataFuture;
    this.executionPayloadValueFuture = executionPayloadValueFuture;
  }

  public ExecutionPayloadContext getExecutionPayloadContext() {
    return executionPayloadContext;
  }

  public Optional<SafeFuture<ExecutionPayload>> getExecutionPayloadFuture() {
    return executionPayloadFuture;
  }

  public Optional<SafeFuture<Optional<BlobsBundle>>> getBlobsBundleFuture() {
    return blobsBundleFuture;
  }

  public Optional<SafeFuture<HeaderWithFallbackData>> getHeaderWithFallbackDataFuture() {
    return headerWithFallbackDataFuture;
  }

  public Optional<SafeFuture<UInt256>> getExecutionPayloadValueFuture() {
    return executionPayloadValueFuture;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ExecutionPayloadResult that = (ExecutionPayloadResult) o;
    return Objects.equals(executionPayloadContext, that.executionPayloadContext)
        && Objects.equals(executionPayloadFuture, that.executionPayloadFuture)
        && Objects.equals(blobsBundleFuture, that.blobsBundleFuture)
        && Objects.equals(headerWithFallbackDataFuture, that.headerWithFallbackDataFuture)
        && Objects.equals(executionPayloadValueFuture, that.executionPayloadValueFuture);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        executionPayloadContext,
        executionPayloadFuture,
        blobsBundleFuture,
        headerWithFallbackDataFuture,
        executionPayloadValueFuture);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("executionPayloadContext", executionPayloadContext)
        .add("executionPayloadFuture", executionPayloadFuture)
        .add("blobsBundleFuture", blobsBundleFuture)
        .add("headerWithFallbackDataFuture", headerWithFallbackDataFuture)
        .add("executionPayloadValueFuture", executionPayloadValueFuture)
        .toString();
  }
}
