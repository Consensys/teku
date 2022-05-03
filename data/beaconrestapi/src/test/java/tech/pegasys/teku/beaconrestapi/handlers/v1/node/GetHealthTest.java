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
import static javax.servlet.http.HttpServletResponse.SC_OK;
import static javax.servlet.http.HttpServletResponse.SC_PARTIAL_CONTENT;
import static javax.servlet.http.HttpServletResponse.SC_SERVICE_UNAVAILABLE;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.SYNCING_STATUS;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beacon.sync.events.SyncState;
import tech.pegasys.teku.beaconrestapi.AbstractMigratedBeaconHandlerTest;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;

public class GetHealthTest extends AbstractMigratedBeaconHandlerTest {

  @Test
  public void shouldReturnSyncingStatusWhenSyncing() throws Exception {
    when(chainDataProvider.isStoreAvailable()).thenReturn(true);
    when(syncService.getCurrentSyncState()).thenReturn(SyncState.SYNCING);

    final GetHealth handler = new GetHealth(syncDataProvider, chainDataProvider);
    final RestApiRequest request = new RestApiRequest(context, handler.getMetadata());

    handler.handleRequest(request);
    checkResponseWithoutBody(SC_PARTIAL_CONTENT);
  }

  @Test
  public void shouldReturnSyncingStatusWhenStartingUp() throws Exception {
    when(chainDataProvider.isStoreAvailable()).thenReturn(true);
    when(syncService.getCurrentSyncState()).thenReturn(SyncState.START_UP);

    final GetHealth handler = new GetHealth(syncDataProvider, chainDataProvider);
    final RestApiRequest request = new RestApiRequest(context, handler.getMetadata());

    handler.handleRequest(request);
    checkResponseWithoutBody(SC_PARTIAL_CONTENT);
  }

  @Test
  public void shouldReturnCustomSyncingStatusWhenSyncing() throws Exception {
    when(context.queryParamMap()).thenReturn(Map.of(SYNCING_STATUS, List.of("100")));
    when(chainDataProvider.isStoreAvailable()).thenReturn(true);
    when(syncService.getCurrentSyncState()).thenReturn(SyncState.SYNCING);

    final GetHealth handler = new GetHealth(syncDataProvider, chainDataProvider);
    final RestApiRequest request = new RestApiRequest(context, handler.getMetadata());

    handler.handleRequest(request);
    checkResponseWithoutBody(SC_CONTINUE);
  }

  @Test
  public void shouldReturnDefaultSyncingStatusWhenSyncingWrongParam() throws Exception {
    when(chainDataProvider.isStoreAvailable()).thenReturn(true);
    when(syncService.getCurrentSyncState()).thenReturn(SyncState.SYNCING);
    when(context.queryParamMap()).thenReturn(Map.of(SYNCING_STATUS, List.of("a")));

    final GetHealth handler = new GetHealth(syncDataProvider, chainDataProvider);
    final RestApiRequest request = new RestApiRequest(context, handler.getMetadata());

    handler.handleRequest(request);
    checkResponseWithoutBody(SC_PARTIAL_CONTENT);
  }

  @Test
  public void shouldReturnDefaultSyncingStatusWhenSyncingMultipleParams() throws Exception {
    when(chainDataProvider.isStoreAvailable()).thenReturn(true);
    when(syncService.getCurrentSyncState()).thenReturn(SyncState.SYNCING);
    when(context.queryParamMap()).thenReturn(Map.of(SYNCING_STATUS, List.of("1", "2")));

    final GetHealth handler = new GetHealth(syncDataProvider, chainDataProvider);
    final RestApiRequest request = new RestApiRequest(context, handler.getMetadata());

    handler.handleRequest(request);
    checkResponseWithoutBody(SC_PARTIAL_CONTENT);
  }

  @Test
  public void shouldReturnOkWhenInSyncAndReady() throws Exception {
    when(chainDataProvider.isStoreAvailable()).thenReturn(true);
    when(syncService.getCurrentSyncState()).thenReturn(SyncState.IN_SYNC);

    final GetHealth handler = new GetHealth(syncDataProvider, chainDataProvider);
    final RestApiRequest request = new RestApiRequest(context, handler.getMetadata());

    handler.handleRequest(request);
    checkResponseWithoutBody(SC_OK);
  }

  @Test
  public void shouldReturnUnavailableWhenStoreNotAvailable() throws Exception {
    when(chainDataProvider.isStoreAvailable()).thenReturn(false);

    final GetHealth handler = new GetHealth(syncDataProvider, chainDataProvider);
    final RestApiRequest request = new RestApiRequest(context, handler.getMetadata());

    handler.handleRequest(request);
    checkResponseWithoutBody(SC_SERVICE_UNAVAILABLE);
  }

  private void checkResponseWithoutBody(final int statusCode) {
    verify(context).status(eq(statusCode));
    verify(context, never()).result(anyString());
  }
}
