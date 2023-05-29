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
import static org.assertj.core.api.Assumptions.assumeThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.api.exceptions.RemoteServiceNotAvailableException;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.ssz.SszDataAssert;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.datastructures.blocks.BlockContainer;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockContainer;
import tech.pegasys.teku.spec.datastructures.builder.SignedValidatorRegistration;
import tech.pegasys.teku.spec.networks.Eth2Network;
import tech.pegasys.teku.spec.schemas.ApiSchemas;
import tech.pegasys.teku.validator.api.SendSignedBlockResult;
import tech.pegasys.teku.validator.api.required.SyncingStatus;
import tech.pegasys.teku.validator.remote.typedef.handlers.RegisterValidatorsRequest;

@TestSpecContext(allMilestones = true, network = Eth2Network.MINIMAL)
class OkHttpValidatorTypeDefClientTest extends AbstractTypeDefRequestTestBase {

  private OkHttpValidatorTypeDefClient okHttpValidatorTypeDefClient;
  private OkHttpValidatorTypeDefClient okHttpValidatorTypeDefClientWithPreferredSsz;
  private RegisterValidatorsRequest sszRegisterValidatorsRequest;

  @BeforeEach
  @Override
  public void beforeEach(final SpecContext specContext) throws Exception {
    super.beforeEach(specContext);
    okHttpValidatorTypeDefClient =
        new OkHttpValidatorTypeDefClient(
            okHttpClient, mockWebServer.url("/"), specContext.getSpec(), false);
    okHttpValidatorTypeDefClientWithPreferredSsz =
        new OkHttpValidatorTypeDefClient(
            okHttpClient, mockWebServer.url("/"), specContext.getSpec(), true);
    sszRegisterValidatorsRequest =
        new RegisterValidatorsRequest(mockWebServer.url("/"), okHttpClient, true);
  }

  @TestTemplate
  void blockProductionFallbacksToNonBlindedFlowIfBlindedEndpointIsNotAvailable()
      throws JsonProcessingException, InterruptedException {
    assumeThat(specMilestone).isGreaterThanOrEqualTo(SpecMilestone.BELLATRIX);
    // simulating blinded endpoint returning 404 Not Found
    mockWebServer.enqueue(new MockResponse().setResponseCode(404));

    final BlockContainer blockContainer;
    if (specMilestone.isGreaterThanOrEqualTo(SpecMilestone.DENEB)) {
      blockContainer = dataStructureUtil.randomBlockContents(UInt64.ONE);
    } else {
      blockContainer = dataStructureUtil.randomBeaconBlock(UInt64.ONE);
    }

    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .setBody(
                "{\"data\": "
                    + serializeBlockContainer(blockContainer)
                    + ", \"version\": \""
                    + specMilestone
                    + "\"}"));

    final Optional<BlockContainer> producedBlock =
        okHttpValidatorTypeDefClient.createUnsignedBlock(
            dataStructureUtil.randomUInt64(),
            dataStructureUtil.randomSignature(),
            Optional.empty(),
            true);

    assertThat(producedBlock).hasValue(blockContainer);

    assertThat(mockWebServer.getRequestCount()).isEqualTo(2);

