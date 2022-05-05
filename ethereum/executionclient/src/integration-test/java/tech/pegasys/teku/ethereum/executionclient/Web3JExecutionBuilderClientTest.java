/*
 * Copyright 2022 ConsenSys AG.
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

package tech.pegasys.teku.ethereum.executionclient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.INTEGER;
import static org.assertj.core.api.InstanceOfAssertFactories.STRING;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.Bytes48;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.ethereum.executionclient.schema.BlindedBeaconBlockV1;
import tech.pegasys.teku.ethereum.executionclient.schema.BuilderBidV1;
import tech.pegasys.teku.ethereum.executionclient.schema.ExecutionPayloadV1;
import tech.pegasys.teku.ethereum.executionclient.schema.Response;
import tech.pegasys.teku.ethereum.executionclient.schema.SignedMessage;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.util.DataStructureUtil;

@TestSpecContext(milestone = SpecMilestone.BELLATRIX)
public class Web3JExecutionBuilderClientTest {
  private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(1);
  private final MockWebServer mockWebServer = new MockWebServer();
  private final StubTimeProvider timeProvider = StubTimeProvider.withTimeInSeconds(0);

  ObjectMapper objectMapper;
  DataStructureUtil dataStructureUtil;

  Web3JExecutionBuilderClient ebClient;

  @BeforeEach
  void setUp(SpecContext specContext) throws IOException {
    objectMapper = new ObjectMapper();
    dataStructureUtil = specContext.getDataStructureUtil();
    mockWebServer.start();
    Web3jClientBuilder web3JClientBuilder = new Web3jClientBuilder();
    Web3JClient web3JClient =
        web3JClientBuilder
            .endpoint("http://localhost:" + mockWebServer.getPort())
            .timeout(DEFAULT_TIMEOUT)
            .jwtConfigOpt(Optional.empty())
            .timeProvider(timeProvider)
            .build();
    ebClient = new Web3JExecutionBuilderClient(web3JClient);
  }

  @AfterEach
  public void afterEach() throws Exception {
    mockWebServer.shutdown();
  }

  @TestTemplate
  void getPayload_shouldRoundtripWithMockedWebServer() throws Exception {
    final String jsonGetPayloadResponse =
        Resources.toString(
            Resources.getResource("builder_getPayloadResponse.json"), StandardCharsets.UTF_8);

    final String bodyResponse =
        "{\"jsonrpc\": \"2.0\", \"id\": 0, \"result\":" + jsonGetPayloadResponse + "}";

    final ExecutionPayloadV1 executionPayloadResponse =
        objectMapper.readValue(jsonGetPayloadResponse, ExecutionPayloadV1.class);

    // double-check that what we are going to respond corresponds to the json data
    final String serializedExecutionPayloadResponse =
        objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(executionPayloadResponse);
    assertThat(serializedExecutionPayloadResponse).isEqualToIgnoringNewLines(jsonGetPayloadResponse);

    mockWebServer.enqueue(
        new MockResponse()
            .setBody(bodyResponse)
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json"));

    final String jsonGetPayloadRequest =
        Resources.toString(
            Resources.getResource("builder_getPayloadRequest.json"), StandardCharsets.UTF_8);

    final SignedMessage<BlindedBeaconBlockV1> signedBlindedBeaconBlockRequest =
        objectMapper.readValue(jsonGetPayloadRequest, new TypeReference<>() {});

    SafeFuture<Response<ExecutionPayloadV1>> futureResponseProposeBlindedBlock =
        ebClient.getPayload(signedBlindedBeaconBlockRequest);

    final RecordedRequest request = mockWebServer.takeRequest();

    final JsonNode requestBodyJsonNode =
        objectMapper.readTree(request.getBody().readString(StandardCharsets.UTF_8));

    // check that sent blinded block match as per sent object as well as received object from the
    // mock
    final String serializedSignedBlindedBeaconBlockRequest =
        objectMapper
            .writerWithDefaultPrettyPrinter()
            .writeValueAsString(signedBlindedBeaconBlockRequest);
    assertThat(serializedSignedBlindedBeaconBlockRequest).isEqualToIgnoringNewLines(jsonGetPayloadRequest);
    assertThat(serializedSignedBlindedBeaconBlockRequest)
        .isEqualTo(requestBodyJsonNode.get("params").get(0).toPrettyString());

    verifyJsonRpcMethodCall(requestBodyJsonNode, "builder_getPayloadV1");

    assertThat(futureResponseProposeBlindedBlock.join())
        .matches(
            executionPayloadV1Response1 ->
                executionPayloadV1Response1.getPayload().equals(executionPayloadResponse));
  }

  @TestTemplate
  void getHeader_shouldRoundtripWithMockedWebServer() throws Exception {
    final String jsonSignedBuilderBidResponse =
        Resources.toString(
            Resources.getResource("builder_getHeaderResponse.json"), StandardCharsets.UTF_8);

    final String bodyResponse =
        "{\"jsonrpc\": \"2.0\", \"id\": 0, \"result\":" + jsonSignedBuilderBidResponse + "}";

    final SignedMessage<BuilderBidV1> signedBuilderBidV1 =
        objectMapper.readValue(jsonSignedBuilderBidResponse, new TypeReference<>() {});

    // double-check that what we are going to respond corresponds to the json data
    final String serializedSignedBuilderBidResponse =
        objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(signedBuilderBidV1);
    assertThat(serializedSignedBuilderBidResponse).isEqualToIgnoringNewLines(jsonSignedBuilderBidResponse);

    mockWebServer.enqueue(
        new MockResponse()
            .setBody(bodyResponse)
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json"));

    final UInt64 slotRequest = dataStructureUtil.randomUInt64();
    final Bytes48 pubKeyRequest = dataStructureUtil.randomPublicKeyBytes();
    final Bytes32 parentHashRequest = dataStructureUtil.randomBytes32();

    SafeFuture<Response<SignedMessage<BuilderBidV1>>> futureResponseExecutionHeader =
        ebClient.getHeader(slotRequest, pubKeyRequest, parentHashRequest);

    final RecordedRequest request = mockWebServer.takeRequest();

    final JsonNode requestBodyJsonNode =
        objectMapper.readTree(request.getBody().readString(StandardCharsets.UTF_8));

    final String slot = requestBodyJsonNode.get("params").get(0).asText();
    final String pubKey = requestBodyJsonNode.get("params").get(1).asText();
    final String parentHash = requestBodyJsonNode.get("params").get(2).asText();

    verifyJsonRpcMethodCall(requestBodyJsonNode, "builder_getHeaderV1");

    assertThat(slotRequest).isEqualTo(UInt64.valueOf(slot));
    assertThat(pubKeyRequest).isEqualTo(Bytes48.fromHexString(pubKey));
    assertThat(parentHashRequest).isEqualTo(Bytes32.fromHexString(parentHash));

    assertThat(futureResponseExecutionHeader.join())
        .matches(
            signedBuilderBidV1Response ->
                signedBuilderBidV1Response.getPayload().equals(signedBuilderBidV1));
  }

  private void verifyJsonRpcMethodCall(final JsonNode requestBodyJsonNode, final String method) {
    assertThat(requestBodyJsonNode.get("method").asText()).asInstanceOf(STRING).isEqualTo(method);
    assertThat(requestBodyJsonNode.get("id").asInt())
        .asInstanceOf(INTEGER)
        .isGreaterThanOrEqualTo(0);
    assertThat(requestBodyJsonNode.get("jsonrpc").asText()).asInstanceOf(STRING).isEqualTo("2.0");
  }
}
