/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.validator.remote.apiclient;

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.fail;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_ACCEPTED;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NO_CONTENT;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.request.v1.validator.BeaconCommitteeSubscriptionRequest;
import tech.pegasys.teku.api.response.v1.beacon.GetGenesisResponse;
import tech.pegasys.teku.api.response.v1.beacon.GetStateForkResponse;
import tech.pegasys.teku.api.response.v1.beacon.GetStateValidatorsResponse;
import tech.pegasys.teku.api.response.v1.beacon.ValidatorResponse;
import tech.pegasys.teku.api.response.v1.validator.GetAggregatedAttestationResponse;
import tech.pegasys.teku.api.response.v1.validator.GetNewBlockResponse;
import tech.pegasys.teku.api.schema.Attestation;
import tech.pegasys.teku.api.schema.AttestationData;
import tech.pegasys.teku.api.schema.BLSSignature;
import tech.pegasys.teku.api.schema.BeaconBlock;
import tech.pegasys.teku.api.schema.Fork;
import tech.pegasys.teku.api.schema.SignedAggregateAndProof;
import tech.pegasys.teku.api.schema.SignedBeaconBlock;
import tech.pegasys.teku.api.schema.SubnetSubscription;
import tech.pegasys.teku.api.schema.ValidatorDuties;
import tech.pegasys.teku.api.schema.ValidatorDutiesRequest;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.provider.JsonProvider;
import tech.pegasys.teku.validator.api.CommitteeSubscriptionRequest;

class OkHttpValidatorRestApiClientTest {

  private final SchemaObjectsTestFixture schemaObjects = new SchemaObjectsTestFixture();
  private final JsonProvider jsonProvider = new JsonProvider();
  private final MockWebServer mockWebServer = new MockWebServer();
  private OkHttpValidatorRestApiClient apiClient;
  private OkHttpClient okHttpClient;

  @BeforeEach
  public void beforeEach() throws Exception {
    mockWebServer.start();
    okHttpClient = new OkHttpClient();
    apiClient = new OkHttpValidatorRestApiClient(mockWebServer.url("/"), okHttpClient);
  }

  @AfterEach
  public void afterEach() throws Exception {
    mockWebServer.shutdown();
  }

