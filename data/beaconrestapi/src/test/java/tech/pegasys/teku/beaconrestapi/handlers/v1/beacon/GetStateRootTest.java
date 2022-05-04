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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;
import org.assertj.core.api.AssertionsForClassTypes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beaconrestapi.AbstractMigratedBeaconHandlerTest;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequestImpl;
import tech.pegasys.teku.spec.datastructures.metadata.StateAndMetaData;

public class GetStateRootTest extends AbstractMigratedBeaconHandlerTest {

  @Test
  public void shouldReturnRootInfo() throws Exception {
    final GetStateRoot handler = new GetStateRoot(chainDataProvider);
    final StateAndMetaData stateAndMetaData =
        new StateAndMetaData(
            dataStructureUtil.randomBeaconState(),
            spec.getGenesisSpec().getMilestone(),
            false,
            false,
            true);
    when(chainDataProvider.getBeaconStateAndMetadata(eq("head")))
        .thenReturn(SafeFuture.completedFuture(Optional.of(stateAndMetaData)));
    when(context.pathParamMap()).thenReturn(Map.of("state_id", "head"));
    RestApiRequest request = new RestApiRequestImpl(context, handler.getMetadata());

    handler.handleRequest(request);

    String expected =
        String.format(
            "{\"data\":{\"root\":\"%s\"}}",
            stateAndMetaData.getData().hashTreeRoot().toHexString());
    AssertionsForClassTypes.assertThat(getFutureResultString()).isEqualTo(expected);
    verify(context, never()).status(any());
  }
}
