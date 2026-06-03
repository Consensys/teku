/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.beaconrestapi.handlers.v1.beacon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFuture.completedFuture;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_ACCEPTABLE;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.getResponseSszFromMetadata;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.getResponseStringFromMetadata;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataErrorResponse;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.migrated.StateBuilderData;
import tech.pegasys.teku.beaconrestapi.AbstractMigratedBeaconHandlerTest;
import tech.pegasys.teku.ethereum.json.types.beacon.StateBuilderRequestBodyType;
import tech.pegasys.teku.infrastructure.restapi.StubRestApiRequest;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.metadata.ObjectAndMetaData;
import tech.pegasys.teku.spec.datastructures.state.versions.gloas.Builder;

public class PostStateBuildersTest extends AbstractMigratedBeaconHandlerTest {

  @BeforeEach
  void setup() {
    setSpec(TestSpecFactory.createMinimalGloas());
    setHandler(new PostStateBuilders(chainDataProvider));
  }

  @Test
  void shouldGetBuildersFromStateWithIds() throws Exception {
    final StateBuilderRequestBodyType requestBody = new StateBuilderRequestBodyType(List.of("0"));
    final StubRestApiRequest request =
        StubRestApiRequest.builder()
            .metadata(handler.getMetadata())
            .pathParameter("state_id", "head")
            .build();
    request.setRequestBody(requestBody);
    final ObjectAndMetaData<SszList<StateBuilderData>> expectedResponse =
        new ObjectAndMetaData<>(getBuildersList(), SpecMilestone.GLOAS, false, true, false);
    when(chainDataProvider.getStateBuilders("head", List.of("0"), List.of()))
        .thenReturn(completedFuture(Optional.of(expectedResponse)));

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_OK);
    assertThat(request.getResponseBody()).isEqualTo(expectedResponse);
  }

  @Test
  void shouldGetBuildersFromStateWithIdsAndStatuses() throws Exception {
    final StateBuilderRequestBodyType requestBody =
        new StateBuilderRequestBodyType(List.of("0"), List.of(StateBuilderData.STATUS_ACTIVE));
    final StubRestApiRequest request =
        StubRestApiRequest.builder()
            .metadata(handler.getMetadata())
            .pathParameter("state_id", "head")
            .build();
    request.setRequestBody(requestBody);
    final ObjectAndMetaData<SszList<StateBuilderData>> expectedResponse =
        new ObjectAndMetaData<>(getBuildersList(), SpecMilestone.GLOAS, false, true, false);
    when(chainDataProvider.getStateBuilders(
            "head", List.of("0"), List.of(StateBuilderData.STATUS_ACTIVE)))
        .thenReturn(completedFuture(Optional.of(expectedResponse)));

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_OK);
    assertThat(request.getResponseBody()).isEqualTo(expectedResponse);
  }

  @Test
  void shouldGetBuildersFromStateWithEmptyRequestBody() throws Exception {
    final StubRestApiRequest request =
        StubRestApiRequest.builder()
            .metadata(handler.getMetadata())
            .pathParameter("state_id", "head")
            .build();
    final ObjectAndMetaData<SszList<StateBuilderData>> expectedResponse =
        new ObjectAndMetaData<>(getBuildersList(), SpecMilestone.GLOAS, false, true, false);
    when(chainDataProvider.getStateBuilders("head", List.of(), List.of()))
        .thenReturn(completedFuture(Optional.of(expectedResponse)));

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_OK);
    assertThat(request.getResponseBody()).isEqualTo(expectedResponse);
  }

  @Test
  void metadata_shouldHandle200() throws IOException {
    final StateBuilderData stateBuilderData = getBuildersList().get(0);
    final ObjectAndMetaData<SszList<StateBuilderData>> responseData =
        new ObjectAndMetaData<>(
            StateBuilderData.SSZ_LIST_SCHEMA.of(stateBuilderData),
            SpecMilestone.GLOAS,
            false,
            true,
            false);

    final String data = getResponseStringFromMetadata(handler, SC_OK, responseData);

    assertThat(data)
        .contains("\"execution_optimistic\":false")
        .contains("\"finalized\":false")
        .contains("\"index\":\"0\"")
        .contains("\"status\":1")
        .contains("\"builder\":")
        .contains("\"pubkey\":\"" + stateBuilderData.getBuilder().getPublicKey() + "\"");
  }

  @Test
  void metadata_shouldHandle200OctetStream() throws IOException {
    final SszList<StateBuilderData> builders = getBuildersList();
    final ObjectAndMetaData<SszList<StateBuilderData>> responseData =
        new ObjectAndMetaData<>(builders, SpecMilestone.GLOAS, false, true, false);

    final byte[] data = getResponseSszFromMetadata(handler, SC_OK, responseData);

    assertThat(Bytes.of(data)).isEqualTo(builders.sszSerialize());
  }

  @Test
  void metadata_shouldHandle400() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_BAD_REQUEST);
  }

  @Test
  void metadata_shouldHandle404() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_NOT_FOUND);
  }

  @Test
  void metadata_shouldHandle406() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_NOT_ACCEPTABLE);
  }

  @Test
  void metadata_shouldHandle500() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_INTERNAL_SERVER_ERROR);
  }

  @Test
  void metadata_shouldHandle503() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_SERVICE_UNAVAILABLE);
  }

  private SszList<StateBuilderData> getBuildersList() {
    final Builder builder = dataStructureUtil.randomBuilder();
    return StateBuilderData.SSZ_LIST_SCHEMA.of(
        StateBuilderData.create(UInt64.ZERO, StateBuilderData.STATUS_ACTIVE, builder));
  }
}
