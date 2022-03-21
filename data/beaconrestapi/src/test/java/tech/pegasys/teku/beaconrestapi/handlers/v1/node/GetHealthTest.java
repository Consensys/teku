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

import static javax.servlet.http.HttpServletResponse.SC_CONTINUE;
import static javax.servlet.http.HttpServletResponse.SC_PARTIAL_CONTENT;
import static javax.servlet.http.HttpServletResponse.SC_SERVICE_UNAVAILABLE;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.refEq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.SYNCING_STATUS;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beacon.sync.events.SyncState;
import tech.pegasys.teku.beaconrestapi.AbstractBeaconHandlerTest;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;

public class GetHealthTest extends AbstractBeaconHandlerTest {

  @Test
  public void shouldReturnSyncingStatusWhenSyncing() throws Exception {
    final RestApiRequest request = mock(RestApiRequest.class);
    final GetHealth handler = new GetHealth(syncDataProvider, chainDataProvider);
    when(chainDataProvider.isStoreAvailable()).thenReturn(true);
    when(syncService.getCurrentSyncState()).thenReturn(SyncState.SYNCING);

    handler.handleRequest(request);
    verify(request)
        .respondError(
            eq(SC_PARTIAL_CONTENT), refEq("Node is syncing but can serve incomplete data"));
  }

  @Test
  public void shouldReturnSyncingStatusWhenStartingUp() throws Exception {
    final RestApiRequest request = mock(RestApiRequest.class);
    final GetHealth handler = new GetHealth(syncDataProvider, chainDataProvider);
    when(chainDataProvider.isStoreAvailable()).thenReturn(true);
    when(syncService.getCurrentSyncState()).thenReturn(SyncState.START_UP);

    handler.handleRequest(request);
    verify(request)
        .respondError(
            eq(SC_PARTIAL_CONTENT), refEq("Node is syncing but can serve incomplete data"));
  }

  @Test
  public void shouldReturnCustomSyncingStatusWhenSyncing() throws Exception {
    final RestApiRequest request = mock(RestApiRequest.class);
    final GetHealth handler = new GetHealth(syncDataProvider, chainDataProvider);
    when(chainDataProvider.isStoreAvailable()).thenReturn(true);
    when(syncService.getCurrentSyncState()).thenReturn(SyncState.SYNCING);
    when(request.getQueryParamMap()).thenReturn(Map.of(SYNCING_STATUS, List.of("100")));

    handler.handleRequest(request);
    verify(request)
        .respondError(eq(SC_CONTINUE), refEq("Node is syncing but can serve incomplete data"));
  }

  @Test
  public void shouldReturnDefaultSyncingStatusWhenSyncingWrongParam() throws Exception {
    final RestApiRequest request = mock(RestApiRequest.class);
    final GetHealth handler = new GetHealth(syncDataProvider, chainDataProvider);
    when(chainDataProvider.isStoreAvailable()).thenReturn(true);
    when(syncService.getCurrentSyncState()).thenReturn(SyncState.SYNCING);
    when(request.getQueryParamMap()).thenReturn(Map.of(SYNCING_STATUS, List.of("a")));

    handler.handleRequest(request);
    verify(request)
        .respondError(
            eq(SC_PARTIAL_CONTENT), refEq("Node is syncing but can serve incomplete data"));
  }

  @Test
  public void shouldReturnDefaultSyncingStatusWhenSyncingMultipleParams() throws Exception {
    final RestApiRequest request = mock(RestApiRequest.class);
    final GetHealth handler = new GetHealth(syncDataProvider, chainDataProvider);
    when(chainDataProvider.isStoreAvailable()).thenReturn(true);
    when(syncService.getCurrentSyncState()).thenReturn(SyncState.SYNCING);
    when(request.getQueryParamMap()).thenReturn(Map.of(SYNCING_STATUS, List.of("1", "2")));

    handler.handleRequest(request);
    verify(request)
        .respondError(
            eq(SC_PARTIAL_CONTENT), refEq("Node is syncing but can serve incomplete data"));
  }

  @Test
  public void shouldReturnOkWhenInSyncAndReady() throws Exception {
    final RestApiRequest request = mock(RestApiRequest.class);
    final GetHealth handler = new GetHealth(syncDataProvider, chainDataProvider);
    when(chainDataProvider.isStoreAvailable()).thenReturn(true);
    when(syncService.getCurrentSyncState()).thenReturn(SyncState.IN_SYNC);

    handler.handleRequest(request);
    verify(request).respondOk(refEq("Node is ready"));
  }

  @Test
  public void shouldReturnUnavailableWhenStoreNotAvailable() throws Exception {
    final RestApiRequest request = mock(RestApiRequest.class);
    final GetHealth handler = new GetHealth(syncDataProvider, chainDataProvider);
    when(chainDataProvider.isStoreAvailable()).thenReturn(false);

    handler.handleRequest(request);
    verify(request)
        .respondError(eq(SC_SERVICE_UNAVAILABLE), refEq("Node not initialized or having issues"));
  }
}
