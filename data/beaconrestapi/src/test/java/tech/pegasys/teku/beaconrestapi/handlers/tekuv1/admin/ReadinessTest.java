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

package tech.pegasys.teku.beaconrestapi.handlers.tekuv1.admin;

import static javax.servlet.http.HttpServletResponse.SC_OK;
import static javax.servlet.http.HttpServletResponse.SC_SERVICE_UNAVAILABLE;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.CACHE_NONE;

import io.javalin.core.util.Header;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beaconrestapi.AbstractBeaconHandlerTest;
import tech.pegasys.teku.sync.events.SyncState;

public class ReadinessTest extends AbstractBeaconHandlerTest {

  @Test
  public void shouldReturnOkWhenInSyncAndReady() throws Exception {
    final Readiness handler = new Readiness(syncDataProvider, chainDataProvider);
    when(chainDataProvider.isStoreAvailable()).thenReturn(true);
    when(syncService.getCurrentSyncState()).thenReturn(SyncState.IN_SYNC);

    handler.handle(context);
    verify(context).header(Header.CACHE_CONTROL, CACHE_NONE);
    verify(context).status(SC_OK);
  }

  @Test
  public void shouldReturnUnavailableWhenStoreNotAvailable() throws Exception {
    final Readiness handler = new Readiness(syncDataProvider, chainDataProvider);
    when(chainDataProvider.isStoreAvailable()).thenReturn(false);

    handler.handle(context);
    verifyCacheStatus(CACHE_NONE);
    verifyStatusCode(SC_SERVICE_UNAVAILABLE);
  }

  @Test
  public void shouldReturnUnavailableWhenStartingUp() throws Exception {
    final Readiness handler = new Readiness(syncDataProvider, chainDataProvider);
    when(chainDataProvider.isStoreAvailable()).thenReturn(true);
    when(syncService.getCurrentSyncState()).thenReturn(SyncState.START_UP);

    handler.handle(context);
    verifyCacheStatus(CACHE_NONE);
    verifyStatusCode(SC_SERVICE_UNAVAILABLE);
  }

  @Test
  public void shouldReturnUnavailableWhenSyncing() throws Exception {
    final Readiness handler = new Readiness(syncDataProvider, chainDataProvider);
    when(chainDataProvider.isStoreAvailable()).thenReturn(true);
    when(syncService.getCurrentSyncState()).thenReturn(SyncState.SYNCING);

    handler.handle(context);
    verifyCacheStatus(CACHE_NONE);
    verifyStatusCode(SC_SERVICE_UNAVAILABLE);
  }
}
