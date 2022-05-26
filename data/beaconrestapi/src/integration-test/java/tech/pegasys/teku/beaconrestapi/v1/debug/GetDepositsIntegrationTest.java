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

package tech.pegasys.teku.beaconrestapi.v1.debug;

import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import static javax.servlet.http.HttpServletResponse.SC_OK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import okhttp3.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.response.v1.debug.GetDepositsResponse;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.handlers.tekuv1.beacon.GetDeposits;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.operations.Deposit;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class GetDepositsIntegrationTest extends AbstractDataBackedRestAPIIntegrationTest {
  private DataStructureUtil dataStructureUtil;

  @BeforeEach
  public void setup() {
    startRestAPIAtGenesis(SpecMilestone.PHASE0);
    dataStructureUtil = new DataStructureUtil(spec);
  }

  @Test
  public void shouldBeGoodWithNoDeposits() throws IOException {
    when(depositProvider.getAvailableDeposits()).thenReturn(new ArrayList<>());
    final Response response = get();
    assertThat(response.code()).isEqualTo(SC_OK);
    GetDepositsResponse getDepositsResponse =
        jsonProvider.jsonToObject(response.body().string(), GetDepositsResponse.class);
    assertThat(getDepositsResponse).isNotNull();
    assertThat(getDepositsResponse.data).isEmpty();
  }

  @Test
  public void shouldBeGoodWithSomeDeposits() throws IOException {
    Deposit deposit1 = dataStructureUtil.randomDeposit();
    Deposit deposit2 = dataStructureUtil.randomDeposit();
    List<Deposit> deposits = new ArrayList<>();
    deposits.add(deposit1);
    deposits.add(deposit2);
    when(depositProvider.getAvailableDeposits()).thenReturn(deposits);
    final Response response = get();
    assertThat(response.code()).isEqualTo(SC_OK);
    GetDepositsResponse getDepositsResponse =
        jsonProvider.jsonToObject(response.body().string(), GetDepositsResponse.class);
    assertThat(getDepositsResponse).isNotNull();
    assertThat(
            getDepositsResponse.data.stream()
                .map(tech.pegasys.teku.api.schema.Deposit::asInternalDeposit)
                .collect(Collectors.toList()))
        .isEqualTo(deposits);
  }

  @Test
  public void shouldBeServerErrorWhenProviderFails() throws IOException {
    when(depositProvider.getAvailableDeposits()).thenThrow(new RuntimeException(""));
    final Response response = get();
    assertThat(response.code()).isEqualTo(SC_INTERNAL_SERVER_ERROR);
  }

  private Response get() throws IOException {
    return getResponse(GetDeposits.ROUTE);
  }
}