    final RecordedRequest firstRequest = mockWebServer.takeRequest();
    assertThat(firstRequest.getPath()).startsWith("/eth/v1/validator/blinded_blocks");
    final RecordedRequest secondRequest = mockWebServer.takeRequest();
    assertThat(secondRequest.getPath()).startsWith("/eth/v2/validator/blocks");
  }

  @TestTemplate
  void publishesBlindedBlockSszEncoded() throws InterruptedException {
    mockWebServer.enqueue(new MockResponse().setResponseCode(200));

    final SignedBlockContainer signedBlockContainer;
    if (specMilestone.isGreaterThanOrEqualTo(SpecMilestone.DENEB)) {
      signedBlockContainer = dataStructureUtil.randomSignedBlindedBlockContents();
    } else {
      signedBlockContainer = dataStructureUtil.randomSignedBlindedBeaconBlock();
    }

    final SendSignedBlockResult result =
        okHttpValidatorTypeDefClientWithPreferredSsz.sendSignedBlock(signedBlockContainer);

    assertThat(result.isPublished()).isTrue();

    final RecordedRequest recordedRequest = mockWebServer.takeRequest();
    assertThat(recordedRequest.getBody().readByteArray())
        .isEqualTo(signedBlockContainer.sszSerialize().toArrayUnsafe());
  }

  @TestTemplate
  void publishesBlindedBlockJsonEncoded() throws InterruptedException, JsonProcessingException {
    mockWebServer.enqueue(new MockResponse().setResponseCode(200));

    final SignedBlockContainer signedBlockContainer;
    if (specMilestone.isGreaterThanOrEqualTo(SpecMilestone.DENEB)) {
      signedBlockContainer = dataStructureUtil.randomSignedBlindedBlockContents();
    } else {
      signedBlockContainer = dataStructureUtil.randomSignedBlindedBeaconBlock();
    }

    final SendSignedBlockResult result =
        okHttpValidatorTypeDefClient.sendSignedBlock(signedBlockContainer);

    assertThat(result.isPublished()).isTrue();

    final RecordedRequest recordedRequest = mockWebServer.takeRequest();

    final String expectedRequest =
        JsonUtil.serialize(
            signedBlockContainer,
            spec.atSlot(UInt64.ONE)
                .getSchemaDefinitions()
                .getSignedBlindedBlockContainerSchema()
                .getJsonTypeDefinition());

    final String actualRequest = recordedRequest.getBody().readUtf8();

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

    final SszList<SignedValidatorRegistration> validatorRegistrations =
        dataStructureUtil.randomSignedValidatorRegistrations(5);

    final String expectedRequest =
        JsonUtil.serialize(
            validatorRegistrations,
            ApiSchemas.SIGNED_VALIDATOR_REGISTRATIONS_SCHEMA.getJsonTypeDefinition());

    okHttpValidatorTypeDefClient.registerValidators(validatorRegistrations);

    final RecordedRequest recordedRequest = mockWebServer.takeRequest();

    verifyRegisterValidatorsPostRequest(recordedRequest, JSON_CONTENT_TYPE);

    final String actualRequest = recordedRequest.getBody().readUtf8();

    assertJsonEquals(actualRequest, expectedRequest);
  }

  @TestTemplate
  void registerValidators_handlesFailures() {
    mockWebServer.enqueue(
        new MockResponse().setResponseCode(400).setBody("{\"code\":400,\"message\":\"oopsy\"}"));

    final SszList<SignedValidatorRegistration> validatorRegistrations =
        dataStructureUtil.randomSignedValidatorRegistrations(5);

    final IllegalArgumentException badRequestException =
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

    final RemoteServiceNotAvailableException serverException =
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

    final SszList<SignedValidatorRegistration> validatorRegistrations =
        dataStructureUtil.randomSignedValidatorRegistrations(5);

    sszRegisterValidatorsRequest.registerValidators(validatorRegistrations);

    final RecordedRequest recordedRequest = mockWebServer.takeRequest();

    verifyRegisterValidatorsPostRequest(recordedRequest, OCTET_STREAM_CONTENT_TYPE);

    byte[] sszBytes = recordedRequest.getBody().readByteArray();

    final SszList<SignedValidatorRegistration> deserializedSszBytes =
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
      final RecordedRequest recordedRequest, final String expectedContentType) {
    assertThat(recordedRequest.getPath()).isEqualTo("/eth/v1/validator/register_validator");
    assertThat(recordedRequest.getMethod()).isEqualTo("POST");
    assertThat(recordedRequest.getHeader("Content-Type")).isEqualTo(expectedContentType);
  }

  private void assertJsonEquals(final String actual, final String expected) {
    try {
      final ObjectMapper objectMapper = JSON_PROVIDER.getObjectMapper();
      assertThat(objectMapper.readTree(actual)).isEqualTo(objectMapper.readTree(expected));
    } catch (JsonProcessingException ex) {
      Assertions.fail(ex);
    }
  }

  private String serializeBlockContainer(final BlockContainer blockContainer)
      throws JsonProcessingException {
    return JsonUtil.serialize(
        blockContainer, schemaDefinitions.getBlockContainerSchema().getJsonTypeDefinition());
  }
}
