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
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class ChainReorgEvent {
  @JsonProperty("slot")
  public final UInt64 slot;

  @JsonProperty("depth")
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
      @JsonProperty("slot") final UInt64 slot,
      @JsonProperty("depth") final UInt64 depth,
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
}
