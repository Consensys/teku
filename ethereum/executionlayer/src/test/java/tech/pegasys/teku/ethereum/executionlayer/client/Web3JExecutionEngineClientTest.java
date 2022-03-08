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
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.databind.exc.ValueInstantiationException;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.ethereum.executionlayer.client.schema.ExecutionPayloadHeaderV1;
import tech.pegasys.teku.ethereum.executionlayer.client.schema.ExecutionPayloadV1;
import tech.pegasys.teku.ethereum.executionlayer.client.schema.ForkChoiceStateV1;
import tech.pegasys.teku.ethereum.executionlayer.client.schema.ForkChoiceUpdatedResult;
import tech.pegasys.teku.ethereum.executionlayer.client.schema.PayloadAttributesV1;
import tech.pegasys.teku.ethereum.executionlayer.client.schema.PayloadStatusV1;
import tech.pegasys.teku.ethereum.executionlayer.client.schema.Response;
import tech.pegasys.teku.ethereum.executionlayer.client.schema.TransitionConfigurationV1;
import tech.pegasys.teku.ethereum.executionlayer.client.serialization.Bytes20Deserializer;
import tech.pegasys.teku.ethereum.executionlayer.client.serialization.Bytes20Serializer;
import tech.pegasys.teku.ethereum.executionlayer.client.serialization.Bytes32Deserializer;
import tech.pegasys.teku.ethereum.executionlayer.client.serialization.Bytes8Deserializer;
import tech.pegasys.teku.ethereum.executionlayer.client.serialization.BytesSerializer;
import tech.pegasys.teku.ethereum.executionlayer.client.serialization.UInt256AsHexDeserializer;
import tech.pegasys.teku.ethereum.executionlayer.client.serialization.UInt256AsHexSerializer;
import tech.pegasys.teku.ethereum.executionlayer.client.serialization.UInt64AsHexDeserializer;
import tech.pegasys.teku.ethereum.executionlayer.client.serialization.UInt64AsHexSerializer;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.bytes.Bytes8;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.executionengine.ExecutionPayloadStatus;
import tech.pegasys.teku.spec.executionengine.PayloadStatus;
import tech.pegasys.teku.spec.executionengine.TransitionConfiguration;
import tech.pegasys.teku.spec.util.DataStructureUtil;

@TestSpecContext(milestone = SpecMilestone.BELLATRIX)
public class Web3JExecutionEngineClientTest {
  Writer jsonWriter;
  JsonGenerator jsonGenerator;
  ObjectMapper objectMapper;
  SerializerProvider serializerProvider;
  DataStructureUtil dataStructureUtil;
  private final MockWebServer mockWebServer = new MockWebServer();
  private final StubTimeProvider timeProvider = StubTimeProvider.withTimeInSeconds(0);

  @AfterEach
  public void stopMWS() throws Exception {
    mockWebServer.shutdown();
  }

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

