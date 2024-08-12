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

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.fail;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.response.v1.beacon.PostDataFailure;
import tech.pegasys.teku.api.response.v1.beacon.PostDataFailureResponse;
import tech.pegasys.teku.api.schema.Attestation;
import tech.pegasys.teku.api.schema.SignedAggregateAndProof;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.provider.JsonProvider;

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

  private String asJson(final Object object) {
    try {
      return jsonProvider.objectToJSON(object);
    } catch (JsonProcessingException e) {
      fail("Error conversing object to json", e);
      return "";
    }
  }
}
