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

package tech.pegasys.teku.beaconrestapi.beacon;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import okhttp3.Response;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beaconrestapi.AbstractBeaconRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.RestApiConstants;
import tech.pegasys.teku.beaconrestapi.handlers.beacon.GetStateRoot;
import tech.pegasys.teku.storage.Store;

public class GetStateRootIntegrationTest extends AbstractBeaconRestAPIIntegrationTest {

  @Test
  public void shouldReturnNoContentIfStoreNotDefined_queryBySlot() throws Exception {
    when(recentChainData.getStore()).thenReturn(null);

    final Response response = getBySlot(1);
    assertNoContent(response);
  }

  @Test
  public void shouldReturnNoContentIfHeadRootUnavailable_queryBySlot() throws Exception {
    final Store store = mock(Store.class);
    when(recentChainData.getStore()).thenReturn(store);
    when(recentChainData.getBestBlockRoot()).thenReturn(Optional.empty());

    final Response response = getBySlot(1);
    assertNoContent(response);
  }

  private Response getBySlot(final int slot) throws IOException {
    return getResponse(
        GetStateRoot.ROUTE, Map.of(RestApiConstants.SLOT, Integer.toString(slot, 10)));
  }
}
