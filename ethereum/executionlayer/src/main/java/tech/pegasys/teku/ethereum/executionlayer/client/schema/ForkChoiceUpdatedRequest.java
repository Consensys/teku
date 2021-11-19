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
import com.google.common.base.MoreObjects;
import java.util.Objects;

public class ForkChoiceUpdatedRequest {
  @JsonProperty("forkchoiceState")
  private final ForkChoiceStateV1 forkchoiceState;

  @JsonProperty("payloadAttributes")
  private final PayloadAttributesV1 payloadAttributes;

  public ForkChoiceUpdatedRequest(
      @JsonProperty("forkchoiceState") ForkChoiceStateV1 forkchoiceState,
      @JsonProperty("payloadAttributes") PayloadAttributesV1 payloadAttributes) {
    checkNotNull(forkchoiceState, "forkchoiceState");
    this.forkchoiceState = forkchoiceState;
    this.payloadAttributes = payloadAttributes;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final ForkChoiceUpdatedRequest that = (ForkChoiceUpdatedRequest) o;
    return Objects.equals(forkchoiceState, that.forkchoiceState)
        && Objects.equals(payloadAttributes, that.payloadAttributes);
  }

  @Override
  public int hashCode() {
    return Objects.hash(forkchoiceState, payloadAttributes);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("forkchoiceState", forkchoiceState)
        .add("payloadAttributes", payloadAttributes)
        .toString();
  }
}
