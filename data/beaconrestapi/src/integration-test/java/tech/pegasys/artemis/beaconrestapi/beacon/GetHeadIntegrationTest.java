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

import static javax.servlet.http.HttpServletResponse.SC_NO_CONTENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Optional;
import okhttp3.Response;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.beaconrestapi.AbstractBeaconRestAPIIntegrationTest;
import tech.pegasys.artemis.beaconrestapi.handlers.beacon.GetHead;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.storage.Store;

public class GetHeadIntegrationTest extends AbstractBeaconRestAPIIntegrationTest {

  @Test
  public void shouldReturnNoContentIfStoreNotDefined() throws Exception {
    when(chainStorageClient.getStore()).thenReturn(null);

    final Response response = get();
    assertThat(response.code()).isEqualTo(SC_NO_CONTENT);
    assertThat(response.body().string()).isEmpty();
  }

  @Test
  public void shouldReturnNoContentIfBestBlockRootIsMissing() throws Exception {
    final Store store = mock(Store.class);
    when(chainStorageClient.getStore()).thenReturn(store);
    when(chainStorageClient.getBestBlockRoot()).thenReturn(Optional.empty());

    final Response response = get();
    assertThat(response.code()).isEqualTo(SC_NO_CONTENT);
    assertThat(response.body().string()).isEmpty();
  }

  @Test
  public void shouldReturnNoContentIfBestBlockIsMissing() throws Exception {
    final Bytes32 headRoot = DataStructureUtil.randomBytes32(1);
    when(chainStorageClient.getBestBlockRoot()).thenReturn(Optional.of(headRoot));
    when(chainStorageClient.getBlockByRoot(headRoot)).thenReturn(Optional.empty());

    final Response response = get();
    assertThat(response.code()).isEqualTo(SC_NO_CONTENT);
    assertThat(response.body().string()).isEmpty();
  }

  private Response get() throws IOException {
    return getResponse(GetHead.ROUTE);
  }
}
