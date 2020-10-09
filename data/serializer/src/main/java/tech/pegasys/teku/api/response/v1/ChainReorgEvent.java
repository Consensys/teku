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
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ChainReorgEvent {
  @JsonProperty(value = "slot", required = true)
  public final UInt64 slot;

  @JsonProperty(value = "depth", required = true)
  public final UInt64 depth;

  @JsonProperty("old_head_block")
  public final Bytes32 oldHeadBlock;

  @JsonProperty("new_head_block")
  public final Bytes32 newHeadBlock;

  @JsonProperty("old_head_state")
  public final Bytes32 oldHeadState;

  @JsonProperty("new_head_state")
  public final Bytes32 newHeadState;

  @JsonProperty("epoch")
  public final UInt64 epoch;

  @JsonCreator
  public ChainReorgEvent(
      @JsonProperty(value = "slot", required = true) final UInt64 slot,
      @JsonProperty(value = "depth", required = true) final UInt64 depth,
      @JsonProperty("old_head_block") final Bytes32 oldHeadBlock,
      @JsonProperty("new_head_block") final Bytes32 newHeadBlock,
      @JsonProperty("old_head_state") final Bytes32 oldHeadState,
      @JsonProperty("new_head_state") final Bytes32 newHeadState,
      @JsonProperty("epoch") final UInt64 epoch) {
    this.slot = slot;
    this.depth = depth;
    this.oldHeadBlock = oldHeadBlock;
    this.newHeadBlock = newHeadBlock;
    this.oldHeadState = oldHeadState;
    this.newHeadState = newHeadState;
    this.epoch = epoch;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final ChainReorgEvent that = (ChainReorgEvent) o;
    return Objects.equals(slot, that.slot)
        && Objects.equals(depth, that.depth)
        && Objects.equals(oldHeadBlock, that.oldHeadBlock)
        && Objects.equals(newHeadBlock, that.newHeadBlock)
        && Objects.equals(oldHeadState, that.oldHeadState)
        && Objects.equals(newHeadState, that.newHeadState)
        && Objects.equals(epoch, that.epoch);
  }

  @Override
  public int hashCode() {
    return Objects.hash(slot, depth, oldHeadBlock, newHeadBlock, oldHeadState, newHeadState, epoch);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("slot", slot)
        .add("depth", depth)
        .add("oldHeadBlock", oldHeadBlock)
        .add("newHeadBlock", newHeadBlock)
        .add("oldHeadState", oldHeadState)
        .add("newHeadState", newHeadState)
        .add("epoch", epoch)
        .toString();
  }
}
