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
import tech.pegasys.teku.spec.datastructures.builder.BuilderBid;

/**
 * {@link #builderBid} would be either a real builder bid or a local "mimicked" bid (if there has
 * been a local fallback)
 *
 * <p>If there has been a local fallback, {@link #fallbackData} will contain the local payload.
 */
public class BuilderBidWithFallbackData {

  private final BuilderBid builderBid;
  private final Optional<FallbackData> fallbackData;

  private BuilderBidWithFallbackData(
      final BuilderBid builderBid, final Optional<FallbackData> fallbackData) {
    this.builderBid = builderBid;
    this.fallbackData = fallbackData;
  }

  public static BuilderBidWithFallbackData create(final BuilderBid builderBid) {
    return new BuilderBidWithFallbackData(builderBid, Optional.empty());
  }

  public static BuilderBidWithFallbackData create(
      final BuilderBid builderBid, final FallbackData fallbackData) {
    return new BuilderBidWithFallbackData(builderBid, Optional.of(fallbackData));
  }

  /**
   * @return the bid from a real builder or a local EL (if there has been a fallback)
   */
  public BuilderBid getBuilderBid() {
    return builderBid;
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
    final BuilderBidWithFallbackData that = (BuilderBidWithFallbackData) o;
    return Objects.equals(builderBid, that.builderBid)
        && Objects.equals(fallbackData, that.fallbackData);
  }

  @Override
  public int hashCode() {
    return Objects.hash(builderBid, fallbackData);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("builderBid", builderBid)
        .add("fallbackData", fallbackData)
        .toString();
  }
}
