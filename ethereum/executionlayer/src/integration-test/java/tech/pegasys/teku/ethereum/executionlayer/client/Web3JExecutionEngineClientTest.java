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

package tech.pegasys.teku.ethereum.executionlayer.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.MapType;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.ethereum.executionlayer.client.schema.ForkChoiceStateV1;
import tech.pegasys.teku.ethereum.executionlayer.client.schema.ForkChoiceUpdatedResult;
import tech.pegasys.teku.ethereum.executionlayer.client.schema.PayloadAttributesV1;
import tech.pegasys.teku.ethereum.executionlayer.client.schema.Response;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.executionengine.PayloadStatus;

public class Web3JExecutionEngineClientTest {
  private final MockWebServer mockWebServer = new MockWebServer();
  private final StubTimeProvider timeProvider = StubTimeProvider.withTimeInSeconds(0);

  @BeforeEach
  public void beforeEach() throws Exception {
    mockWebServer.shutdown();
  }

  @AfterEach
  public void afterEach() throws Exception {
    mockWebServer.shutdown();
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldRoundtripWithMockedWebServer() throws InterruptedException, IOException {
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

    final Map<String, Object> data =
        parseGenericJson(request.getBody().readString(StandardCharsets.UTF_8));

    final Map<String, Object> forkChoiceState =
        (Map<String, Object>) ((List<Object>) data.get("params")).get(0);
    final Map<String, Object> payloadAttributes =
        (Map<String, Object>) ((List<Object>) data.get("params")).get(1);

    assertThat(data.get("method")).isEqualTo("engine_forkchoiceUpdatedV1");
    assertThat(data.get("id")).isEqualTo(0);

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

  private Map<String, Object> parseGenericJson(final String json) throws JsonProcessingException {
    final ObjectMapper mapper = new ObjectMapper();
    final MapType type =
        mapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class);
    return mapper.readValue(json, type);
  }
}
