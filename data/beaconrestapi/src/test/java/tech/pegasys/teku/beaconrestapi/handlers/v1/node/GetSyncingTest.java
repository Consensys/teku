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
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.beaconrestapi.CacheControlUtils.CACHE_NONE;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.response.v1.node.SyncingResponse;
import tech.pegasys.teku.beaconrestapi.AbstractBeaconHandlerTest;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class GetSyncingTest extends AbstractBeaconHandlerTest {
  @Test
  public void shouldGetSyncingStatusSyncing() throws Exception {
    GetSyncing handler = new GetSyncing(syncDataProvider, jsonProvider);
    when(syncService.getSyncStatus()).thenReturn(getSyncStatus(true, 1, 7, 10));
    handler.handle(context);
    verifyCacheStatus(CACHE_NONE);

    SyncingResponse response = getResponseObject(SyncingResponse.class);
    assertThat(response.data.headSlot).isEqualTo(UInt64.valueOf(7));
    assertThat(response.data.syncDistance).isEqualTo(UInt64.valueOf(3));
  }

  @Test
  public void shouldGetSyncStatusInSync() throws Exception {
    GetSyncing handler = new GetSyncing(syncDataProvider, jsonProvider);
    when(syncService.getSyncStatus()).thenReturn(getSyncStatus(false, 1, 10, 11));
    handler.handle(context);
    verifyCacheStatus(CACHE_NONE);

    SyncingResponse response = getResponseObject(SyncingResponse.class);
    assertThat(response.data.headSlot).isEqualTo(UInt64.valueOf(10));
    assertThat(response.data.syncDistance).isEqualTo(UInt64.valueOf(0));
  }
}
