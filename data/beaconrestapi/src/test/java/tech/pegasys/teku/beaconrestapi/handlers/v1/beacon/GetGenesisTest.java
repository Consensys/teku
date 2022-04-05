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
import static org.mockito.ArgumentMatchers.refEq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beaconrestapi.AbstractBeaconHandlerTest;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.genesis.GenesisData;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class GetGenesisTest extends AbstractBeaconHandlerTest {
  final DataStructureUtil dataStructureUtil =
      new DataStructureUtil(TestSpecFactory.createDefault());

  @Test
  public void shouldReturnUnavailableWhenStoreNotAvailable() throws Exception {
    final RestApiRequest request = mock(RestApiRequest.class);
    final GetGenesis handler = new GetGenesis(chainDataProvider);
    when(chainDataProvider.isStoreAvailable()).thenReturn(false);

    handler.handleRequest(request);
    verify(request).respondWithCode(SC_NOT_FOUND);
  }

  @Test
  public void shouldReturnGenesisInformation() throws Exception {
    final RestApiRequest request = mock(RestApiRequest.class);
    final GetGenesis handler = new GetGenesis(chainDataProvider);

    final GenesisData expectedGenesisData =
        new GenesisData(dataStructureUtil.randomUInt64(), dataStructureUtil.randomBytes32());
    when(chainDataProvider.isStoreAvailable()).thenReturn(true);
    when(chainDataProvider.getStateGenesisData()).thenReturn(expectedGenesisData);

    handler.handleRequest(request);
    verify(request).respondOk(refEq(expectedGenesisData));
  }
}
