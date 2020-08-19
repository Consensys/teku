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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.fail;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.request.SubscribeToBeaconCommitteeRequest;
import tech.pegasys.teku.api.response.GetForkResponse;
import tech.pegasys.teku.api.schema.Attestation;
import tech.pegasys.teku.api.schema.BLSSignature;
import tech.pegasys.teku.api.schema.BeaconBlock;
import tech.pegasys.teku.api.schema.SignedAggregateAndProof;
import tech.pegasys.teku.api.schema.SignedBeaconBlock;
import tech.pegasys.teku.api.schema.SubnetSubscription;
import tech.pegasys.teku.api.schema.ValidatorDuties;
import tech.pegasys.teku.api.schema.ValidatorDutiesRequest;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.provider.JsonProvider;

class OkHttpValidatorRestApiClientTest {

  private final SchemaObjectsTestFixture schemaObjects = new SchemaObjectsTestFixture();
  private final JsonProvider jsonProvider = new JsonProvider();
  private final MockWebServer mockWebServer = new MockWebServer();
  private OkHttpValidatorRestApiClient apiClient;

  @BeforeEach
  public void beforeEach() throws Exception {
    mockWebServer.start();
    apiClient = new OkHttpValidatorRestApiClient(mockWebServer.url("/").toString());
  }

  @AfterEach
  public void afterEach() throws Exception {
    mockWebServer.shutdown();
  }

  @Test
  public void getFork_MakesExpectedRequest() throws Exception {
    mockWebServer.enqueue(new MockResponse().setResponseCode(204));

    apiClient.getFork();

    RecordedRequest request = mockWebServer.takeRequest();

    assertThat(request.getMethod()).isEqualTo("GET");
    assertThat(request.getPath()).contains(ValidatorApiMethod.GET_FORK.getPath());
  }

  @Test
  public void getFork_WhenServerError_ThrowsRuntimeException() {
    mockWebServer.enqueue(new MockResponse().setResponseCode(500));

    assertThatThrownBy(() -> apiClient.getFork())
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Unexpected response from Beacon Node API");
  }

  @Test
  public void getFork_WhenNoContent_ReturnsEmpty() {
    mockWebServer.enqueue(new MockResponse().setResponseCode(204));

    assertThat(apiClient.getFork()).isEmpty();
  }

  @Test
  public void getFork_WhenSuccess_ReturnsForkResponse() {
    final GetForkResponse getForkResponse = schemaObjects.getForkResponse();

    mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody(asJson(getForkResponse)));

    Optional<GetForkResponse> fork = apiClient.getFork();

