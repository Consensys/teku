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

package tech.pegasys.teku.beaconrestapi.handlers.node;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.beaconrestapi.CacheControlUtils.CACHE_NONE;

import io.javalin.core.util.Header;
import io.javalin.http.Context;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.SyncDataProvider;
import tech.pegasys.teku.api.schema.SyncStatus;
import tech.pegasys.teku.api.schema.SyncingStatus;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.provider.JsonProvider;
import tech.pegasys.teku.sync.SyncService;

public class GetSyncingTest {

  private Context context = mock(Context.class);
  private final JsonProvider jsonProvider = new JsonProvider();
  private final SyncService syncService = mock(SyncService.class);
  private final SyncDataProvider syncDataProvider = new SyncDataProvider(syncService);

  @Test
  public void shouldReturnTrueWhenSyncing() throws Exception {
    final boolean isSyncing = true;
    final UInt64 startSlot = UInt64.ONE;
    final UInt64 currentSlot = UInt64.valueOf(5);
    final UInt64 highestSlot = UInt64.valueOf(10);
    final tech.pegasys.teku.sync.events.SyncingStatus syncingStatus =
        new tech.pegasys.teku.sync.events.SyncingStatus(
            isSyncing, currentSlot, Optional.of(startSlot), Optional.of(highestSlot));
    final GetSyncing handler = new GetSyncing(syncDataProvider, jsonProvider);
    final SyncingStatus expectedResponse =
        new SyncingStatus(true, new SyncStatus(startSlot, currentSlot, highestSlot));

    when(syncService.getSyncStatus()).thenReturn(syncingStatus);
    handler.handle(context);
    verify(context).result(jsonProvider.objectToJSON(expectedResponse));
    verify(context).header(Header.CACHE_CONTROL, CACHE_NONE);
  }

  @Test
  public void shouldReturnFalseWhenNotSyncing() throws Exception {
    final boolean isSyncing = false;
    final tech.pegasys.teku.sync.events.SyncingStatus syncingStatus =
        new tech.pegasys.teku.sync.events.SyncingStatus(isSyncing, null);
    final SyncingStatus expectedResponse =
        new SyncingStatus(false, new SyncStatus(null, null, null));
    final GetSyncing handler = new GetSyncing(syncDataProvider, jsonProvider);

    when(syncService.getSyncStatus()).thenReturn(syncingStatus);
    handler.handle(context);
    verify(context).result(jsonProvider.objectToJSON(expectedResponse));
    verify(context).header(Header.CACHE_CONTROL, CACHE_NONE);
  }
}
