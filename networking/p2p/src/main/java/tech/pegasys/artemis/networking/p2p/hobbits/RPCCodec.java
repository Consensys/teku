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
import de.undercouch.bson4jackson.BsonFactory;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.Nullable;
import net.consensys.cava.bytes.Bytes;
import net.consensys.cava.bytes.Bytes32;
import net.consensys.cava.units.bigints.UInt64;
import org.xerial.snappy.Snappy;

public final class RPCCodec {

  private static class UInt64Serializer extends JsonSerializer<UInt64> {

    @Override
    public void serialize(UInt64 bytes, JsonGenerator jGen, SerializerProvider serializerProvider)
        throws IOException {
      jGen.writeString(bytes.toHexString());
    }
  }

  private static class UInt64Deserializer extends JsonDeserializer<UInt64> {

    @Override
    public UInt64 deserialize(JsonParser p, DeserializationContext ctxt)
        throws IOException, JsonProcessingException {
      return UInt64.fromHexString(p.getValueAsString());
    }
  }

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

  private static final class BytesModule extends SimpleModule {

    BytesModule() {
      super("bytes");
      addSerializer(Bytes.class, new BytesSerializer());
      addDeserializer(Bytes.class, new BytesDeserializer());
      addSerializer(Bytes32.class, new Bytes32Serializer());
      addDeserializer(Bytes32.class, new Bytes32Deserializer());
      addSerializer(UInt64.class, new UInt64Serializer());
      addDeserializer(UInt64.class, new UInt64Deserializer());
    }
  }

  static final ObjectMapper mapper =
      new ObjectMapper(new BsonFactory()).registerModule(new BytesModule());

  private static final AtomicLong counter = new AtomicLong(1);

  private static long nextRequestNumber() {
    long requestNumber = counter.getAndIncrement();
    if (requestNumber < 1) {
      counter.set(1);
      return 1;
    }
    return requestNumber;
  }

  private RPCCodec() {}

  /**
   * Creates an empty goodbye message.
   *
   * @return the encoded bytes of a goodbye message.
   */
  public static Bytes createGoodbye() {
    return encode(RPCMethod.GOODBYE, Collections.emptyMap(), null);
  }

  /**
   * Encodes a message into a RPC request
   *
   * @param methodId the RPC method
   * @param request the payload of the request
   * @param pendingResponses the set of pending responses code to update
   * @return the encoded RPC message
   */
  public static Bytes encode(
      RPCMethod methodId, Object request, @Nullable Set<Long> pendingResponses) {
    long requestNumber = nextRequestNumber();
    if (pendingResponses != null) {
      pendingResponses.add(requestNumber);
    }
    return encode(methodId, request, requestNumber);
  }

  /**
   * Encodes a message into a RPC request
   *
   * @param methodId the RPC method
   * @param request the payload of the request
   * @param requestNumber a request number
   * @return the encoded RPC message
   */
  public static Bytes encode(RPCMethod methodId, Object request, long requestNumber) {
    ObjectNode node = mapper.createObjectNode();

    node.put("id", requestNumber);
    node.put("method_id", methodId.code());
    node.putPOJO("body", request);
    try {
      Bytes body = Bytes.wrap(Snappy.compress(mapper.writer().writeValueAsBytes(node)));
      return Bytes.concatenate(
          Bytes.wrap(new byte[] {(byte) 1, (byte) 2}), Bytes.ofUnsignedLong(body.size()), body);
    } catch (IOException e) {
      throw new IllegalArgumentException(e);
    }
  }

  /**
   * Decodes a RPC message into a payload.
   *
   * @param message the bytes of the message to read
   * @return the payload, decoded
   */
  public static RPCMessage decode(Bytes message) {
    boolean applySnappyCompression = message.get(0) == (byte) 1;
    if (message.get(1) != (byte) 2) {
      return null;
    }
    long bodySize = message.getLong(2);
    if (message.size() < bodySize + 10) {
      return null;
    }
    // TODO add max body size checks.
    try {
      byte[] payload = message.slice(10, (int) bodySize).toArrayUnsafe();
      if (applySnappyCompression) {
        payload = Snappy.uncompress(payload);
      }
      ObjectNode rpcmessage = (ObjectNode) mapper.readTree(payload);
      long id = rpcmessage.get("id").longValue();
      int methodId = rpcmessage.get("method_id").intValue();
      return new RPCMessage(
          id, RPCMethod.valueOf(methodId), rpcmessage.get("body"), (int) (bodySize + 10));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
