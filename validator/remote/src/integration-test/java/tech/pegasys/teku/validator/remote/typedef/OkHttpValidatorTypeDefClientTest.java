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
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_METHOD_NOT_ALLOWED;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NO_CONTENT;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_CONSENSUS_VERSION;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.PARAM_BROADCAST_VALIDATION;
import static tech.pegasys.teku.infrastructure.json.JsonUtil.serialize;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.spec.SpecMilestone.ALTAIR;
import static tech.pegasys.teku.spec.SpecMilestone.BELLATRIX;
import static tech.pegasys.teku.spec.SpecMilestone.ELECTRA;
import static tech.pegasys.teku.spec.config.SpecConfig.FAR_FUTURE_EPOCH;

import com.fasterxml.jackson.core.JsonProcessingException;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
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
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.builder.SignedValidatorRegistration;
import tech.pegasys.teku.spec.datastructures.metadata.ObjectAndMetaData;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SignedContributionAndProof;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeContribution;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeContributionSchema;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.validator.BroadcastValidationLevel;
import tech.pegasys.teku.spec.datastructures.validator.SubnetSubscription;
import tech.pegasys.teku.spec.networks.Eth2Network;
import tech.pegasys.teku.spec.schemas.ApiSchemas;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsAltair;
import tech.pegasys.teku.validator.api.CommitteeSubscriptionRequest;
import tech.pegasys.teku.validator.api.SendSignedBlockResult;
import tech.pegasys.teku.validator.api.required.SyncingStatus;
import tech.pegasys.teku.validator.remote.apiclient.PostStateValidatorsNotExistingException;
import tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod;
import tech.pegasys.teku.validator.remote.typedef.handlers.RegisterValidatorsRequest;

@TestSpecContext(allMilestones = true, network = Eth2Network.MINIMAL)
class OkHttpValidatorTypeDefClientTest extends AbstractTypeDefRequestTestBase {

  final String serverErrorFromApi = "Server error from Beacon Node API";
  private OkHttpValidatorTypeDefClient typeDefClient;
  private OkHttpValidatorTypeDefClient okHttpValidatorTypeDefClientWithPreferredSsz;
  private RegisterValidatorsRequest sszRegisterValidatorsRequest;

  @BeforeEach
  @Override
  public void beforeEach(final SpecContext specContext) throws Exception {
    super.beforeEach(specContext);
    typeDefClient =
        new OkHttpValidatorTypeDefClient(
            okHttpClient, mockWebServer.url("/"), specContext.getSpec(), false, false);
    okHttpValidatorTypeDefClientWithPreferredSsz =
        new OkHttpValidatorTypeDefClient(
            okHttpClient, mockWebServer.url("/"), specContext.getSpec(), true, false);
    sszRegisterValidatorsRequest =
        new RegisterValidatorsRequest(mockWebServer.url("/"), okHttpClient, true);
  }

  @TestTemplate
  public void createSyncCommitteeContribution_whenSuccess_returnsContribution()
      throws JsonProcessingException {
    assumeThat(specMilestone).isGreaterThanOrEqualTo(ALTAIR);
    final SyncCommitteeContribution contribution =
        dataStructureUtil.randomSyncCommitteeContribution();
    final SyncCommitteeContributionSchema syncCommitteeContributionSchema =
        SchemaDefinitionsAltair.required(spec.atSlot(contribution.getSlot()).getSchemaDefinitions())
            .getSyncCommitteeContributionSchema();

    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(SC_OK)
            .setBody(
                "{\"data\": "
                    + serialize(
                        contribution, syncCommitteeContributionSchema.getJsonTypeDefinition())
                    + "}"));

    final Optional<SyncCommitteeContribution> response =
        typeDefClient.createSyncCommitteeContribution(
            contribution.getSlot(),
            contribution.getSubcommitteeIndex().intValue(),
            contribution.getBeaconBlockRoot());

