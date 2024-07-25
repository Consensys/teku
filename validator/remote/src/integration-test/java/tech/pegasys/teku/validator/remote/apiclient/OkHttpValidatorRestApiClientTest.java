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

package tech.pegasys.teku.validator.remote.apiclient;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.fail;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NO_CONTENT;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.response.v1.beacon.PostDataFailure;
import tech.pegasys.teku.api.response.v1.beacon.PostDataFailureResponse;
import tech.pegasys.teku.api.response.v1.validator.GetAggregatedAttestationResponse;
import tech.pegasys.teku.api.response.v1.validator.GetSyncCommitteeContributionResponse;
import tech.pegasys.teku.api.schema.Attestation;
import tech.pegasys.teku.api.schema.SignedAggregateAndProof;
import tech.pegasys.teku.api.schema.SubnetSubscription;
import tech.pegasys.teku.api.schema.altair.SyncCommitteeContribution;
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
  public void sendSignedAttestation_MakesExpectedRequest() throws Exception {
    final Attestation attestation = schemaObjects.attestation();

    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_OK));

    apiClient.sendSignedAttestations(List.of(attestation));

    RecordedRequest request = mockWebServer.takeRequest();

    assertThat(request.getMethod()).isEqualTo("POST");
    assertThat(request.getPath())
        .contains(ValidatorApiMethod.SEND_SIGNED_ATTESTATION.getPath(emptyMap()));
    assertThat(request.getBody().readString(StandardCharsets.UTF_8))
        .isEqualTo(asJson(List.of(attestation)));
  }

  @Test
  public void sendSignedAttestation_WhenBadParameters_ThrowsIllegalArgumentException() {
    final Attestation attestation = schemaObjects.attestation();

    final PostDataFailureResponse response =
        new PostDataFailureResponse(
            SC_BAD_REQUEST, "Computer said no", List.of(new PostDataFailure(UInt64.ZERO, "Bad")));
    mockWebServer.enqueue(
        new MockResponse().setResponseCode(SC_BAD_REQUEST).setBody(asJson(response)));

    assertThat(apiClient.sendSignedAttestations(List.of(attestation)))
        .isPresent()
        .get()
        .usingRecursiveComparison()
        .isEqualTo(response);
  }

  @Test
  public void sendSignedAttestation_WhenServerError_ThrowsRuntimeException() {
    final Attestation attestation = schemaObjects.attestation();

    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_INTERNAL_SERVER_ERROR));

    assertThatThrownBy(() -> apiClient.sendSignedAttestations(List.of(attestation)))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Unexpected response from Beacon Node API");
  }

  @Test
  public void createAggregate_MakesExpectedRequest() throws Exception {
    final UInt64 slot = UInt64.valueOf(323);
    final Bytes32 attestationHashTreeRoot = Bytes32.random();

    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_NO_CONTENT));

    apiClient.createAggregate(slot, attestationHashTreeRoot);

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

    assertThatThrownBy(() -> apiClient.createAggregate(UInt64.ONE, attestationHashTreeRoot))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void createAggregate_WhenNotFound_ReturnsEmpty() {
    final Bytes32 attestationHashTreeRoot = Bytes32.random();

    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_NOT_FOUND));

    assertThat(apiClient.createAggregate(UInt64.ONE, attestationHashTreeRoot)).isEmpty();
  }

  @Test
  public void createAggregate_WhenServerError_ThrowsRuntimeException() {
    final Bytes32 attestationHashTreeRoot = Bytes32.random();

    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_INTERNAL_SERVER_ERROR));

    assertThatThrownBy(() -> apiClient.createAggregate(UInt64.ONE, attestationHashTreeRoot))
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

    final Optional<Attestation> attestation =
        apiClient.createAggregate(UInt64.ONE, attestationHashTreeRoot);

    assertThat(attestation).isPresent();
    assertThat(attestation.get()).usingRecursiveComparison().isEqualTo(expectedAttestation);
  }

  @Test
  public void createSyncCommitteeContribution_WhenSuccess_ReturnsContribution() {
    final SyncCommitteeContribution contribution =
        schemaObjects.syncCommitteeContribution(UInt64.ONE);

    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(SC_OK)
            .setBody(asJson(new GetSyncCommitteeContributionResponse(contribution))));

    final Optional<SyncCommitteeContribution> response =
        apiClient.createSyncCommitteeContribution(
            contribution.slot,
            contribution.subcommitteeIndex.intValue(),
            contribution.beaconBlockRoot);

    assertThat(response).isPresent();
    assertThat(response.get()).usingRecursiveComparison().isEqualTo(contribution);
  }

  @Test
  public void createSyncCommitteeContribution_WhenNotFound_ReturnsEmpty() {
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_NOT_FOUND));

    assertThat(apiClient.createSyncCommitteeContribution(UInt64.ONE, 0, Bytes32.ZERO)).isEmpty();
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
  public void sendAggregateAndProofs_WhenBadParameters_ReturnsErrorResponse() {
    final SignedAggregateAndProof signedAggregateAndProof = schemaObjects.signedAggregateAndProof();

    final PostDataFailureResponse response =
        new PostDataFailureResponse(
            SC_BAD_REQUEST, "Computer said no", List.of(new PostDataFailure(UInt64.ZERO, "Bad")));
    mockWebServer.enqueue(
        new MockResponse().setResponseCode(SC_BAD_REQUEST).setBody(asJson(response)));

    assertThat(apiClient.sendAggregateAndProofs(List.of(signedAggregateAndProof)))
        .isPresent()
        .get()
        .usingRecursiveComparison()
        .isEqualTo(response);
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

    final String expectedRequest =
        "[{\"validator_index\":\"6\",\"committee_index\":\"1\",\"committees_at_slot\":\"10\",\"slot\":\"15\",\"is_aggregator\":true},"
            + "{\"validator_index\":\"7\",\"committee_index\":\"2\",\"committees_at_slot\":\"11\",\"slot\":\"16\",\"is_aggregator\":false}]";

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
    assertThat(request.getBody().readUtf8()).isEqualTo(expectedRequest);
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
  void sendSyncCommitteeMessages_shouldReturnEmptyWhenResponseIsOk() {
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_OK));

    assertThat(apiClient.sendSyncCommitteeMessages(emptyList())).isEmpty();
  }

  @Test
  void sendSyncCommitteeMessages_shouldReturnErrorsFromBadResponse() {
    final PostDataFailureResponse response =
        new PostDataFailureResponse(
            SC_BAD_REQUEST, "Computer said no", List.of(new PostDataFailure(UInt64.ZERO, "Bad")));
    mockWebServer.enqueue(
        new MockResponse().setResponseCode(SC_BAD_REQUEST).setBody(asJson(response)));

    assertThat(apiClient.sendSyncCommitteeMessages(emptyList()))
        .isPresent()
        .get()
        .usingRecursiveComparison()
        .isEqualTo(response);
  }

  private String asJson(final Object object) {
    try {
      return jsonProvider.objectToJSON(object);
    } catch (JsonProcessingException e) {
      fail("Error conversing object to json", e);
      return "";
    }
  }
}
