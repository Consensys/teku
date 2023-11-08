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
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;

/**
 * if we serve unblind production, external optional is empty
 *
 * <p>if we serve builderGetHeader using local execution engine, we store fallback in internal
 * optional
 *
 * <p>if we serve builderGetHeader using builder, we store slot->Optional.empty() in internal
 * optional to signal that we must call the builder to serve builderGetPayload later
 */
public class HeaderWithFallbackData {

  private final ExecutionPayloadHeader executionPayloadHeader;
  private final Optional<SszList<SszKZGCommitment>> blobKzgCommitments;
  private final Optional<FallbackData> fallbackData;

  private HeaderWithFallbackData(
      final ExecutionPayloadHeader executionPayloadHeader,
      final Optional<SszList<SszKZGCommitment>> blobKzgCommitments,
      final Optional<FallbackData> fallbackData) {
    this.executionPayloadHeader = executionPayloadHeader;
    this.blobKzgCommitments = blobKzgCommitments;
    this.fallbackData = fallbackData;
  }

  public static HeaderWithFallbackData create(
      final ExecutionPayloadHeader executionPayloadHeader, final FallbackData fallbackData) {
    return new HeaderWithFallbackData(
        executionPayloadHeader, Optional.empty(), Optional.of(fallbackData));
  }

  public static HeaderWithFallbackData create(final ExecutionPayloadHeader executionPayloadHeader) {
    return new HeaderWithFallbackData(executionPayloadHeader, Optional.empty(), Optional.empty());
  }

  public static HeaderWithFallbackData create(
      final ExecutionPayloadHeader executionPayloadHeader,
      final Optional<SszList<SszKZGCommitment>> blobKzgCommitments,
      final FallbackData fallbackData) {
    return new HeaderWithFallbackData(
        executionPayloadHeader, blobKzgCommitments, Optional.of(fallbackData));
  }

  public static HeaderWithFallbackData create(
      final ExecutionPayloadHeader executionPayloadHeader,
      final Optional<SszList<SszKZGCommitment>> blobKzgCommitments) {
    return new HeaderWithFallbackData(executionPayloadHeader, blobKzgCommitments, Optional.empty());
  }

  public ExecutionPayloadHeader getExecutionPayloadHeader() {
    return executionPayloadHeader;
  }

  public Optional<SszList<SszKZGCommitment>> getBlobKzgCommitments() {
    return blobKzgCommitments;
  }

  public Optional<FallbackData> getFallbackData() {
    return fallbackData;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final HeaderWithFallbackData that = (HeaderWithFallbackData) o;
    return Objects.equals(executionPayloadHeader, that.executionPayloadHeader)
        && Objects.equals(blobKzgCommitments, that.blobKzgCommitments)
        && Objects.equals(fallbackData, that.fallbackData);
  }

  @Override
  public int hashCode() {
    return Objects.hash(executionPayloadHeader, blobKzgCommitments, fallbackData);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("executionPayloadHeader", executionPayloadHeader)
        .add("blobKzgCommitments", blobKzgCommitments)
        .add("fallbackData", fallbackData)
        .toString();
  }
}
