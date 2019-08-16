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

package tech.pegasys.artemis.networking.p2p.mothra.gossip;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.undercouch.bson4jackson.BsonFactory;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.hobbits.Message;
import org.apache.tuweni.hobbits.Protocol;
import tech.pegasys.artemis.util.alogger.ALogger;
import tech.pegasys.artemis.util.json.BytesModule;

public final class GossipCodec {

  private static final ALogger LOG = new ALogger(GossipCodec.class.getName());

  static final ObjectMapper mapper =
      new ObjectMapper(new BsonFactory()).registerModule(new BytesModule());

  private GossipCodec() {}

  /**
   * Encodes a payload into a Gossip request
   *
   * @param method the Gossip method
   * @param topic
   * @param timestamp
   * @param messageHash
   * @param body the payload of the request
   * @return the encoded Gossip message
   */
  public static Message encode(
      int method, String topic, BigInteger timestamp, byte[] messageHash, byte[] body) {

    ObjectNode node = mapper.createObjectNode();

    node.put("method_id", method);
    node.put("topic", topic);
    node.put("timestamp", timestamp);
    node.put("message_hash", messageHash);
    try {
      byte[] header = mapper.writer().writeValueAsBytes(node);
      Message message = new Message(3, Protocol.GOSSIP, Bytes.wrap(header), Bytes.wrap(body));
      return message;
    } catch (IOException e) {
      throw new IllegalArgumentException(e);
    }
  }

  /**
   * Decodes a Gossip message into a payload.
   *
   * @param message the bytes of the message to read
   * @return the payload, decoded
   */
  public static GossipMessage decode(Message message) {
    try {
      byte[] headers = message.getHeaders().toArrayUnsafe();
      byte[] body = message.getBody().toArrayUnsafe();
      ObjectNode gossipmessage = (ObjectNode) mapper.readTree(headers);
      int methodId = gossipmessage.get("method_id").intValue();
      String topic = gossipmessage.get("topic").asText();
      BigInteger timestamp = gossipmessage.get("timestamp").bigIntegerValue();
      byte[] messageHash = gossipmessage.get("message_hash").binaryValue();
      return new GossipMessage(methodId, topic, timestamp, messageHash, body, message.size());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