  @Test
  public void getFork_MakesExpectedRequest() throws Exception {
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_NO_CONTENT));

    apiClient.getFork();

    RecordedRequest request = mockWebServer.takeRequest();

    assertThat(request.getMethod()).isEqualTo("GET");
    assertThat(request.getPath())
        .contains(ValidatorApiMethod.GET_FORK.getPath(Map.of("state_id", "head")));
  }

  @Test
  public void getFork_WhenServerError_ThrowsRuntimeException() {
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_INTERNAL_SERVER_ERROR));

    assertThatThrownBy(() -> apiClient.getFork())
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Unexpected response from Beacon Node API");
  }

  @Test
  public void getFork_WhenNoContent_ReturnsEmpty() {
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_NO_CONTENT));

    assertThat(apiClient.getFork()).isEmpty();
  }

  @Test
  public void getFork_WhenSuccess_ReturnsForkResponse() {
    final GetStateForkResponse getStateForkResponse = schemaObjects.getStateForkResponse();

    mockWebServer.enqueue(
        new MockResponse().setResponseCode(SC_OK).setBody(asJson(getStateForkResponse)));

    Optional<Fork> fork = apiClient.getFork();

    assertThat(fork).isPresent();
    assertThat(fork.get()).isEqualTo(getStateForkResponse.data);
  }

  @Test
  public void getGenesis_MakesExpectedRequest() throws Exception {
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_NO_CONTENT));

    apiClient.getGenesis();

    RecordedRequest request = mockWebServer.takeRequest();

    assertThat(request.getMethod()).isEqualTo("GET");
    assertThat(request.getPath()).contains(ValidatorApiMethod.GET_GENESIS.getPath(emptyMap()));
  }

  @Test
  public void getGenesis_WhenServerError_ThrowsRuntimeException() {
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_INTERNAL_SERVER_ERROR));

    assertThatThrownBy(() -> apiClient.getGenesis())
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Unexpected response from Beacon Node API");
  }

  @Test
  public void getGenesis_WhenNoContent_ReturnsEmpty() {
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_NO_CONTENT));

    assertThat(apiClient.getGenesis()).isEmpty();
  }

  @Test
  public void getGenesis_WhenSuccess_ReturnsGenesisResponse() {
    final GetGenesisResponse getGenesisResponse = schemaObjects.getGenesisResponse();

    mockWebServer.enqueue(
        new MockResponse().setResponseCode(SC_OK).setBody(asJson(getGenesisResponse)));

    Optional<GetGenesisResponse> genesis = apiClient.getGenesis();

    assertThat(genesis).isPresent();
    assertThat(genesis.get()).usingRecursiveComparison().isEqualTo(getGenesisResponse);
  }

  @Test
  void getValidators_MakesExpectedRequest() throws Exception {
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_NO_CONTENT));

    apiClient.getValidators(List.of("1", "0x1234"));

    final RecordedRequest request = mockWebServer.takeRequest();
    assertThat(request.getMethod()).isEqualTo("GET");
    assertThat(request.getPath()).contains(ValidatorApiMethod.GET_VALIDATORS.getPath(emptyMap()));
    // %2C is ,
    assertThat(request.getPath()).contains("?id=1%2C0x1234");
  }

  @Test
  void getValidators_WhenServerError_ThrowsRuntimeException() {
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_INTERNAL_SERVER_ERROR));

    assertThatThrownBy(() -> apiClient.getValidators(List.of("1")))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Unexpected response from Beacon Node API");
  }

  @Test
  public void getValidators_WhenNoContent_ReturnsEmpty() {
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_NO_CONTENT));

    assertThat(apiClient.getValidators(List.of("1"))).isEmpty();
  }

  @Test
  public void getValidators_WhenSuccess_ReturnsResponse() {
    final List<ValidatorResponse> expected =
        List.of(schemaObjects.validatorResponse(), schemaObjects.validatorResponse());
    final GetStateValidatorsResponse response = new GetStateValidatorsResponse(expected);

    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_OK).setBody(asJson(response)));

    Optional<List<ValidatorResponse>> result = apiClient.getValidators(List.of("1", "2"));

    assertThat(result).isPresent();
    assertThat(result.get()).usingRecursiveComparison().isEqualTo(expected);
  }

  @Test
  public void getDuties_MakesExpectedRequest() throws Exception {
    final ValidatorDutiesRequest validatorDutiesRequest = schemaObjects.validatorDutiesRequest();

    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_NO_CONTENT));

    apiClient.getDuties(validatorDutiesRequest);

    RecordedRequest request = mockWebServer.takeRequest();

    assertThat(request.getMethod()).isEqualTo("POST");
    assertThat(request.getPath()).contains(ValidatorApiMethod.GET_DUTIES.getPath(emptyMap()));
    assertThat(request.getBody().readString(StandardCharsets.UTF_8))
        .isEqualTo(asJson(validatorDutiesRequest));
  }

  @Test
  public void getDuties_WhenServerError_ThrowsRuntimeException() {
    final ValidatorDutiesRequest validatorDutiesRequest = schemaObjects.validatorDutiesRequest();

    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_INTERNAL_SERVER_ERROR));

    assertThatThrownBy(() -> apiClient.getDuties(validatorDutiesRequest))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Unexpected response from Beacon Node API");
  }

  @Test
  public void getDuties_WhenBadRequest_ThrowException() {
    final ValidatorDutiesRequest validatorDutiesRequest = schemaObjects.validatorDutiesRequest();

    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_BAD_REQUEST));

    assertThatThrownBy(() -> apiClient.getDuties(validatorDutiesRequest))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid params response from Beacon Node API");
  }

  @Test
  public void getDuties_WhenNoContent_ReturnsEmpty() {
    final ValidatorDutiesRequest validatorDutiesRequest = schemaObjects.validatorDutiesRequest();

    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_NO_CONTENT));

    assertThat(apiClient.getDuties(validatorDutiesRequest)).isEmpty();
  }

  @Test
  public void getDuties_WhenSuccess_ReturnsValidatorDuties() {
    final ValidatorDutiesRequest validatorDutiesRequest = schemaObjects.validatorDutiesRequest();
    final ValidatorDuties validatorDuties = schemaObjects.validatorDuties();

    mockWebServer.enqueue(
        new MockResponse().setResponseCode(SC_OK).setBody(asJson(List.of(validatorDuties))));

    List<ValidatorDuties> duties = apiClient.getDuties(validatorDutiesRequest);

    assertThat(duties).hasSize(1);
    assertThat(duties).first().usingRecursiveComparison().isEqualTo(validatorDuties);
  }

  @Test
  public void createUnsignedBlock_MakesExpectedRequest() throws Exception {
    final UInt64 slot = UInt64.ONE;
    final BLSSignature blsSignature = schemaObjects.BLSSignature();
    final Optional<Bytes32> graffiti = Optional.of(Bytes32.random());

    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_NO_CONTENT));

    apiClient.createUnsignedBlock(slot, blsSignature, graffiti);

    RecordedRequest request = mockWebServer.takeRequest();

    assertThat(request.getMethod()).isEqualTo("GET");
    assertThat(request.getPath())
        .contains(ValidatorApiMethod.GET_UNSIGNED_BLOCK.getPath(Map.of("slot", "1")));
    assertThat(request.getRequestUrl().queryParameter("randao_reveal"))
        .isEqualTo(blsSignature.toHexString());
    assertThat(request.getRequestUrl().queryParameter("graffiti"))
        .isEqualTo(graffiti.get().toHexString());
  }

  @Test
  public void createUnsignedBlock_WhenNoContent_ReturnsEmpty() {
    final UInt64 slot = UInt64.ONE;
    final BLSSignature blsSignature = schemaObjects.BLSSignature();
    final Optional<Bytes32> graffiti = Optional.of(Bytes32.random());

    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_NO_CONTENT));

    assertThat(apiClient.createUnsignedBlock(slot, blsSignature, graffiti)).isEmpty();
  }

  @Test
  public void createUnsignedBlock_WhenBadRequest_ThrowsIllegalArgumentException() {
    final UInt64 slot = UInt64.ONE;
    final BLSSignature blsSignature = schemaObjects.BLSSignature();
    final Optional<Bytes32> graffiti = Optional.of(Bytes32.random());

    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_BAD_REQUEST));

    assertThatThrownBy(() -> apiClient.createUnsignedBlock(slot, blsSignature, graffiti))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void createUnsignedBlock_WhenSuccess_ReturnsBeaconBlock() {
    final UInt64 slot = UInt64.ONE;
    final BLSSignature blsSignature = schemaObjects.BLSSignature();
    final Optional<Bytes32> graffiti = Optional.of(Bytes32.random());
    final BeaconBlock expectedBeaconBlock = schemaObjects.beaconBlock();

    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(SC_OK)
            .setBody(asJson(new GetNewBlockResponse(expectedBeaconBlock))));

    Optional<BeaconBlock> beaconBlock = apiClient.createUnsignedBlock(slot, blsSignature, graffiti);

    assertThat(beaconBlock).isPresent();
    assertThat(beaconBlock.get()).usingRecursiveComparison().isEqualTo(expectedBeaconBlock);
  }

  @Test
  public void sendSignedBlock_MakesExpectedRequest() throws Exception {
    final Bytes32 blockRoot = Bytes32.fromHexStringLenient("0x1234");
    final SignedBeaconBlock signedBeaconBlock = schemaObjects.signedBeaconBlock();

    // Block has been successfully broadcast, validated and imported
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_OK).setBody(asJson(blockRoot)));

    apiClient.sendSignedBlock(signedBeaconBlock);

    RecordedRequest request = mockWebServer.takeRequest();

    assertThat(request.getMethod()).isEqualTo("POST");
    assertThat(request.getPath())
        .contains(ValidatorApiMethod.SEND_SIGNED_BLOCK.getPath(emptyMap()));
    assertThat(request.getBody().readString(StandardCharsets.UTF_8))
        .isEqualTo(asJson(signedBeaconBlock));
  }

  @Test
  public void sendSignedBlock_WhenAccepted_DoesNotFailHandlingResponse() {
    final SignedBeaconBlock signedBeaconBlock = schemaObjects.signedBeaconBlock();

    // Block has been successfully broadcast, but failed validation and has not been imported
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_ACCEPTED));

    apiClient.sendSignedBlock(signedBeaconBlock);
  }

  @Test
  public void sendSignedBlock_WhenBadParameters_ThrowsIllegalArgumentException() {
    final SignedBeaconBlock signedBeaconBlock = schemaObjects.signedBeaconBlock();

    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_BAD_REQUEST));

    assertThatThrownBy(() -> apiClient.sendSignedBlock(signedBeaconBlock))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void sendSignedBlock_WhenServiceUnavailable_DoesNotFailHandlingResponse() {
    final SignedBeaconBlock signedBeaconBlock = schemaObjects.signedBeaconBlock();

    // node is syncing
    mockWebServer.enqueue(new MockResponse().setResponseCode(503));

    apiClient.sendSignedBlock(signedBeaconBlock);
  }

  @Test
  public void sendSignedBlock_WhenServerError_ThrowsRuntimeException() {
    final SignedBeaconBlock signedBeaconBlock = schemaObjects.signedBeaconBlock();

    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_INTERNAL_SERVER_ERROR));

    assertThatThrownBy(() -> apiClient.sendSignedBlock(signedBeaconBlock))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Unexpected response from Beacon Node API");
  }

  @Test
  public void createUnsignedAttestation_MakesExpectedRequest() throws Exception {
    final UInt64 slot = UInt64.ONE;
    final int committeeIndex = 1;

    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_NO_CONTENT));

    apiClient.createUnsignedAttestation(slot, committeeIndex);

    RecordedRequest request = mockWebServer.takeRequest();

    assertThat(request.getMethod()).isEqualTo("GET");
    assertThat(request.getPath())
        .contains(ValidatorApiMethod.GET_UNSIGNED_ATTESTATION.getPath(emptyMap()));
    assertThat(request.getRequestUrl().queryParameter("slot")).isEqualTo(slot.toString());
    assertThat(request.getRequestUrl().queryParameter("committee_index"))
        .isEqualTo(String.valueOf(committeeIndex));
  }

  @Test
  public void createUnsignedAttestation_WhenBadRequest_ThrowsIllegalArgumentException() {
    final UInt64 slot = UInt64.ONE;
    final int committeeIndex = 1;

    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_BAD_REQUEST));

    assertThatThrownBy(() -> apiClient.createUnsignedAttestation(slot, committeeIndex))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void createUnsignedAttestation_WhenNotFound_ThrowsException() {
    final UInt64 slot = UInt64.ONE;
    final int committeeIndex = 1;

    // An attestation could not be created for the specified slot
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_NOT_FOUND));

    assertThatThrownBy(() -> apiClient.createUnsignedAttestation(slot, committeeIndex))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Unexpected response from Beacon Node API");
  }

  @Test
  public void createUnsignedAttestation_WhenSuccess_ReturnsAttestation() {
    final UInt64 slot = UInt64.ONE;
    final int committeeIndex = 1;
    final Attestation expectedAttestation = schemaObjects.attestation();

    mockWebServer.enqueue(
        new MockResponse().setResponseCode(SC_OK).setBody(asJson(expectedAttestation)));

    Optional<Attestation> attestation = apiClient.createUnsignedAttestation(slot, committeeIndex);

    assertThat(attestation).isPresent();
    assertThat(attestation.get()).usingRecursiveComparison().isEqualTo(expectedAttestation);
  }

  @Test
  public void createAttestationData_MakesExpectedRequest() throws Exception {
    final UInt64 slot = UInt64.ONE;
    final int committeeIndex = 1;

    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_NO_CONTENT));

    apiClient.createAttestationData(slot, committeeIndex);

    RecordedRequest request = mockWebServer.takeRequest();

    assertThat(request.getMethod()).isEqualTo("GET");
    assertThat(request.getPath())
        .contains(ValidatorApiMethod.GET_ATTESTATION_DATA.getPath(emptyMap()));
    assertThat(request.getRequestUrl().queryParameter("slot")).isEqualTo(slot.toString());
    assertThat(request.getRequestUrl().queryParameter("committee_index"))
        .isEqualTo(String.valueOf(committeeIndex));
  }

  @Test
  public void createAttestationData_WhenBadRequest_ThrowsIllegalArgumentException() {
    final UInt64 slot = UInt64.ONE;
    final int committeeIndex = 1;

    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_BAD_REQUEST));

    assertThatThrownBy(() -> apiClient.createAttestationData(slot, committeeIndex))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void createAttestationData_WhenNotFound_ThrowsError() {
    final UInt64 slot = UInt64.ONE;
    final int committeeIndex = 1;

    // An attestation could not be created for the specified slot
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_NOT_FOUND));

    assertThatThrownBy(() -> apiClient.createAttestationData(slot, committeeIndex))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Unexpected response from Beacon Node API");
  }

  @Test
  public void createAttestationData_WhenSuccess_ReturnsAttestation() {
    final UInt64 slot = UInt64.ONE;
    final int committeeIndex = 1;
    final AttestationData expectedAttestationData = schemaObjects.attestation().data;

    mockWebServer.enqueue(
        new MockResponse().setResponseCode(SC_OK).setBody(asJson(expectedAttestationData)));

    Optional<AttestationData> attestationData =
        apiClient.createAttestationData(slot, committeeIndex);

    assertThat(attestationData).isPresent();
    assertThat(attestationData.get()).usingRecursiveComparison().isEqualTo(expectedAttestationData);
  }

  @Test
  public void sendSignedAttestation_MakesExpectedRequest() throws Exception {
    final Attestation attestation = schemaObjects.attestation();

    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_OK));

    apiClient.sendSignedAttestation(attestation);

    RecordedRequest request = mockWebServer.takeRequest();

    assertThat(request.getMethod()).isEqualTo("POST");
    assertThat(request.getPath())
        .contains(ValidatorApiMethod.SEND_SIGNED_ATTESTATION.getPath(emptyMap()));
    assertThat(request.getBody().readString(StandardCharsets.UTF_8)).isEqualTo(asJson(attestation));
  }

  @Test
  public void sendSignedAttestation_WhenBadParameters_ThrowsIllegalArgumentException() {
    final Attestation attestation = schemaObjects.attestation();

    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_BAD_REQUEST));

    assertThatThrownBy(() -> apiClient.sendSignedAttestation(attestation))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void sendSignedAttestation_WhenServerError_ThrowsRuntimeException() {
    final Attestation attestation = schemaObjects.attestation();

    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_INTERNAL_SERVER_ERROR));

    assertThatThrownBy(() -> apiClient.sendSignedAttestation(attestation))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Unexpected response from Beacon Node API");
  }

  @Test
  public void createAggregate_MakesExpectedRequest() throws Exception {
    final UInt64 slot = UInt64.ZERO;
    final Bytes32 attestationHashTreeRoot = Bytes32.random();

    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_NO_CONTENT));

    apiClient.createAggregate(attestationHashTreeRoot);

    RecordedRequest request = mockWebServer.takeRequest();

    assertThat(request.getMethod()).isEqualTo("GET");
    assertThat(request.getPath()).contains(ValidatorApiMethod.GET_AGGREGATE.getPath(emptyMap()));
    assertThat(request.getRequestUrl().queryParameter("slot")).isEqualTo(slot.toString());
    assertThat(request.getRequestUrl().queryParameter("attestation_data_root"))
        .isEqualTo(attestationHashTreeRoot.toHexString());
  }

  @Test
  public void createAggregate_WhenBadParameters_ThrowsIllegalArgumentException() {
    final Bytes32 attestationHashTreeRoot = Bytes32.random();

    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_BAD_REQUEST));

    assertThatThrownBy(() -> apiClient.createAggregate(attestationHashTreeRoot))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void createAggregate_WhenServerError_ThrowsRuntimeException() {
    final Bytes32 attestationHashTreeRoot = Bytes32.random();

    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_INTERNAL_SERVER_ERROR));

    assertThatThrownBy(() -> apiClient.createAggregate(attestationHashTreeRoot))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Unexpected response from Beacon Node API");
  }

  @Test
  public void createAggregate_WhenSuccess_ReturnsAttestation() {
    final Bytes32 attestationHashTreeRoot = Bytes32.random();
    final Attestation expectedAttestation = schemaObjects.attestation();

    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(SC_OK)
            .setBody(asJson(new GetAggregatedAttestationResponse(expectedAttestation))));

    final Optional<Attestation> attestation = apiClient.createAggregate(attestationHashTreeRoot);

    assertThat(attestation).isPresent();
    assertThat(attestation.get()).usingRecursiveComparison().isEqualTo(expectedAttestation);
  }

  @Test
  public void sendAggregateAndProofs_MakesExpectedRequest() throws Exception {
    final SignedAggregateAndProof signedAggregateAndProof = schemaObjects.signedAggregateAndProof();

    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_OK));

    apiClient.sendAggregateAndProofs(List.of(signedAggregateAndProof));

    RecordedRequest request = mockWebServer.takeRequest();

    assertThat(request.getMethod()).isEqualTo("POST");
    assertThat(request.getPath())
        .contains(ValidatorApiMethod.SEND_SIGNED_AGGREGATE_AND_PROOF.getPath(emptyMap()));
    assertThat(request.getBody().readString(StandardCharsets.UTF_8))
        .isEqualTo(asJson(List.of(signedAggregateAndProof)));
  }

  @Test
  public void sendAggregateAndProofs_WhenBadParameters_ThrowsIllegalArgumentException() {
    final SignedAggregateAndProof signedAggregateAndProof = schemaObjects.signedAggregateAndProof();

    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_BAD_REQUEST));

    assertThatThrownBy(() -> apiClient.sendAggregateAndProofs(List.of(signedAggregateAndProof)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void sendAggregateAndProofs_WhenServerError_ThrowsRuntimeException() {
    final SignedAggregateAndProof signedAggregateAndProof = schemaObjects.signedAggregateAndProof();

    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_INTERNAL_SERVER_ERROR));

    assertThatThrownBy(() -> apiClient.sendAggregateAndProofs(List.of(signedAggregateAndProof)))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Unexpected response from Beacon Node API");
  }

  @Test
  public void subscribeToBeaconCommitteeForAggregation_MakesExpectedRequest() throws Exception {
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

    final BeaconCommitteeSubscriptionRequest[] expectedRequest = {
      new BeaconCommitteeSubscriptionRequest(
          validatorIndex1, committeeIndex1, committeesAtSlot1, slot1, aggregator1),
      new BeaconCommitteeSubscriptionRequest(
          validatorIndex2, committeeIndex2, committeesAtSlot2, slot2, aggregator2)
    };

    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_OK));

    apiClient.subscribeToBeaconCommittee(
        List.of(
            new CommitteeSubscriptionRequest(
                validatorIndex1, committeeIndex1, committeesAtSlot1, slot1, aggregator1),
            new CommitteeSubscriptionRequest(
                validatorIndex2, committeeIndex2, committeesAtSlot2, slot2, aggregator2)));

    RecordedRequest request = mockWebServer.takeRequest();

    assertThat(request.getMethod()).isEqualTo("POST");
    assertThat(request.getPath())
        .contains(ValidatorApiMethod.SUBSCRIBE_TO_BEACON_COMMITTEE_SUBNET.getPath(emptyMap()));
    assertThat(request.getBody().readString(StandardCharsets.UTF_8))
        .isEqualTo(asJson(expectedRequest));
  }

  @Test
  public void
      subscribeToBeaconCommitteeForAggregation_WhenBadRequest_ThrowsIllegalArgumentException() {
    final int committeeIndex = 1;
    final UInt64 aggregationSlot = UInt64.ONE;

    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_BAD_REQUEST));

    assertThatThrownBy(
            () ->
                apiClient.subscribeToBeaconCommittee(
                    List.of(
                        new CommitteeSubscriptionRequest(
                            1, committeeIndex, UInt64.valueOf(10), aggregationSlot, true))))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void subscribeToBeaconCommitteeForAggregation_WhenServerError_ThrowsRuntimeException() {
    final int committeeIndex = 1;
    final UInt64 aggregationSlot = UInt64.ONE;

    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_INTERNAL_SERVER_ERROR));

    assertThatThrownBy(
            () ->
                apiClient.subscribeToBeaconCommittee(
                    List.of(
                        new CommitteeSubscriptionRequest(
                            1, committeeIndex, UInt64.valueOf(10), aggregationSlot, true))))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Unexpected response from Beacon Node API");
  }

  @Test
  public void subscribeToPersistentSubnets_MakesExpectedRequest() throws Exception {
    final Set<SubnetSubscription> subnetSubscriptions = Set.of(schemaObjects.subnetSubscription());

    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_OK));

    apiClient.subscribeToPersistentSubnets(subnetSubscriptions);

    RecordedRequest request = mockWebServer.takeRequest();

    assertThat(request.getMethod()).isEqualTo("POST");
    assertThat(request.getPath())
        .contains(ValidatorApiMethod.SUBSCRIBE_TO_PERSISTENT_SUBNETS.getPath(emptyMap()));
    assertThat(request.getBody().readString(StandardCharsets.UTF_8))
        .isEqualTo(asJson(subnetSubscriptions));
  }

  @Test
  public void subscribeToPersistentSubnets_WhenBadRequest_ThrowsIllegalArgumentException() {
    final Set<SubnetSubscription> subnetSubscriptions = Set.of(schemaObjects.subnetSubscription());

    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_BAD_REQUEST));
    assertThatThrownBy(() -> apiClient.subscribeToPersistentSubnets(subnetSubscriptions))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void subscribeToPersistentSubnets_WhenServerError_ThrowsRuntimeException() {
    final Set<SubnetSubscription> subnetSubscriptions = Set.of(schemaObjects.subnetSubscription());

    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_INTERNAL_SERVER_ERROR));

    assertThatThrownBy(() -> apiClient.subscribeToPersistentSubnets(subnetSubscriptions))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Unexpected response from Beacon Node API");
  }

  @Test
  void shouldIncludeAuthorizationHeaderWhenBaseUrlIncludesCredentialsForGetRequest()
      throws Exception {
    final HttpUrl url =
        mockWebServer.url("/").newBuilder().username("user").password("password").build();
    apiClient = new OkHttpValidatorRestApiClient(url, okHttpClient);
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_NO_CONTENT));

    apiClient.getFork();

    RecordedRequest request = mockWebServer.takeRequest();

    assertThat(request.getMethod()).isEqualTo("GET");
    final String authorization = request.getHeader("Authorization");
    // Base64 encoded version of credentials.
    assertThat(authorization).isEqualTo("Basic dXNlcjpwYXNzd29yZA==");
  }

  @Test
  void shouldIncludeAuthorizationHeaderWhenBaseUrlIncludesCredentialsForPostRequest()
      throws Exception {
    final HttpUrl url =
        mockWebServer.url("/").newBuilder().username("user").password("password").build();
    apiClient = new OkHttpValidatorRestApiClient(url, okHttpClient);

    final ValidatorDutiesRequest validatorDutiesRequest = schemaObjects.validatorDutiesRequest();
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_NO_CONTENT));

    apiClient.getDuties(validatorDutiesRequest);

    RecordedRequest request = mockWebServer.takeRequest();

    assertThat(request.getMethod()).isEqualTo("POST");
    final String authorization = request.getHeader("Authorization");
    // Base64 encoded version of credentials.
    assertThat(authorization).isEqualTo("Basic dXNlcjpwYXNzd29yZA==");
  }

  @Test
  void shouldThrowRateLimitedExceptionWhen429ResponseReceived() {
    mockWebServer.enqueue(new MockResponse().setResponseCode(429));

    assertThatThrownBy(() -> apiClient.getFork()).isInstanceOf(RateLimitedException.class);
  }

  private String asJson(Object object) {
    try {
      return jsonProvider.objectToJSON(object);
    } catch (JsonProcessingException e) {
      fail("Error conversing object to json", e);
      return "";
    }
  }
}
