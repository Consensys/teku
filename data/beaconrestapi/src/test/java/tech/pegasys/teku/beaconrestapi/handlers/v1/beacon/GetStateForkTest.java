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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_FOUND;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beaconrestapi.AbstractMigratedBeaconHandlerWithChainDataProviderTest;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.spec.SpecMilestone;

public class GetStateForkTest extends AbstractMigratedBeaconHandlerWithChainDataProviderTest {
  private RestApiRequest request;
  private GetStateFork handler;

  @BeforeEach
  void setUp() {
    initialise(SpecMilestone.PHASE0);
    genesis();
    handler = new GetStateFork(chainDataProvider);
  }

  @Test
  public void shouldReturnForkInfo() throws Exception {
    when(context.pathParamMap()).thenReturn(Map.of("state_id", "head"));
    request = new RestApiRequest(context, handler.getMetadata());

    handler.handleRequest(request);

    assertThat(getFutureResultString())
        .isEqualTo(
            "{\"data\":{\"previous_version\":\"0x00000001\",\"current_version\":\"0x00000001\",\"epoch\":\"0\"}}");
    verify(context, never()).status(any());
  }

  @Test
  public void shouldReturnNotFound() throws Exception {
    when(context.pathParamMap())
        .thenReturn(Map.of("state_id", dataStructureUtil.randomBytes32().toHexString()));
    request = new RestApiRequest(context, handler.getMetadata());

    handler.handleRequest(request);

    assertThat(getFutureResultString()).isEqualTo("{\"code\":404,\"message\":\"Not found\"}");
    verify(context).status(SC_NOT_FOUND);
  }
}
