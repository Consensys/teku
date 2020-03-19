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

package tech.pegasys.artemis.beaconrestapi.beacon;

import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Map;
import okhttp3.Response;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.beaconrestapi.AbstractBeaconRestAPIIntegrationTest;
import tech.pegasys.artemis.beaconrestapi.RestApiConstants;
import tech.pegasys.artemis.beaconrestapi.handlers.beacon.GetStateRoot;

public class GetStateRootIntegrationTest extends AbstractBeaconRestAPIIntegrationTest {

  @Test
  public void shouldReturnNoContentIfStoreNotDefined_queryBySlot() throws Exception {
    when(chainStorageClient.getStore()).thenReturn(null);

    final Response response = getBySlot(1);
    assertNoContent(response);
  }

  private Response getBySlot(final int slot) throws IOException {
    return getResponse(
        GetStateRoot.ROUTE, Map.of(RestApiConstants.SLOT, Integer.toString(slot, 10)));
  }
}
