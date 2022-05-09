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
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beaconrestapi.AbstractMigratedBeaconHandlerWithChainDataProviderTest;
import tech.pegasys.teku.infrastructure.restapi.StubRestApiRequest;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.metadata.StateAndMetaData;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

public class GetStateRootTest extends AbstractMigratedBeaconHandlerWithChainDataProviderTest {
  private GetStateRoot handler;
  private final StubRestApiRequest request =
      StubRestApiRequest.builder().pathParameter("state_id", "head").build();

  @BeforeEach
  public void setup() {
    initialise(SpecMilestone.PHASE0);
    genesis();
    handler = new GetStateRoot(chainDataProvider);
  }

  @Test
  public void shouldReturnRootInfo() throws Exception {
    final BeaconState state = recentChainData.getBestState().orElseThrow().get();

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_OK);
    StateAndMetaData expectedBody =
        new StateAndMetaData(state, spec.getGenesisSpec().getMilestone(), false, false, true);
    assertThat(request.getResponseBody()).isEqualTo(expectedBody);
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
  void metadata_shouldHandle200() throws IOException {
    final StateAndMetaData responseData =
        new StateAndMetaData(
            dataStructureUtil.randomBeaconState(),
            spec.getGenesisSpec().getMilestone(),
            false,
            false,
            true);
    final String data = getResponseStringFromMetadata(handler, SC_OK, responseData);
    String expected =
        String.format("{\"data\":{\"root\":\"%s\"}}", responseData.getData().hashTreeRoot());
    assertThat(data).isEqualTo(expected);
  }
}
