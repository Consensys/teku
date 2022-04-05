/*
 * Copyright 2022 ConsenSys AG.
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

package tech.pegasys.teku.beaconrestapi.handlers.v1.events;

import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BYTES32_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.UINT64_TYPE;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;

public class BlockEvent extends Event<BlockEvent.BlockData> {
  private static final SerializableTypeDefinition<BlockData> BLOCK_EVENT_TYPE =
      SerializableTypeDefinition.object(BlockData.class)
          .name("BlockEvent")
          .withField("slot", UINT64_TYPE, BlockData::getSlot)
          .withField("block", BYTES32_TYPE, BlockData::getBlock)
          // TODO #5264
          // .withOptionalField("execution_optimistic", BOOLEAN_TYPE,
          // BlockData::getExecutionOptimistic)
          .build();

  BlockEvent(final SignedBeaconBlock block, final Boolean executionOptimistic) {
    super(
        BLOCK_EVENT_TYPE,
        new BlockData(block.getSlot(), block.getRoot(), Optional.ofNullable(executionOptimistic)));
  }

  public static class BlockData {
    private final UInt64 slot;
    private final Bytes32 block;
    private final Optional<Boolean> executionOptimistic;

    BlockData(final UInt64 slot, final Bytes32 block, final Optional<Boolean> executionOptimistic) {
      this.slot = slot;
      this.block = block;
      this.executionOptimistic = executionOptimistic;
    }

    private UInt64 getSlot() {
      return slot;
    }

    private Bytes32 getBlock() {
      return block;
    }

    @SuppressWarnings("UnusedMethod")
    private Optional<Boolean> getExecutionOptimistic() {
      return executionOptimistic;
    }
  }
}
