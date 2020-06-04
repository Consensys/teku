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
import java.util.Optional;
import okhttp3.Response;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beaconrestapi.AbstractBeaconRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.handlers.beacon.GetHead;
import tech.pegasys.teku.storage.store.UpdatableStore;

public class GetHeadIntegrationTest extends AbstractBeaconRestAPIIntegrationTest {

  @Test
  public void shouldReturnNoContentIfStoreNotDefined() throws Exception {
    when(recentChainData.getStore()).thenReturn(null);

    final Response response = get();
    assertNoContent(response);
  }

  @Test
  public void shouldReturnNoContentIfBestBlockRootIsMissing() throws Exception {
    final UpdatableStore store = mock(UpdatableStore.class);
    when(recentChainData.getStore()).thenReturn(store);
    when(recentChainData.getBestBlockRoot()).thenReturn(Optional.empty());

    final Response response = get();
    assertNoContent(response);
  }

  @Test
  public void shouldReturnNoContentIfBestBlockIsMissing() throws Exception {
    final Bytes32 headRoot = dataStructureUtil.randomBytes32();
    when(recentChainData.getBestBlockRoot()).thenReturn(Optional.of(headRoot));
    when(recentChainData.getBlockByRoot(headRoot)).thenReturn(Optional.empty());

    final Response response = get();
    assertNoContent(response);
  }

  private Response get() throws IOException {
    return getResponse(GetHead.ROUTE);
  }
}
