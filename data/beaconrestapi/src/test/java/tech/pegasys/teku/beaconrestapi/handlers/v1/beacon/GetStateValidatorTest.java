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
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;

import com.google.common.io.Resources;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.response.v1.beacon.ValidatorStatus;
import tech.pegasys.teku.api.schema.Validator;
import tech.pegasys.teku.beaconrestapi.AbstractBeaconHandlerTest;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.metadata.StateAndMetaData;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class GetStateValidatorTest extends AbstractBeaconHandlerTest {
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final GetStateValidator handler = new GetStateValidator(chainDataProvider);
  private final Validator validator = new Validator(dataStructureUtil.randomValidator());

  private final tech.pegasys.teku.spec.datastructures.state.Validator randomValidator =
      dataStructureUtil.randomValidator();

  private final GetStateValidator.StateValidatorData validatorResponse =
      new GetStateValidator.StateValidatorData(
          ONE, UInt64.valueOf("32000000000"), ValidatorStatus.active_ongoing, randomValidator);

  @Test
  public void shouldGetValidatorFromState() throws Exception {
    when(context.pathParamMap()).thenReturn(Map.of("state_id", "head", "validator_id", "1"));
    final BeaconState beaconState = dataStructureUtil.randomBeaconState(UInt64.ONE);
    final StateAndMetaData stateAndMetaData =
        new StateAndMetaData(beaconState, spec.getGenesisSpec().getMilestone(), false, false, true);
    when(chainDataProvider.getBeaconStateAndMetadata(eq("head")))
        .thenReturn(SafeFuture.completedFuture(Optional.of(stateAndMetaData)));
    when(chainDataProvider.getCurrentEpoch(any())).thenReturn(spec.getCurrentEpoch(beaconState));
    when(chainDataProvider.getValidatorSelector(any(), any())).thenReturn(IntStream.of(1));

    RestApiRequest request = new RestApiRequest(context, handler.getMetadata());
    handler.handleRequest(request);

    SafeFuture<String> future = getResultFuture();
    assertThat(future).isCompleted();
    String expected =
        Resources.toString(
            Resources.getResource(GetStateValidatorTest.class, "validatorState.json"), UTF_8);
    assertThat(future.get()).isEqualTo(expected);
  }

  @Test
  public void shouldGetNotFoundForMissingState() throws Exception {
    when(context.pathParamMap()).thenReturn(Map.of("state_id", "head", "validator_id", "1"));
    final BeaconState beaconState = dataStructureUtil.randomBeaconState(UInt64.ONE);
    when(chainDataProvider.getBeaconStateAndMetadata(eq("head")))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));
    when(chainDataProvider.getCurrentEpoch(any())).thenReturn(spec.getCurrentEpoch(beaconState));
    when(chainDataProvider.getValidatorSelector(any(), any())).thenReturn(IntStream.of(1));

    RestApiRequest request = new RestApiRequest(context, handler.getMetadata());
    handler.handleRequest(request);

    verify(context).status(SC_NOT_FOUND);
  }
}
