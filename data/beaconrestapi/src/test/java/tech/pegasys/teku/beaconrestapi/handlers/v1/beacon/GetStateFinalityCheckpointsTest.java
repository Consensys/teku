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
import static org.mockito.Mockito.when;

import java.util.Map;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beaconrestapi.AbstractMigratedBeaconHandlerWithChainDataProviderTest;
import tech.pegasys.teku.infrastructure.restapi.endpoints.JavalinRestApiRequest;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

public class GetStateFinalityCheckpointsTest
    extends AbstractMigratedBeaconHandlerWithChainDataProviderTest {

  @Test
  public void shouldReturnFinalityCheckpointsInfo() throws Exception {
    initialise(SpecMilestone.PHASE0);
    genesis();

    chainUpdater.updateBestBlock(chainBuilder.generateNextBlock());
    chainUpdater.finalizeCurrentChain();

    final GetStateFinalityCheckpoints handler = new GetStateFinalityCheckpoints(chainDataProvider);
    final BeaconState beaconState = recentChainData.getBestState().orElseThrow().get();

    when(context.pathParamMap()).thenReturn(Map.of("state_id", "head"));
    final RestApiRequest request = new JavalinRestApiRequest(context, handler.getMetadata());

    handler.handleRequest(request);

    String expected =
        String.format(
            "{\"data\":{\"previous_justified\":{\"epoch\":\"%s\",\"root\":\"%s\"},\"current_justified\":"
                + "{\"epoch\":\"%s\",\"root\":\"%s\"},\"finalized\":{\"epoch\":\"%s\",\"root\":\"%s\"}}}",
            beaconState.getPreviousJustifiedCheckpoint().getEpoch(),
            beaconState.getPreviousJustifiedCheckpoint().getRoot(),
            beaconState.getCurrentJustifiedCheckpoint().getEpoch(),
            beaconState.getCurrentJustifiedCheckpoint().getRoot(),
            beaconState.getFinalizedCheckpoint().getEpoch(),
            beaconState.getFinalizedCheckpoint().getRoot());

    assertThat(getFutureResultString()).isEqualTo(expected);
  }
}
