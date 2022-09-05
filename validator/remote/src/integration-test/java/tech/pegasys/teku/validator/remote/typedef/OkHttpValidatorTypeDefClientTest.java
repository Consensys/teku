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

package tech.pegasys.teku.validator.remote.typedef;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.api.exceptions.RemoteServiceNotAvailableException;
import tech.pegasys.teku.api.response.v2.validator.GetNewBlockResponseV2;
import tech.pegasys.teku.api.schema.bellatrix.BeaconBlockBellatrix;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.ssz.SszDataAssert;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.provider.JsonProvider;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.builder.SignedValidatorRegistration;
import tech.pegasys.teku.spec.networks.Eth2Network;
import tech.pegasys.teku.spec.schemas.ApiSchemas;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.api.SendSignedBlockResult;
import tech.pegasys.teku.validator.api.required.SyncingStatus;
import tech.pegasys.teku.validator.remote.typedef.handlers.RegisterValidatorsRequest;

@TestSpecContext(
    milestone = SpecMilestone.BELLATRIX,
    network = {Eth2Network.MAINNET})
class OkHttpValidatorTypeDefClientTest {

  private static final String JSON_CONTENT_TYPE = "application/json; charset=utf-8";
  private static final String OCTET_STREAM_CONTENT_TYPE = "application/octet-stream";

  private static final JsonProvider JSON_PROVIDER = new JsonProvider();

  private DataStructureUtil dataStructureUtil;
  private Spec spec;
  private SpecMilestone specMilestone;

  private OkHttpValidatorTypeDefClient okHttpValidatorTypeDefClient;
  private OkHttpValidatorTypeDefClient okHttpValidatorTypeDefClientWithPreferredSsz;

  private RegisterValidatorsRequest sszRegisterValidatorsRequest;

  private final MockWebServer mockWebServer = new MockWebServer();
  private final OkHttpClient okHttpClient = new OkHttpClient.Builder().build();

  @BeforeEach
  public void beforeEach(SpecContext specContext) throws Exception {
    mockWebServer.start();
    okHttpValidatorTypeDefClient =
        new OkHttpValidatorTypeDefClient(
            okHttpClient, mockWebServer.url("/"), specContext.getSpec(), false);
    okHttpValidatorTypeDefClientWithPreferredSsz =
        new OkHttpValidatorTypeDefClient(
            okHttpClient, mockWebServer.url("/"), specContext.getSpec(), true);
    sszRegisterValidatorsRequest =
        new RegisterValidatorsRequest(mockWebServer.url("/"), okHttpClient, true);
    dataStructureUtil = specContext.getDataStructureUtil();
    spec = specContext.getSpec();
    specMilestone = specContext.getSpecMilestone();
  }

  @AfterEach
  public void afterEach() throws Exception {
    mockWebServer.shutdown();
  }

  @TestTemplate
  void blockProductionFallbacksToNonBlindedFlowIfBlindedEndpointIsNotAvailable()
      throws JsonProcessingException, InterruptedException {
    mockWebServer.enqueue(new MockResponse().setResponseCode(404));

    final BeaconBlock beaconBlock = dataStructureUtil.randomBeaconBlock(UInt64.ONE);
    final GetNewBlockResponseV2 beaconNodeResponse =
        new GetNewBlockResponseV2(specMilestone, new BeaconBlockBellatrix(beaconBlock));

    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .setBody(JSON_PROVIDER.objectToJSON(beaconNodeResponse)));

    final Optional<BeaconBlock> producedBlock =
        okHttpValidatorTypeDefClient.createUnsignedBlock(
            dataStructureUtil.randomUInt64(),
            dataStructureUtil.randomSignature(),
            Optional.empty(),
            true);

    assertThat(producedBlock).hasValue(beaconBlock);

    assertThat(mockWebServer.getRequestCount()).isEqualTo(2);

