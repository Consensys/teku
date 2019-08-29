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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.util.SimpleOffsetSerializer;

@JsonSerialize(using = BlockBodiesMessage.BlockBodiesSerializer.class)
@JsonDeserialize(using = BlockBodiesMessage.BlockBodiesDeserializer.class)
public final class BlockBodiesMessage {

  static class BlockBodiesSerializer extends StdSerializer<BlockBodiesMessage> {

    protected BlockBodiesSerializer() {
      super(BlockBodiesMessage.class);
    }

    @Override
    public void serialize(
        BlockBodiesMessage blockBodiesMessage, JsonGenerator jgen, SerializerProvider provider)
        throws IllegalArgumentException {
      try {
        jgen.writeStartObject();
        jgen.writeArrayFieldStart("bodies");
        blockBodiesMessage
            .bodies()
            .forEach(
                item -> {
                  try {
                    jgen.writeBinary(SimpleOffsetSerializer.serialize(item).toArrayUnsafe());
                  } catch (java.io.IOException e) {
                    throw new IllegalArgumentException(e.getMessage());
                  }
                });
        jgen.writeEndArray();
        jgen.writeEndObject();
      } catch (java.io.IOException e) {
        throw new IllegalArgumentException(e.getMessage());
      }
    }
  }

  static class BlockBodiesDeserializer extends StdDeserializer<BlockBodiesMessage> {

    protected BlockBodiesDeserializer() {
      super(BlockBodiesMessage.class);
    }

    @Override
    public BlockBodiesMessage deserialize(JsonParser jp, DeserializationContext ctxt)
        throws IllegalArgumentException {
      List<BlockBody> elts = new ArrayList<>();
      try {
        JsonNode node = jp.getCodec().readTree(jp);
        Iterator<JsonNode> iterator = node.withArray("bodies").iterator();
        while (iterator.hasNext()) {
          elts.add(new BlockBody(Bytes.wrap(iterator.next().binaryValue())));
        }
      } catch (IOException e) {
        throw new IllegalArgumentException(e.getMessage());
      }
      return new BlockBodiesMessage(elts, true);
    }
  }

  static class BlockBody {

    private final Bytes bytes;

    BlockBody(Bytes bytes) {
      this.bytes = bytes;
    }

    public Bytes bytes() {
      return bytes;
    }
  }

  private List<BeaconBlock> bodies = new ArrayList<>();

  BlockBodiesMessage(List<BlockBody> blockBodies, boolean flag) {
    blockBodies.forEach(
        a -> this.bodies.add(SimpleOffsetSerializer.deserialize(a.bytes(), BeaconBlock.class)));
  }

  @JsonCreator
  public BlockBodiesMessage(@JsonProperty("bodies") List<BeaconBlock> beaconBlocks) {
    this.bodies = beaconBlocks;
  }

  @JsonProperty("bodies")
  public List<BeaconBlock> bodies() {
    return bodies;
  }
}
