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

package tech.pegasys.teku.beaconrestapi.handlers.v1.beacon;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.emptySet;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static javax.servlet.http.HttpServletResponse.SC_OK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static tech.pegasys.teku.api.response.v1.beacon.ValidatorStatus.active_exiting;
import static tech.pegasys.teku.api.response.v1.beacon.ValidatorStatus.active_ongoing;
import static tech.pegasys.teku.api.response.v1.beacon.ValidatorStatus.withdrawal_done;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.getResponseStringFromMetadata;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataEmptyResponse;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataErrorResponse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.io.Resources;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.assertj.core.api.AssertionsForClassTypes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.exceptions.BadRequestException;
import tech.pegasys.teku.api.migrated.StateValidatorData;
import tech.pegasys.teku.beaconrestapi.AbstractMigratedBeaconHandlerWithChainDataProviderTest;
import tech.pegasys.teku.infrastructure.http.HttpStatusCodes;
import tech.pegasys.teku.infrastructure.restapi.StubRestApiRequest;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.metadata.ObjectAndMetaData;

public class GetStateValidatorsTest extends AbstractMigratedBeaconHandlerWithChainDataProviderTest {
  private GetStateValidators handler;

  @BeforeEach
  void setup() {
    initialise(SpecMilestone.ALTAIR);
    genesis();

    handler = new GetStateValidators(chainDataProvider);
  }

  @Test
  public void shouldGetValidatorFromState() throws Exception {
    final StubRestApiRequest request =
        StubRestApiRequest.builder()
            .pathParameter("state_id", "head")
            .listQueryParameter("id", List.of("1", "2", "3,4"))
            .build();

    final ObjectAndMetaData<List<StateValidatorData>> expectedResponse =
        chainDataProvider
            .getStateValidators("head", List.of("1", "2", "3", "4"), emptySet())
            .get()
            .orElseThrow();

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_OK);
    assertThat(request.getResponseBody()).isEqualTo(expectedResponse);
  }

  @Test
  public void shouldGetValidatorFromStateWithList() throws Exception {
    final StubRestApiRequest request =
        StubRestApiRequest.builder()
            .pathParameter("state_id", "head")
            .listQueryParameter("id", List.of("1", "2"))
            .listQueryParameter(
                "status", List.of("active_ongoing", "active_exiting, withdrawal_done"))
            .build();

    final ObjectAndMetaData<List<StateValidatorData>> expectedResponse =
        chainDataProvider
            .getStateValidators(
                "head", List.of("1", "2"), Set.of(active_ongoing, active_exiting, withdrawal_done))
            .get()
            .orElseThrow();

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_OK);
    assertThat(request.getResponseBody()).isEqualTo(expectedResponse);
  }

  @Test
  public void shouldGetBadRequestForInvalidState() {
    final StubRestApiRequest request =
        StubRestApiRequest.builder()
            .pathParameter("state_id", "invalid")
            .listQueryParameter("id", List.of("1"))
            .build();

    assertThatThrownBy(() -> handler.handleRequest(request))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("Invalid state");
  }

  @Test
  void metadata_shouldHandle400() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_BAD_REQUEST);
  }

  @Test
  void metadata_shouldHandle404() {
    verifyMetadataEmptyResponse(new GetGenesis(chainDataProvider), SC_NOT_FOUND);
  }

  @Test
  void metadata_shouldHandle500() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_INTERNAL_SERVER_ERROR);
  }

  @Test
  void metadata_shouldHandle200() throws IOException {
    final StateValidatorData data1 =
        new StateValidatorData(
            UInt64.valueOf(0),
            dataStructureUtil.randomUInt64(),
            active_ongoing,
            dataStructureUtil.randomValidator());
    final StateValidatorData data2 =
        new StateValidatorData(
            UInt64.valueOf(1),
            dataStructureUtil.randomUInt64(),
            active_ongoing,
            dataStructureUtil.randomValidator());
    final List<StateValidatorData> value = List.of(data1, data2);
    final ObjectAndMetaData<List<StateValidatorData>> responseData = withMetaData(value);

    final String data = getResponseStringFromMetadata(handler, HttpStatusCodes.SC_OK, responseData);
    final String expected =
        Resources.toString(
            Resources.getResource(GetStateValidatorsTest.class, "getStateValidatorsTest.json"),
            UTF_8);
    AssertionsForClassTypes.assertThat(data).isEqualTo(expected);
  }
}
