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
import net.consensys.cava.bytes.Bytes;
import net.consensys.cava.bytes.Bytes32;

@JsonDeserialize(using = BlockHeaders.BlockHeadersDeserializer.class)
final class BlockHeaders {

  static class BlockHeadersDeserializer extends StdDeserializer<BlockHeaders> {

    protected BlockHeadersDeserializer() {
      super(BlockHeaders.class);
    }

    @Override
    public BlockHeaders deserialize(JsonParser jp, DeserializationContext ctxt)
        throws IOException, JsonProcessingException {
      JsonNode node = jp.getCodec().readTree(jp);
      Iterator<JsonNode> iterator = node.iterator();
      List<BlockHeader> elts = new ArrayList<>();
      while (iterator.hasNext()) {
        JsonNode child = iterator.next();
        elts.add(
            new BlockHeader(
                child.get("slot").asLong(),
                Bytes32.fromHexString(child.get("previous_block_root").asText()),
                Bytes32.fromHexString(child.get("state_root").asText()),
                Bytes32.fromHexString(child.get("block_body_root").asText()),
                Bytes.fromHexString(child.get("signature").asText())));
      }

      return new BlockHeaders(elts);
    }
  }

  static class BlockHeader {

    private final long slot;
    private final Bytes32 previousBlockRoot;
    private final Bytes32 stateRoot;
    private final Bytes32 blockBodyRoot;
    private final Bytes signature;

    BlockHeader(
        long slot,
        Bytes32 previousBlockRoot,
        Bytes32 stateRoot,
        Bytes32 blockBodyRoot,
        Bytes signature) {
      this.slot = slot;
      this.previousBlockRoot = previousBlockRoot;
      this.stateRoot = stateRoot;
      this.blockBodyRoot = blockBodyRoot;
      this.signature = signature;
    }

    @JsonProperty("slot")
    public long slot() {
      return slot;
    }

    @JsonProperty("previous_block_root")
    public Bytes32 previousBlockRoot() {
      return previousBlockRoot;
    }

    @JsonProperty("state_root")
    public Bytes32 stateRoot() {
      return stateRoot;
    }

    @JsonProperty("block_body_root")
    public Bytes32 blockBodyRoot() {
      return blockBodyRoot;
    }

    @JsonProperty("signature")
    public Bytes signature() {
      return signature;
    }
  }

  private final List<BlockHeader> headers;

  BlockHeaders(List<BlockHeader> headers) {
    this.headers = headers;
  }

  @JsonValue
  public List<BlockHeader> headers() {
    return headers;
  }
}