    assertThat(response).isPresent();
    assertThat(response.get()).isEqualTo(contribution);
  }

  @TestTemplate
  public void createSyncCommitteeContribution_whenNotFound_returnsEmpty() {
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_NOT_FOUND));

    assertThat(typeDefClient.createSyncCommitteeContribution(UInt64.ONE, 0, Bytes32.ZERO))
        .isEmpty();
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
        typeDefClient.sendSignedBlock(signedBeaconBlock, BroadcastValidationLevel.GOSSIP);

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

    final SyncingStatus result = typeDefClient.getSyncingStatus();

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
        RemoteServiceNotAvailableException.class, () -> typeDefClient.getSyncingStatus());
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

    typeDefClient.registerValidators(validatorRegistrations);

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
            () -> typeDefClient.registerValidators(validatorRegistrations));

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
            () -> typeDefClient.registerValidators(validatorRegistrations));

    assertThat(serverException.getMessage())
        .matches(
            serverErrorFromApi
                + " \\(url = (.*), status = 500, message = Internal server error\\)");
  }

  @TestTemplate
  void registerValidators_makesSszRequestIfSszEncodingPreferred() throws InterruptedException {

    mockWebServer.enqueue(new MockResponse().setResponseCode(200));

    final SszList<SignedValidatorRegistration> validatorRegistrations =
        dataStructureUtil.randomSignedValidatorRegistrations(5);

    sszRegisterValidatorsRequest.submit(validatorRegistrations);

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

    sszRegisterValidatorsRequest.submit(validatorRegistrations);

    assertThat(mockWebServer.getRequestCount()).isEqualTo(2);

    verifyRegisterValidatorsPostRequest(mockWebServer.takeRequest(), OCTET_STREAM_CONTENT_TYPE);
    verifyRegisterValidatorsPostRequest(mockWebServer.takeRequest(), JSON_CONTENT_TYPE);

    // subsequent requests default immediately to json
    sszRegisterValidatorsRequest.submit(validatorRegistrations);

    assertThat(mockWebServer.getRequestCount()).isEqualTo(3);

    verifyRegisterValidatorsPostRequest(mockWebServer.takeRequest(), JSON_CONTENT_TYPE);
  }

  @TestTemplate
  void postValidators_makesExpectedRequest() throws Exception {
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_NO_CONTENT));

    typeDefClient.postStateValidators(List.of("1", "0x1234"));

    final RecordedRequest request = mockWebServer.takeRequest();
    assertThat(request.getMethod()).isEqualTo("POST");

    assertThat(request.getPath()).contains(ValidatorApiMethod.GET_VALIDATORS.getPath(emptyMap()));
    assertThat(request.getBody().readUtf8()).isEqualTo("{\"ids\":[\"1\",\"0x1234\"]}");
  }

  @TestTemplate
  void getStateValidators_makesExpectedRequest() throws Exception {
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_NO_CONTENT));

    typeDefClient.getStateValidators(List.of("1", "0x1234"));

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

    assertThat(typeDefClient.postStateValidators(List.of("1"))).isEmpty();
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
    assertThatThrownBy(() -> typeDefClient.postStateValidators(List.of("1")))
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
        typeDefClient.postStateValidators(List.of("1", "2"));

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
    Optional<SyncCommitteeDuties> result = typeDefClient.postSyncDuties(epoch, validatorIndices);

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
    Optional<AttesterDuties> result = typeDefClient.postAttesterDuties(epoch, validatorIndices);

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

    typeDefClient.subscribeToSyncCommitteeSubnets(subscriptions);
    final RecordedRequest recordedRequest = mockWebServer.takeRequest();
    assertThat(recordedRequest.getPath())
        .isEqualTo("/eth/v1/validator/sync_committee_subscriptions");
    assertThat(recordedRequest.getMethod()).isEqualTo("POST");
    assertThat(recordedRequest.getHeader("Content-Type")).isEqualTo(JSON_CONTENT_TYPE);
    assertThat(recordedRequest.getBody().readUtf8())
        .isEqualTo(
            "[{\"validator_index\":\"0\",\"sync_committee_indices\":[\"1\"],\"until_epoch\":\"0\"}]");
  }

  @TestTemplate
  public void subscribeToPersistentSubnets_makesExpectedRequest() throws Exception {
    final Set<SubnetSubscription> subnetSubscriptions =
        Set.of(
            new SubnetSubscription(
                dataStructureUtil.randomPositiveInt(64), dataStructureUtil.randomSlot()));

    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_OK));

    typeDefClient.subscribeToPersistentSubnets(subnetSubscriptions);

    RecordedRequest request = mockWebServer.takeRequest();

    assertThat(request.getMethod()).isEqualTo("POST");
    assertThat(request.getPath())
        .contains(ValidatorApiMethod.SUBSCRIBE_TO_PERSISTENT_SUBNETS.getPath(emptyMap()));
    assertThat(request.getBody().readString(StandardCharsets.UTF_8))
        .isEqualTo("[{\"subnet_id\":\"35\",\"unsubscription_slot\":\"24752339414\"}]");
  }

  @TestTemplate
  public void subscribeToPersistentSubnets_whenBadRequest_throwsIllegalArgumentException() {
    final Set<SubnetSubscription> subnetSubscriptions =
        Set.of(
            new SubnetSubscription(
                dataStructureUtil.randomPositiveInt(64), dataStructureUtil.randomSlot()));

    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_BAD_REQUEST));
    assertThatThrownBy(() -> typeDefClient.subscribeToPersistentSubnets(subnetSubscriptions))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @TestTemplate
  public void subscribeToPersistentSubnets_whenServerError_throwsRuntimeException() {
    final Set<SubnetSubscription> subnetSubscriptions =
        Set.of(
            new SubnetSubscription(
                dataStructureUtil.randomPositiveInt(64), dataStructureUtil.randomSlot()));

    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_INTERNAL_SERVER_ERROR));

    assertThatThrownBy(() -> typeDefClient.subscribeToPersistentSubnets(subnetSubscriptions))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining(serverErrorFromApi);
  }

  @TestTemplate
  public void subscribeToBeaconCommitteeForAggregation_makesExpectedRequest() throws Exception {
    final int committeeIndex1 = 1;
    final int validatorIndex1 = 6;
    final UInt64 committeesAtSlot1 = UInt64.valueOf(10);
    final UInt64 slot1 = UInt64.valueOf(15);
    final boolean aggregator1 = true;

    final int committeeIndex2 = 2;
    final int validatorIndex2 = 7;
    final UInt64 committeesAtSlot2 = UInt64.valueOf(11);
    final UInt64 slot2 = UInt64.valueOf(16);
    final boolean aggregator2 = false;

    final String expectedRequest =
        "[{\"validator_index\":\"6\",\"committee_index\":\"1\",\"committees_at_slot\":\"10\",\"slot\":\"15\",\"is_aggregator\":true},"
            + "{\"validator_index\":\"7\",\"committee_index\":\"2\",\"committees_at_slot\":\"11\",\"slot\":\"16\",\"is_aggregator\":false}]";

    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_OK));

    typeDefClient.subscribeToBeaconCommittee(
        List.of(
            new CommitteeSubscriptionRequest(
                validatorIndex1, committeeIndex1, committeesAtSlot1, slot1, aggregator1),
            new CommitteeSubscriptionRequest(
                validatorIndex2, committeeIndex2, committeesAtSlot2, slot2, aggregator2)));

    RecordedRequest request = mockWebServer.takeRequest();

    assertThat(request.getMethod()).isEqualTo("POST");
    assertThat(request.getPath())
        .contains(ValidatorApiMethod.SUBSCRIBE_TO_BEACON_COMMITTEE_SUBNET.getPath(emptyMap()));
    assertThat(request.getBody().readUtf8()).isEqualTo(expectedRequest);
  }

  @TestTemplate
  public void
      subscribeToBeaconCommitteeForAggregation_whenBadRequest_throwsIllegalArgumentException() {
    final int committeeIndex = 1;
    final UInt64 aggregationSlot = UInt64.ONE;

    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_BAD_REQUEST));

    assertThatThrownBy(
            () ->
                typeDefClient.subscribeToBeaconCommittee(
                    List.of(
                        new CommitteeSubscriptionRequest(
                            1, committeeIndex, UInt64.valueOf(10), aggregationSlot, true))))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @TestTemplate
  public void subscribeToBeaconCommitteeForAggregation_whenServerError_throwsRuntimeException() {
    final int committeeIndex = 1;
    final UInt64 aggregationSlot = UInt64.ONE;

    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_INTERNAL_SERVER_ERROR));

    assertThatThrownBy(
            () ->
                typeDefClient.subscribeToBeaconCommittee(
                    List.of(
                        new CommitteeSubscriptionRequest(
                            1, committeeIndex, UInt64.valueOf(10), aggregationSlot, true))))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining(serverErrorFromApi);
  }

  @TestTemplate
  public void sendSignedContributionAndProof_emptyListIsNoop() {
    assumeThat(specMilestone).isGreaterThanOrEqualTo(ALTAIR);
    typeDefClient.sendContributionAndProofs(List.of());
    assertThat(mockWebServer.getRequestCount()).isEqualTo(0);
  }

  @TestTemplate
  public void sendSignedContributionAndProof_acceptsPopulatedList() throws InterruptedException {
    assumeThat(specMilestone).isGreaterThanOrEqualTo(ALTAIR);
    final SignedContributionAndProof proof = dataStructureUtil.randomSignedContributionAndProof();
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_OK));
    typeDefClient.sendContributionAndProofs(List.of(proof));

    final RecordedRequest request = mockWebServer.takeRequest();
    assertThat(request.getMethod()).isEqualTo("POST");
    assertThat(request.getRequestUrl().encodedPath())
        .isEqualTo("/eth/v1/validator/contribution_and_proofs");
    assertThat(request.getBody().readUtf8())
        .contains("\"contribution\":{\"slot\":\"4666673844721362956\"");
  }

  @TestTemplate
  public void sendSignedContributionAndProof_canRespondFailure() {
    assumeThat(specMilestone).isGreaterThanOrEqualTo(ALTAIR);
    final SignedContributionAndProof proof = dataStructureUtil.randomSignedContributionAndProof();
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_BAD_REQUEST));
    assertThatThrownBy(() -> typeDefClient.sendContributionAndProofs(List.of(proof)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @TestTemplate
  public void prepareBeaconProposer_emptyListIsNoop() {
    assumeThat(specMilestone).isGreaterThanOrEqualTo(BELLATRIX);
    typeDefClient.prepareBeaconProposer(List.of());
    assertThat(mockWebServer.getRequestCount()).isEqualTo(0);
  }

  @TestTemplate
  public void prepareBeaconProposer_canRespondFailure() {
    assumeThat(specMilestone).isGreaterThanOrEqualTo(BELLATRIX);
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_BAD_REQUEST));
    assertThatThrownBy(
            () ->
                typeDefClient.prepareBeaconProposer(
                    List.of(dataStructureUtil.randomBeaconPreparableProposer())))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @TestTemplate
  public void prepareBeaconProposer_acceptsPopulatedList() throws InterruptedException {
    assumeThat(specMilestone).isGreaterThanOrEqualTo(BELLATRIX);
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_OK));
    typeDefClient.prepareBeaconProposer(
        List.of(dataStructureUtil.randomBeaconPreparableProposer()));

    final RecordedRequest request = mockWebServer.takeRequest();
    assertThat(request.getMethod()).isEqualTo("POST");
    assertThat(request.getRequestUrl().encodedPath())
        .isEqualTo("/eth/v1/validator/prepare_beacon_proposer");
    assertThat(request.getBody().readUtf8())
        .isEqualTo(
            "[{\"validator_index\":\"4666673844721362956\",\"fee_recipient\":\"0x367CbD40AC7318427aAdB97345a91FA2e965DAf3\"}]");
  }

  @TestTemplate
  public void createAggregate_makesExpectedRequest_preElectra() throws Exception {
    assumeThat(specMilestone).isLessThan(ELECTRA);
    final UInt64 slot = UInt64.valueOf(323);
    final Bytes32 attestationHashTreeRoot = Bytes32.random();

    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_NO_CONTENT));

    typeDefClient.createAggregate(slot, attestationHashTreeRoot, Optional.empty());

    RecordedRequest request = mockWebServer.takeRequest();

    assertThat(request.getMethod()).isEqualTo("GET");
    assertThat(request.getPath()).contains(ValidatorApiMethod.GET_AGGREGATE.getPath(emptyMap()));
    assertThat(request.getRequestUrl().queryParameter("slot")).isEqualTo(slot.toString());
    assertThat(request.getRequestUrl().queryParameter("attestation_data_root"))
        .isEqualTo(attestationHashTreeRoot.toHexString());
  }

  @TestTemplate
  public void createAggregate_makesExpectedRequest_postElectra() throws Exception {
    assumeThat(specMilestone).isGreaterThanOrEqualTo(ELECTRA);
    final UInt64 slot = UInt64.valueOf(323);
    final Bytes32 attestationHashTreeRoot = Bytes32.random();

    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_NO_CONTENT));

    typeDefClient.createAggregate(
        slot, attestationHashTreeRoot, Optional.of(dataStructureUtil.randomUInt64()));

    RecordedRequest request = mockWebServer.takeRequest();

    assertThat(request.getMethod()).isEqualTo("GET");
    assertThat(request.getPath()).contains(ValidatorApiMethod.GET_AGGREGATE_V2.getPath(emptyMap()));
    assertThat(request.getRequestUrl().queryParameter("slot")).isEqualTo(slot.toString());
    assertThat(request.getRequestUrl().queryParameter("attestation_data_root"))
        .isEqualTo(attestationHashTreeRoot.toHexString());
  }

  @TestTemplate
  public void createAggregate_whenBadParameters_throwsIllegalArgumentException() {
    final Bytes32 attestationHashTreeRoot = Bytes32.random();

    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_BAD_REQUEST));

    assertThatThrownBy(
            () ->
                typeDefClient.createAggregate(
                    UInt64.ONE,
                    attestationHashTreeRoot,
                    Optional.of(dataStructureUtil.randomUInt64())))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @TestTemplate
  public void createAggregate_whenNotFound_returnsEmpty() {
    final Bytes32 attestationHashTreeRoot = Bytes32.random();

    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_NOT_FOUND));

    assertThat(
            typeDefClient.createAggregate(
                UInt64.ONE, attestationHashTreeRoot, Optional.of(dataStructureUtil.randomUInt64())))
        .isEmpty();
  }

  @TestTemplate
  public void createAggregate_whenServerError_throwsRuntimeException() {
    final Bytes32 attestationHashTreeRoot = Bytes32.random();

    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_INTERNAL_SERVER_ERROR));

    assertThatThrownBy(
            () ->
                typeDefClient.createAggregate(
                    UInt64.ONE,
                    attestationHashTreeRoot,
                    Optional.of(dataStructureUtil.randomUInt64())))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Server error from Beacon Node API");
  }

  @TestTemplate
  public void createAggregate_whenSuccess_returnsAttestation_preElectra()
      throws JsonProcessingException {
    assumeThat(specMilestone).isLessThan(ELECTRA);
    final Bytes32 attestationHashTreeRoot = Bytes32.random();
    final Attestation expectedAttestation = dataStructureUtil.randomAttestation();
    final String body =
        serialize(
            expectedAttestation,
            spec.getGenesisSchemaDefinitions().getAttestationSchema().getJsonTypeDefinition());
    mockWebServer.enqueue(
        new MockResponse().setResponseCode(SC_OK).setBody("{\"data\": " + body + "}"));

    final Optional<ObjectAndMetaData<Attestation>> attestation =
        typeDefClient.createAggregate(UInt64.ONE, attestationHashTreeRoot, Optional.empty());

    assertThat(attestation).isPresent();
    assertThat(attestation.get().getData()).isEqualTo(expectedAttestation);
  }

  @TestTemplate
  public void createAggregate_whenSuccess_returnsAttestation_postElectra()
      throws JsonProcessingException {
    assumeThat(specMilestone).isGreaterThanOrEqualTo(ELECTRA);
    final Bytes32 attestationHashTreeRoot = Bytes32.random();
    final Attestation expectedAttestation = dataStructureUtil.randomAttestation();
    final String body =
        serialize(
            expectedAttestation,
            spec.getGenesisSchemaDefinitions().getAttestationSchema().getJsonTypeDefinition());
    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(SC_OK)
            .setBody(
                "{ "
                    + "\"version\": \""
                    + specMilestone.name().toLowerCase(Locale.ROOT)
                    + "\", "
                    + "\"data\": "
                    + body
                    + " }"));

    final Optional<ObjectAndMetaData<Attestation>> attestation =
        typeDefClient.createAggregate(
            UInt64.ONE, attestationHashTreeRoot, Optional.of(dataStructureUtil.randomUInt64()));

    assertThat(attestation).isPresent();
    assertThat(attestation.get().getData()).isEqualTo(expectedAttestation);
  }

  @TestTemplate
  public void createAggregate_whenMissingCommitteeIndex_returnsEmpty_postElectra() {
    assumeThat(specMilestone).isGreaterThanOrEqualTo(ELECTRA);
    assertThatThrownBy(
            () ->
                typeDefClient.createAggregate(
                    UInt64.ONE, dataStructureUtil.randomBytes32(), Optional.empty()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Missing required parameter: committee index");
    assertThat(mockWebServer.getRequestCount()).isZero();
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
      assertThat(OBJECT_MAPPER.readTree(actual)).isEqualTo(OBJECT_MAPPER.readTree(expected));
    } catch (JsonProcessingException ex) {
      Assertions.fail(ex);
    }
  }
}
