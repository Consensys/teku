/*
 * Copyright 2019 ConsenSys AG.
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

package tech.pegasys.artemis.networking.p2p.hobbits;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.consensys.cava.bytes.Bytes32;

@JsonDeserialize(using = BlockRoots.BlockRootsDeserializer.class)
final class BlockRoots {

  static class BlockRootsDeserializer extends StdDeserializer<BlockRoots> {

    protected BlockRootsDeserializer() {
      super(BlockRoots.class);
    }

    @Override
    public BlockRoots deserialize(JsonParser jp, DeserializationContext ctxt)
        throws IOException, JsonProcessingException {
      JsonNode node = jp.getCodec().readTree(jp);
      Iterator<JsonNode> iterator = node.iterator();
      List<BlockRootAndSlot> elts = new ArrayList<>();
      while (iterator.hasNext()) {
        JsonNode child = iterator.next();
        elts.add(
            new BlockRootAndSlot(
                Bytes32.fromHexString(child.get("block_root").asText()),
                child.get("slot").asLong()));
      }

      return new BlockRoots(elts);
    }
  }

  static class BlockRootAndSlot {

    private final Bytes32 blockRoot;
    private final long slot;

    BlockRootAndSlot(Bytes32 blockRoot, long slot) {
      this.blockRoot = blockRoot;
      this.slot = slot;
    }

    @JsonProperty("block_root")
    public Bytes32 blockRoot() {
      return blockRoot;
    }

    @JsonProperty("slot")
    public long slot() {
      return slot;
    }
  }

  private final List<BlockRootAndSlot> rootsAndSlots;

  BlockRoots(List<BlockRootAndSlot> rootsAndSlots) {
    this.rootsAndSlots = rootsAndSlots;
  }

  @JsonValue
  public List<BlockRootAndSlot> rootsAndSlots() {
    return rootsAndSlots;
  }
}
