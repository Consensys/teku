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
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.response.GetForkResponse;
import tech.pegasys.teku.api.response.v1.beacon.GenesisData;
import tech.pegasys.teku.api.response.v1.beacon.GetGenesisResponse;
import tech.pegasys.teku.beaconrestapi.AbstractBeaconHandlerTest;
import tech.pegasys.teku.datastructures.state.ForkInfo;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.util.config.Constants;

public class GetGenesisTest extends AbstractBeaconHandlerTest {
  final DataStructureUtil dataStructureUtil = new DataStructureUtil();

  @Test
  public void shouldReturnUnavailableWhenStoreNotAvailable() throws Exception {
    final GetGenesis handler = new GetGenesis(chainDataProvider, jsonProvider);
    when(chainDataProvider.isStoreAvailable()).thenReturn(false);

    handler.handle(context);
    verifyStatusCode(SC_NOT_FOUND);
  }

  @Test
  public void shouldReturnGenesisInformation() throws Exception {
    final GetGenesis handler = new GetGenesis(chainDataProvider, jsonProvider);
    final UInt64 genesisTime = dataStructureUtil.randomUInt64();
    final ForkInfo forkInfo = dataStructureUtil.randomForkInfo();
    when(chainDataProvider.isStoreAvailable()).thenReturn(true);
    when(chainDataProvider.getGenesisTime()).thenReturn(genesisTime);
    when(chainDataProvider.getForkInfo()).thenReturn(new GetForkResponse(forkInfo));

    handler.handle(context);
    final GetGenesisResponse response = getResponseObject(GetGenesisResponse.class);
    assertThat(response.data)
        .isEqualTo(
            new GenesisData(
                genesisTime, forkInfo.getGenesisValidatorsRoot(), Constants.GENESIS_FORK_VERSION));
  }
}
