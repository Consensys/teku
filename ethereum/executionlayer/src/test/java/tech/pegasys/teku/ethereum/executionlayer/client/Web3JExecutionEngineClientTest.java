/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.ethereum.executionlayer.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.ValueInstantiationException;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.ethereum.executionlayer.client.schema.ExecutePayloadResult;
import tech.pegasys.teku.ethereum.executionlayer.client.schema.ExecutionPayloadV1;
import tech.pegasys.teku.ethereum.executionlayer.client.schema.ForkChoiceStateV1;
import tech.pegasys.teku.ethereum.executionlayer.client.schema.ForkChoiceUpdatedResult;
import tech.pegasys.teku.ethereum.executionlayer.client.schema.PayloadAttributesV1;
import tech.pegasys.teku.ethereum.executionlayer.client.serialization.Bytes20Deserializer;
import tech.pegasys.teku.ethereum.executionlayer.client.serialization.Bytes20Serializer;
import tech.pegasys.teku.ethereum.executionlayer.client.serialization.Bytes32Deserializer;
import tech.pegasys.teku.ethereum.executionlayer.client.serialization.Bytes8Deserializer;
import tech.pegasys.teku.ethereum.executionlayer.client.serialization.BytesSerializer;
import tech.pegasys.teku.ethereum.executionlayer.client.serialization.UInt256AsHexDeserializer;
import tech.pegasys.teku.ethereum.executionlayer.client.serialization.UInt256AsHexSerializer;
import tech.pegasys.teku.ethereum.executionlayer.client.serialization.UInt64AsHexDeserializer;
import tech.pegasys.teku.ethereum.executionlayer.client.serialization.UInt64AsHexSerializer;
import tech.pegasys.teku.infrastructure.ssz.type.Bytes20;
import tech.pegasys.teku.infrastructure.ssz.type.Bytes8;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.executionengine.ExecutionPayloadStatus;
import tech.pegasys.teku.spec.executionengine.ForkChoiceUpdatedStatus;
import tech.pegasys.teku.spec.util.DataStructureUtil;

@TestSpecContext(milestone = SpecMilestone.MERGE)
public class Web3JExecutionEngineClientTest {
  Writer jsonWriter;
  JsonGenerator jsonGenerator;
  ObjectMapper objectMapper;
  SerializerProvider serializerProvider;
  DataStructureUtil dataStructureUtil;

  @BeforeEach
  @TestTemplate
  void setUp(SpecContext specContext) throws IOException {
    jsonWriter = new StringWriter();
    jsonGenerator = new JsonFactory().createGenerator(jsonWriter);
    objectMapper = new ObjectMapper();
    serializerProvider = objectMapper.getSerializerProvider();
    dataStructureUtil = specContext.getDataStructureUtil();
  }

  @TestTemplate
  void shouldDeserializeBytes8() throws IOException {
    Bytes8 originalBytes8 = dataStructureUtil.randomBytes8();

    JsonParser parser = prepareDeserializationContext("\"" + originalBytes8.toHexString() + "\"");
    Bytes8Deserializer deserializer = new Bytes8Deserializer();
    Bytes8 result = deserializer.deserialize(parser, objectMapper.getDeserializationContext());

    assertThat(originalBytes8).isEqualTo(result);
  }

  @TestTemplate
  void shouldSerializeDeserializeBytes20() throws IOException {
    Bytes20 originalBytes20 = dataStructureUtil.randomBytes20();

    new Bytes20Serializer().serialize(originalBytes20, jsonGenerator, serializerProvider);
    jsonGenerator.flush();

    JsonParser parser = prepareDeserializationContext(jsonWriter.toString());
    Bytes20Deserializer deserializer = new Bytes20Deserializer();
    Bytes20 result = deserializer.deserialize(parser, objectMapper.getDeserializationContext());

    assertThat(originalBytes20).isEqualTo(result);
  }

