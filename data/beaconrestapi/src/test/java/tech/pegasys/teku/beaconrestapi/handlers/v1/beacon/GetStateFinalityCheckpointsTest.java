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

import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.response.v1.beacon.FinalityCheckpointsResponse;
import tech.pegasys.teku.api.response.v1.beacon.GetStateFinalityCheckpointsResponse;
import tech.pegasys.teku.api.schema.Checkpoint;
import tech.pegasys.teku.beaconrestapi.AbstractBeaconHandlerTest;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class GetStateFinalityCheckpointsTest extends AbstractBeaconHandlerTest {
  final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  final BeaconState state = dataStructureUtil.randomBeaconState();

  @Test
  public void shouldReturnUnavailableWhenStoreNotAvailable() throws Exception {
    final GetStateFinalityCheckpoints handler =
        new GetStateFinalityCheckpoints(chainDataProvider, jsonProvider);
    when(context.pathParamMap()).thenReturn(Map.of("state_id", "head"));
    when(chainDataProvider.isStoreAvailable()).thenReturn(false);

    handler.handle(context);
    verifyStatusCode(SC_NOT_FOUND);
  }

  @Test
  public void shouldReturnFinalityCheckpointsInfo() throws Exception {
    final GetStateFinalityCheckpoints handler =
        new GetStateFinalityCheckpoints(chainDataProvider, jsonProvider);
    when(context.pathParamMap()).thenReturn(Map.of("state_id", "head"));
    when(chainDataProvider.isStoreAvailable()).thenReturn(true);
    when(chainDataProvider.stateParameterToSlot("head")).thenReturn(Optional.of(UInt64.ONE));
    when(chainDataProvider.getStateFinalityCheckpointsBySlot(any()))
        .thenReturn(
            SafeFuture.completedFuture(Optional.of(FinalityCheckpointsResponse.fromState(state))));

    handler.handle(context);

    final GetStateFinalityCheckpointsResponse response =
        getResponseFromFuture(GetStateFinalityCheckpointsResponse.class);
    assertThat(new Checkpoint(state.getPrevious_justified_checkpoint()))
        .isEqualTo(response.data.previous_justified);
    assertThat(new Checkpoint(state.getCurrent_justified_checkpoint()))
        .isEqualTo(response.data.current_justified);
    assertThat(new Checkpoint(state.getFinalized_checkpoint())).isEqualTo(response.data.finalized);
  }
}
