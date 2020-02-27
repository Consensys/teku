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

package tech.pegasys.artemis.beaconrestapi.beaconhandlers;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static com.google.common.primitives.UnsignedLong.ZERO;


import com.google.common.primitives.UnsignedLong;
import io.javalin.http.Context;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.beaconrestapi.schema.SyncingResponse;
import tech.pegasys.artemis.provider.JsonProvider;
import tech.pegasys.artemis.sync.SyncService;
import tech.pegasys.artemis.sync.SyncStatus;
import tech.pegasys.artemis.sync.SyncingStatus;

public class NodeSyncingHandlerTest {
  private Context context = mock(Context.class);
  private final JsonProvider jsonProvider = new JsonProvider();
  private final SyncService syncService = mock(SyncService.class);

  @Test
  public void shouldReturnTrueWhenSyncing() throws Exception {
    final boolean isSyncing = true;
    final UnsignedLong startSlot = UnsignedLong.ONE;
    final UnsignedLong currentSlot = UnsignedLong.valueOf(5);
    final UnsignedLong highestSlot = UnsignedLong.valueOf(10);
    SyncingStatus syncingStatus =
        new SyncingStatus(isSyncing, new SyncStatus(startSlot, currentSlot, highestSlot));
    SyncStatus syncStatus = new SyncStatus(startSlot, currentSlot, highestSlot);
    SyncingResponse syncingResponse = new SyncingResponse(isSyncing, syncStatus);
    NodeSyncingHandler handler = new NodeSyncingHandler(syncService, jsonProvider);

    when(syncService.getSyncStatus()).thenReturn(syncingStatus);
    handler.handle(context);
    verify(context).result(jsonProvider.objectToJSON(syncingResponse));
  }

  @Test
  public void shouldReturnFalseWhenNotSyncing() throws Exception {
    final boolean isSyncing = true;
    final UnsignedLong startSlot = ZERO;
    final UnsignedLong currentSlot = ZERO;
    final UnsignedLong highestSlot = ZERO;
    SyncingStatus syncingStatus =
        new SyncingStatus(isSyncing, new SyncStatus(startSlot, currentSlot, highestSlot));
    SyncStatus syncStatus = new SyncStatus(startSlot, currentSlot, highestSlot);
    SyncingResponse syncingResponse = new SyncingResponse(isSyncing, syncStatus);
    NodeSyncingHandler handler = new NodeSyncingHandler(syncService, jsonProvider);

    when(syncService.getSyncStatus()).thenReturn(syncingStatus);
    handler.handle(context);
    verify(context).result(jsonProvider.objectToJSON(syncingResponse));
  }
}
