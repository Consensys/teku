/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.beaconrestapi.v1.node;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;

import java.io.IOException;
import okhttp3.Response;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.response.v1.node.Syncing;
import tech.pegasys.teku.api.response.v1.node.SyncingResponse;
import tech.pegasys.teku.beacon.sync.events.SyncState;
import tech.pegasys.teku.beacon.sync.events.SyncingStatus;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.handlers.v1.node.GetSyncing;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class GetSyncingIntegrationTest extends AbstractDataBackedRestAPIIntegrationTest {

  @Test
  public void shouldGetSyncStatusWhenSyncing() throws IOException {
    startRestAPIAtGenesis();
    when(syncService.getSyncStatus()).thenReturn(getSyncStatus(true, 1, 10, 15));
    when(syncService.getCurrentSyncState()).thenReturn(SyncState.SYNCING);

    final Response response = get();
    assertThat(response.code()).isEqualTo(SC_OK);
    final SyncingResponse syncingResponse =
        jsonProvider.jsonToObject(response.body().string(), SyncingResponse.class);
    assertThat(syncingResponse.data)
        .isEqualTo(new Syncing(UInt64.valueOf(10), UInt64.valueOf(5), true));
  }

  @Test
  public void shouldGetSyncStatusWhenNotSyncing() throws IOException {
    startRestAPIAtGenesis();
    when(syncService.getSyncStatus()).thenReturn(getSyncStatus(false, 6, 11, 16));
    when(syncService.getCurrentSyncState()).thenReturn(SyncState.IN_SYNC);
    when(executionClientDataProvider.isExecutionClientAvailable()).thenReturn(true);

    final Response response = get();
    assertThat(response.code()).isEqualTo(SC_OK);
    final SyncingResponse syncingResponse =
        jsonProvider.jsonToObject(response.body().string(), SyncingResponse.class);
    assertThat(syncingResponse.data)
        // 0 sync distance because we're not syncing.
        .isEqualTo(new Syncing(UInt64.valueOf(11), UInt64.ZERO, false));
  }

  private Response get() throws IOException {
    return getResponse(GetSyncing.ROUTE);
  }

  private SyncingStatus getSyncStatus(
      final boolean isSyncing,
      final long startSlot,
      final long currentSlot,
      final long highestSlot) {
    return new SyncingStatus(
        isSyncing,
        UInt64.valueOf(currentSlot),
        UInt64.valueOf(startSlot),
        UInt64.valueOf(highestSlot));
  }
}
