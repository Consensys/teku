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

package tech.pegasys.teku.beaconrestapi.handlers.v1.node;

import static javax.servlet.http.HttpServletResponse.SC_OK;
import static javax.servlet.http.HttpServletResponse.SC_PARTIAL_CONTENT;
import static javax.servlet.http.HttpServletResponse.SC_SERVICE_UNAVAILABLE;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.beaconrestapi.CacheControlUtils.CACHE_NONE;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beaconrestapi.AbstractBeaconHandlerTest;

public class GetHealthTest extends AbstractBeaconHandlerTest {

  @Test
  public void shouldReturnSyncingStatusWhenSyncing() throws Exception {
    final GetHealth handler = new GetHealth(syncDataProvider, chainDataProvider);
    when(chainDataProvider.isStoreAvailable()).thenReturn(true);
    when(syncService.isSyncActive()).thenReturn(true);

    handler.handle(context);
    verifyCacheStatus(CACHE_NONE);
    verifyStatusCode(SC_PARTIAL_CONTENT);
  }

  @Test
  public void shouldReturnOkWhenInSyncAndReady() throws Exception {
    final GetHealth handler = new GetHealth(syncDataProvider, chainDataProvider);
    when(chainDataProvider.isStoreAvailable()).thenReturn(true);
    when(syncService.isSyncActive()).thenReturn(false);

    handler.handle(context);
    verifyCacheStatus(CACHE_NONE);
    verifyStatusCode(SC_OK);
  }

  @Test
  public void shouldReturnUnavailableWhenStoreNotAvailable() throws Exception {
    final GetHealth handler = new GetHealth(syncDataProvider, chainDataProvider);
    when(chainDataProvider.isStoreAvailable()).thenReturn(false);

    handler.handle(context);
    verifyCacheStatus(CACHE_NONE);
    verifyStatusCode(SC_SERVICE_UNAVAILABLE);
  }
}
