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

package tech.pegasys.artemis.util.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import java.io.IOException;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt64;
import tech.pegasys.artemis.bls.BLSPublicKey;
import tech.pegasys.artemis.bls.BLSSignature;

public class BytesModule extends SimpleModule {

  public BytesModule() {
    super("bytes");
    addSerializer(Bytes.class, new BytesSerializer());
    addDeserializer(Bytes.class, new BytesDeserializer());
    addSerializer(Bytes32.class, new Bytes32Serializer());
    addDeserializer(Bytes32.class, new Bytes32Deserializer());
    addSerializer(UInt64.class, new UInt64Serializer());
    addDeserializer(UInt64.class, new UInt64Deserializer());
    addSerializer(BLSSignature.class, new BLSSerializer());
    addSerializer(BLSPublicKey.class, new BLSPublicKeySerializer());
  }

  private static class UInt64Serializer extends JsonSerializer<UInt64> {

    @Override
    public void serialize(UInt64 bytes, JsonGenerator jGen, SerializerProvider serializerProvider)
        throws IOException {
      jGen.writeString(bytes.toHexString());
    }
  }

  private static class UInt64Deserializer extends JsonDeserializer<UInt64> {

    @Override
    public UInt64 deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
      return UInt64.fromHexString(p.getValueAsString());
    }
  }

  private static class BLSSerializer extends JsonSerializer<BLSSignature> {

    @Override
    public void serialize(
        BLSSignature signature, JsonGenerator jGen, SerializerProvider serializerProvider)
        throws IOException {
      jGen.writeString(signature.toString());
    }
  }

  private static class BLSPublicKeySerializer extends JsonSerializer<BLSPublicKey> {

    @Override
    public void serialize(
        BLSPublicKey publicKey, JsonGenerator jGen, SerializerProvider serializerProvider)
        throws IOException {
      jGen.writeString(publicKey.toString());
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
    public Bytes32 deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
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
    public Bytes deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
      return Bytes.fromHexString(p.getValueAsString());
    }
  }
}