    final RecordedRequest firstRequest = mockWebServer.takeRequest();
    assertThat(firstRequest.getPath()).startsWith("/eth/v1/validator/blinded_blocks");
    final RecordedRequest secondRequest = mockWebServer.takeRequest();
    assertThat(secondRequest.getPath()).startsWith("/eth/v2/validator/blocks");
  }

  @TestTemplate
  void publishesBlindedBlockSszEncoded() throws InterruptedException {
    mockWebServer.enqueue(new MockResponse().setResponseCode(200));

    final SignedBeaconBlock signedBeaconBlock = dataStructureUtil.randomSignedBlindedBeaconBlock();

    final SendSignedBlockResult result =
        okHttpValidatorTypeDefClientWithPreferredSsz.sendSignedBlock(signedBeaconBlock);

    assertThat(result.isPublished()).isTrue();

    final RecordedRequest recordedRequest = mockWebServer.takeRequest();
    assertThat(recordedRequest.getBody().readByteArray())
        .isEqualTo(signedBeaconBlock.sszSerialize().toArrayUnsafe());
  }

  @TestTemplate
  void publishesBlindedBlockJsonEncoded() throws InterruptedException, JsonProcessingException {
    mockWebServer.enqueue(new MockResponse().setResponseCode(200));

    final SignedBeaconBlock signedBeaconBlock =
        dataStructureUtil.randomSignedBlindedBeaconBlock(UInt64.ONE);

    final SendSignedBlockResult result =
        okHttpValidatorTypeDefClient.sendSignedBlock(signedBeaconBlock);

    assertThat(result.isPublished()).isTrue();

    final RecordedRequest recordedRequest = mockWebServer.takeRequest();

    final String expectedRequest =
        JsonUtil.serialize(
            signedBeaconBlock,
            spec.atSlot(UInt64.ONE)
                .getSchemaDefinitions()
                .getSignedBlindedBeaconBlockSchema()
                .getJsonTypeDefinition());

    String actualRequest = recordedRequest.getBody().readUtf8();

    assertJsonEquals(actualRequest, expectedRequest);
  }

  @TestTemplate
  void getsSyncingStatus() {
    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .setBody(
                "{\n"
                    + "  \"data\": {\n"
                    + "    \"head_slot\": \"1\",\n"
                    + "    \"sync_distance\": \"1\",\n"
                    + "    \"is_syncing\": true,\n"
                    + "    \"is_optimistic\": true\n"
                    + "  }\n"
                    + "}"));

    final SyncingStatus result = okHttpValidatorTypeDefClient.getSyncingStatus();

    assertThat(result)
        .satisfies(
            syncingStatus -> {
              assertThat(syncingStatus.getHeadSlot()).isEqualTo(UInt64.ONE);
              assertThat(syncingStatus.getSyncDistance()).isEqualTo(UInt64.ONE);
              assertThat(syncingStatus.isSyncing()).isTrue();
              assertThat(syncingStatus.getIsOptimistic()).hasValue(true);
            });
  }

  @TestTemplate
  void getsSyncingStatus_handlesFailure() {
    mockWebServer.enqueue(new MockResponse().setResponseCode(500));

    Assertions.assertThrows(
        RemoteServiceNotAvailableException.class,
        () -> okHttpValidatorTypeDefClient.getSyncingStatus());
  }

  @TestTemplate
  void registerValidators_makesJsonRequest() throws InterruptedException, JsonProcessingException {

    mockWebServer.enqueue(new MockResponse().setResponseCode(200));

    SszList<SignedValidatorRegistration> validatorRegistrations =
        dataStructureUtil.randomSignedValidatorRegistrations(5);

    String expectedRequest =
        JsonUtil.serialize(
            validatorRegistrations,
            ApiSchemas.SIGNED_VALIDATOR_REGISTRATIONS_SCHEMA.getJsonTypeDefinition());

    okHttpValidatorTypeDefClient.registerValidators(validatorRegistrations);

    RecordedRequest recordedRequest = mockWebServer.takeRequest();

    verifyRegisterValidatorsPostRequest(recordedRequest, JSON_CONTENT_TYPE);

    String actualRequest = recordedRequest.getBody().readUtf8();

    assertJsonEquals(actualRequest, expectedRequest);
  }

  @TestTemplate
  void registerValidators_handlesFailures() {
    mockWebServer.enqueue(
        new MockResponse().setResponseCode(400).setBody("{\"code\":400,\"message\":\"oopsy\"}"));

    SszList<SignedValidatorRegistration> validatorRegistrations =
        dataStructureUtil.randomSignedValidatorRegistrations(5);

    IllegalArgumentException badRequestException =
        Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> okHttpValidatorTypeDefClient.registerValidators(validatorRegistrations));

    assertThat(badRequestException.getMessage())
        .matches(
            "Invalid params response from Beacon Node API \\(url = (.*), status = 400, message = oopsy\\)");

    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(500)
            .setBody("{\"code\":500,\"message\":\"Internal server error\"}"));

    RemoteServiceNotAvailableException serverException =
        Assertions.assertThrows(
            RemoteServiceNotAvailableException.class,
            () -> okHttpValidatorTypeDefClient.registerValidators(validatorRegistrations));

    assertThat(serverException.getMessage())
        .matches(
            "Server error from Beacon Node API \\(url = (.*), status = 500, message = Internal server error\\)");
  }

  @TestTemplate
  void registerValidators_makesSszRequestIfSszEncodingPreferred() throws InterruptedException {

    mockWebServer.enqueue(new MockResponse().setResponseCode(200));

    SszList<SignedValidatorRegistration> validatorRegistrations =
        dataStructureUtil.randomSignedValidatorRegistrations(5);

    sszRegisterValidatorsRequest.registerValidators(validatorRegistrations);

    RecordedRequest recordedRequest = mockWebServer.takeRequest();

    verifyRegisterValidatorsPostRequest(recordedRequest, OCTET_STREAM_CONTENT_TYPE);

    byte[] sszBytes = recordedRequest.getBody().readByteArray();

    SszList<SignedValidatorRegistration> deserializedSszBytes =
        ApiSchemas.SIGNED_VALIDATOR_REGISTRATIONS_SCHEMA.sszDeserialize(Bytes.of(sszBytes));

    SszDataAssert.assertThatSszData(deserializedSszBytes)
        .isEqualByAllMeansTo(validatorRegistrations);
  }

  @TestTemplate
  void registerValidators_fallbacksToJsonIfSszNotSupported() throws InterruptedException {

    mockWebServer.enqueue(new MockResponse().setResponseCode(415));
    mockWebServer.enqueue(new MockResponse().setResponseCode(200));
    mockWebServer.enqueue(new MockResponse().setResponseCode(200));

    SszList<SignedValidatorRegistration> validatorRegistrations =
        dataStructureUtil.randomSignedValidatorRegistrations(5);

    sszRegisterValidatorsRequest.registerValidators(validatorRegistrations);

    assertThat(mockWebServer.getRequestCount()).isEqualTo(2);

    verifyRegisterValidatorsPostRequest(mockWebServer.takeRequest(), OCTET_STREAM_CONTENT_TYPE);
    verifyRegisterValidatorsPostRequest(mockWebServer.takeRequest(), JSON_CONTENT_TYPE);

    // subsequent requests default immediately to json
    sszRegisterValidatorsRequest.registerValidators(validatorRegistrations);

    assertThat(mockWebServer.getRequestCount()).isEqualTo(3);

    verifyRegisterValidatorsPostRequest(mockWebServer.takeRequest(), JSON_CONTENT_TYPE);
  }

  private void verifyRegisterValidatorsPostRequest(
      RecordedRequest recordedRequest, String expectedContentType) {
    assertThat(recordedRequest.getPath()).isEqualTo("/eth/v1/validator/register_validator");
    assertThat(recordedRequest.getMethod()).isEqualTo("POST");
    assertThat(recordedRequest.getHeader("Content-Type")).isEqualTo(expectedContentType);
  }

  private void assertJsonEquals(String actual, String expected) {
    try {
      final ObjectMapper objectMapper = JSON_PROVIDER.getObjectMapper();
      assertThat(objectMapper.readTree(actual)).isEqualTo(objectMapper.readTree(expected));
    } catch (JsonProcessingException ex) {
      Assertions.fail(ex);
    }
  }
}
