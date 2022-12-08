/*
 * Copyright ConsenSys Software Inc., 2022
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
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.getResponseStringFromMetadata;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataErrorResponse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.io.Resources;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beaconrestapi.AbstractMigratedBeaconHandlerWithChainDataProviderTest;
import tech.pegasys.teku.infrastructure.restapi.StubRestApiRequest;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.metadata.StateAndMetaData;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

public class GetStateFinalityCheckpointsTest
    extends AbstractMigratedBeaconHandlerWithChainDataProviderTest {

  private StateAndMetaData responseData;

  @BeforeEach
  void setup() throws ExecutionException, InterruptedException {
    initialise(SpecMilestone.PHASE0);
    genesis();

    chainUpdater.updateBestBlock(chainBuilder.generateNextBlock());
    chainUpdater.finalizeCurrentChain();

    final BeaconState beaconState = recentChainData.getBestState().orElseThrow().get();
    responseData =
        new StateAndMetaData(beaconState, spec.getGenesisSpec().getMilestone(), false, true, false);

    setHandler(new GetStateFinalityCheckpoints(chainDataProvider));
  }

  @Test
  public void shouldReturnFinalityCheckpointsInfo() throws JsonProcessingException {
    final StubRestApiRequest request =
        StubRestApiRequest.builder()
            .metadata(handler.getMetadata())
            .pathParameter("state_id", "head")
            .build();

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_OK);
    assertThat(request.getResponseBody()).isEqualTo(responseData);
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
    final String data = getResponseStringFromMetadata(handler, SC_OK, responseData);
    final String resource =
        Resources.toString(
            Resources.getResource(
                GetStateFinalityCheckpointsTest.class, "stateFinalityCheckpoints.json"),
            UTF_8);

    final BeaconState beaconState = responseData.getData();
    final String expected =
        String.format(
            resource,
            beaconState.getPreviousJustifiedCheckpoint().getRoot(),
            beaconState.getCurrentJustifiedCheckpoint().getRoot(),
            beaconState.getFinalizedCheckpoint().getRoot());

    assertThat(data).isEqualTo(expected);
  }
}