    assertThat(fork).isPresent();
    assertThat(fork.get()).usingRecursiveComparison().isEqualTo(getForkResponse);
  }

  @Test
  public void getDuties_MakesExpectedRequest() throws Exception {
    final ValidatorDutiesRequest validatorDutiesRequest = schemaObjects.validatorDutiesRequest();

    mockWebServer.enqueue(new MockResponse().setResponseCode(204));

    apiClient.getDuties(validatorDutiesRequest);

    RecordedRequest request = mockWebServer.takeRequest();

    assertThat(request.getMethod()).isEqualTo("POST");
    assertThat(request.getPath()).contains(ValidatorApiMethod.GET_DUTIES.getPath());
    assertThat(request.getBody().readString(StandardCharsets.UTF_8))
        .isEqualTo(asJson(validatorDutiesRequest));
  }

  @Test
  public void getDuties_WhenServerError_ThrowsRuntimeException() {
    final ValidatorDutiesRequest validatorDutiesRequest = schemaObjects.validatorDutiesRequest();

    mockWebServer.enqueue(new MockResponse().setResponseCode(500));

    assertThatThrownBy(() -> apiClient.getDuties(validatorDutiesRequest))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Unexpected response from Beacon Node API");
  }

  @Test
  public void getDuties_WhenBadRequest_ReturnsEmpty() {
    final ValidatorDutiesRequest validatorDutiesRequest = schemaObjects.validatorDutiesRequest();

    mockWebServer.enqueue(new MockResponse().setResponseCode(404));

    assertThat(apiClient.getDuties(validatorDutiesRequest)).isEmpty();
  }

  @Test
  public void getDuties_WhenNoContent_ReturnsEmpty() {
    final ValidatorDutiesRequest validatorDutiesRequest = schemaObjects.validatorDutiesRequest();

    mockWebServer.enqueue(new MockResponse().setResponseCode(204));

    assertThat(apiClient.getDuties(validatorDutiesRequest)).isEmpty();
  }

  @Test
  public void getDuties_WhenSuccess_ReturnsValidatorDuties() {
    final ValidatorDutiesRequest validatorDutiesRequest = schemaObjects.validatorDutiesRequest();
    final ValidatorDuties validatorDuties = schemaObjects.validatorDuties();

    mockWebServer.enqueue(
        new MockResponse().setResponseCode(200).setBody(asJson(List.of(validatorDuties))));

    List<ValidatorDuties> duties = apiClient.getDuties(validatorDutiesRequest);

    assertThat(duties).hasSize(1);
    assertThat(duties).first().usingRecursiveComparison().isEqualTo(validatorDuties);
  }

  @Test
  public void createUnsignedBlock_MakesExpectedRequest() throws Exception {
    final UInt64 slot = UInt64.ONE;
    final BLSSignature blsSignature = schemaObjects.BLSSignature();
    final Optional<Bytes32> graffiti = Optional.of(Bytes32.random());

    mockWebServer.enqueue(new MockResponse().setResponseCode(204));

    apiClient.createUnsignedBlock(slot, blsSignature, graffiti);

    RecordedRequest request = mockWebServer.takeRequest();

    assertThat(request.getMethod()).isEqualTo("GET");
    assertThat(request.getPath()).contains(ValidatorApiMethod.GET_UNSIGNED_BLOCK.getPath());
    assertThat(request.getRequestUrl().queryParameter("slot")).isEqualTo(slot.toString());
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

    mockWebServer.enqueue(new MockResponse().setResponseCode(204));

    assertThat(apiClient.createUnsignedBlock(slot, blsSignature, graffiti)).isEmpty();
  }

  @Test
  public void createUnsignedBlock_WhenBadRequest_ReturnsEmpty() {
    final UInt64 slot = UInt64.ONE;
    final BLSSignature blsSignature = schemaObjects.BLSSignature();
    final Optional<Bytes32> graffiti = Optional.of(Bytes32.random());

    mockWebServer.enqueue(new MockResponse().setResponseCode(400));

    assertThat(apiClient.createUnsignedBlock(slot, blsSignature, graffiti)).isEmpty();
  }

  @Test
  public void createUnsignedBlock_WhenSuccess_ReturnsBeaconBlock() {
    final UInt64 slot = UInt64.ONE;
    final BLSSignature blsSignature = schemaObjects.BLSSignature();
    final Optional<Bytes32> graffiti = Optional.of(Bytes32.random());
    final BeaconBlock expectedBeaconBlock = schemaObjects.beaconBlock();

    mockWebServer.enqueue(
        new MockResponse().setResponseCode(200).setBody(asJson(expectedBeaconBlock)));

    Optional<BeaconBlock> beaconBlock = apiClient.createUnsignedBlock(slot, blsSignature, graffiti);

    assertThat(beaconBlock).isPresent();
    assertThat(beaconBlock.get()).usingRecursiveComparison().isEqualTo(expectedBeaconBlock);
  }

  @Test
  public void sendSignedBlock_MakesExpectedRequest() throws Exception {
    final SignedBeaconBlock signedBeaconBlock = schemaObjects.signedBeaconBlock();

    // Block has been successfully broadcast, validated and imported
    mockWebServer.enqueue(new MockResponse().setResponseCode(200));

    apiClient.sendSignedBlock(signedBeaconBlock);

    RecordedRequest request = mockWebServer.takeRequest();

    assertThat(request.getMethod()).isEqualTo("POST");
    assertThat(request.getPath()).contains(ValidatorApiMethod.SEND_SIGNED_BLOCK.getPath());
    assertThat(request.getBody().readString(StandardCharsets.UTF_8))
        .isEqualTo(asJson(signedBeaconBlock));
  }

  @Test
  public void sendSignedBlock_WhenAccepted_DoesNotFailHandlingResponse() {
    final SignedBeaconBlock signedBeaconBlock = schemaObjects.signedBeaconBlock();

    // Block has been successfully broadcast, but failed validation and has not been imported
    mockWebServer.enqueue(new MockResponse().setResponseCode(202));

    apiClient.sendSignedBlock(signedBeaconBlock);
  }

  @Test
  public void sendSignedBlock_WhenBadParameters_DoesNotFailHandlingResponse() {
    final SignedBeaconBlock signedBeaconBlock = schemaObjects.signedBeaconBlock();

    mockWebServer.enqueue(new MockResponse().setResponseCode(400));

    apiClient.sendSignedBlock(signedBeaconBlock);
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

    mockWebServer.enqueue(new MockResponse().setResponseCode(500));

    assertThatThrownBy(() -> apiClient.sendSignedBlock(signedBeaconBlock))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Unexpected response from Beacon Node API");
  }

  @Test
  public void createUnsignedAttestation_MakesExpectedRequest() throws Exception {
    final UInt64 slot = UInt64.ONE;
    final int committeeIndex = 1;

    mockWebServer.enqueue(new MockResponse().setResponseCode(204));

    apiClient.createUnsignedAttestation(slot, committeeIndex);

    RecordedRequest request = mockWebServer.takeRequest();

    assertThat(request.getMethod()).isEqualTo("GET");
    assertThat(request.getPath()).contains(ValidatorApiMethod.GET_UNSIGNED_ATTESTATION.getPath());
    assertThat(request.getRequestUrl().queryParameter("slot")).isEqualTo(slot.toString());
    assertThat(request.getRequestUrl().queryParameter("committee_index"))
        .isEqualTo(String.valueOf(committeeIndex));
  }

  @Test
  public void createUnsignedAttestation_WhenBadRequest_ReturnsEmpty() {
    final UInt64 slot = UInt64.ONE;
    final int committeeIndex = 1;

    mockWebServer.enqueue(new MockResponse().setResponseCode(400));

    assertThat(apiClient.createUnsignedAttestation(slot, committeeIndex)).isEmpty();
  }

  @Test
  public void createUnsignedAttestation_WhenNotFound_ReturnsEmpty() {
    final UInt64 slot = UInt64.ONE;
    final int committeeIndex = 1;

    // An attestation could not be created for the specified slot
    mockWebServer.enqueue(new MockResponse().setResponseCode(404));

    assertThat(apiClient.createUnsignedAttestation(slot, committeeIndex)).isEmpty();
  }

  @Test
  public void createUnsignedAttestation_WhenSuccess_ReturnsAttestation() {
    final UInt64 slot = UInt64.ONE;
    final int committeeIndex = 1;
    final Attestation expectedAttestation = schemaObjects.attestation();

    mockWebServer.enqueue(
        new MockResponse().setResponseCode(200).setBody(asJson(expectedAttestation)));

    Optional<Attestation> attestation = apiClient.createUnsignedAttestation(slot, committeeIndex);

    assertThat(attestation).isPresent();
    assertThat(attestation.get()).usingRecursiveComparison().isEqualTo(expectedAttestation);
  }

  @Test
  public void sendSignedAttestation_MakesExpectedRequest() throws Exception {
    final Attestation attestation = schemaObjects.attestation();

    mockWebServer.enqueue(new MockResponse().setResponseCode(200));

    apiClient.sendSignedAttestation(attestation);

    RecordedRequest request = mockWebServer.takeRequest();

    assertThat(request.getMethod()).isEqualTo("POST");
    assertThat(request.getPath()).contains(ValidatorApiMethod.SEND_SIGNED_ATTESTATION.getPath());
    assertThat(request.getBody().readString(StandardCharsets.UTF_8)).isEqualTo(asJson(attestation));
  }

  @Test
  public void sendSignedAttestation_WhenBadParameters_DoesNotFailHandlingResponse() {
    final Attestation attestation = schemaObjects.attestation();

    mockWebServer.enqueue(new MockResponse().setResponseCode(400));

    apiClient.sendSignedAttestation(attestation);
  }

  @Test
  public void sendSignedAttestation_WhenServerError_ThrowsRuntimeException() {
    final Attestation attestation = schemaObjects.attestation();

    mockWebServer.enqueue(new MockResponse().setResponseCode(500));

    assertThatThrownBy(() -> apiClient.sendSignedAttestation(attestation))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Unexpected response from Beacon Node API");
  }

  @Test
  public void createAggregate_MakesExpectedRequest() throws Exception {
    final UInt64 slot = UInt64.ZERO;
    final Bytes32 attestationHashTreeRoot = Bytes32.random();

    mockWebServer.enqueue(new MockResponse().setResponseCode(204));

    apiClient.createAggregate(attestationHashTreeRoot);

    RecordedRequest request = mockWebServer.takeRequest();

    assertThat(request.getMethod()).isEqualTo("GET");
    assertThat(request.getPath()).contains(ValidatorApiMethod.GET_AGGREGATE.getPath());
    assertThat(request.getRequestUrl().queryParameter("slot")).isEqualTo(slot.toString());
    assertThat(request.getRequestUrl().queryParameter("attestation_data_root"))
        .isEqualTo(attestationHashTreeRoot.toHexString());
  }

  @Test
  public void createAggregate_WhenBadParameters_ReturnsEmpty() {
    final Bytes32 attestationHashTreeRoot = Bytes32.random();

    mockWebServer.enqueue(new MockResponse().setResponseCode(400));

    assertThat(apiClient.createAggregate(attestationHashTreeRoot)).isEmpty();
  }

  @Test
  public void createAggregate_WhenServerError_ThrowsRuntimeException() {
    final Bytes32 attestationHashTreeRoot = Bytes32.random();

    mockWebServer.enqueue(new MockResponse().setResponseCode(500));

    assertThatThrownBy(() -> apiClient.createAggregate(attestationHashTreeRoot))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Unexpected response from Beacon Node API");
  }

  @Test
  public void createAggregate_WhenSuccess_ReturnsAttestation() {
    final Bytes32 attestationHashTreeRoot = Bytes32.random();
    final Attestation expectedAttestation = schemaObjects.attestation();

    mockWebServer.enqueue(
        new MockResponse().setResponseCode(200).setBody(asJson(expectedAttestation)));

    final Optional<Attestation> attestation = apiClient.createAggregate(attestationHashTreeRoot);

    assertThat(attestation).isPresent();
    assertThat(attestation.get()).usingRecursiveComparison().isEqualTo(expectedAttestation);
  }

  @Test
  public void sendAggregateAndProof_MakesExpectedRequest() throws Exception {
    final SignedAggregateAndProof signedAggregateAndProof = schemaObjects.signedAggregateAndProof();

    mockWebServer.enqueue(new MockResponse().setResponseCode(200));

    apiClient.sendAggregateAndProof(signedAggregateAndProof);

    RecordedRequest request = mockWebServer.takeRequest();

    assertThat(request.getMethod()).isEqualTo("POST");
    assertThat(request.getPath())
        .contains(ValidatorApiMethod.SEND_SIGNED_AGGREGATE_AND_PROOF.getPath());
    assertThat(request.getBody().readString(StandardCharsets.UTF_8))
        .isEqualTo(asJson(signedAggregateAndProof));
  }

  @Test
  public void sendAggregateAndProof_WhenBadParameters_DoesNotFailHandlingResponse() {
    final SignedAggregateAndProof signedAggregateAndProof = schemaObjects.signedAggregateAndProof();

    mockWebServer.enqueue(new MockResponse().setResponseCode(400));

    apiClient.sendAggregateAndProof(signedAggregateAndProof);
  }

  @Test
  public void sendAggregateAndProof_WhenServerError_ThrowsRuntimeException() {
    final SignedAggregateAndProof signedAggregateAndProof = schemaObjects.signedAggregateAndProof();

    mockWebServer.enqueue(new MockResponse().setResponseCode(500));

    assertThatThrownBy(() -> apiClient.sendAggregateAndProof(signedAggregateAndProof))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Unexpected response from Beacon Node API");
  }

  @Test
  public void subscribeToBeaconCommitteeForAggregation_MakesExpectedRequest() throws Exception {
    final int committeeIndex = 1;
    final UInt64 aggregationSlot = UInt64.ONE;

    final SubscribeToBeaconCommitteeRequest expectedRequest =
        new SubscribeToBeaconCommitteeRequest(committeeIndex, aggregationSlot);

    mockWebServer.enqueue(new MockResponse().setResponseCode(200));

    apiClient.subscribeToBeaconCommitteeForAggregation(committeeIndex, aggregationSlot);

    RecordedRequest request = mockWebServer.takeRequest();

    assertThat(request.getMethod()).isEqualTo("POST");
    assertThat(request.getPath())
        .contains(ValidatorApiMethod.SUBSCRIBE_TO_COMMITTEE_FOR_AGGREGATION.getPath());
    assertThat(request.getBody().readString(StandardCharsets.UTF_8))
        .isEqualTo(asJson(expectedRequest));
  }

  @Test
  public void
      subscribeToBeaconCommitteeForAggregation_WhenBadRequest_DoesNotFailHandlingResponse() {
    final int committeeIndex = 1;
    final UInt64 aggregationSlot = UInt64.ONE;

    mockWebServer.enqueue(new MockResponse().setResponseCode(400));

    apiClient.subscribeToBeaconCommitteeForAggregation(committeeIndex, aggregationSlot);
  }

  @Test
  public void subscribeToBeaconCommitteeForAggregation_WhenServerError_ThrowsRuntimeException() {
    final int committeeIndex = 1;
    final UInt64 aggregationSlot = UInt64.ONE;

    mockWebServer.enqueue(new MockResponse().setResponseCode(500));

    assertThatThrownBy(
            () ->
                apiClient.subscribeToBeaconCommitteeForAggregation(committeeIndex, aggregationSlot))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Unexpected response from Beacon Node API");
  }

  @Test
  public void subscribeToPersistentSubnets_MakesExpectedRequest() throws Exception {
    final Set<SubnetSubscription> subnetSubscriptions = Set.of(schemaObjects.subnetSubscription());

    mockWebServer.enqueue(new MockResponse().setResponseCode(200));

    apiClient.subscribeToPersistentSubnets(subnetSubscriptions);

    RecordedRequest request = mockWebServer.takeRequest();

    assertThat(request.getMethod()).isEqualTo("POST");
    assertThat(request.getPath())
        .contains(ValidatorApiMethod.SUBSCRIBE_TO_PERSISTENT_SUBNETS.getPath());
    assertThat(request.getBody().readString(StandardCharsets.UTF_8))
        .isEqualTo(asJson(subnetSubscriptions));
  }

  @Test
  public void subscribeToPersistentSubnets_WhenBadRequest_DoesNotFailHandlingResponse() {
    final Set<SubnetSubscription> subnetSubscriptions = Set.of(schemaObjects.subnetSubscription());

    mockWebServer.enqueue(new MockResponse().setResponseCode(400));

    apiClient.subscribeToPersistentSubnets(subnetSubscriptions);
  }

  @Test
  public void subscribeToPersistentSubnets_WhenServerError_ThrowsRuntimeException() {
    final Set<SubnetSubscription> subnetSubscriptions = Set.of(schemaObjects.subnetSubscription());

    mockWebServer.enqueue(new MockResponse().setResponseCode(500));

    assertThatThrownBy(() -> apiClient.subscribeToPersistentSubnets(subnetSubscriptions))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Unexpected response from Beacon Node API");
  }

  private String asJson(Object object) {
    try {
      return jsonProvider.objectToJSON(object);
    } catch (JsonProcessingException e) {
      fail("Error conversing object to json", e);
      return null;
    }
  }
}
