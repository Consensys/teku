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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.beaconrestapi.CacheControlUtils.CACHE_NONE;

import io.javalin.core.util.Header;
import io.javalin.http.Context;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.SyncDataProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.sync.SyncService;

public class GetHealthTest {
  private Context context = mock(Context.class);
  private final SyncService syncService = mock(SyncService.class);
  private final SyncDataProvider syncDataProvider = new SyncDataProvider(syncService);
  private final ChainDataProvider chainDataProvider = mock(ChainDataProvider.class);

  @Test
  public void shouldReturnSyncingStatusWhenSyncing() throws Exception {
    final GetHealth handler = new GetHealth(syncDataProvider, chainDataProvider);

    when(chainDataProvider.isStoreAvailable()).thenReturn(true);
    when(syncService.getSyncStatus()).thenReturn(getSyncStatus(true, 1, 10, 10));

    handler.handle(context);
    verify(context).header(Header.CACHE_CONTROL, CACHE_NONE);
    verify(context).status(SC_PARTIAL_CONTENT);
  }

  @Test
  public void shouldReturnOkWhenInSyncAndReady() throws Exception {
    final GetHealth handler = new GetHealth(syncDataProvider, chainDataProvider);

    when(chainDataProvider.isStoreAvailable()).thenReturn(true);
    when(syncService.getSyncStatus()).thenReturn(getSyncStatus(false, 1, 10, 10));

    handler.handle(context);
    verify(context).header(Header.CACHE_CONTROL, CACHE_NONE);
    verify(context).status(SC_OK);
  }

  @Test
  public void shouldReturnUnavailableWhenStoreNotAvailable() throws Exception {
    final GetHealth handler = new GetHealth(syncDataProvider, chainDataProvider);

    when(chainDataProvider.isStoreAvailable()).thenReturn(false);

    handler.handle(context);
    verify(context).header(Header.CACHE_CONTROL, CACHE_NONE);
    verify(context).status(SC_SERVICE_UNAVAILABLE);
  }

  private tech.pegasys.teku.sync.SyncingStatus getSyncStatus(
      final boolean isSyncing,
      final long startSlot,
      final long currentSlot,
      final long highestSlot) {
    return new tech.pegasys.teku.sync.SyncingStatus(
        isSyncing,
        new tech.pegasys.teku.sync.SyncStatus(
            UInt64.valueOf(startSlot), UInt64.valueOf(currentSlot), UInt64.valueOf(highestSlot)));
  }
}
