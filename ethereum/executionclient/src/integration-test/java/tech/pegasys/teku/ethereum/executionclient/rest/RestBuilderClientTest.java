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

package tech.pegasys.teku.ethereum.executionclient.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.spec.schemas.ApiSchemas.SIGNED_VALIDATOR_REGISTRATIONS_SCHEMA;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.Resources;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okio.Buffer;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.Bytes48;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.ethereum.executionclient.schema.BuilderApiResponse;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.builder.SignedBuilderBid;
import tech.pegasys.teku.spec.datastructures.builder.SignedValidatorRegistration;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.networks.Eth2Network;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsBellatrix;

@TestSpecContext(
    milestone = SpecMilestone.BELLATRIX,
    network = {Eth2Network.MAINNET})
class RestBuilderClientTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private static final Duration WAIT_FOR_CALL_COMPLETION = Duration.ofSeconds(10);

  private static final String INTERNAL_SERVER_ERROR_MESSAGE =
      "{\"code\":500,\"message\":\"Internal server error\"}";

  private static final String SIGNED_VALIDATOR_REGISTRATIONS_REQUEST =
      readResource("builder/signedValidatorRegistrations.json");

  private static final String SIGNED_BLINDED_BEACON_BLOCK_REQUEST =
      readResource("builder/signedBlindedBeaconBlock.json");

  private static final String EXECUTION_PAYLOAD_HEADER_RESPONSE =
      readResource("builder/executionPayloadHeaderResponse.json");

  private static final String UNBLINDED_EXECUTION_PAYLOAD_RESPONSE =
      readResource("builder/unblindedExecutionPayloadResponse.json");

  private static final UInt64 SLOT = UInt64.ONE;

  private static final Bytes32 PARENT_HASH =
      Bytes32.fromHexString("0xcf8e0d4e9587369b2301d0790347320302cc0943d5a1884560367e8208d920f2");

  private static final BLSPublicKey PUB_KEY =
      BLSPublicKey.fromBytesCompressed(
          Bytes48.fromHexString(
              "0x93247f2209abcacf57b75a51dafae777f9dd38bc7053d1af526f220a7489a6d3a2753e5f3e8b1cfe39b56f43611df74a"));

  private final MockWebServer mockWebServer = new MockWebServer();
  private final OkHttpClient okHttpClient = new OkHttpClient.Builder().build();

  private SchemaDefinitionsBellatrix schemaDefinitionsBellatrix;

  private RestBuilderClient restBuilderClient;

  @BeforeEach
  void setUp(SpecContext specContext) throws IOException {
    mockWebServer.start();
    Spec spec = specContext.getSpec();
    String endpoint = "http://localhost:" + mockWebServer.getPort();
    OkHttpRestClient okHttpRestClient = new OkHttpRestClient(okHttpClient, endpoint);
    this.schemaDefinitionsBellatrix =
        spec.forMilestone(specContext.getSpecMilestone())
            .getSchemaDefinitions()
            .toVersionBellatrix()
            .orElseThrow();
    this.restBuilderClient = new RestBuilderClient(okHttpRestClient, spec);
  }

  @AfterEach
  void afterEach() throws Exception {
    mockWebServer.shutdown();
  }

  @TestTemplate
  void getStatus_success() {
    mockWebServer.enqueue(new MockResponse().setResponseCode(200));

    assertThat(restBuilderClient.status())
        .succeedsWithin(WAIT_FOR_CALL_COMPLETION)
        .satisfies(
            response -> {
              assertThat(response.isSuccess()).isTrue();
              assertThat(response.getPayload()).isNull();
            });

    verifyGetRequest("/eth/v1/builder/status");
  }

  @TestTemplate
  void getStatus_failures() {
    mockWebServer.enqueue(
        new MockResponse().setResponseCode(500).setBody(INTERNAL_SERVER_ERROR_MESSAGE));

    assertThat(restBuilderClient.status())
        .succeedsWithin(WAIT_FOR_CALL_COMPLETION)
        .satisfies(
            response -> {
              assertThat(response.isFailure()).isTrue();
              assertThat(response.getErrorMessage()).isEqualTo(INTERNAL_SERVER_ERROR_MESSAGE);
            });

    verifyGetRequest("/eth/v1/builder/status");
  }

  @TestTemplate
  void registerValidators_success() {

    mockWebServer.enqueue(new MockResponse().setResponseCode(200));

    SszList<SignedValidatorRegistration> signedValidatorRegistrations =
        createSignedValidatorRegistrations();

    assertThat(restBuilderClient.registerValidators(SLOT, signedValidatorRegistrations))
        .succeedsWithin(WAIT_FOR_CALL_COMPLETION)
        .satisfies(
            response -> {
              assertThat(response.isSuccess()).isTrue();
              assertThat(response.getPayload()).isNull();
            });

    verifyPostRequest("/eth/v1/builder/validators", SIGNED_VALIDATOR_REGISTRATIONS_REQUEST);
  }

  @TestTemplate
  void registerValidators_zeroRegistrationsDoesNotMakeRequest() {

    SszList<SignedValidatorRegistration> zeroRegistrations =
        SIGNED_VALIDATOR_REGISTRATIONS_SCHEMA.getDefault();

    assertThat(restBuilderClient.registerValidators(SLOT, zeroRegistrations))
        .succeedsWithin(WAIT_FOR_CALL_COMPLETION)
        .satisfies(
            response -> {
              assertThat(response.isSuccess()).isTrue();
              assertThat(response.getPayload()).isNull();
            });

    assertThat(mockWebServer.getRequestCount()).isEqualTo(0);
  }

  @TestTemplate
  void registerValidators_failures() {

    String unknownValidatorError = "{\"code\":400,\"message\":\"unknown validator\"}";

    mockWebServer.enqueue(new MockResponse().setResponseCode(400).setBody(unknownValidatorError));

    SszList<SignedValidatorRegistration> signedValidatorRegistrations =
        createSignedValidatorRegistrations();

    assertThat(restBuilderClient.registerValidators(SLOT, signedValidatorRegistrations))
        .succeedsWithin(WAIT_FOR_CALL_COMPLETION)
        .satisfies(
            response -> {
              assertThat(response.isFailure()).isTrue();
              assertThat(response.getErrorMessage()).isEqualTo(unknownValidatorError);
            });

    verifyPostRequest("/eth/v1/builder/validators", SIGNED_VALIDATOR_REGISTRATIONS_REQUEST);

    mockWebServer.enqueue(
        new MockResponse().setResponseCode(500).setBody(INTERNAL_SERVER_ERROR_MESSAGE));

    assertThat(restBuilderClient.registerValidators(SLOT, signedValidatorRegistrations))
        .succeedsWithin(WAIT_FOR_CALL_COMPLETION)
        .satisfies(
            response -> {
              assertThat(response.isFailure()).isTrue();
              assertThat(response.getErrorMessage()).isEqualTo(INTERNAL_SERVER_ERROR_MESSAGE);
            });

    verifyPostRequest("/eth/v1/builder/validators", SIGNED_VALIDATOR_REGISTRATIONS_REQUEST);
  }

  @TestTemplate
  void getExecutionPayloadHeader_success() {

    mockWebServer.enqueue(
        new MockResponse().setResponseCode(200).setBody(EXECUTION_PAYLOAD_HEADER_RESPONSE));

    assertThat(restBuilderClient.getHeader(SLOT, PUB_KEY, PARENT_HASH))
        .succeedsWithin(WAIT_FOR_CALL_COMPLETION)
        .satisfies(
            response -> {
              assertThat(response.isSuccess()).isTrue();
              assertThat(response.getPayload())
                  .isPresent()
                  .hasValueSatisfying(this::verifySignedBuilderBidResponse);
            });

    verifyGetRequest("/eth/v1/builder/header/1/" + PARENT_HASH + "/" + PUB_KEY);
  }

  @TestTemplate
  void getExecutionPayloadHeader_noHeaderAvailable() {

    mockWebServer.enqueue(new MockResponse().setResponseCode(204));

    assertThat(restBuilderClient.getHeader(SLOT, PUB_KEY, PARENT_HASH))
        .succeedsWithin(WAIT_FOR_CALL_COMPLETION)
        .satisfies(
            response -> {
              assertThat(response.isSuccess()).isTrue();
              assertThat(response.getPayload()).isEmpty();
            });

    verifyGetRequest("/eth/v1/builder/header/1/" + PARENT_HASH + "/" + PUB_KEY);
  }

  @TestTemplate
  void getExecutionPayloadHeader_failures() {

    String missingParentHashError =
        "{\"code\":400,\"message\":\"Unknown hash: missing parent hash\"}";
    mockWebServer.enqueue(new MockResponse().setResponseCode(400).setBody(missingParentHashError));

    assertThat(restBuilderClient.getHeader(SLOT, PUB_KEY, PARENT_HASH))
        .succeedsWithin(WAIT_FOR_CALL_COMPLETION)
        .satisfies(
            response -> {
              assertThat(response.isFailure()).isTrue();
              assertThat(response.getErrorMessage()).isEqualTo(missingParentHashError);
            });

    verifyGetRequest("/eth/v1/builder/header/1/" + PARENT_HASH + "/" + PUB_KEY);

    mockWebServer.enqueue(
        new MockResponse().setResponseCode(500).setBody(INTERNAL_SERVER_ERROR_MESSAGE));

    assertThat(restBuilderClient.getHeader(SLOT, PUB_KEY, PARENT_HASH))
        .succeedsWithin(WAIT_FOR_CALL_COMPLETION)
        .satisfies(
            response -> {
              assertThat(response.isFailure()).isTrue();
              assertThat(response.getErrorMessage()).isEqualTo(INTERNAL_SERVER_ERROR_MESSAGE);
            });

    verifyGetRequest("/eth/v1/builder/header/1/" + PARENT_HASH + "/" + PUB_KEY);
  }

  @TestTemplate
  void sendSignedBlindedBlock_success() {

    mockWebServer.enqueue(
        new MockResponse().setResponseCode(200).setBody(UNBLINDED_EXECUTION_PAYLOAD_RESPONSE));

    SignedBeaconBlock signedBlindedBeaconBlock = createSignedBlindedBeaconBlock();

    assertThat(restBuilderClient.getPayload(signedBlindedBeaconBlock))
        .succeedsWithin(WAIT_FOR_CALL_COMPLETION)
        .satisfies(
            response -> {
              assertThat(response.isSuccess()).isTrue();
              ExecutionPayload responsePayload = response.getPayload();
              verifyExecutionPayloadResponse(responsePayload);
            });

    verifyPostRequest("/eth/v1/builder/blinded_blocks", SIGNED_BLINDED_BEACON_BLOCK_REQUEST);
  }

  @TestTemplate
  void sendSignedBlindedBlock_failures() {

    String missingSignatureError =
        "{\"code\":400,\"message\":\"Invalid block: missing signature\"}";
    mockWebServer.enqueue(new MockResponse().setResponseCode(400).setBody(missingSignatureError));

    SignedBeaconBlock signedBlindedBeaconBlock = createSignedBlindedBeaconBlock();

    assertThat(restBuilderClient.getPayload(signedBlindedBeaconBlock))
        .succeedsWithin(WAIT_FOR_CALL_COMPLETION)
        .satisfies(
            response -> {
              assertThat(response.isFailure()).isTrue();
              assertThat(response.getErrorMessage()).isEqualTo(missingSignatureError);
            });

    verifyPostRequest("/eth/v1/builder/blinded_blocks", SIGNED_BLINDED_BEACON_BLOCK_REQUEST);

    mockWebServer.enqueue(
        new MockResponse().setResponseCode(500).setBody(INTERNAL_SERVER_ERROR_MESSAGE));

    assertThat(restBuilderClient.getPayload(signedBlindedBeaconBlock))
        .succeedsWithin(WAIT_FOR_CALL_COMPLETION)
        .satisfies(
            response -> {
              assertThat(response.isFailure()).isTrue();
              assertThat(response.getErrorMessage()).isEqualTo(INTERNAL_SERVER_ERROR_MESSAGE);
            });

    verifyPostRequest("/eth/v1/builder/blinded_blocks", SIGNED_BLINDED_BEACON_BLOCK_REQUEST);
  }

  private void verifyGetRequest(String apiPath) {
    verifyRequest("GET", apiPath, Optional.empty());
  }

  private void verifyPostRequest(String apiPath, String requestBody) {
    verifyRequest("POST", apiPath, Optional.of(requestBody));
  }

  private <T> void verifyRequest(
      String method, String apiPath, Optional<String> expectedRequestBody) {
    try {
      RecordedRequest request = mockWebServer.takeRequest();
      assertThat(request.getMethod()).isEqualTo(method);
      assertThat(request.getPath()).isEqualTo(apiPath);
      Buffer actualRequestBody = request.getBody();
      if (expectedRequestBody.isEmpty()) {
        assertThat(actualRequestBody.size()).isZero();
      } else {
        assertThat(actualRequestBody.size()).isNotZero();
        assertThat(OBJECT_MAPPER.readTree(expectedRequestBody.get()))
            .isEqualTo(OBJECT_MAPPER.readTree(actualRequestBody.readUtf8()));
      }
    } catch (InterruptedException | JsonProcessingException ex) {
      Assertions.fail(ex);
    }
  }

  private SszList<SignedValidatorRegistration> createSignedValidatorRegistrations() {
    try {
      return JsonUtil.parse(
          SIGNED_VALIDATOR_REGISTRATIONS_REQUEST,
          SIGNED_VALIDATOR_REGISTRATIONS_SCHEMA.getJsonTypeDefinition());
    } catch (JsonProcessingException ex) {
      throw new UncheckedIOException(ex);
    }
  }

  private void verifySignedBuilderBidResponse(SignedBuilderBid actual) {
    DeserializableTypeDefinition<BuilderApiResponse<SignedBuilderBid>> responseTypeDefinition =
        BuilderApiResponse.createTypeDefinition(
            schemaDefinitionsBellatrix.getSignedBuilderBidSchema().getJsonTypeDefinition());
    try {
      SignedBuilderBid expected =
          JsonUtil.parse(EXECUTION_PAYLOAD_HEADER_RESPONSE, responseTypeDefinition).getData();
      assertThat(actual).isEqualTo(expected);
    } catch (JsonProcessingException ex) {
      Assertions.fail(ex);
    }
  }

  private SignedBeaconBlock createSignedBlindedBeaconBlock() {
    try {
      return JsonUtil.parse(
          SIGNED_BLINDED_BEACON_BLOCK_REQUEST,
          schemaDefinitionsBellatrix.getSignedBlindedBeaconBlockSchema().getJsonTypeDefinition());
    } catch (JsonProcessingException ex) {
      throw new UncheckedIOException(ex);
    }
  }

  private void verifyExecutionPayloadResponse(ExecutionPayload actual) {
    DeserializableTypeDefinition<BuilderApiResponse<ExecutionPayload>> responseTypeDefinition =
        BuilderApiResponse.createTypeDefinition(
            schemaDefinitionsBellatrix.getExecutionPayloadSchema().getJsonTypeDefinition());
    try {
      ExecutionPayload expected =
          JsonUtil.parse(UNBLINDED_EXECUTION_PAYLOAD_RESPONSE, responseTypeDefinition).getData();
      assertThat(actual).isEqualTo(expected);
    } catch (JsonProcessingException ex) {
      Assertions.fail(ex);
    }
  }

  private static String readResource(String resource) {
    try {
      return Resources.toString(Resources.getResource(resource), StandardCharsets.UTF_8);
    } catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }
  }
}
