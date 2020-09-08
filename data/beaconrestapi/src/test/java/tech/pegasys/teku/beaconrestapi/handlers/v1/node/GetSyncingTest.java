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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.beaconrestapi.CacheControlUtils.CACHE_NONE;

import io.javalin.core.util.Header;
import io.javalin.http.Context;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.api.SyncDataProvider;
import tech.pegasys.teku.api.response.v1.node.SyncingResponse;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.provider.JsonProvider;
import tech.pegasys.teku.sync.SyncService;

public class GetSyncingTest {
  private final JsonProvider jsonProvider = new JsonProvider();
  private final SyncService syncService = mock(SyncService.class);
  private final SyncDataProvider syncDataProvider = new SyncDataProvider(syncService);
  private final Context context = mock(Context.class);

  private final ArgumentCaptor<String> stringArgs = ArgumentCaptor.forClass(String.class);

  @Test
  public void shouldGetSyncingStatusSyncing() throws Exception {
    GetSyncing handler = new GetSyncing(syncDataProvider, jsonProvider);
    when(syncService.getSyncStatus()).thenReturn(getSyncStatus(true, 1, 7, 10));
    handler.handle(context);
    verify(context).result(stringArgs.capture());
    verify(context).header(Header.CACHE_CONTROL, CACHE_NONE);
    String val = stringArgs.getValue();
    assertThat(val).isNotNull();
    SyncingResponse response = jsonProvider.jsonToObject(val, SyncingResponse.class);
    assertThat(response.data.headSlot).isEqualTo(UInt64.valueOf(7));
    assertThat(response.data.syncDistance).isEqualTo(UInt64.valueOf(3));
  }

  @Test
  public void shouldGetSyncStatusInSync() throws Exception {
    GetSyncing handler = new GetSyncing(syncDataProvider, jsonProvider);
    when(syncService.getSyncStatus()).thenReturn(getSyncStatus(false, 1, 10, 11));
    handler.handle(context);

    verify(context).result(stringArgs.capture());
    String val = stringArgs.getValue();
    assertThat(val).isNotNull();
    SyncingResponse response = jsonProvider.jsonToObject(val, SyncingResponse.class);
    assertThat(response.data.headSlot).isEqualTo(UInt64.valueOf(10));
    assertThat(response.data.syncDistance).isEqualTo(UInt64.valueOf(0));
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