  @TestTemplate
  void shouldSerializeDeserializeBytes32() throws IOException {
    Bytes32 originalBytes32 = dataStructureUtil.randomBytes32();

    new BytesSerializer().serialize(originalBytes32, jsonGenerator, serializerProvider);
    jsonGenerator.flush();

    JsonParser parser = prepareDeserializationContext(jsonWriter.toString());
    Bytes32Deserializer deserializer = new Bytes32Deserializer();
    Bytes32 result = deserializer.deserialize(parser, objectMapper.getDeserializationContext());

    assertThat(originalBytes32).isEqualTo(result);
  }

  @TestTemplate
  void shouldSerializeDeserializeUInt64() throws IOException {
    UInt64 originalUInt64 = dataStructureUtil.randomUInt64();

    new UInt64AsHexSerializer().serialize(originalUInt64, jsonGenerator, serializerProvider);
    jsonGenerator.flush();

    JsonParser parser = prepareDeserializationContext(jsonWriter.toString());
    UInt64AsHexDeserializer deserializer = new UInt64AsHexDeserializer();
    UInt64 result = deserializer.deserialize(parser, objectMapper.getDeserializationContext());

    assertThat(originalUInt64).isEqualTo(result);
  }

  @TestTemplate
  void shouldSerializeDeserializeUInt256() throws IOException {
    UInt256 originalUInt256 = dataStructureUtil.randomUInt256();

    new UInt256AsHexSerializer().serialize(originalUInt256, jsonGenerator, serializerProvider);
    jsonGenerator.flush();

    JsonParser parser = prepareDeserializationContext(jsonWriter.toString());
    UInt256AsHexDeserializer deserializer = new UInt256AsHexDeserializer();
    UInt256 result = deserializer.deserialize(parser, objectMapper.getDeserializationContext());

    assertThat(originalUInt256).isEqualTo(result);
  }

  private JsonParser prepareDeserializationContext(String serializedValue) throws IOException {
    String json = String.format("{\"value\":%s}", serializedValue);
    JsonParser parser = objectMapper.getFactory().createParser(json);
    parser.nextToken();
    parser.nextToken();
    parser.nextToken();
    return parser;
  }

  @TestTemplate
  void shouldSerializeDeserializeForkChoiceStateV1() throws IOException {
    ForkChoiceStateV1 forkChoiceStateV1Orig =
        new ForkChoiceStateV1(
            dataStructureUtil.randomBytes32(),
            dataStructureUtil.randomBytes32(),
            dataStructureUtil.randomBytes32());

    String forkChoiceStateV1OrigSerialized = objectMapper.writeValueAsString(forkChoiceStateV1Orig);
    ForkChoiceStateV1 forkChoiceStateV1New =
        objectMapper.readValue(forkChoiceStateV1OrigSerialized, ForkChoiceStateV1.class);

    assertThat(forkChoiceStateV1Orig).isEqualTo(forkChoiceStateV1New);
  }

  @TestTemplate
  void shouldSerializeDeserializePayloadAttributesV1() throws IOException {
    PayloadAttributesV1 payloadAttributesV1Orig =
        new PayloadAttributesV1(
            dataStructureUtil.randomUInt64(),
            dataStructureUtil.randomBytes32(),
            dataStructureUtil.randomBytes20());

    String payloadAttributesV1OrigSerialized =
        objectMapper.writeValueAsString(payloadAttributesV1Orig);
    PayloadAttributesV1 payloadAttributesV1New =
        objectMapper.readValue(payloadAttributesV1OrigSerialized, PayloadAttributesV1.class);

    assertThat(payloadAttributesV1Orig).isEqualTo(payloadAttributesV1New);
  }

  @TestTemplate
  void shouldSerializeDeserializeExecutionPayloadV1() throws IOException {
    ExecutionPayload internalExecutionPayload = dataStructureUtil.randomExecutionPayload();
    ExecutionPayloadV1 executionPayloadV1Orig =
        ExecutionPayloadV1.fromInternalExecutionPayload(internalExecutionPayload);

    String executionPayloadV1OrigSerialized =
        objectMapper.writeValueAsString(executionPayloadV1Orig);
    ExecutionPayloadV1 executionPayloadV1New =
        objectMapper.readValue(executionPayloadV1OrigSerialized, ExecutionPayloadV1.class);

    assertThat(executionPayloadV1Orig).isEqualTo(executionPayloadV1New);
    assertThat(
            executionPayloadV1Orig.asInternalExecutionPayload(internalExecutionPayload.getSchema()))
        .isEqualTo(internalExecutionPayload);
  }

