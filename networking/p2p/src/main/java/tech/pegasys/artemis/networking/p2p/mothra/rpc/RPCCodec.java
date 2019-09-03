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

package tech.pegasys.artemis.networking.p2p.mothra.rpc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.undercouch.bson4jackson.BsonFactory;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.artemis.util.json.BytesModule;

public final class RPCCodec {

  static final ObjectMapper mapper =
      new ObjectMapper(new BsonFactory()).registerModule(new BytesModule());

  private RPCCodec() {}

  /**
   * Gets the json object mapper
   *
   * @return
   */
  public static ObjectMapper getMapper() {
    return mapper;
  }

  /**
   * Encodes a message into a RPC request
   *
   * @param request the payload of the request
   * @return the encoded RPC message
   */
  public static Bytes encode(Object request) {
    ObjectNode bodyNode = mapper.createObjectNode();
    bodyNode.putPOJO("body", request);
    try {
      return Bytes.wrap(mapper.writer().writeValueAsBytes(bodyNode));
    } catch (IOException e) {
      throw new IllegalArgumentException(e.getMessage());
    }
  }

  /**
   * Decodes a RPC message into a payload.
   *
   * @param message the bytes of the message to read
   * @return the payload, decoded
   */
  public static RPCMessage decode(Bytes message) {
    try {
      byte[] body = message.toArrayUnsafe();
      ObjectNode bodyNode = (ObjectNode) mapper.readTree(body);
      return new RPCMessage(bodyNode.get("body"));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
