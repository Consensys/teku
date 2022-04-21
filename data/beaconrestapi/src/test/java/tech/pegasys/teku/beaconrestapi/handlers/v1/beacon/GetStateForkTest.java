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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_FOUND;

import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beaconrestapi.AbstractBeaconHandlerTest;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.spec.datastructures.metadata.StateAndMetaData;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class GetStateForkTest extends AbstractBeaconHandlerTest {
  private final GetStateFork handler = new GetStateFork(chainDataProvider);
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private RestApiRequest request;
  private final StateAndMetaData stateAndMetaData =
      new StateAndMetaData(
          dataStructureUtil.randomBeaconState(),
          spec.getGenesisSpec().getMilestone(),
          false,
          false,
          true);

  @BeforeEach
  void setUp() {
    when(context.pathParamMap()).thenReturn(Map.of("state_id", "head"));
  }

  @Test
  public void shouldReturnForkInfo() throws Exception {
    when(chainDataProvider.getBeaconStateAndMetadata(eq("head")))
        .thenReturn(SafeFuture.completedFuture(Optional.of(stateAndMetaData)));
    request = new RestApiRequest(context, handler.getMetadata());

    handler.handleRequest(request);

    assertThat(getBytesResultString())
        .isEqualTo(
            "{\"data\":{\"previous_version\":\"0x103ac940\",\"current_version\":\"0x6fdfab40\",\"epoch\":\"4658411424342975020\"}}");
    verify(context, never()).status(any());
  }

  @Test
  public void shouldReturnNotFound() throws Exception {
    when(chainDataProvider.getBeaconStateAndMetadata(eq("head")))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));
    request = new RestApiRequest(context, handler.getMetadata());

    handler.handleRequest(request);

    assertThat(getBytesResultString()).isEqualTo("{\"code\":404,\"message\":\"Not found\"}");
    verify(context).status(SC_NOT_FOUND);
  }
}
