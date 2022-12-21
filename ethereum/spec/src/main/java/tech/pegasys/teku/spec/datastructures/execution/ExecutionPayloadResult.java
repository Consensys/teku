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
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.kzg.KZGCommitment;
import tech.pegasys.teku.spec.datastructures.execution.versions.eip4844.Blob;
import tech.pegasys.teku.spec.datastructures.execution.versions.eip4844.BlobsBundle;

public class ExecutionPayloadResult {
  private final ExecutionPayloadContext executionPayloadContext;
  private final Optional<SafeFuture<ExecutionPayload>> executionPayloadFuture;
  private final Optional<SafeFuture<ExecutionPayloadHeader>> executionPayloadHeaderFuture;
  private final Optional<SafeFuture<BlobsBundle>> blobsBundleFuture;
  private final Optional<SafeFuture<Optional<FallbackData>>> fallbackDataFuture;

  public ExecutionPayloadResult(
      final ExecutionPayloadContext executionPayloadContext,
      final Optional<SafeFuture<ExecutionPayload>> executionPayloadFuture,
      final Optional<SafeFuture<ExecutionPayloadHeader>> executionPayloadHeaderFuture,
      final Optional<SafeFuture<Optional<FallbackData>>> fallbackDataFuture,
      final Optional<SafeFuture<BlobsBundle>> blobsBundleFuture) {
    this.executionPayloadContext = executionPayloadContext;
    this.executionPayloadFuture = executionPayloadFuture;
    this.executionPayloadHeaderFuture = executionPayloadHeaderFuture;
    this.fallbackDataFuture = fallbackDataFuture;
    this.blobsBundleFuture = blobsBundleFuture;
  }

  public ExecutionPayloadContext getExecutionPayloadContext() {
    return executionPayloadContext;
  }

  public Optional<SafeFuture<ExecutionPayload>> getExecutionPayloadFuture() {
    return executionPayloadFuture;
  }

  public Optional<SafeFuture<ExecutionPayloadHeader>> getExecutionPayloadHeaderFuture() {
    return executionPayloadHeaderFuture;
  }

  /**
   * if we serve unblind production, external optional is empty
   *
   * <p>if we serve builderGetHeader using local execution engine, we store fallback in internal
   * optional
   *
   * <p>if we serve builderGetHeader using builder, we store slot->Optional.empty() in internal
   * optional to signal that we must call the builder to serve builderGetPayload later
   */
  public Optional<SafeFuture<Optional<FallbackData>>> getFallbackDataFuture() {
    return fallbackDataFuture;
  }

  public Optional<SafeFuture<BlobsBundle>> getBlobsBundleFuture() {
    return blobsBundleFuture;
  }

  public Optional<SafeFuture<List<KZGCommitment>>> getKzgs() {
    return blobsBundleFuture.map(future -> future.thenApply(BlobsBundle::getKzgs));
  }

  public Optional<SafeFuture<List<Blob>>> getBlobs() {
    return blobsBundleFuture.map(future -> future.thenApply(BlobsBundle::getBlobs));
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
        && Objects.equals(executionPayloadHeaderFuture, that.executionPayloadHeaderFuture)
        && Objects.equals(fallbackDataFuture, that.fallbackDataFuture)
        && Objects.equals(blobsBundleFuture, that.blobsBundleFuture);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        executionPayloadContext,
        executionPayloadFuture,
        executionPayloadHeaderFuture,
        fallbackDataFuture,
        blobsBundleFuture);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("executionPayloadContext", executionPayloadContext)
        .add("executionPayloadFuture", executionPayloadFuture)
        .add("executionPayloadHeaderFuture", executionPayloadHeaderFuture)
        .add("fallbackDataFuture", fallbackDataFuture)
        .add("blobsBundleFuture", blobsBundleFuture)
        .toString();
  }
}
