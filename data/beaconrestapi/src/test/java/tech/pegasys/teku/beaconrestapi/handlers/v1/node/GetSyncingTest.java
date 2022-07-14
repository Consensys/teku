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

package tech.pegasys.teku.beaconrestapi.handlers.v1.node;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.getResponseStringFromMetadata;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataErrorResponse;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beacon.sync.events.SyncState;
import tech.pegasys.teku.beaconrestapi.AbstractMigratedBeaconHandlerTest;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.validator.api.SyncingStatus;

public class GetSyncingTest extends AbstractMigratedBeaconHandlerTest {

  @BeforeEach
  void setUp() {
    setHandler(new GetSyncing(syncDataProvider));
  }

  @Test
  public void shouldGetSyncingStatusSyncing() throws Exception {
    when(syncService.getCurrentSyncState()).thenReturn(SyncState.SYNCING);
    when(syncService.getSyncStatus()).thenReturn(getSyncStatus(true, 1, 7, 10));

    handler.handleRequest(request);
    assertThat(request.getResponseCode()).isEqualTo(SC_OK);
    assertThat(request.getResponseBody())
        .isEqualTo(
            new SyncingStatus(UInt64.valueOf(7), UInt64.valueOf(3), true, Optional.of(false)));
  }

  @Test
  public void shouldGetSyncStatusInSync() throws Exception {
    when(syncService.getSyncStatus()).thenReturn(getSyncStatus(false, 1, 10, 11));
    when(syncService.getCurrentSyncState()).thenReturn(SyncState.IN_SYNC);

    handler.handleRequest(request);
    assertThat(request.getResponseCode()).isEqualTo(SC_OK);
    assertThat(request.getResponseBody())
        .isEqualTo(new SyncingStatus(UInt64.valueOf(10), UInt64.ZERO, false, Optional.of(false)));
  }

  @Test
  void metadata_shouldHandle400() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_BAD_REQUEST);
  }

  @Test
  void metadata_shouldHandle500() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_INTERNAL_SERVER_ERROR);
  }

  @Test
  void metadata_shouldHandle200() throws JsonProcessingException {
    final String data =
        getResponseStringFromMetadata(
            handler,
            SC_OK,
            new SyncingStatus(UInt64.valueOf(10), UInt64.ZERO, false, Optional.of(false)));
    assertThat(data)
        .isEqualTo(
            "{\"data\":{\"head_slot\":\"10\",\"sync_distance\":\"0\",\"is_syncing\":false,\"is_optimistic\":false}}");
  }
}
