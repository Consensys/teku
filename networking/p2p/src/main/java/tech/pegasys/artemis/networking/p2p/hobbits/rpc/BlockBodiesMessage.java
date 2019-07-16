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

package tech.pegasys.artemis.networking.p2p.hobbits.rpc;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;

@JsonDeserialize(using = BlockBodiesMessage.BlockBodiesDeserializer.class)
public final class BlockBodiesMessage {

  static class BlockBodiesDeserializer extends StdDeserializer<BlockBodiesMessage> {

    protected BlockBodiesDeserializer() {
      super(BlockBodiesMessage.class);
    }

    @Override
    public BlockBodiesMessage deserialize(JsonParser jp, DeserializationContext ctxt)
        throws IOException {
      JsonNode node = jp.getCodec().readTree(jp);
      Iterator<JsonNode> iterator = node.iterator();
      List<BeaconBlock> elts = new ArrayList<>();
      while (iterator.hasNext()) {
        JsonNode child = iterator.next();
        elts.add(BeaconBlock.fromBytes(Bytes.wrap(child.get("bytes").binaryValue())));
      }
      return new BlockBodiesMessage(elts);
    }
  }

  static class BlockBody {

    private final Bytes bytes;

    BlockBody(Bytes bytes) {
      this.bytes = bytes;
    }

    @JsonProperty("bytes")
    public Bytes bytes() {
      return bytes;
    }
  }

  private final List<BeaconBlock> bodies;

  BlockBodiesMessage(List<BeaconBlock> bodies) {
    this.bodies = bodies;
  }

  @JsonValue
  public List<BeaconBlock> bodies() {
    return bodies;
  }
}
