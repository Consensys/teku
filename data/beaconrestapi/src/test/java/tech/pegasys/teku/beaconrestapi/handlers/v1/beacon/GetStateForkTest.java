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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.concurrent.ExecutionException;
import javax.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beaconrestapi.AbstractMigratedBeaconHandlerWithChainDataProviderTest;
import tech.pegasys.teku.infrastructure.http.HttpErrorResponse;
import tech.pegasys.teku.infrastructure.restapi.StubRestApiRequest;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.metadata.StateAndMetaData;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

public class GetStateForkTest extends AbstractMigratedBeaconHandlerWithChainDataProviderTest {
  private GetStateFork handler;
  private StateAndMetaData responseData;
  private final StubRestApiRequest request =
      StubRestApiRequest.builder().pathParameter("state_id", "head").build();

  @BeforeEach
  void setup() throws ExecutionException, InterruptedException {
    initialise(SpecMilestone.PHASE0);
    genesis();
    handler = new GetStateFork(chainDataProvider);

    final BeaconState beaconState = recentChainData.getBestState().orElseThrow().get();
    responseData =
        new StateAndMetaData(beaconState, spec.getGenesisSpec().getMilestone(), false, false, true);
  }

  @Test
  public void shouldReturnForkInfo() throws Exception {
    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_OK);
    assertThat(request.getResponseBody()).isInstanceOf(StateAndMetaData.class);
    assertThat(request.getResponseBody()).isEqualTo(responseData);
  }

  @Test
  public void shouldReturnNotFound() throws Exception {
    final StubRestApiRequest request =
        StubRestApiRequest.builder()
            .pathParameter("state_id", dataStructureUtil.randomBytes32().toHexString())
            .build();

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_NOT_FOUND);
    assertThat(request.getResponseBody())
        .isEqualTo(new HttpErrorResponse(SC_NOT_FOUND, "Not found"));
  }

  @Test
  void metadata_shouldHandle400() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_BAD_REQUEST);
  }

  @Test
  void metadata_shouldHandle404() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, HttpServletResponse.SC_NOT_FOUND);
  }

  @Test
  void metadata_shouldHandle500() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_INTERNAL_SERVER_ERROR);
  }

  @Test
  void metadata_shouldHandle200() throws JsonProcessingException {
    final String data = getResponseStringFromMetadata(handler, SC_OK, responseData);
    assertThat(data)
        .isEqualTo(
            "{\"data\":{\"previous_version\":\"0x00000001\",\"current_version\":\"0x00000001\",\"epoch\":\"0\"}}");
  }
}
