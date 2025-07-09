/*
 * Copyright Consensys Software Inc., 2025
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

package tech.pegasys.teku.beaconrestapi.handlers.v2.validator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NO_CONTENT;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_CONSENSUS_VERSION;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.getResponseStringFromMetadata;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataEmptyResponse;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataErrorResponse;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.IOException;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beaconrestapi.AbstractMigratedBeaconHandlerTest;
import tech.pegasys.teku.ethereum.json.types.SharedApiTypes;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.http.RestApiConstants;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.metadata.ObjectAndMetaData;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationSchema;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionCache;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class GetAggregateAttestationV2Test extends AbstractMigratedBeaconHandlerTest {
  private final Spec specMinimalPhase0 = TestSpecFactory.createMinimalPhase0();
  private final Spec specMinimalElectra = TestSpecFactory.createMinimalElectra();

  private final DataStructureUtil phase0DataStructureUtil =
      new DataStructureUtil(specMinimalPhase0);
  private final DataStructureUtil electraDataStructureUtil =
      new DataStructureUtil(specMinimalElectra);

  private final Bytes32 blockRoot = phase0DataStructureUtil.randomBytes32();
  private final UInt64 slot = phase0DataStructureUtil.randomSlot();
  private final UInt64 committeeIndex = phase0DataStructureUtil.randomUInt64();

  @BeforeEach
  void setUp() {
    setHandler(new GetAggregateAttestationV2(validatorDataProvider, schemaDefinitionCache));
    request.setQueryParameter(RestApiConstants.ATTESTATION_DATA_ROOT, blockRoot.toHexString());
    request.setQueryParameter(RestApiConstants.SLOT, slot.toString());
    request.setQueryParameter(RestApiConstants.COMMITTEE_INDEX, committeeIndex.toString());
  }

  @Test
  public void shouldReturnAggregateAttestationForPhase0() throws JsonProcessingException {
    final ObjectAndMetaData<Attestation> phase0ResponseData = getPhase0ResponseData(false);
    when(validatorDataProvider.createAggregateAndMetaData(slot, blockRoot, committeeIndex))
        .thenReturn(SafeFuture.completedFuture(Optional.of(phase0ResponseData)));

    handler.handleRequest(request);
    assertThat(request.getResponseCode()).isEqualTo(SC_OK);
    assertThat(phase0ResponseData.getData().size()).isGreaterThan(0);
    assertThat(request.getResponseBody()).isEqualTo(phase0ResponseData);
    assertThat(request.getResponseHeaders(HEADER_CONSENSUS_VERSION))
        .isEqualTo(phase0ResponseData.getMilestone().lowerCaseName());
  }

  @Test
  public void shouldReturnAggregateAttestationForElectra() throws JsonProcessingException {
    final ObjectAndMetaData<Attestation> electraResponseData = getElectraResponseData(false);
    when(validatorDataProvider.createAggregateAndMetaData(slot, blockRoot, committeeIndex))
        .thenReturn(SafeFuture.completedFuture(Optional.of(electraResponseData)));

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_OK);
    assertThat(electraResponseData.getData().size()).isGreaterThan(0);
    assertThat(request.getResponseBody()).isEqualTo(electraResponseData);
    assertThat(request.getResponseHeaders(HEADER_CONSENSUS_VERSION))
        .isEqualTo(electraResponseData.getMilestone().lowerCaseName());
  }

  @Test
  public void shouldReturnUnavailableIfOptimistic() throws JsonProcessingException {
    final Optional<ObjectAndMetaData<Attestation>> maybeData =
        Optional.of(getElectraResponseData(true));
    when(validatorDataProvider.createAggregateAndMetaData(slot, blockRoot, committeeIndex))
        .thenReturn(SafeFuture.completedFuture(maybeData));

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_SERVICE_UNAVAILABLE);
  }

  @Test
  public void shouldReturnUnavailableIfOptimisticPhase0() throws JsonProcessingException {
    final Optional<ObjectAndMetaData<Attestation>> maybeData =
        Optional.of(getPhase0ResponseData(true));
    when(validatorDataProvider.createAggregateAndMetaData(slot, blockRoot, committeeIndex))
        .thenReturn(SafeFuture.completedFuture(maybeData));

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_SERVICE_UNAVAILABLE);
  }

  @Test
  void metadata_shouldHandle400() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_BAD_REQUEST);
  }

  @Test
  void metadata_shouldHandle500() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_INTERNAL_SERVER_ERROR);
  }

  @Test
  void metadata_shouldHandle204() {
    verifyMetadataEmptyResponse(handler, SC_NO_CONTENT);
  }

  @Test
  void metadata_shouldHandle503() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_SERVICE_UNAVAILABLE);
  }

  @Test
  void metadata_shouldHandle200_phase0Attestation() throws IOException {
    final ObjectAndMetaData<Attestation> phase0ResponseData = getPhase0ResponseData(false);
    setHandler(
        new GetAggregateAttestationV2(
            validatorDataProvider, new SchemaDefinitionCache(specMinimalPhase0)));
    final String data = getResponseStringFromMetadata(handler, SC_OK, phase0ResponseData);

    final AttestationSchema<Attestation> phase0AttestationSchema =
        schemaDefinitionCache.getSchemaDefinition(SpecMilestone.PHASE0).getAttestationSchema();
    final Attestation attestation = parseAttestationFromResponse(data, phase0AttestationSchema);

    assertThat(attestation.requiresCommitteeBits()).isFalse();
  }

  @Test
  void metadata_shouldHandle200_electraAttestation() throws IOException {
    setHandler(
        new GetAggregateAttestationV2(
            validatorDataProvider, new SchemaDefinitionCache(specMinimalElectra)));
    final String data =
        getResponseStringFromMetadata(handler, SC_OK, getElectraResponseData(false));

    final AttestationSchema<Attestation> electraAttestationSchema =
        schemaDefinitionCache.getSchemaDefinition(SpecMilestone.ELECTRA).getAttestationSchema();
    final Attestation attestation = parseAttestationFromResponse(data, electraAttestationSchema);

    assertThat(attestation.requiresCommitteeBits()).isTrue();
  }

  private <T extends Attestation> T parseAttestationFromResponse(
      final String data, final AttestationSchema<T> schema) throws JsonProcessingException {
    return JsonUtil.parse(
        data, SharedApiTypes.withDataWrapper("Attestation", schema.getJsonTypeDefinition()));
  }

  private ObjectAndMetaData<Attestation> getElectraResponseData(final boolean executionOptimistic) {

    final Attestation electraAttestation = electraDataStructureUtil.randomAttestation();
    return new ObjectAndMetaData<>(
        electraAttestation,
        specMinimalElectra.getGenesisSpec().getMilestone(),
        executionOptimistic,
        true,
        false);
  }

  private ObjectAndMetaData<Attestation> getPhase0ResponseData(final boolean executionOptimistic) {

    final Attestation phase0Attestation = phase0DataStructureUtil.randomAttestation();
    return new ObjectAndMetaData<>(
        phase0Attestation,
        specMinimalPhase0.getGenesisSpec().getMilestone(),
        executionOptimistic,
        false,
        false);
  }
}
