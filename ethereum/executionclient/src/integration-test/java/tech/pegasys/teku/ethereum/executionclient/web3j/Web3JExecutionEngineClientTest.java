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

package tech.pegasys.teku.ethereum.executionclient.web3j;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.INTEGER;
import static org.assertj.core.api.InstanceOfAssertFactories.STRING;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.ethereum.executionclient.events.ExecutionClientEventsChannel;
import tech.pegasys.teku.ethereum.executionclient.schema.ForkChoiceStateV1;
import tech.pegasys.teku.ethereum.executionclient.schema.ForkChoiceUpdatedResult;
import tech.pegasys.teku.ethereum.executionclient.schema.PayloadAttributesV1;
import tech.pegasys.teku.ethereum.executionclient.schema.Response;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.json.JsonTestUtil;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.executionlayer.PayloadStatus;
import tech.pegasys.teku.spec.util.DataStructureUtil;

@TestSpecContext(milestone = SpecMilestone.BELLATRIX)
public class Web3JExecutionEngineClientTest {
  private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(1);
  private final MockWebServer mockWebServer = new MockWebServer();
  private final StubTimeProvider timeProvider = StubTimeProvider.withTimeInSeconds(0);
  private final ExecutionClientEventsChannel executionClientEventsPublisher =
      mock(ExecutionClientEventsChannel.class);

  Writer jsonWriter;
  JsonGenerator jsonGenerator;
  ObjectMapper objectMapper;
  DataStructureUtil dataStructureUtil;
  Spec spec;

  Web3JExecutionEngineClient eeClient;

  @BeforeEach
  void setUp(SpecContext specContext) throws IOException {
    jsonWriter = new StringWriter();
    jsonGenerator = new JsonFactory().createGenerator(jsonWriter);
    objectMapper = new ObjectMapper();
    dataStructureUtil = specContext.getDataStructureUtil();
    spec = specContext.getSpec();
    mockWebServer.start();
    Web3jClientBuilder web3JClientBuilder = new Web3jClientBuilder();
    Web3JClient web3JClient =
        web3JClientBuilder
            .endpoint("http://localhost:" + mockWebServer.getPort())
            .timeout(DEFAULT_TIMEOUT)
            .jwtConfigOpt(Optional.empty())
            .timeProvider(timeProvider)
            .executionClientEventsPublisher(executionClientEventsPublisher)
            .build();
    eeClient = new Web3JExecutionEngineClient(web3JClient);
  }

  @AfterEach
  public void afterEach() throws Exception {
    mockWebServer.shutdown();
  }

  @TestTemplate
  @SuppressWarnings("unchecked")
  void forkChoiceUpdated_shouldRoundtripWithMockedWebServer() throws Exception {
    final Bytes32 latestValidHash =
        Bytes32.fromHexString("0x135bc3400c2839fd856a524871200bd5e362db615fc4565e1870ed9a2a936464");
    final String validationError = "error";
    final PayloadStatus payloadStatusResponse =
        PayloadStatus.invalid(Optional.of(latestValidHash), Optional.of(validationError));

    final String bodyResponse =
        "{\"jsonrpc\": \"2.0\", \"id\": 0, \"result\":"
            + "{\"payloadStatus\" : { \"status\": \"INVALID\", \"latestValidHash\": \""
            + latestValidHash
            + "\", \"validationError\": \""
            + validationError
            + "\"}}}";

    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .setBody(bodyResponse)
            .addHeader("Content-Type", "application/json"));

    final ForkChoiceStateV1 forkChoiceStateV1Request =
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
        eeClient.forkChoiceUpdatedV1(
            forkChoiceStateV1Request, Optional.of(payloadAttributesV1Request));

    final RecordedRequest request = mockWebServer.takeRequest();

    final Map<String, Object> data =
        JsonTestUtil.parse(request.getBody().readString(StandardCharsets.UTF_8));

    final Map<String, Object> forkChoiceState =
        (Map<String, Object>) ((List<Object>) data.get("params")).get(0);
    final Map<String, Object> payloadAttributes =
        (Map<String, Object>) ((List<Object>) data.get("params")).get(1);

    verifyJsonRpcMethodCall(data, "engine_forkchoiceUpdatedV1");

    assertThat(forkChoiceState.get("headBlockHash"))
        .isEqualTo("0x235bc3400c2839fd856a524871200bd5e362db615fc4565e1870ed9a2a936464");
    assertThat(forkChoiceState.get("safeBlockHash"))
        .isEqualTo("0x367cbd40ac7318427aadb97345a91fa2e965daf3158d7f1846f1306305f41bef");
    assertThat(forkChoiceState.get("finalizedBlockHash"))
        .isEqualTo("0xfd18cf40cc907a739be483f1ca0ee23ad65cdd3df23205eabc6d660a75d1f54e");

    assertThat(payloadAttributes.get("timestamp")).isEqualTo("0xa");
    assertThat(payloadAttributes.get("prevRandao"))
        .isEqualTo("0x367cbd40ac7318427aadb97345a91fa2e965daf3158d7f1846f1306305f41bef");
    assertThat(payloadAttributes.get("suggestedFeeRecipient"))
        .isEqualTo("0xfd18cf40cc907a739be483f1ca0ee23ad65cdd3d");

    assertThat(futureResponseForkChoiceUpdatedResult.join())
        .matches(
            forkChoiceUpdatedResultResponse ->
                forkChoiceUpdatedResultResponse
                    .getPayload()
                    .asInternalExecutionPayload()
                    .getPayloadStatus()
                    .equals(payloadStatusResponse));
  }

  private void verifyJsonRpcMethodCall(Map<String, Object> data, final String method) {
    assertThat(data.get("method")).asInstanceOf(STRING).isEqualTo(method);
    assertThat(data.get("id")).asInstanceOf(INTEGER).isGreaterThanOrEqualTo(0);
    assertThat(data.get("jsonrpc")).asInstanceOf(STRING).isEqualTo("2.0");
  }
}
