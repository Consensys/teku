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

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Splitter;
import de.undercouch.bson4jackson.BsonFactory;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import net.consensys.cava.bytes.Bytes;
import net.consensys.cava.bytes.Bytes32;
import net.consensys.cava.plumtree.MessageSender;
import org.xerial.snappy.Snappy;

public final class GossipCodec implements Codec {

  private static class Bytes32Serializer extends JsonSerializer<Bytes32> {

    @Override
    public void serialize(Bytes32 bytes, JsonGenerator jGen, SerializerProvider serializerProvider)
        throws IOException {
      jGen.writeString(bytes.toHexString());
    }
  }

  private static class Bytes32Deserializer extends JsonDeserializer<Bytes32> {

    @Override
    public Bytes32 deserialize(JsonParser p, DeserializationContext ctxt)
        throws IOException, JsonProcessingException {
      return Bytes32.fromHexString(p.getValueAsString());
    }
  }

  private static class BytesSerializer extends JsonSerializer<Bytes> {

    @Override
    public void serialize(Bytes bytes, JsonGenerator jGen, SerializerProvider serializerProvider)
        throws IOException {
      jGen.writeString(bytes.toHexString());
    }
  }

  private static class BytesDeserializer extends JsonDeserializer<Bytes> {

    @Override
    public Bytes deserialize(JsonParser p, DeserializationContext ctxt)
        throws IOException, JsonProcessingException {
      return Bytes.fromHexString(p.getValueAsString());
    }
  }

  static final class BytesModule extends SimpleModule {

    BytesModule() {
      super("bytes");
      addSerializer(Bytes.class, new BytesSerializer());
      addDeserializer(Bytes.class, new BytesDeserializer());
      addSerializer(Bytes32.class, new Bytes32Serializer());
      addDeserializer(Bytes32.class, new Bytes32Deserializer());
    }
  }

  static final ObjectMapper mapper =
      new ObjectMapper(new BsonFactory()).registerModule(new BytesModule());

  private GossipCodec() {}

  /**
   * Encodes a payload into a Gossip request
   *
   * @param verb the Gossip method
   * @param payload the payload of the request
   * @return the encoded Gossip message
   */
  public static Bytes encode(
      MessageSender.Verb verb, Bytes messageHash, Bytes32 hashSignature, Bytes payload) {

    String requestLine = "EWP 0.2 GOSSIP ";
    ObjectNode node = mapper.createObjectNode();

    node.put("method_id", verb.name());
    node.put("message_hash", messageHash.toHexString());
    node.put("hash_signature", hashSignature.toHexString());
    try {
      Bytes headers = Bytes.wrap(Snappy.compress(mapper.writer().writeValueAsBytes(node)));
      Bytes compressedPayload = Bytes.wrap(Snappy.compress(payload.toArrayUnsafe()));

      requestLine += headers.size();
      requestLine += " ";
      requestLine += compressedPayload.size();
      requestLine += "\n";
      return Bytes.concatenate(
          Bytes.wrap(requestLine.getBytes(StandardCharsets.UTF_8)), headers, compressedPayload);
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
        requestLineBytes = message.slice(0, i);
        break;
      }
    }
    if (requestLineBytes == null) {
      return null;
    }
    String requestLine = new String(requestLineBytes.toArrayUnsafe(), StandardCharsets.UTF_8);
    Iterator<String> segments = Splitter.on(" ").split(requestLine).iterator();
    String protocol = segments.next();
    String version = segments.next();
    String command = segments.next();
    int headerLength = Integer.parseInt(segments.next());
    int bodyLength = Integer.parseInt(segments.next());

    if (message.size() < bodyLength + headerLength + requestLineBytes.size()) {
      return null;
    }

    try {
      byte[] headers = message.slice(requestLineBytes.size() + 1, headerLength).toArrayUnsafe();
      headers = Snappy.uncompress(headers);
      byte[] payload =
          message.slice(requestLineBytes.size() + 1 + headerLength, bodyLength).toArrayUnsafe();
      payload = Snappy.uncompress(payload);
      ObjectNode gossipmessage = (ObjectNode) mapper.readTree(headers);
      int methodId = gossipmessage.get("method_id").intValue();
      Bytes32 messageHash = Bytes32.fromHexString(gossipmessage.get("message_hash").asText());
      Bytes32 hashSignature = Bytes32.fromHexString(gossipmessage.get("hash_signature").asText());
      return new GossipMessage(
          GossipMethod.valueOf(methodId),
          messageHash,
          hashSignature,
          Bytes.wrap(payload),
          (bodyLength + requestLineBytes.size() + headerLength));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