  @TestTemplate
  void shouldThrowDeserializingUIntQuantityNot0xPrefixed() {

    assertThrows(
        IllegalArgumentException.class,
        () -> {
          JsonParser parser = prepareDeserializationContext("123");
          UInt256AsHexDeserializer deserializer = new UInt256AsHexDeserializer();
          deserializer.deserialize(parser, objectMapper.getDeserializationContext());
        });

    assertThrows(
        IllegalArgumentException.class,
        () -> {
          JsonParser parser = prepareDeserializationContext("123");
          UInt64AsHexDeserializer deserializer = new UInt64AsHexDeserializer();
          deserializer.deserialize(parser, objectMapper.getDeserializationContext());
        });
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
  void shouldSerializeDeserializeExecutionPayloadHeaderV1() throws IOException {
    ExecutionPayloadHeader internalExecutionPayloadHeader =
        dataStructureUtil.randomExecutionPayloadHeader();
    ExecutionPayloadHeaderV1 executionPayloadHeaderV1Orig =
        ExecutionPayloadHeaderV1.fromInternalExecutionPayloadHeader(internalExecutionPayloadHeader);

    String executionPayloadHeaderV1OrigSerialized =
        objectMapper.writeValueAsString(executionPayloadHeaderV1Orig);
    ExecutionPayloadHeaderV1 executionPayloadHeaderV1New =
        objectMapper.readValue(
            executionPayloadHeaderV1OrigSerialized, ExecutionPayloadHeaderV1.class);

    assertThat(executionPayloadHeaderV1Orig).isEqualTo(executionPayloadHeaderV1New);
    assertThat(
            executionPayloadHeaderV1Orig.asInternalExecutionPayloadHeader(
                internalExecutionPayloadHeader.getSchema()))
        .isEqualTo(internalExecutionPayloadHeader);
  }

  @TestTemplate
  void shouldDeserializePayloadStatusWithNulls() throws IOException {
    PayloadStatusV1 payloadStatusV1Expected =
        new PayloadStatusV1(ExecutionPayloadStatus.VALID, null, null);
    PayloadStatus internalPayloadStatusExpected = PayloadStatus.VALID;
    String json = "{\"status\": \"VALID\", \"latestValidHash\": null, \"validationError\": null }";
    PayloadStatusV1 payloadStatusV1Deserialized =
        objectMapper.readValue(json, PayloadStatusV1.class);

    PayloadStatus internalPayloadStatus = payloadStatusV1Deserialized.asInternalExecutionPayload();

    assertThat(payloadStatusV1Deserialized).isEqualTo(payloadStatusV1Expected);
    assertThat(internalPayloadStatus).isEqualTo(internalPayloadStatusExpected);
  }

  @TestTemplate
  void shouldDeserializePayloadStatus() throws IOException {
    Bytes32 lastValidHash = dataStructureUtil.randomBytes32();
    PayloadStatusV1 payloadStatusV1Expected =
        new PayloadStatusV1(ExecutionPayloadStatus.INVALID, lastValidHash, "test");
    PayloadStatus internalPayloadStatusExpected =
        PayloadStatus.invalid(Optional.of(lastValidHash), Optional.of("test"));

    String json =
        "{\"status\": \"INVALID\", \"latestValidHash\": \""
            + lastValidHash.toHexString()
            + "\", \"validationError\": \"test\" }";
    PayloadStatusV1 payloadStatusV1Deserialized =
        objectMapper.readValue(json, PayloadStatusV1.class);

    PayloadStatus internalPayloadStatus = payloadStatusV1Deserialized.asInternalExecutionPayload();

    assertThat(payloadStatusV1Deserialized).isEqualTo(payloadStatusV1Expected);
    assertThat(internalPayloadStatus).isEqualTo(internalPayloadStatusExpected);
  }

  @TestTemplate
  void shouldThrowDeserializingInvalidPayloadStatusV1() {
    assertThrows(
        InvalidFormatException.class,
        () -> {
          String json =
              "{\"status\": \"wrong\", \"latestValidHash\": null, \"validationError\": null }";
          objectMapper.readValue(json, PayloadStatusV1.class);
        });

    assertThrows(
        ValueInstantiationException.class,
        () -> {
          String json = "{\"status\": null, \"latestValidHash\": null, \"validationError\": null }";
          objectMapper.readValue(json, PayloadStatusV1.class);
        });

    assertThrows(
        JsonMappingException.class,
        () -> {
          String json =
              "{\"status\": null, \"latestValidHash\": \"wrong\", \"validationError\": null }";
          objectMapper.readValue(json, PayloadStatusV1.class);
        });
  }

  @TestTemplate
  void shouldDeserializeForkChoiceUpdatedResultWithNulls() throws IOException {
    ForkChoiceUpdatedResult forkChoiceUpdatedResultExpected =
        createExternalForkChoiceUpdatedResult(ExecutionPayloadStatus.SYNCING, null, null);
    tech.pegasys.teku.spec.executionengine.ForkChoiceUpdatedResult
        internalForkChoiceUpdatedResultExpected =
            createInternalForkChoiceUpdatedResult(
                ExecutionPayloadStatus.SYNCING, Optional.empty(), Optional.empty());
    String json = "{\"payloadStatus\": {\"status\": \"SYNCING\"}, \"payloadId\": null }";
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
    Bytes32 latestValidHash = dataStructureUtil.randomBytes32();
    ForkChoiceUpdatedResult forkChoiceUpdatedResultExpected =
        createExternalForkChoiceUpdatedResult(
            ExecutionPayloadStatus.VALID, latestValidHash, payloadId);
    tech.pegasys.teku.spec.executionengine.ForkChoiceUpdatedResult
        internalForkChoiceUpdatedResultExpected =
            createInternalForkChoiceUpdatedResult(
                ExecutionPayloadStatus.VALID, Optional.of(latestValidHash), Optional.of(payloadId));
    String json =
        "{\"payloadStatus\": {\"status\": \"VALID\", \"latestValidHash\": \""
            + latestValidHash.toHexString()
            + "\"}, \"payloadId\": \""
            + payloadId.toHexString()
            + "\" }";
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
        ValueInstantiationException.class,
        () -> {
          String json = "{\"wrong\": \"wrong\", \"payloadId\": null }";
          objectMapper.readValue(json, ForkChoiceUpdatedResult.class);
        });

    assertThrows(
        MismatchedInputException.class,
        () -> {
          String json = "{\"payloadStatus\": \"wrong\", \"payloadId\": null }";
          objectMapper.readValue(json, ForkChoiceUpdatedResult.class);
        });

    assertThrows(
        InvalidFormatException.class,
        () -> {
          String json = "{\"payloadStatus\": {\"status\": \"wrong\"}, \"payloadId\": null }";
          objectMapper.readValue(json, ForkChoiceUpdatedResult.class);
        });

    assertThrows(
        ValueInstantiationException.class,
        () -> {
          String json = "{\"payloadStatus\": null, \"payloadId\": null }";
          objectMapper.readValue(json, ForkChoiceUpdatedResult.class);
        });

    assertThrows(
        JsonMappingException.class,
        () -> {
          String json = "{\"payloadStatus\": null, \"payloadId\": \"wrong\" }";
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

  @TestTemplate
  void shouldSerializeTransitionConfigurationV1() throws IOException {
    final TransitionConfigurationV1 externalTransitionConfiguration =
        new TransitionConfigurationV1(
            dataStructureUtil.randomUInt256(),
            dataStructureUtil.randomBytes32(),
            dataStructureUtil.randomUInt64());

    String serialized = objectMapper.writeValueAsString(externalTransitionConfiguration);

    assertThat(serialized).isNotEmpty();
  }

  @TestTemplate
  void shouldDeserializeTransitionConfigurationV1() throws IOException {
    final String json =
        "{\"terminalTotalDifficulty\": \"0xF123657934589000000000000000001\", "
            + "\"terminalBlockHash\": \"0x95b8e2ba063ab62f68ebe7db0a9669ab9e7906aa4e060e1cc0b67b294ce8c5e4\", "
            + "\"terminalBlockNumber\": \"0xa79345890\" }";

    final TransitionConfigurationV1 externalTransitionConfiguration =
        objectMapper.readValue(json, TransitionConfigurationV1.class);

    assertThat(externalTransitionConfiguration.terminalTotalDifficulty)
        .isEqualTo(UInt256.fromHexString("0xF123657934589000000000000000001"));
    assertThat(externalTransitionConfiguration.terminalBlockHash)
        .isEqualTo(
            Bytes32.fromHexString(
                "0x95b8e2ba063ab62f68ebe7db0a9669ab9e7906aa4e060e1cc0b67b294ce8c5e4"));
    assertThat(externalTransitionConfiguration.terminalBlockNumber)
        .isEqualTo(
            UInt64.valueOf(Bytes.fromHexStringLenient("0xa79345890").toUnsignedBigInteger()));
  }

  @TestTemplate
  void shouldSerializeDeserializeTransitionConfigurationV1() throws IOException {
    final TransitionConfigurationV1 externalTransitionConfiguration =
        new TransitionConfigurationV1(
            dataStructureUtil.randomUInt256(),
            dataStructureUtil.randomBytes32(),
            dataStructureUtil.randomUInt64());

    String serialized = objectMapper.writeValueAsString(externalTransitionConfiguration);

    assertThat(serialized).isNotEmpty();

    TransitionConfigurationV1 externalTransitionConfiguration2 =
        objectMapper.readValue(serialized, TransitionConfigurationV1.class);

    assertThat(externalTransitionConfiguration).isEqualTo(externalTransitionConfiguration2);
  }

  @TestTemplate
  void shouldTransitionConfigurationRoundtrip() {
    final TransitionConfigurationV1 externalTransitionConfiguration =
        new TransitionConfigurationV1(
            dataStructureUtil.randomUInt256(),
            dataStructureUtil.randomBytes32(),
            dataStructureUtil.randomUInt64());

    final TransitionConfiguration internalTransitionConfiguration =
        externalTransitionConfiguration.asInternalTransitionConfiguration();

    assertThat(internalTransitionConfiguration.getTerminalBlockHash())
        .isEqualTo(externalTransitionConfiguration.terminalBlockHash);
    assertThat(internalTransitionConfiguration.getTerminalBlockNumber())
        .isEqualTo(externalTransitionConfiguration.terminalBlockNumber);
    assertThat(internalTransitionConfiguration.getTerminalTotalDifficulty())
        .isEqualTo(externalTransitionConfiguration.terminalTotalDifficulty);

    final TransitionConfigurationV1 externalTransitionConfiguration2 =
        TransitionConfigurationV1.fromInternalTransitionConfiguration(
            internalTransitionConfiguration);

    assertThat(externalTransitionConfiguration2).isEqualTo(externalTransitionConfiguration);
  }

  @TestTemplate
  void shouldRoundtripWithMockedWebServer() throws InterruptedException, IOException {

    mockWebServer.start();

    final Web3JExecutionEngineClient eeClient =
        new Web3JExecutionEngineClient(
            "http://localhost:" + mockWebServer.getPort(), timeProvider, Optional.empty());

    final Bytes32 latestValidHash =
        Bytes32.fromHexString("0x135bc3400c2839fd856a524871200bd5e362db615fc4565e1870ed9a2a936464");
    final String validationError = "error";
    final PayloadStatus payloadStatusResponse =
        PayloadStatus.invalid(Optional.of(latestValidHash), Optional.of(validationError));

    final String bodyResponse =
        "{\"jsonrpc\": \"2.0\", \"id\": 0, \"result\":"
            + "{\"payloadStatus\" : { \"status\": \"INVALID\", \"latestValidHash\": "
            + latestValidHash
            + ", \"validationError\": "
            + validationError
            + "}}}";

    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .setBody(bodyResponse)
            .addHeader("Content-Type", "application/json"));

    final ForkChoiceStateV1 forkChoiceStateV1Orig =
        new ForkChoiceStateV1(
            Bytes32.fromHexString(
                "0x235bc3400c2839fd856a524871200bd5e362db615fc4565e1870ed9a2a936464"),
            Bytes32.fromHexString(
                "0x367cbd40ac7318427aadb97345a91fa2e965daf3158d7f1846f1306305f41bef"),
            Bytes32.fromHexString(
                "0xfd18cf40cc907a739be483f1ca0ee23ad65cdd3df23205eabc6d660a75d1f54e"));

    final PayloadAttributesV1 payloadAttributesV1Request =
        new PayloadAttributesV1(
            UInt64.valueOf(10),
            Bytes32.fromHexString(
                "0x367cbd40ac7318427aadb97345a91fa2e965daf3158d7f1846f1306305f41bef"),
            Bytes20.fromHexString("0xfd18cf40cc907a739be483f1ca0ee23ad65cdd3d"));

    final SafeFuture<Response<ForkChoiceUpdatedResult>> futureResponseForkChoiceUpdatedResult =
        eeClient.forkChoiceUpdated(forkChoiceStateV1Orig, Optional.of(payloadAttributesV1Request));

    final RecordedRequest request = mockWebServer.takeRequest();

    assertThat(request.getBody().readString(StandardCharsets.UTF_8))
        .isEqualTo(
            "{\"jsonrpc\":\"2.0\",\"method\":\"engine_forkchoiceUpdatedV1\",\"params\":["
                + "{\"headBlockHash\":\"0x235bc3400c2839fd856a524871200bd5e362db615fc4565e1870ed9a2a936464\","
                + "\"safeBlockHash\":\"0x367cbd40ac7318427aadb97345a91fa2e965daf3158d7f1846f1306305f41bef\","
                + "\"finalizedBlockHash\":\"0xfd18cf40cc907a739be483f1ca0ee23ad65cdd3df23205eabc6d660a75d1f54e\"},"
                + "{\"timestamp\":\"0xa\","
                + "\"prevRandao\":\"0x367cbd40ac7318427aadb97345a91fa2e965daf3158d7f1846f1306305f41bef\","
                + "\"suggestedFeeRecipient\":\"0xfd18cf40cc907a739be483f1ca0ee23ad65cdd3d\"}],"
                + "\"id\":0}");

    assertThat(futureResponseForkChoiceUpdatedResult.join())
        .matches(
            forkChoiceUpdatedResultResponse ->
                forkChoiceUpdatedResultResponse
                    .getPayload()
                    .asInternalExecutionPayload()
                    .getPayloadStatus()
                    .equals(payloadStatusResponse));
  }

  private ForkChoiceUpdatedResult createExternalForkChoiceUpdatedResult(
      ExecutionPayloadStatus status, Bytes32 latestValidHash, Bytes8 payloadId) {
    return new ForkChoiceUpdatedResult(
        new PayloadStatusV1(status, latestValidHash, null), payloadId);
  }

  private tech.pegasys.teku.spec.executionengine.ForkChoiceUpdatedResult
      createInternalForkChoiceUpdatedResult(
          ExecutionPayloadStatus status,
          Optional<Bytes32> latestValidHash,
          Optional<Bytes8> payloadId) {
    return new tech.pegasys.teku.spec.executionengine.ForkChoiceUpdatedResult(
        PayloadStatus.create(status, latestValidHash, Optional.empty()), payloadId);
  }
}
