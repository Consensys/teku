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
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.bytes.Bytes8;
import tech.pegasys.teku.spec.datastructures.execution.versions.eip4844.BlobsBundle;
import tech.pegasys.teku.spec.executionlayer.ForkChoiceState;
import tech.pegasys.teku.spec.executionlayer.PayloadBuildingAttributes;

public class ExecutionPayloadContext {
  private final Bytes8 payloadId;
  private final ForkChoiceState forkChoiceState;
  private final PayloadBuildingAttributes payloadBuildingAttributes;
  private final Optional<SafeFuture<ExecutionPayload>> executionPayloadFuture;
  private final Optional<FallbackData> fallbackData;
  private final Optional<SafeFuture<BlobsBundle>> blobsBundleFuture;

  private ExecutionPayloadContext(
      final Bytes8 payloadId,
      final ForkChoiceState forkChoiceState,
      final PayloadBuildingAttributes payloadBuildingAttributes,
      final Optional<SafeFuture<ExecutionPayload>> executionPayloadFuture,
      final Optional<FallbackData> fallbackData,
      final Optional<SafeFuture<BlobsBundle>> blobsBundleFuture) {
    this.payloadId = payloadId;
    this.forkChoiceState = forkChoiceState;
    this.payloadBuildingAttributes = payloadBuildingAttributes;
    this.executionPayloadFuture = executionPayloadFuture;
    this.fallbackData = fallbackData;
    this.blobsBundleFuture = blobsBundleFuture;
  }

  public ExecutionPayloadContext(
      final Bytes8 payloadId,
      final ForkChoiceState forkChoiceState,
      final PayloadBuildingAttributes payloadBuildingAttributes) {
    this.payloadId = payloadId;
    this.forkChoiceState = forkChoiceState;
    this.payloadBuildingAttributes = payloadBuildingAttributes;
    this.executionPayloadFuture = Optional.empty();
    this.fallbackData = Optional.empty();
    this.blobsBundleFuture = Optional.empty();
  }

  public ExecutionPayloadContext withExecutionPayloadFuture(
      final SafeFuture<ExecutionPayload> executionPayloadFuture) {
    return new ExecutionPayloadContext(
        this.payloadId,
        this.forkChoiceState,
        this.payloadBuildingAttributes,
        Optional.of(executionPayloadFuture),
        this.fallbackData,
        this.blobsBundleFuture);
  }

  public ExecutionPayloadContext withFallbackData(final FallbackData fallbackData) {
    return new ExecutionPayloadContext(
        this.payloadId,
        this.forkChoiceState,
        this.payloadBuildingAttributes,
        this.executionPayloadFuture,
        Optional.of(fallbackData),
        this.blobsBundleFuture);
  }

  public ExecutionPayloadContext withBlobsBundle(final SafeFuture<BlobsBundle> blobsBundleFuture) {
    return new ExecutionPayloadContext(
        this.payloadId,
        this.forkChoiceState,
        this.payloadBuildingAttributes,
        this.executionPayloadFuture,
        this.fallbackData,
        Optional.of(blobsBundleFuture));
  }

  public Bytes8 getPayloadId() {
    return payloadId;
  }

  public ForkChoiceState getForkChoiceState() {
    return forkChoiceState;
  }

  public PayloadBuildingAttributes getPayloadBuildingAttributes() {
    return payloadBuildingAttributes;
  }

  public Bytes32 getParentHash() {
    return forkChoiceState.getHeadExecutionBlockHash();
  }

  public Optional<SafeFuture<ExecutionPayload>> getExecutionPayloadFuture() {
    return executionPayloadFuture;
  }

  public Optional<FallbackData> getFallbackData() {
    return fallbackData;
  }

  public Optional<SafeFuture<BlobsBundle>> getBlobsBundleFuture() {
    return blobsBundleFuture;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ExecutionPayloadContext that = (ExecutionPayloadContext) o;
    return Objects.equals(payloadId, that.payloadId)
        && Objects.equals(forkChoiceState, that.forkChoiceState)
        && Objects.equals(payloadBuildingAttributes, that.payloadBuildingAttributes)
        && Objects.equals(executionPayloadFuture, that.executionPayloadFuture)
        && Objects.equals(fallbackData, that.fallbackData)
        && Objects.equals(blobsBundleFuture, that.blobsBundleFuture);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        payloadId,
        forkChoiceState,
        payloadBuildingAttributes,
        executionPayloadFuture,
        fallbackData,
        blobsBundleFuture);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("payloadId", payloadId)
        .add("forkChoiceState", forkChoiceState)
        .add("payloadBuildingAttributes", payloadBuildingAttributes)
        .add("executionPayloadFuture", executionPayloadFuture)
        .add("fallbackData", fallbackData)
        .add("blobsBundleFuture", blobsBundleFuture)
        .toString();
  }
}
