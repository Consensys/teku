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
import tech.pegasys.teku.spec.datastructures.builder.BlindedBlobsBundle;

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
  private final Optional<BlindedBlobsBundle> blindedBlobsBundle;
  private final Optional<FallbackData> fallbackDataOptional;

  private HeaderWithFallbackData(
      final ExecutionPayloadHeader executionPayloadHeader,
      final Optional<BlindedBlobsBundle> blindedBlobsBundle,
      final Optional<FallbackData> fallbackDataOptional) {
    this.executionPayloadHeader = executionPayloadHeader;
    this.blindedBlobsBundle = blindedBlobsBundle;
    this.fallbackDataOptional = fallbackDataOptional;
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
      final Optional<BlindedBlobsBundle> blindedBlobsBundle,
      final FallbackData fallbackData) {
    return new HeaderWithFallbackData(
        executionPayloadHeader, blindedBlobsBundle, Optional.of(fallbackData));
  }

  public static HeaderWithFallbackData create(
      final ExecutionPayloadHeader executionPayloadHeader,
      final Optional<BlindedBlobsBundle> blindedBlobsBundle) {
    return new HeaderWithFallbackData(executionPayloadHeader, blindedBlobsBundle, Optional.empty());
  }

  public ExecutionPayloadHeader getExecutionPayloadHeader() {
    return executionPayloadHeader;
  }

  public Optional<BlindedBlobsBundle> getBlindedBlobsBundle() {
    return blindedBlobsBundle;
  }

  public Optional<FallbackData> getFallbackDataOptional() {
    return fallbackDataOptional;
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
        && Objects.equals(blindedBlobsBundle, that.blindedBlobsBundle)
        && Objects.equals(fallbackDataOptional, that.fallbackDataOptional);
  }

  @Override
  public int hashCode() {
    return Objects.hash(executionPayloadHeader, blindedBlobsBundle, fallbackDataOptional);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("executionPayloadHeader", executionPayloadHeader)
        .add("blindedBlobsBundle", blindedBlobsBundle)
        .add("fallbackDataOptional", fallbackDataOptional)
        .toString();
  }
}
