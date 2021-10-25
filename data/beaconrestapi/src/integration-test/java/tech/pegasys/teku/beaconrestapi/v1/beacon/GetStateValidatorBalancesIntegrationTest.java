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

package tech.pegasys.teku.beaconrestapi.v1.beacon;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import okhttp3.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.response.v1.beacon.GetStateValidatorBalancesResponse;
import tech.pegasys.teku.api.response.v1.beacon.ValidatorBalanceResponse;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetStateValidatorBalances;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class GetStateValidatorBalancesIntegrationTest
    extends AbstractDataBackedRestAPIIntegrationTest {

  @BeforeEach
  public void setup() {
    startRestAPIAtGenesis();
  }

  @Test
  public void queryFiltersCanRemoveAllResults() throws IOException {
    final Response response = get("head", Map.of("id", "1024000"));
    assertThat(response.code()).isEqualTo(SC_OK);
    final GetStateValidatorBalancesResponse body =
        jsonProvider.jsonToObject(
            response.body().string(), GetStateValidatorBalancesResponse.class);
    assertThat(body.data).isEmpty();
  }

  @Test
  public void queryFiltersCanFilterOnValidatorId() throws IOException {
    final Response response = get("genesis", Map.of("id", "1,16"));
    assertThat(response.code()).isEqualTo(SC_OK);
    final GetStateValidatorBalancesResponse body =
        jsonProvider.jsonToObject(
            response.body().string(), GetStateValidatorBalancesResponse.class);
    assertThat(body.data)
        .containsExactly(
            new ValidatorBalanceResponse(UInt64.valueOf(1), specConfig.getMaxEffectiveBalance()));
  }

  @Test
  public void shouldReturnAllBalancesWithoutQueryParameter() throws IOException {
    final Response response = get("finalized", Collections.emptyMap());
    assertThat(response.code()).isEqualTo(SC_OK);
    final GetStateValidatorBalancesResponse body =
        jsonProvider.jsonToObject(
            response.body().string(), GetStateValidatorBalancesResponse.class);
    assertThat(body.data).hasSize(16);
  }

  public Response get(final String stateIdString, final Map<String, String> query)
      throws IOException {
    return getResponse(GetStateValidatorBalances.ROUTE.replace("{state_id}", stateIdString), query);
  }
}
