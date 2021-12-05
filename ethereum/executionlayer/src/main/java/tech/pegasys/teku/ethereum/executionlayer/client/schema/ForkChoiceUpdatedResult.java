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

package tech.pegasys.teku.ethereum.executionlayer.client.schema;

import static com.google.common.base.Preconditions.checkNotNull;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.base.MoreObjects;
import java.util.Objects;
import java.util.Optional;
import tech.pegasys.teku.ethereum.executionlayer.client.serialization.Bytes8Deserializer;
import tech.pegasys.teku.infrastructure.ssz.type.Bytes8;
import tech.pegasys.teku.spec.executionengine.ForkChoiceUpdatedStatus;

public class ForkChoiceUpdatedResult {
  private final ForkChoiceUpdatedStatus status;

  @JsonDeserialize(using = Bytes8Deserializer.class)
  private final Bytes8 payloadId;

  public ForkChoiceUpdatedResult(
      @JsonProperty("status") ForkChoiceUpdatedStatus status,
      @JsonProperty("payloadId") Bytes8 payloadId) {
    checkNotNull(status, "status");
    this.status = status;
    this.payloadId = payloadId;
  }

  public tech.pegasys.teku.spec.executionengine.ForkChoiceUpdatedResult
      asInternalExecutionPayload() {
    return new tech.pegasys.teku.spec.executionengine.ForkChoiceUpdatedResult(
        status, Optional.ofNullable(payloadId));
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final ForkChoiceUpdatedResult that = (ForkChoiceUpdatedResult) o;
    return Objects.equals(status, that.status) && Objects.equals(payloadId, that.payloadId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(status, payloadId);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("status", status)
        .add("payloadId", payloadId)
        .toString();
  }
}
