/*
 * Copyright Consensys Software Inc., 2022
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

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assumptions.assumeThat;
import static tech.pegasys.teku.ethereum.json.types.beacon.StateValidatorDataBuilder.STATE_VALIDATORS_RESPONSE_TYPE;
import static tech.pegasys.teku.ethereum.json.types.validator.AttesterDutiesBuilder.ATTESTER_DUTIES_RESPONSE_TYPE;
import static tech.pegasys.teku.ethereum.json.types.validator.SyncCommitteeDutiesBuilder.SYNC_COMMITTEE_DUTIES_TYPE;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_METHOD_NOT_ALLOWED;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NO_CONTENT;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_CONSENSUS_VERSION;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.PARAM_BROADCAST_VALIDATION;
import static tech.pegasys.teku.infrastructure.json.JsonUtil.serialize;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.spec.config.SpecConfig.FAR_FUTURE_EPOCH;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.api.exceptions.RemoteServiceNotAvailableException;
import tech.pegasys.teku.api.response.v1.beacon.ValidatorStatus;
import tech.pegasys.teku.ethereum.json.types.beacon.StateValidatorData;
import tech.pegasys.teku.ethereum.json.types.validator.AttesterDuties;
import tech.pegasys.teku.ethereum.json.types.validator.AttesterDuty;
import tech.pegasys.teku.ethereum.json.types.validator.SyncCommitteeDuties;
import tech.pegasys.teku.ethereum.json.types.validator.SyncCommitteeDuty;
import tech.pegasys.teku.ethereum.json.types.validator.SyncCommitteeSubnetSubscription;
import tech.pegasys.teku.infrastructure.ssz.SszDataAssert;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.datastructures.blocks.BlockContainer;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.builder.SignedValidatorRegistration;
import tech.pegasys.teku.spec.datastructures.metadata.BlockContainerAndMetaData;
import tech.pegasys.teku.spec.datastructures.metadata.ObjectAndMetaData;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.validator.BroadcastValidationLevel;
import tech.pegasys.teku.spec.networks.Eth2Network;
import tech.pegasys.teku.spec.schemas.ApiSchemas;
import tech.pegasys.teku.validator.api.SendSignedBlockResult;
import tech.pegasys.teku.validator.api.required.SyncingStatus;
import tech.pegasys.teku.validator.remote.apiclient.PostStateValidatorsNotExistingException;
import tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod;
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
      blockContainer = dataStructureUtil.randomBlockContents(ONE);
    } else {
      blockContainer = dataStructureUtil.randomBeaconBlock(ONE);
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

    final Optional<BlockContainerAndMetaData> maybeBlockContainerAndMetaData =
        okHttpValidatorTypeDefClient.createUnsignedBlock(
            dataStructureUtil.randomUInt64(),
            dataStructureUtil.randomSignature(),
            Optional.empty(),
            true);

    assertThat(maybeBlockContainerAndMetaData.map(BlockContainerAndMetaData::blockContainer))
        .hasValue(blockContainer);

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
        okHttpValidatorTypeDefClientWithPreferredSsz.sendSignedBlock(
            signedBeaconBlock, BroadcastValidationLevel.GOSSIP);

    assertThat(result.isPublished()).isTrue();

    final RecordedRequest recordedRequest = mockWebServer.takeRequest();
    assertThat(recordedRequest.getBody().readByteArray())
        .isEqualTo(signedBeaconBlock.sszSerialize().toArrayUnsafe());
    assertThat(recordedRequest.getRequestUrl().queryParameter(PARAM_BROADCAST_VALIDATION))
        .isEqualTo("gossip");
    assertThat(recordedRequest.getHeader(HEADER_CONSENSUS_VERSION))
        .isEqualTo(specMilestone.name().toLowerCase(Locale.ROOT));
  }

  @TestTemplate
  void publishesBlindedBlockJsonEncoded() throws InterruptedException, JsonProcessingException {
    mockWebServer.enqueue(new MockResponse().setResponseCode(200));

    final SignedBeaconBlock signedBeaconBlock = dataStructureUtil.randomSignedBlindedBeaconBlock();

    final SendSignedBlockResult result =
        okHttpValidatorTypeDefClient.sendSignedBlock(
            signedBeaconBlock, BroadcastValidationLevel.GOSSIP);

    assertThat(result.isPublished()).isTrue();

    final RecordedRequest recordedRequest = mockWebServer.takeRequest();

    final String expectedRequest =
        serialize(
            signedBeaconBlock,
            spec.atSlot(ONE)
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
              assertThat(syncingStatus.getHeadSlot()).isEqualTo(ONE);
              assertThat(syncingStatus.getSyncDistance()).isEqualTo(ONE);
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
        serialize(
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

  @TestTemplate
  void blockV3ShouldFallbacksToBlockV2WhenNotFound()
      throws JsonProcessingException, InterruptedException {
    mockWebServer.enqueue(new MockResponse().setResponseCode(404));

    final BlockContainer blockContainer = dataStructureUtil.randomBlindedBeaconBlock();

    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .setBody(
                "{\"data\": "
                    + serializeBlockContainer(blockContainer)
                    + ", \"version\": \""
                    + specMilestone
                    + "\"}"));

    final Optional<BlockContainerAndMetaData> maybeBlockContainerAndMetaData =
        okHttpValidatorTypeDefClient.createUnsignedBlock(
            dataStructureUtil.randomUInt64(),
            dataStructureUtil.randomSignature(),
            Optional.empty(),
            Optional.empty());

    assertThat(maybeBlockContainerAndMetaData.map(BlockContainerAndMetaData::blockContainer))
        .hasValue(blockContainer);

    assertThat(mockWebServer.getRequestCount()).isEqualTo(2);

    final RecordedRequest firstRequest = mockWebServer.takeRequest();
    assertThat(firstRequest.getPath()).startsWith("/eth/v3/validator/blocks");
    final RecordedRequest secondRequest = mockWebServer.takeRequest();
    assertThat(secondRequest.getPath()).startsWith("/eth/v1/validator/blinded_blocks");
  }

  @TestTemplate
  void postValidators_makesExpectedRequest() throws Exception {
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_NO_CONTENT));

    okHttpValidatorTypeDefClient.postStateValidators(List.of("1", "0x1234"));

    final RecordedRequest request = mockWebServer.takeRequest();
    assertThat(request.getMethod()).isEqualTo("POST");

    assertThat(request.getPath()).contains(ValidatorApiMethod.GET_VALIDATORS.getPath(emptyMap()));
    assertThat(request.getBody().readUtf8()).isEqualTo("{\"ids\":[\"1\",\"0x1234\"]}");
  }

  @TestTemplate
  void getStateValidators_makesExpectedRequest() throws Exception {
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_NO_CONTENT));

    okHttpValidatorTypeDefClient.getStateValidators(List.of("1", "0x1234"));

    final RecordedRequest request = mockWebServer.takeRequest();
    assertThat(request.getMethod()).isEqualTo("GET");

    assertThat(request.getPath()).contains(ValidatorApiMethod.GET_VALIDATORS.getPath(emptyMap()));
    // comma-separated GET query array parameters shouldn't be encoded
    // and must pass AS IS as per RFC-3986
    assertThat(request.getPath()).contains("?id=1,0x1234");
  }

  @TestTemplate
  public void postValidators_whenNoContent_returnsEmpty() {
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_NO_CONTENT));

    assertThat(okHttpValidatorTypeDefClient.postStateValidators(List.of("1"))).isEmpty();
  }

  @TestTemplate
  public void postValidators_whenNotExisting_throwsException() {
    final List<Integer> responseCodes =
        List.of(SC_BAD_REQUEST, SC_NOT_FOUND, SC_METHOD_NOT_ALLOWED);
    for (int code : responseCodes) {
      checkThrowsExceptionForCode(code);
    }
  }

  private void checkThrowsExceptionForCode(final int responseCode) {
    mockWebServer.enqueue(new MockResponse().setResponseCode(responseCode));
    assertThatThrownBy(() -> okHttpValidatorTypeDefClient.postStateValidators(List.of("1")))
        .isInstanceOf(PostStateValidatorsNotExistingException.class);
  }

  @TestTemplate
  public void postValidators_whenSuccess_returnsResponse() throws JsonProcessingException {
    final List<StateValidatorData> expected =
        List.of(generateStateValidatorData(), generateStateValidatorData());
    final ObjectAndMetaData<List<StateValidatorData>> response =
        new ObjectAndMetaData<>(expected, specMilestone, false, true, false);

    final String body = serialize(response, STATE_VALIDATORS_RESPONSE_TYPE);
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_OK).setBody(body));

    Optional<List<StateValidatorData>> result =
        okHttpValidatorTypeDefClient.postStateValidators(List.of("1", "2"));

    assertThat(result).isPresent();
    assertThat(result.get()).isEqualTo(expected);
  }

  private StateValidatorData generateStateValidatorData() {
    final long index = dataStructureUtil.randomLong();
    final Validator validator =
        new Validator(
            dataStructureUtil.randomPublicKey(),
            dataStructureUtil.randomBytes32(),
            dataStructureUtil.randomUInt64(),
            false,
            UInt64.ZERO,
            UInt64.ZERO,
            FAR_FUTURE_EPOCH,
            FAR_FUTURE_EPOCH);
    return new StateValidatorData(
        UInt64.valueOf(index),
        dataStructureUtil.randomUInt64(),
        ValidatorStatus.active_ongoing,
        validator);
  }

  @TestTemplate
  public void postSyncDuties_whenSuccess_returnsResponse()
      throws JsonProcessingException, InterruptedException {
    final List<SyncCommitteeDuty> duties =
        List.of(
            new SyncCommitteeDuty(
                dataStructureUtil.randomPublicKey(),
                dataStructureUtil.randomValidatorIndex().intValue(),
                IntSet.of(1, 2)));
    final SyncCommitteeDuties response = new SyncCommitteeDuties(true, duties);

    final String body = serialize(response, SYNC_COMMITTEE_DUTIES_TYPE);
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_OK).setBody(body));

    final UInt64 epoch = ONE;
    final IntList validatorIndices = IntList.of(1, 2);
    Optional<SyncCommitteeDuties> result =
        okHttpValidatorTypeDefClient.postSyncDuties(epoch, validatorIndices);

    final RecordedRequest recordedRequest = mockWebServer.takeRequest();
    assertThat(recordedRequest.getPath()).isEqualTo("/eth/v1/validator/duties/sync/" + epoch);
    assertThat(recordedRequest.getMethod()).isEqualTo("POST");
    assertThat(recordedRequest.getHeader("Content-Type")).isEqualTo(JSON_CONTENT_TYPE);
    assertThat(recordedRequest.getBody().readByteArray())
        .isEqualTo("[\"1\",\"2\"]".getBytes(UTF_8));

    assertThat(result).isPresent();
    assertThat(result.get()).isEqualTo(response);
  }

  @TestTemplate
  public void postAttesterDuties_whenSuccess_returnsResponse()
      throws JsonProcessingException, InterruptedException {
    final List<AttesterDuty> duties = List.of(randomAttesterDuty(), randomAttesterDuty());
    final AttesterDuties response =
        new AttesterDuties(true, dataStructureUtil.randomBytes32(), duties);

    final String body = serialize(response, ATTESTER_DUTIES_RESPONSE_TYPE);
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_OK).setBody(body));

    final UInt64 epoch = ONE;
    final IntList validatorIndices = IntList.of(1, 2);
    Optional<AttesterDuties> result =
        okHttpValidatorTypeDefClient.postAttesterDuties(epoch, validatorIndices);

    final RecordedRequest recordedRequest = mockWebServer.takeRequest();
    assertThat(recordedRequest.getPath()).isEqualTo("/eth/v1/validator/duties/attester/" + epoch);
    assertThat(recordedRequest.getMethod()).isEqualTo("POST");
    assertThat(recordedRequest.getHeader("Content-Type")).isEqualTo(JSON_CONTENT_TYPE);
    assertThat(recordedRequest.getBody().readByteArray())
        .isEqualTo("[\"1\",\"2\"]".getBytes(UTF_8));

    assertThat(result).isPresent();
    assertThat(result.get()).isEqualTo(response);
  }

  @TestTemplate
  public void postSubscribeToSyncCommitteeSubnets_makesExpectedRequest()
      throws InterruptedException {
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_NO_CONTENT));

    final Collection<SyncCommitteeSubnetSubscription> subscriptions =
        List.of(new SyncCommitteeSubnetSubscription(0, IntSet.of(1), UInt64.ZERO));

    okHttpValidatorTypeDefClient.subscribeToSyncCommitteeSubnets(subscriptions);
    final RecordedRequest recordedRequest = mockWebServer.takeRequest();
    assertThat(recordedRequest.getPath())
        .isEqualTo("/eth/v1/validator/sync_committee_subscriptions");
    assertThat(recordedRequest.getMethod()).isEqualTo("POST");
    assertThat(recordedRequest.getHeader("Content-Type")).isEqualTo(JSON_CONTENT_TYPE);
    assertThat(recordedRequest.getBody().readUtf8())
        .isEqualTo(
            "[{\"validator_index\":\"0\",\"sync_committee_indices\":[\"1\"],\"until_epoch\":\"0\"}]");
  }

  private AttesterDuty randomAttesterDuty() {
    return new AttesterDuty(
        dataStructureUtil.randomPublicKey(),
        dataStructureUtil.randomValidatorIndex().intValue(),
        dataStructureUtil.randomPositiveInt(),
        dataStructureUtil.randomPositiveInt(),
        dataStructureUtil.randomPositiveInt(),
        dataStructureUtil.randomPositiveInt(),
        dataStructureUtil.randomSlot());
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
    return serialize(
        blockContainer,
        blockContainer.isBlinded()
            ? schemaDefinitions.getBlindedBlockContainerSchema().getJsonTypeDefinition()
            : schemaDefinitions.getBlockContainerSchema().getJsonTypeDefinition());
  }
}
