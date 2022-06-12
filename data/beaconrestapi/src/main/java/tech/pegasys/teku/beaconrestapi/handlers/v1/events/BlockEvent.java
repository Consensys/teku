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

package tech.pegasys.teku.beaconrestapi.handlers.v1.events;

import static tech.pegasys.teku.infrastructure.http.RestApiConstants.EXECUTION_OPTIMISTIC;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BOOLEAN_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BYTES32_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.UINT64_TYPE;

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
          .withField(EXECUTION_OPTIMISTIC, BOOLEAN_TYPE, BlockData::isExecutionOptimistic)
          .build();

  BlockEvent(final SignedBeaconBlock block, final boolean executionOptimistic) {
    super(BLOCK_EVENT_TYPE, new BlockData(block.getSlot(), block.getRoot(), executionOptimistic));
  }

  public static class BlockData {
    private final UInt64 slot;
    private final Bytes32 block;
    private final boolean executionOptimistic;

    BlockData(final UInt64 slot, final Bytes32 block, final boolean executionOptimistic) {
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

    private boolean isExecutionOptimistic() {
      return executionOptimistic;
    }
  }
}
