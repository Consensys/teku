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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.beacon.sync.events.SyncState;
import tech.pegasys.teku.beaconrestapi.AbstractBeaconHandlerTest;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;

public class GetSyncingTest extends AbstractBeaconHandlerTest {
  private final ArgumentCaptor<byte[]> args = ArgumentCaptor.forClass(byte[].class);

  @Test
  public void shouldGetSyncingStatusSyncing() throws Exception {
    GetSyncing handler = new GetSyncing(syncDataProvider);
    final RestApiRequest request = new RestApiRequest(context, handler.getMetadata());
    when(syncService.getCurrentSyncState()).thenReturn(SyncState.SYNCING);
    when(syncService.getSyncStatus()).thenReturn(getSyncStatus(true, 1, 7, 10));

    handler.handleRequest(request);
    checkResponse("7", "3", true);
  }

  @Test
  public void shouldGetSyncStatusInSync() throws Exception {
    GetSyncing handler = new GetSyncing(syncDataProvider);
    final RestApiRequest request = new RestApiRequest(context, handler.getMetadata());
    when(syncService.getSyncStatus()).thenReturn(getSyncStatus(false, 1, 10, 11));
    when(syncService.getCurrentSyncState()).thenReturn(SyncState.IN_SYNC);

    handler.handleRequest(request);
    checkResponse("10", "0", false);
  }

  private void checkResponse(String headSlot, String syncDistance, boolean isSyncing) {
    final String expectedResponse =
        String.format(
            "{\"data\":{\"head_slot\":\"%s\",\"sync_distance\":\"%s\",\"is_syncing\":%s,\"is_optimistic\":false}}",
            headSlot, syncDistance, isSyncing);

    verify(context).result(args.capture());
    String response = new String(args.getValue(), StandardCharsets.UTF_8);
    assertThat(response).isEqualTo(expectedResponse);
  }
}
