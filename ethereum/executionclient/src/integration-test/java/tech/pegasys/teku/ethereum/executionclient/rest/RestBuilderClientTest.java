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
import static tech.pegasys.teku.spec.SpecMilestone.BELLATRIX;
import static tech.pegasys.teku.spec.SpecMilestone.CAPELLA;
import static tech.pegasys.teku.spec.SpecMilestone.DENEB;
import static tech.pegasys.teku.spec.schemas.ApiSchemas.SIGNED_VALIDATOR_REGISTRATIONS_SCHEMA;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.Resources;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.regex.Pattern;
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
import tech.pegasys.teku.infrastructure.json.exceptions.MissingRequiredFieldException;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlindedBlockContainer;
import tech.pegasys.teku.spec.datastructures.builder.BuilderPayload;
import tech.pegasys.teku.spec.datastructures.builder.SignedBuilderBid;
import tech.pegasys.teku.spec.datastructures.builder.SignedValidatorRegistration;
import tech.pegasys.teku.spec.networks.Eth2Network;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsBellatrix;

@TestSpecContext(
    milestone = {BELLATRIX, CAPELLA, DENEB},
    network = Eth2Network.MAINNET)
class RestBuilderClientTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private static final Duration WAIT_FOR_CALL_COMPLETION = Duration.ofSeconds(10);

  private static final String INTERNAL_SERVER_ERROR_MESSAGE =
      "{\"code\":500,\"message\":\"Internal server error\"}";

  private static final UInt64 SLOT = UInt64.ONE;

  private static final Bytes32 PARENT_HASH =
      Bytes32.fromHexString("0xcf8e0d4e9587369b2301d0790347320302cc0943d5a1884560367e8208d920f2");

  private static final BLSPublicKey PUB_KEY =
      BLSPublicKey.fromBytesCompressed(
          Bytes48.fromHexString(
              "0x93247f2209abcacf57b75a51dafae777f9dd38bc7053d1af526f220a7489a6d3a2753e5f3e8b1cfe39b56f43611df74a"));

  private static final Pattern TEKU_USER_AGENT_REGEX = Pattern.compile("teku/v.*");

  private static final Consumer<RecordedRequest> USER_AGENT_HEADER_ASSERTION =
      recordedRequest ->
          assertThat(recordedRequest.getHeader("User-Agent")).matches(TEKU_USER_AGENT_REGEX);

  private final OkHttpClient okHttpClient = new OkHttpClient.Builder().build();
  private final MockWebServer mockWebServer = new MockWebServer();

  private Spec spec;
  private SpecMilestone milestone;
  private OkHttpRestClient okHttpRestClient;
  private SchemaDefinitionsBellatrix schemaDefinitions;

  private RestBuilderClient restBuilderClient;

  private String signedValidatorRegistrationsRequest;
  private String signedBlindedBlockContainerRequest;

  private String signedBuilderBidResponse;
  private String unblindedBuilderPayloadResponse;

  @BeforeEach
  void setUp(final SpecContext specContext) throws IOException {
    mockWebServer.start();

    spec = specContext.getSpec();
    final String endpoint = "http://localhost:" + mockWebServer.getPort();
    okHttpRestClient = new OkHttpRestClient(okHttpClient, endpoint);

    milestone = specContext.getSpecMilestone();

    schemaDefinitions = SchemaDefinitionsBellatrix.required(specContext.getSchemaDefinitions());

    signedValidatorRegistrationsRequest = readResource("builder/signedValidatorRegistrations.json");
    final String milestoneFolder = "builder/" + milestone.toString().toLowerCase(Locale.ROOT);
    signedBlindedBlockContainerRequest =
        readResource(milestoneFolder + "/signedBlindedBlockContainer.json");
    signedBuilderBidResponse = readResource(milestoneFolder + "/signedBuilderBid.json");
    unblindedBuilderPayloadResponse =
        readResource(milestoneFolder + "/unblindedBuilderPayload.json");

    restBuilderClient = new RestBuilderClient(okHttpRestClient, spec, true);
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

    final SszList<SignedValidatorRegistration> signedValidatorRegistrations =
        createSignedValidatorRegistrations();

    assertThat(restBuilderClient.registerValidators(SLOT, signedValidatorRegistrations))
        .succeedsWithin(WAIT_FOR_CALL_COMPLETION)
        .satisfies(
            response -> {
              assertThat(response.isSuccess()).isTrue();
              assertThat(response.getPayload()).isNull();
            });

    verifyPostRequest("/eth/v1/builder/validators", signedValidatorRegistrationsRequest);
  }

  @TestTemplate
  void registerValidators_zeroRegistrationsDoesNotMakeRequest() {

    final SszList<SignedValidatorRegistration> zeroRegistrations =
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

    final String unknownValidatorError = "{\"code\":400,\"message\":\"unknown validator\"}";

    mockWebServer.enqueue(new MockResponse().setResponseCode(400).setBody(unknownValidatorError));

    final SszList<SignedValidatorRegistration> signedValidatorRegistrations =
        createSignedValidatorRegistrations();

    assertThat(restBuilderClient.registerValidators(SLOT, signedValidatorRegistrations))
        .succeedsWithin(WAIT_FOR_CALL_COMPLETION)
        .satisfies(
            response -> {
              assertThat(response.isFailure()).isTrue();
              assertThat(response.getErrorMessage()).isEqualTo(unknownValidatorError);
            });

    verifyPostRequest("/eth/v1/builder/validators", signedValidatorRegistrationsRequest);

    mockWebServer.enqueue(
        new MockResponse().setResponseCode(500).setBody(INTERNAL_SERVER_ERROR_MESSAGE));

    assertThat(restBuilderClient.registerValidators(SLOT, signedValidatorRegistrations))
        .succeedsWithin(WAIT_FOR_CALL_COMPLETION)
        .satisfies(
            response -> {
              assertThat(response.isFailure()).isTrue();
              assertThat(response.getErrorMessage()).isEqualTo(INTERNAL_SERVER_ERROR_MESSAGE);
            });

    verifyPostRequest("/eth/v1/builder/validators", signedValidatorRegistrationsRequest);
  }

  @TestTemplate
  void getHeader_success_doesNotSetUserAgentHeader() {

    restBuilderClient = new RestBuilderClient(okHttpRestClient, spec, false);

    mockWebServer.enqueue(
        new MockResponse().setResponseCode(200).setBody(signedBuilderBidResponse));

    assertThat(restBuilderClient.getHeader(SLOT, PUB_KEY, PARENT_HASH))
        .succeedsWithin(WAIT_FOR_CALL_COMPLETION)
        .satisfies(
            response -> {
              assertThat(response.isSuccess()).isTrue();
              assertThat(response.getPayload())
                  .isPresent()
                  .hasValueSatisfying(this::verifySignedBuilderBidResponse);
            });

    verifyGetRequest(
        "/eth/v1/builder/header/1/" + PARENT_HASH + "/" + PUB_KEY,
        recordedRequest -> {
          assertThat(recordedRequest.getHeader("User-Agent")).doesNotMatch(TEKU_USER_AGENT_REGEX);
        });
  }

  @TestTemplate
  void getHeader_success() {

    mockWebServer.enqueue(
        new MockResponse().setResponseCode(200).setBody(signedBuilderBidResponse));

    assertThat(restBuilderClient.getHeader(SLOT, PUB_KEY, PARENT_HASH))
        .succeedsWithin(WAIT_FOR_CALL_COMPLETION)
        .satisfies(
            response -> {
              assertThat(response.isSuccess()).isTrue();
              assertThat(response.getPayload())
                  .isPresent()
                  .hasValueSatisfying(this::verifySignedBuilderBidResponse);
            });

    verifyGetRequest(
        "/eth/v1/builder/header/1/" + PARENT_HASH + "/" + PUB_KEY, USER_AGENT_HEADER_ASSERTION);
  }

  @TestTemplate
  void getHeader_noHeaderAvailable() {

    mockWebServer.enqueue(new MockResponse().setResponseCode(204));

    assertThat(restBuilderClient.getHeader(SLOT, PUB_KEY, PARENT_HASH))
        .succeedsWithin(WAIT_FOR_CALL_COMPLETION)
        .satisfies(
            response -> {
              assertThat(response.isSuccess()).isTrue();
              assertThat(response.getPayload()).isEmpty();
            });

    verifyGetRequest(
        "/eth/v1/builder/header/1/" + PARENT_HASH + "/" + PUB_KEY, USER_AGENT_HEADER_ASSERTION);
  }

  @TestTemplate
  void getHeader_failures() {

    final String missingParentHashError =
        "{\"code\":400,\"message\":\"Unknown hash: missing parent hash\"}";
    mockWebServer.enqueue(new MockResponse().setResponseCode(400).setBody(missingParentHashError));

    assertThat(restBuilderClient.getHeader(SLOT, PUB_KEY, PARENT_HASH))
        .succeedsWithin(WAIT_FOR_CALL_COMPLETION)
        .satisfies(
            response -> {
              assertThat(response.isFailure()).isTrue();
              assertThat(response.getErrorMessage()).isEqualTo(missingParentHashError);
            });

    verifyGetRequest(
        "/eth/v1/builder/header/1/" + PARENT_HASH + "/" + PUB_KEY, USER_AGENT_HEADER_ASSERTION);

    mockWebServer.enqueue(
        new MockResponse().setResponseCode(500).setBody(INTERNAL_SERVER_ERROR_MESSAGE));

    assertThat(restBuilderClient.getHeader(SLOT, PUB_KEY, PARENT_HASH))
        .succeedsWithin(WAIT_FOR_CALL_COMPLETION)
        .satisfies(
            response -> {
              assertThat(response.isFailure()).isTrue();
              assertThat(response.getErrorMessage()).isEqualTo(INTERNAL_SERVER_ERROR_MESSAGE);
            });

    verifyGetRequest(
        "/eth/v1/builder/header/1/" + PARENT_HASH + "/" + PUB_KEY, USER_AGENT_HEADER_ASSERTION);
  }

  @TestTemplate
  void getHeader_wrongFork(final SpecContext specContext) {
    specContext.assumeCapellaActive();

    final String milestoneFolder =
        "builder/" + milestone.getPreviousMilestone().toString().toLowerCase(Locale.ROOT);

    signedBuilderBidResponse = readResource(milestoneFolder + "/signedBuilderBid.json");

    mockWebServer.enqueue(
        new MockResponse().setResponseCode(200).setBody(signedBuilderBidResponse));

    assertThat(restBuilderClient.getHeader(SLOT, PUB_KEY, PARENT_HASH))
        .failsWithin(WAIT_FOR_CALL_COMPLETION)
        .withThrowableOfType(ExecutionException.class)
        .withRootCauseInstanceOf(MissingRequiredFieldException.class)
        .withMessageMatching(".+required fields: (.+) were not set");

    verifyGetRequest(
        "/eth/v1/builder/header/1/" + PARENT_HASH + "/" + PUB_KEY, USER_AGENT_HEADER_ASSERTION);
  }

  @TestTemplate
  void getHeader_wrongVersion(final SpecContext specContext) {
    specContext.assumeCapellaActive();

    signedBuilderBidResponse =
        changeResponseVersion(signedBuilderBidResponse, milestone.getPreviousMilestone());

    mockWebServer.enqueue(
        new MockResponse().setResponseCode(200).setBody(signedBuilderBidResponse));

    assertThat(restBuilderClient.getHeader(SLOT, PUB_KEY, PARENT_HASH))
        .failsWithin(WAIT_FOR_CALL_COMPLETION)
        .withThrowableOfType(ExecutionException.class)
        .withRootCauseInstanceOf(IllegalArgumentException.class)
        .withMessageContaining(
            "java.lang.IllegalArgumentException: Wrong response version: expected %s, received %s",
            milestone, milestone.getPreviousMilestone());

    verifyGetRequest(
        "/eth/v1/builder/header/1/" + PARENT_HASH + "/" + PUB_KEY, USER_AGENT_HEADER_ASSERTION);
  }

  @TestTemplate
  void getPayload_success() {

    mockWebServer.enqueue(
        new MockResponse().setResponseCode(200).setBody(unblindedBuilderPayloadResponse));

    final SignedBlindedBlockContainer signedBlindedBlockContainer =
        createSignedBlindedBlockContainer();

    assertThat(restBuilderClient.getPayload(signedBlindedBlockContainer))
        .succeedsWithin(WAIT_FOR_CALL_COMPLETION)
        .satisfies(
            response -> {
              assertThat(response.isSuccess()).isTrue();
              final BuilderPayload builderPayload = response.getPayload();
              verifyBuilderPayloadResponse(builderPayload);
            });

    verifyPostRequest("/eth/v1/builder/blinded_blocks", signedBlindedBlockContainerRequest);
  }

  @TestTemplate
  void getPayload_failures() {

    String missingSignatureError =
        "{\"code\":400,\"message\":\"Invalid block: missing signature\"}";
    mockWebServer.enqueue(new MockResponse().setResponseCode(400).setBody(missingSignatureError));

    final SignedBlindedBlockContainer signedBlindedBlockContainer =
        createSignedBlindedBlockContainer();

    assertThat(restBuilderClient.getPayload(signedBlindedBlockContainer))
        .succeedsWithin(WAIT_FOR_CALL_COMPLETION)
        .satisfies(
            response -> {
              assertThat(response.isFailure()).isTrue();
              assertThat(response.getErrorMessage()).isEqualTo(missingSignatureError);
            });

    verifyPostRequest("/eth/v1/builder/blinded_blocks", signedBlindedBlockContainerRequest);

    mockWebServer.enqueue(
        new MockResponse().setResponseCode(500).setBody(INTERNAL_SERVER_ERROR_MESSAGE));

    assertThat(restBuilderClient.getPayload(signedBlindedBlockContainer))
        .succeedsWithin(WAIT_FOR_CALL_COMPLETION)
        .satisfies(
            response -> {
              assertThat(response.isFailure()).isTrue();
              assertThat(response.getErrorMessage()).isEqualTo(INTERNAL_SERVER_ERROR_MESSAGE);
            });

    verifyPostRequest("/eth/v1/builder/blinded_blocks", signedBlindedBlockContainerRequest);
  }

  @TestTemplate
  void getPayload_wrongVersion(final SpecContext specContext) {
    specContext.assumeCapellaActive();

    unblindedBuilderPayloadResponse =
        changeResponseVersion(unblindedBuilderPayloadResponse, milestone.getPreviousMilestone());

    mockWebServer.enqueue(
        new MockResponse().setResponseCode(200).setBody(unblindedBuilderPayloadResponse));

    final SignedBlindedBlockContainer signedBlindedBlockContainer =
        createSignedBlindedBlockContainer();

    assertThat(restBuilderClient.getPayload(signedBlindedBlockContainer))
        .succeedsWithin(WAIT_FOR_CALL_COMPLETION)
        .satisfies(
            response -> {
              assertThat(response.isSuccess()).isTrue();
              final BuilderPayload builderPayload = response.getPayload();
              verifyBuilderPayloadResponse(builderPayload);
            });

    verifyPostRequest("/eth/v1/builder/blinded_blocks", signedBlindedBlockContainerRequest);
  }

  private void verifyGetRequest(final String apiPath) {
    verifyRequest("GET", apiPath, Optional.empty());
  }

  private void verifyGetRequest(
      final String apiPath, final Consumer<RecordedRequest> additionalRequestAssertions) {
    verifyRequest("GET", apiPath, Optional.empty(), Optional.of(additionalRequestAssertions));
  }

  private void verifyPostRequest(final String apiPath, final String requestBody) {
    verifyRequest("POST", apiPath, Optional.of(requestBody));
  }

  private void verifyRequest(
      final String method, final String apiPath, final Optional<String> expectedRequestBody) {
    verifyRequest(method, apiPath, expectedRequestBody, Optional.empty());
  }

  private void verifyRequest(
      final String method,
      final String apiPath,
      final Optional<String> expectedRequestBody,
      final Optional<Consumer<RecordedRequest>> additionalRequestAssertions) {
    try {
      final RecordedRequest request = mockWebServer.takeRequest();
      assertThat(request.getMethod()).isEqualTo(method);
      assertThat(request.getPath()).isEqualTo(apiPath);
      final Buffer actualRequestBody = request.getBody();
      if (expectedRequestBody.isEmpty()) {
        assertThat(actualRequestBody.size()).isZero();
      } else {
        assertThat(actualRequestBody.size()).isNotZero();
        assertThat(OBJECT_MAPPER.readTree(expectedRequestBody.get()))
            .isEqualTo(OBJECT_MAPPER.readTree(actualRequestBody.readUtf8()));
      }
      additionalRequestAssertions.ifPresent(assertions -> assertions.accept(request));
    } catch (final InterruptedException | JsonProcessingException ex) {
      Assertions.fail(ex);
    }
  }

  private SszList<SignedValidatorRegistration> createSignedValidatorRegistrations() {
    try {
      return JsonUtil.parse(
          signedValidatorRegistrationsRequest,
          SIGNED_VALIDATOR_REGISTRATIONS_SCHEMA.getJsonTypeDefinition());
    } catch (JsonProcessingException ex) {
      throw new UncheckedIOException(ex);
    }
  }

  private void verifySignedBuilderBidResponse(final SignedBuilderBid actual) {
    final DeserializableTypeDefinition<BuilderApiResponse<SignedBuilderBid>>
        responseTypeDefinition =
            BuilderApiResponse.createTypeDefinition(
                schemaDefinitions.getSignedBuilderBidSchema().getJsonTypeDefinition());
    try {
      final SignedBuilderBid expected =
          JsonUtil.parse(signedBuilderBidResponse, responseTypeDefinition).getData();
      assertThat(actual).isEqualTo(expected);
    } catch (final JsonProcessingException ex) {
      Assertions.fail(ex);
    }
  }

  private SignedBlindedBlockContainer createSignedBlindedBlockContainer() {
    try {
      return JsonUtil.parse(
              signedBlindedBlockContainerRequest,
              schemaDefinitions.getSignedBlindedBlockContainerSchema().getJsonTypeDefinition())
          .toBlinded()
          .orElseThrow();
    } catch (final JsonProcessingException ex) {
      throw new UncheckedIOException(ex);
    }
  }

  private void verifyBuilderPayloadResponse(final BuilderPayload actual) {
    final DeserializableTypeDefinition<? extends BuilderApiResponse<? extends BuilderPayload>>
        responseTypeDefinition =
            BuilderApiResponse.createTypeDefinition(
                schemaDefinitions.getBuilderPayloadSchema().getJsonTypeDefinition());
    try {
      final BuilderPayload expected =
          JsonUtil.parse(unblindedBuilderPayloadResponse, responseTypeDefinition).getData();
      assertThat(actual).isEqualTo(expected);
    } catch (final JsonProcessingException ex) {
      Assertions.fail(ex);
    }
  }

  private static String readResource(final String resource) {
    try {
      return Resources.toString(Resources.getResource(resource), StandardCharsets.UTF_8);
    } catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }
  }

  private static String changeResponseVersion(final String json, final SpecMilestone newVersion) {
    return json.replaceFirst(
        "(?<=version\":\\s?\")\\w+", newVersion.toString().toLowerCase(Locale.ROOT));
  }
}
