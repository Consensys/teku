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

package tech.pegasys.teku.api.response.v1;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;

public class BlockEvent {
  @JsonProperty(value = "slot", required = true)
  public final UInt64 slot;

  @JsonProperty(value = "block", required = true)
  public final Bytes32 block;

  @JsonCreator
  public BlockEvent(
      @JsonProperty(value = "slot", required = true) final UInt64 slot,
      @JsonProperty(value = "block", required = true) final Bytes32 block) {
    this.slot = slot;
    this.block = block;
  }

  public static BlockEvent fromSignedBeaconBlock(final SignedBeaconBlock signedBeaconBlock) {
    return new BlockEvent(signedBeaconBlock.getSlot(), signedBeaconBlock.getRoot());
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final BlockEvent that = (BlockEvent) o;
    return Objects.equals(slot, that.slot) && Objects.equals(block, that.block);
  }

  @Override
  public int hashCode() {
    return Objects.hash(slot, block);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("slot", slot).add("block", block).toString();
  }
}
