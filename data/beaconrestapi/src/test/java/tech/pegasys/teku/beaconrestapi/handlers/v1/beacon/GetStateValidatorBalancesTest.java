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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.response.v1.beacon.GetStateValidatorBalancesResponse;
import tech.pegasys.teku.api.response.v1.beacon.ValidatorBalanceResponse;
import tech.pegasys.teku.beaconrestapi.AbstractBeaconHandlerTest;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class GetStateValidatorBalancesTest extends AbstractBeaconHandlerTest {
  private final GetStateValidatorBalances handler =
      new GetStateValidatorBalances(chainDataProvider, jsonProvider);
  private final ValidatorBalanceResponse validatorBalanceResponse =
      new ValidatorBalanceResponse(ONE, UInt64.valueOf("32000000000"));

  @Test
  public void shouldGetValidatorBalancesFromState() throws Exception {
    when(context.pathParamMap()).thenReturn(Map.of("state_id", "head"));
    when(context.queryParamMap()).thenReturn(Map.of("id", List.of("1", "2", "3,4")));
    when(chainDataProvider.getStateValidatorBalances("head", List.of("1", "2", "3", "4")))
        .thenReturn(SafeFuture.completedFuture(Optional.of(List.of(validatorBalanceResponse))));
    handler.handle(context);
    GetStateValidatorBalancesResponse response =
        getResponseFromFuture(GetStateValidatorBalancesResponse.class);
    assertThat(response.data).containsExactly(validatorBalanceResponse);
  }
}