  @TestTemplate
  void shouldDeserializeExecutionPayloadResultWithNulls() throws IOException {
    ExecutePayloadResult executePayloadResultExpected =
        new ExecutePayloadResult(ExecutionPayloadStatus.VALID, null, null);
    tech.pegasys.teku.spec.executionengine.ExecutePayloadResult
        internalExecutePayloadResultExpected =
            tech.pegasys.teku.spec.executionengine.ExecutePayloadResult.VALID;
    String json = "{\"status\": \"VALID\", \"latestValidHash\": null, \"validationError\": null }";
    ExecutePayloadResult executePayloadResultDeserialized =
        objectMapper.readValue(json, ExecutePayloadResult.class);

    tech.pegasys.teku.spec.executionengine.ExecutePayloadResult internalExecutePayloadResult =
        executePayloadResultDeserialized.asInternalExecutionPayload();

    assertThat(executePayloadResultDeserialized).isEqualTo(executePayloadResultExpected);
    assertThat(internalExecutePayloadResult).isEqualTo(internalExecutePayloadResultExpected);
  }

  @TestTemplate
  void shouldDeserializeExecutionPayloadResult() throws IOException {
    Bytes32 lastValidHash = dataStructureUtil.randomBytes32();
    ExecutePayloadResult executePayloadResultExpected =
        new ExecutePayloadResult(ExecutionPayloadStatus.INVALID, lastValidHash, "test");
    tech.pegasys.teku.spec.executionengine.ExecutePayloadResult
        internalExecutePayloadResultExpected =
            tech.pegasys.teku.spec.executionengine.ExecutePayloadResult.invalid(
                Optional.of(lastValidHash), Optional.of("test"));

    String json =
        "{\"status\": \"INVALID\", \"latestValidHash\": \""
            + lastValidHash.toHexString()
            + "\", \"validationError\": \"test\" }";
    ExecutePayloadResult executePayloadResultDeserialized =
        objectMapper.readValue(json, ExecutePayloadResult.class);

    tech.pegasys.teku.spec.executionengine.ExecutePayloadResult internalExecutePayloadResult =
        executePayloadResultDeserialized.asInternalExecutionPayload();

    assertThat(executePayloadResultDeserialized).isEqualTo(executePayloadResultExpected);
    assertThat(internalExecutePayloadResult).isEqualTo(internalExecutePayloadResultExpected);
  }

  @TestTemplate
  void shouldThrowDeserializingInvalidExecutionPayloadResult() {
    assertThrows(
        InvalidFormatException.class,
        () -> {
          String json =
              "{\"status\": \"wrong\", \"latestValidHash\": null, \"validationError\": null }";
          objectMapper.readValue(json, ExecutePayloadResult.class);
        });

    assertThrows(
        ValueInstantiationException.class,
        () -> {
          String json = "{\"status\": null, \"latestValidHash\": null, \"validationError\": null }";
          objectMapper.readValue(json, ExecutePayloadResult.class);
        });

    assertThrows(
        JsonMappingException.class,
        () -> {
          String json =
              "{\"status\": null, \"latestValidHash\": \"wrong\", \"validationError\": null }";
          objectMapper.readValue(json, ExecutePayloadResult.class);
        });
  }

