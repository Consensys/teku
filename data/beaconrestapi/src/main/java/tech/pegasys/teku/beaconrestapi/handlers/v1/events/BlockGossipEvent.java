/*
 * Copyright Consensys Software Inc., 2024
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

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;

public class BlockGossipEvent extends Event<BlockGossipEvent.BlockData> {

  private static final SerializableTypeDefinition<BlockGossipEvent.BlockData>
      BLOCK_GOSSIP_EVENT_TYPE =
          SerializableTypeDefinition.object(BlockGossipEvent.BlockData.class)
              .name("BlockGossipEvent")
              .withField("slot", UINT64_TYPE, BlockGossipEvent.BlockData::slot)
              .withField("block", BYTES32_TYPE, BlockGossipEvent.BlockData::block)
              .build();

  BlockGossipEvent(final SignedBeaconBlock block) {
    super(BLOCK_GOSSIP_EVENT_TYPE, new BlockData(block.getSlot(), block.getRoot()));
  }

  public record BlockData(UInt64 slot, Bytes32 block) {}
}
