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

package tech.pegasys.artemis.networking.p2p.hobbits.gossip;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Splitter;
import de.undercouch.bson4jackson.BsonFactory;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import org.apache.logging.log4j.Level;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.networking.p2p.hobbits.Codec;
import tech.pegasys.artemis.util.alogger.ALogger;
import tech.pegasys.artemis.util.json.BytesModule;

public final class GossipCodec implements Codec {

  private static final ALogger LOG = new ALogger(GossipCodec.class.getName());

  static final ObjectMapper mapper =
      new ObjectMapper(new BsonFactory()).registerModule(new BytesModule());

  private GossipCodec() {}

  /**
   * Encodes a payload into a Gossip request
   *
   * @param verb the Gossip method
   * @param topic
   * @param timestamp
   * @param messageHash
   * @param hashSignature
   * @param payload the payload of the request
   * @return the encoded Gossip message
   */
  public static Bytes encode(
      int verb,
      String topic,
      long timestamp,
      Bytes messageHash,
      Bytes32 hashSignature,
      Bytes payload) {

    String requestLine = "EWP " + VERSION + " GOSSIP ";
    ObjectNode node = mapper.createObjectNode();

    node.put("method_id", verb);
    node.put("topic", topic);
    node.put("timestamp", timestamp);
    node.put("message_hash", messageHash.toHexString());
    node.put("hash_signature", hashSignature.toHexString());
    try {
      Bytes header = Bytes.wrap(mapper.writer().writeValueAsBytes(node));
      requestLine += header.size();
      requestLine += " ";
      requestLine += payload.size();
      requestLine += "\n";
      return Bytes.concatenate(
          Bytes.wrap(requestLine.getBytes(StandardCharsets.UTF_8)), header, payload);
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
  public static GossipMessage decode(Bytes message) {
    Bytes requestLineBytes = null;
    for (int i = 0; i < message.size(); i++) {
      if (message.get(i) == (byte) '\n') {
        requestLineBytes = message.slice(0, i + 1);
        break;
      }
    }
    if (requestLineBytes == null) {
      LOG.log(Level.DEBUG, "Gossip message without request line");
      return null;
    }
    String requestLine = new String(requestLineBytes.toArrayUnsafe(), StandardCharsets.UTF_8);
    Iterator<String> segments = Splitter.on(" ").split(requestLine).iterator();
    String preamble = segments.next();
    String version = segments.next();
    String protocol = segments.next();
    int headerLength = Integer.parseInt(segments.next());
    int bodyLength = Integer.parseInt(segments.next().trim());

    LOG.log(Level.DEBUG, "Gossip message " + requestLine + " " + message.size());
    if (message.size() < bodyLength + headerLength + requestLineBytes.size()) {
      LOG.log(Level.DEBUG, "Message too short");
      return null;
    } else {
      LOG.log(Level.DEBUG, "Message ok");
    }

    try {
      byte[] headers = message.slice(requestLineBytes.size(), headerLength).toArrayUnsafe();
      byte[] payload =
          message.slice(requestLineBytes.size() + headerLength, bodyLength).toArrayUnsafe();
      ObjectNode gossipmessage = (ObjectNode) mapper.readTree(headers);
      int methodId = gossipmessage.get("method_id").intValue();
      String topic = gossipmessage.get("topic").asText();
      long timestamp = gossipmessage.get("timestamp").asLong();
      Bytes32 messageHash = Bytes32.fromHexString(gossipmessage.get("message_hash").asText());
      Bytes32 hashSignature = Bytes32.fromHexString(gossipmessage.get("hash_signature").asText());
      return new GossipMessage(
          methodId,
          topic,
          timestamp,
          messageHash,
          hashSignature,
          Bytes.wrap(payload),
          (bodyLength + requestLineBytes.size() + headerLength));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
