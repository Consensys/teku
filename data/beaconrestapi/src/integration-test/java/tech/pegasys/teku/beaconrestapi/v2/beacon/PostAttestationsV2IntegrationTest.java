/*
 * Copyright Consensys Software Inc., 2024
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

package tech.pegasys.teku.beaconrestapi.v2.beacon;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_CONSENSUS_VERSION;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import okhttp3.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.handlers.v2.beacon.PostAttestationsV2;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.json.JsonTestUtil;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.api.SubmitDataError;

@TestSpecContext(milestone = {SpecMilestone.PHASE0, SpecMilestone.ELECTRA})
public class PostAttestationsV2IntegrationTest extends AbstractDataBackedRestAPIIntegrationTest {

  private DataStructureUtil dataStructureUtil;
  private SpecMilestone specMilestone;
  private SerializableTypeDefinition<List<Attestation>> attestationsListTypeDef;

  @BeforeEach
  void setup(final TestSpecInvocationContextProvider.SpecContext specContext) {
    spec = specContext.getSpec();
    specMilestone = specContext.getSpecMilestone();
    startRestAPIAtGenesis(specMilestone);
    dataStructureUtil = specContext.getDataStructureUtil();
    attestationsListTypeDef =
        SerializableTypeDefinition.listOf(
            spec.getGenesisSchemaDefinitions()
                .getAttestationSchema()
                .castTypeToAttestationSchema()
                .getJsonTypeDefinition());
  }

  @TestTemplate
  void shouldPostAttestations_NoErrors() throws Exception {
    final List<Attestation> attestations =
        List.of(dataStructureUtil.randomAttestation(), dataStructureUtil.randomAttestation());

    when(validatorApiChannel.sendSignedAttestations(attestations))
        .thenReturn(SafeFuture.completedFuture(Collections.emptyList()));

    final Response response =
        post(
            PostAttestationsV2.ROUTE,
            JsonUtil.serialize(attestations, attestationsListTypeDef),
            Collections.emptyMap(),
            Optional.of(specMilestone.name().toLowerCase(Locale.ROOT)));

    assertThat(response.code()).isEqualTo(SC_OK);
    assertThat(response.body().string()).isEmpty();
  }

  @TestTemplate
  void shouldPartiallyPostAttestations_ReturnsErrors() throws Exception {
    final SubmitDataError firstSubmitDataError =
        new SubmitDataError(UInt64.ZERO, "Bad attestation");
    final SubmitDataError secondSubmitDataError =
        new SubmitDataError(UInt64.ONE, "Very bad attestation");

    final List<Attestation> attestations =
        List.of(
            dataStructureUtil.randomAttestation(),
            dataStructureUtil.randomAttestation(),
            dataStructureUtil.randomAttestation());

    when(validatorApiChannel.sendSignedAttestations(attestations))
        .thenReturn(
            SafeFuture.completedFuture(List.of(firstSubmitDataError, secondSubmitDataError)));

    final Response response =
        post(
            PostAttestationsV2.ROUTE,
            JsonUtil.serialize(attestations, attestationsListTypeDef),
            Collections.emptyMap(),
            Optional.of(specMilestone.name().toLowerCase(Locale.ROOT)));

    assertThat(response.code()).isEqualTo(SC_BAD_REQUEST);
    final JsonNode resultAsJsonNode = JsonTestUtil.parseAsJsonNode(response.body().string());

    assertThat(resultAsJsonNode.get("message").asText())
        .isEqualTo("Some items failed to publish, refer to errors for details");
    assertThat(resultAsJsonNode.get("failures").size()).isEqualTo(2);
    assertThat(resultAsJsonNode.get("failures").get(0).get("index").asText())
        .isEqualTo(firstSubmitDataError.index().toString());
    assertThat(resultAsJsonNode.get("failures").get(0).get("message").asText())
        .isEqualTo(firstSubmitDataError.message());
    assertThat(resultAsJsonNode.get("failures").get(1).get("index").asText())
        .isEqualTo(secondSubmitDataError.index().toString());
    assertThat(resultAsJsonNode.get("failures").get(1).get("message").asText())
        .isEqualTo(secondSubmitDataError.message());
  }

  @TestTemplate
  void shouldFailWhenMissingConsensusHeader() throws Exception {
    final List<Attestation> attestations =
        List.of(dataStructureUtil.randomAttestation(), dataStructureUtil.randomAttestation());

    when(validatorApiChannel.sendSignedAttestations(attestations))
        .thenReturn(SafeFuture.completedFuture(Collections.emptyList()));

    final Response response =
        post(PostAttestationsV2.ROUTE, JsonUtil.serialize(attestations, attestationsListTypeDef));

    assertThat(response.code()).isEqualTo(SC_BAD_REQUEST);

    final JsonNode resultAsJsonNode = JsonTestUtil.parseAsJsonNode(response.body().string());
    assertThat(resultAsJsonNode.get("message").asText())
        .isEqualTo("Missing required header value for (%s)", HEADER_CONSENSUS_VERSION);
  }

  @TestTemplate
  void shouldFailWhenBadConsensusHeaderValue() throws Exception {
    final List<Attestation> attestations =
        List.of(dataStructureUtil.randomAttestation(), dataStructureUtil.randomAttestation());

    when(validatorApiChannel.sendSignedAttestations(attestations))
        .thenReturn(SafeFuture.completedFuture(Collections.emptyList()));
    final String badConsensusHeaderValue = "NonExistingMileStone";
    final Response response =
        post(
            PostAttestationsV2.ROUTE,
            JsonUtil.serialize(attestations, attestationsListTypeDef),
            Collections.emptyMap(),
            Optional.of(badConsensusHeaderValue));

    assertThat(response.code()).isEqualTo(SC_BAD_REQUEST);

    final JsonNode resultAsJsonNode = JsonTestUtil.parseAsJsonNode(response.body().string());
    assertThat(resultAsJsonNode.get("message").asText())
        .isEqualTo(
            String.format(
                "Invalid value for (%s) header: %s",
                HEADER_CONSENSUS_VERSION, badConsensusHeaderValue));
  }
}
