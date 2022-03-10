/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.api.response.v1;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class FinalizedCheckpointEvent {
  @JsonProperty("block")
  public final Bytes32 block;

  @JsonProperty("state")
  public final Bytes32 state;

  @JsonProperty("epoch")
  public final UInt64 epoch;

  @JsonProperty("execution_optimistic")
  @JsonInclude(Include.NON_NULL)
  @Schema(hidden = true)
  public final Boolean executionOptimistic;

  @JsonCreator
  public FinalizedCheckpointEvent(
      @JsonProperty("block") final Bytes32 block,
      @JsonProperty("state") final Bytes32 state,
      @JsonProperty("epoch") final UInt64 epoch,
      @JsonProperty("execution_optimistic") final Boolean executionOptimistic) {
    this.block = block;
    this.state = state;
    this.epoch = epoch;
    this.executionOptimistic = executionOptimistic;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final FinalizedCheckpointEvent that = (FinalizedCheckpointEvent) o;
    return Objects.equals(block, that.block)
        && Objects.equals(state, that.state)
        && Objects.equals(epoch, that.epoch)
        && Objects.equals(executionOptimistic, that.executionOptimistic);
  }

  @Override
  public int hashCode() {
    return Objects.hash(block, state, epoch, executionOptimistic);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("block", block)
        .add("state", state)
        .add("epoch", epoch)
        .add("executionOptimistic", executionOptimistic)
        .toString();
  }
}
