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
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;

public class SlotAndExecutionPayload {
  private final UInt64 slot;
  private final ExecutionPayload executionPayload;

  public SlotAndExecutionPayload(final UInt64 slot, final ExecutionPayload executionPayload) {
    this.slot = slot;
    this.executionPayload = executionPayload;
  }

  public static Optional<SlotAndExecutionPayload> fromBlock(final SignedBeaconBlock block) {
    return block
        .getMessage()
        .getBody()
        .getOptionalExecutionPayload()
        .map(payload -> new SlotAndExecutionPayload(block.getSlot(), payload));
  }

  public UInt64 getSlot() {
    return slot;
  }

  public ExecutionPayload getExecutionPayload() {
    return executionPayload;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final SlotAndExecutionPayload that = (SlotAndExecutionPayload) o;
    return Objects.equals(slot, that.slot)
        && Objects.equals(executionPayload, that.executionPayload);
  }

  @Override
  public int hashCode() {
    return Objects.hash(slot, executionPayload);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("slot", slot)
        .add("executionPayload", executionPayload)
        .toString();
  }
}
