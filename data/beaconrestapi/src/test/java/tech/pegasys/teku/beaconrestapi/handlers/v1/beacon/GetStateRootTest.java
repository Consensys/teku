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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.assertj.core.api.AssertionsForClassTypes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beaconrestapi.AbstractMigratedBeaconHandlerWithChainDataProviderTest;
import tech.pegasys.teku.infrastructure.restapi.endpoints.JavalinRestApiRequest;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

public class GetStateRootTest extends AbstractMigratedBeaconHandlerWithChainDataProviderTest {

  @BeforeEach
  public void setup() {
    initialise(SpecMilestone.PHASE0);
    genesis();
  }

  @Test
  public void shouldReturnRootInfo() throws Exception {
    final BeaconState state = recentChainData.getBestState().orElseThrow().get();
    final GetStateRoot handler = new GetStateRoot(chainDataProvider);

    when(context.pathParamMap()).thenReturn(Map.of("state_id", "head"));
    RestApiRequest request = new JavalinRestApiRequest(context, handler.getMetadata());

    handler.handleRequest(request);

    String expected =
        String.format("{\"data\":{\"root\":\"%s\"}}", state.hashTreeRoot().toHexString());
    AssertionsForClassTypes.assertThat(getFutureResultString()).isEqualTo(expected);
    verify(context, never()).status(any());
  }
}
