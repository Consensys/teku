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

package tech.pegasys.artemis.provider;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.google.common.primitives.UnsignedLong;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.api.schema.BLSPubKey;
import tech.pegasys.artemis.api.schema.BLSSignature;
import tech.pegasys.artemis.util.SSZTypes.Bitlist;
import tech.pegasys.artemis.util.SSZTypes.Bitvector;
import tech.pegasys.artemis.util.SSZTypes.Bytes4;

public class JsonProvider {
  private void addTekuMappers() {
    SimpleModule module = new SimpleModule("TekuJson", new Version(1, 0, 0, null, null, null));

    module.addSerializer(Bitlist.class, new BitlistSerializer());
    module.addDeserializer(Bitlist.class, new BitlistDeserializer());
    module.addDeserializer(Bitvector.class, new BitvectorDeserializer());
    module.addSerializer(Bitvector.class, new BitvectorSerializer());

    module.addSerializer(BLSPubKey.class, new BLSPubKeySerializer());
    module.addDeserializer(BLSPubKey.class, new BLSPubKeyDeserializer());
    module.addDeserializer(BLSSignature.class, new BLSSignatureDeserializer());
    module.addSerializer(BLSSignature.class, new BLSSignatureSerializer());

    module.addDeserializer(Bytes32.class, new Bytes32Deserializer());
    module.addDeserializer(Bytes4.class, new Bytes4Deserializer());
    module.addSerializer(Bytes4.class, new Bytes4Serializer());
    module.addDeserializer(Bytes.class, new BytesDeserializer());
    module.addSerializer(Bytes.class, new BytesSerializer());

    module.addDeserializer(UnsignedLong.class, new UnsignedLongDeserializer());
    module.addSerializer(UnsignedLong.class, new UnsignedLongSerializer());

    objectMapper.registerModule(module).writer(new DefaultPrettyPrinter());
  }

  private final ObjectMapper objectMapper;

  public JsonProvider() {
    objectMapper = new ObjectMapper();
    addTekuMappers();
  }

  public <T> String objectToJSON(T object) throws JsonProcessingException {
    return objectMapper.writeValueAsString(object);
  }

  public <T> T jsonToObject(String json, Class<T> clazz) throws JsonProcessingException {
    return objectMapper.readValue(json, clazz);
  }

  public ObjectMapper getObjectMapper() {
    return objectMapper;
  }
}
