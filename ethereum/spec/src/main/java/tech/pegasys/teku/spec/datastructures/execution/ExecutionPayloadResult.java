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

import com.google.common.base.MoreObjects;
import java.util.Objects;
import java.util.Optional;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.datastructures.builder.BuilderBid;

/**
 * In non-blinded flow, both {@link #executionPayloadFuture} and {@link #blobsBundleFuture} would be
 * present. The {@link #blobsBundleFuture} will have a value when the future is complete only after
 * Deneb, otherwise it will be empty.
 *
 * <p>In blinded flow, {@link #builderBidOrFallbackDataFuture} would be present
 */
public class ExecutionPayloadResult {

  private final ExecutionPayloadContext executionPayloadContext;
  private final Optional<SafeFuture<ExecutionPayload>> executionPayloadFuture;
  private final Optional<SafeFuture<Optional<BlobsBundle>>> blobsBundleFuture;
  private final Optional<SafeFuture<BuilderBidOrFallbackData>> builderBidOrFallbackDataFuture;
  private final SafeFuture<UInt256> executionPayloadValueFuture;

  private ExecutionPayloadResult(
      final ExecutionPayloadContext executionPayloadContext,
      final Optional<SafeFuture<ExecutionPayload>> executionPayloadFuture,
      final Optional<SafeFuture<Optional<BlobsBundle>>> blobsBundleFuture,
      final Optional<SafeFuture<BuilderBidOrFallbackData>> builderBidOrFallbackDataFuture,
      final SafeFuture<UInt256> executionPayloadValueFuture) {
    this.executionPayloadContext = executionPayloadContext;
    this.executionPayloadFuture = executionPayloadFuture;
    this.blobsBundleFuture = blobsBundleFuture;
    this.builderBidOrFallbackDataFuture = builderBidOrFallbackDataFuture;
    this.executionPayloadValueFuture = executionPayloadValueFuture;
  }

  public ExecutionPayloadContext getExecutionPayloadContext() {
    return executionPayloadContext;
  }

  public Optional<SafeFuture<ExecutionPayload>> getExecutionPayloadFutureFromNonBlindedFlow() {
    return executionPayloadFuture;
  }

  public Optional<SafeFuture<Optional<BlobsBundle>>> getBlobsBundleFutureFromNonBlindedFlow() {
    return blobsBundleFuture;
  }

  public Optional<SafeFuture<BuilderBidOrFallbackData>> getBuilderBidOrFallbackDataFuture() {
    return builderBidOrFallbackDataFuture;
  }

  /**
   * @return the value from the local payload, the builder bid or the local fallback payload
   */
  public SafeFuture<UInt256> getExecutionPayloadValueFuture() {
    return executionPayloadValueFuture;
  }

  public boolean isFromNonBlindedFlow() {
    return executionPayloadFuture.isPresent();
  }

  public static ExecutionPayloadResult createForNonBlindedFlow(
      final ExecutionPayloadContext executionPayloadContext,
      final SafeFuture<GetPayloadResponse> getPayloadResponseFuture) {
    final SafeFuture<UInt256> executionPayloadValueFuture =
        getPayloadResponseFuture.thenApply(GetPayloadResponse::getExecutionPayloadValue);
    return new ExecutionPayloadResult(
        executionPayloadContext,
        Optional.of(getPayloadResponseFuture.thenApply(GetPayloadResponse::getExecutionPayload)),
        Optional.of(getPayloadResponseFuture.thenApply(GetPayloadResponse::getBlobsBundle)),
        Optional.empty(),
        executionPayloadValueFuture);
  }

  public static ExecutionPayloadResult createForBlindedFlow(
      final ExecutionPayloadContext executionPayloadContext,
      final SafeFuture<BuilderBidOrFallbackData> builderBidOrFallbackDataFuture) {
    final SafeFuture<UInt256> executionPayloadValueFuture =
        builderBidOrFallbackDataFuture.thenApply(
            builderBidOrFallbackData ->
                builderBidOrFallbackData
                    .getBuilderBid()
                    // from the builder bid
                    .map(BuilderBid::getValue)
                    // from the local fallback
                    .orElseGet(
                        () ->
                            builderBidOrFallbackData
                                .getFallbackDataRequired()
                                .getExecutionPayloadValue()));
    return new ExecutionPayloadResult(
        executionPayloadContext,
        Optional.empty(),
        Optional.empty(),
        Optional.of(builderBidOrFallbackDataFuture),
        executionPayloadValueFuture);
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
        && Objects.equals(builderBidOrFallbackDataFuture, that.builderBidOrFallbackDataFuture)
        && Objects.equals(executionPayloadValueFuture, that.executionPayloadValueFuture);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        executionPayloadContext,
        executionPayloadFuture,
        blobsBundleFuture,
        builderBidOrFallbackDataFuture,
        executionPayloadValueFuture);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("executionPayloadContext", executionPayloadContext)
        .add("executionPayloadFuture", executionPayloadFuture)
        .add("blobsBundleFuture", blobsBundleFuture)
        .add("builderBidOrFallbackDataFuture", builderBidOrFallbackDataFuture)
        .add("executionPayloadValueFuture", executionPayloadValueFuture)
        .toString();
  }
}