  @TestTemplate
  void shouldDeserializeForkChoiceUpdatedResultWithNulls() throws IOException {
    ForkChoiceUpdatedResult forkChoiceUpdatedResultExpected =
        new ForkChoiceUpdatedResult(ForkChoiceUpdatedStatus.SYNCING, null);
    tech.pegasys.teku.spec.executionengine.ForkChoiceUpdatedResult
        internalForkChoiceUpdatedResultExpected =
            new tech.pegasys.teku.spec.executionengine.ForkChoiceUpdatedResult(
                ForkChoiceUpdatedStatus.SYNCING, Optional.empty());
    String json = "{\"status\": \"SYNCING\", \"payloadId\": null }";
    ForkChoiceUpdatedResult executeForkChoiceUpdatedResultDeserialized =
        objectMapper.readValue(json, ForkChoiceUpdatedResult.class);

    tech.pegasys.teku.spec.executionengine.ForkChoiceUpdatedResult internalForkChoiceUpdatedResult =
        executeForkChoiceUpdatedResultDeserialized.asInternalExecutionPayload();

    assertThat(executeForkChoiceUpdatedResultDeserialized)
        .isEqualTo(forkChoiceUpdatedResultExpected);
    assertThat(internalForkChoiceUpdatedResult).isEqualTo(internalForkChoiceUpdatedResultExpected);
  }

  @TestTemplate
  void shouldDeserializeForkChoiceUpdatedResult() throws IOException {
    Bytes8 payloadId = dataStructureUtil.randomBytes8();
    ForkChoiceUpdatedResult forkChoiceUpdatedResultExpected =
        new ForkChoiceUpdatedResult(ForkChoiceUpdatedStatus.SUCCESS, payloadId);
    tech.pegasys.teku.spec.executionengine.ForkChoiceUpdatedResult
        internalForkChoiceUpdatedResultExpected =
            new tech.pegasys.teku.spec.executionengine.ForkChoiceUpdatedResult(
                ForkChoiceUpdatedStatus.SUCCESS, Optional.of(payloadId));
    String json = "{\"status\": \"SUCCESS\", \"payloadId\": \"" + payloadId.toHexString() + "\" }";
    ForkChoiceUpdatedResult executeForkChoiceUpdatedResultDeserialized =
        objectMapper.readValue(json, ForkChoiceUpdatedResult.class);

    tech.pegasys.teku.spec.executionengine.ForkChoiceUpdatedResult internalForkChoiceUpdatedResult =
        executeForkChoiceUpdatedResultDeserialized.asInternalExecutionPayload();

    assertThat(executeForkChoiceUpdatedResultDeserialized)
        .isEqualTo(forkChoiceUpdatedResultExpected);
    assertThat(internalForkChoiceUpdatedResult).isEqualTo(internalForkChoiceUpdatedResultExpected);
  }

  @TestTemplate
  void shouldThrowDeserializingInvalidForkChoiceUpdatedResult() {
    assertThrows(
        InvalidFormatException.class,
        () -> {
          String json = "{\"status\": \"wrong\", \"payloadId\": null }";
          objectMapper.readValue(json, ForkChoiceUpdatedResult.class);
        });

    assertThrows(
        ValueInstantiationException.class,
        () -> {
          String json = "{\"status\": null, \"payloadId\": null }";
          objectMapper.readValue(json, ForkChoiceUpdatedResult.class);
        });

    assertThrows(
        JsonMappingException.class,
        () -> {
          String json = "{\"status\": null, \"payloadId\": \"wrong\" }";
          objectMapper.readValue(json, ForkChoiceUpdatedResult.class);
        });
  }

  @TestTemplate
  void shouldSerializeForkChoiceState() throws IOException {
    final ForkChoiceStateV1 forkChoiceState =
        new ForkChoiceStateV1(
            dataStructureUtil.randomBytes32(),
            dataStructureUtil.randomBytes32(),
            dataStructureUtil.randomBytes32());

    String serialized = objectMapper.writeValueAsString(forkChoiceState);

    assertThat(serialized).isNotEmpty();
  }

  @TestTemplate
  void shouldSerializePayloadAttributes() throws IOException {
    final PayloadAttributesV1 payloadAttributes =
        new PayloadAttributesV1(
            dataStructureUtil.randomUInt64(),
            dataStructureUtil.randomBytes32(),
            dataStructureUtil.randomBytes20());

    String serialized = objectMapper.writeValueAsString(payloadAttributes);

    assertThat(serialized).isNotEmpty();
  }
}
