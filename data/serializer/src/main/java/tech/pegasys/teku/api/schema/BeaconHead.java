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

package tech.pegasys.teku.api.schema;

import static tech.pegasys.teku.api.schema.SchemaConstants.DESCRIPTION_BYTES32;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.blocks.BeaconBlockAndState;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class BeaconHead {
  @Schema(type = "string", format = "uint64")
  public final UInt64 slot;

  @Schema(type = "string", format = "byte", description = DESCRIPTION_BYTES32)
  public final Bytes32 block_root;

  @Schema(type = "string", format = "byte", description = DESCRIPTION_BYTES32)
  public final Bytes32 state_root;

  @JsonCreator
  public BeaconHead(
      @JsonProperty("slot") final UInt64 slot,
      @JsonProperty("block_root") final Bytes32 block_root,
      @JsonProperty("state_root") final Bytes32 state_root) {
    this.slot = slot;
    this.block_root = block_root;
    this.state_root = state_root;
  }

  public BeaconHead(final BeaconBlockAndState beaconBlockAndState) {
    this.slot = beaconBlockAndState.getSlot();
    this.block_root = beaconBlockAndState.getRoot();
    this.state_root = beaconBlockAndState.getState().hash_tree_root();
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final BeaconHead that = (BeaconHead) o;
    return Objects.equals(slot, that.slot)
        && Objects.equals(block_root, that.block_root)
        && Objects.equals(state_root, that.state_root);
  }

  public UInt64 getSlot() {
    return slot;
  }

  @Override
  public int hashCode() {
    return Objects.hash(slot, block_root, state_root);
  }
}
