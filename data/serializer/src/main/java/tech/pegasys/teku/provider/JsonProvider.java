/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.provider;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.Bytes48;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.api.response.v1.validator.GetNewBlindedBlockResponse;
import tech.pegasys.teku.api.response.v1.validator.GetNewBlockResponse;
import tech.pegasys.teku.api.response.v2.debug.GetStateResponseV2;
import tech.pegasys.teku.api.response.v2.validator.GetNewBlockResponseV2;
import tech.pegasys.teku.api.schema.BLSPubKey;
import tech.pegasys.teku.api.schema.BLSSignature;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.ethereum.jackson.Eth1AddressDeserializer;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.jackson.deserializers.bytes.ByteArrayDeserializer;
import tech.pegasys.teku.infrastructure.jackson.deserializers.bytes.ByteArraySerializer;
import tech.pegasys.teku.infrastructure.jackson.deserializers.bytes.Bytes20Deserializer;
import tech.pegasys.teku.infrastructure.jackson.deserializers.bytes.Bytes20Serializer;
import tech.pegasys.teku.infrastructure.jackson.deserializers.bytes.Bytes32Deserializer;
import tech.pegasys.teku.infrastructure.jackson.deserializers.bytes.Bytes48KeyDeserializer;
import tech.pegasys.teku.infrastructure.jackson.deserializers.bytes.Bytes4Deserializer;
import tech.pegasys.teku.infrastructure.jackson.deserializers.bytes.Bytes4Serializer;
import tech.pegasys.teku.infrastructure.jackson.deserializers.bytes.BytesDeserializer;
import tech.pegasys.teku.infrastructure.jackson.deserializers.bytes.BytesSerializer;
import tech.pegasys.teku.infrastructure.jackson.deserializers.bytes.DoubleDeserializer;
import tech.pegasys.teku.infrastructure.jackson.deserializers.bytes.DoubleSerializer;
import tech.pegasys.teku.infrastructure.jackson.deserializers.uints.UInt256Deserializer;
import tech.pegasys.teku.infrastructure.jackson.deserializers.uints.UInt256Serializer;
import tech.pegasys.teku.infrastructure.jackson.deserializers.uints.UInt64Deserializer;
import tech.pegasys.teku.infrastructure.jackson.deserializers.uints.UInt64Serializer;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class JsonProvider {
  private void addTekuMappers() {
    SimpleModule module = new SimpleModule("TekuJson", new Version(1, 0, 0, null, null, null));

    module.addSerializer(SszBitvector.class, new SszBitvectorSerializer());

    module.addSerializer(BLSPubKey.class, new BLSPubKeySerializer());
    module.addDeserializer(BLSPubKey.class, new BLSPubKeyDeserializer());
    module.addDeserializer(BLSPublicKey.class, new BLSPublicKeyDeserializer());
    module.addSerializer(BLSPublicKey.class, new BLSPublicKeySerializer());
    module.addDeserializer(BLSSignature.class, new BLSSignatureDeserializer());
    module.addSerializer(BLSSignature.class, new BLSSignatureSerializer());

    module.addKeyDeserializer(Bytes48.class, new Bytes48KeyDeserializer());

    module.addDeserializer(Bytes32.class, new Bytes32Deserializer());
    module.addDeserializer(Bytes4.class, new Bytes4Deserializer());
    module.addSerializer(Bytes4.class, new Bytes4Serializer());
    module.addDeserializer(Bytes20.class, new Bytes20Deserializer());
    module.addDeserializer(Eth1Address.class, new Eth1AddressDeserializer());

    module.addSerializer(Bytes20.class, new Bytes20Serializer());
    module.addDeserializer(Bytes.class, new BytesDeserializer());
    module.addSerializer(Bytes.class, new BytesSerializer());
    module.addDeserializer(Double.class, new DoubleDeserializer());
    module.addSerializer(Double.class, new DoubleSerializer());

    module.addDeserializer(UInt64.class, new UInt64Deserializer());
    module.addSerializer(UInt64.class, new UInt64Serializer());

    module.addDeserializer(UInt256.class, new UInt256Deserializer());
    module.addSerializer(UInt256.class, new UInt256Serializer());

    module.addSerializer(byte[].class, new ByteArraySerializer());
    module.addDeserializer(byte[].class, new ByteArrayDeserializer());

    module.addDeserializer(
        GetNewBlockResponse.class, new GetNewBlockResponseV1Deserializer(objectMapper));

    module.addDeserializer(
        GetNewBlockResponseV2.class, new GetNewBlockResponseV2Deserializer(objectMapper));
    module.addDeserializer(
        GetStateResponseV2.class, new GetStateResponseV2Deserializer(objectMapper));
    module.addDeserializer(
        GetNewBlindedBlockResponse.class, new GetNewBlindedBlockResponseDeserializer(objectMapper));

    objectMapper.registerModule(module);
  }

  private final ObjectMapper objectMapper;

  public JsonProvider() {
    objectMapper = new ObjectMapper();
    objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    addTekuMappers();
  }

  public <T> String objectToJSON(T object) throws JsonProcessingException {
    return objectMapper.writeValueAsString(object);
  }

  public <T> String objectToPrettyJSON(T object) throws JsonProcessingException {
    return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(object);
  }

  public <T> T jsonToObject(String json, Class<T> clazz) throws JsonProcessingException {
    return objectMapper.readValue(json, clazz);
  }

  public ObjectMapper getObjectMapper() {
    return objectMapper;
  }
}
