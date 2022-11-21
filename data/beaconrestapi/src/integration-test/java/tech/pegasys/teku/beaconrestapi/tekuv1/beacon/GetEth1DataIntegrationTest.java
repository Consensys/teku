/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.beaconrestapi.tekuv1.beacon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;

import java.io.IOException;
import okhttp3.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.response.v1.teku.GetEth1DataResponse;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.handlers.tekuv1.beacon.GetEth1Data;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class GetEth1DataIntegrationTest extends AbstractDataBackedRestAPIIntegrationTest {
  private DataStructureUtil dataStructureUtil;

  @BeforeEach
  public void setup() {
    startRestAPIAtGenesis(SpecMilestone.PHASE0);
    dataStructureUtil = new DataStructureUtil(spec);
  }

  @Test
  public void shouldPassVoteToResponse() throws IOException {
    final Eth1Data eth1Data = dataStructureUtil.randomEth1Data();
    when(eth1DataProvider.getEth1Vote(any())).thenReturn(eth1Data);
    final Response response = get();
    assertThat(response.code()).isEqualTo(SC_OK);
    GetEth1DataResponse getEth1DataResponse =
        jsonProvider.jsonToObject(response.body().string(), GetEth1DataResponse.class);
    assertThat(getEth1DataResponse).isNotNull();
    assertThat(getEth1DataResponse.data.asInternalEth1Data()).isEqualTo(eth1Data);
  }

  private Response get() throws IOException {
    return getResponse(GetEth1Data.ROUTE);
  }
}
