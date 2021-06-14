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

package tech.pegasys.teku.beaconrestapi.handlers.v1.validator;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_SERVICE_UNAVAILABLE;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.javalin.http.Handler;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beaconrestapi.AbstractBeaconHandlerTest;
import tech.pegasys.teku.sync.events.SyncState;

public abstract class AbstractValidatorApiTest extends AbstractBeaconHandlerTest {
  protected Handler handler;

  @Test
  public void shouldReturnBadRequestIfEpochDoesNotParse() throws Exception {
    when(validatorDataProvider.isStoreAvailable()).thenReturn(true);
    when(syncService.getCurrentSyncState()).thenReturn(SyncState.IN_SYNC);
    when(context.pathParamMap()).thenReturn(Map.of("epoch", "epoch"));
    handler.handle(context);
    verifyStatusCode(SC_BAD_REQUEST);
  }

  @Test
  public void shouldReturnNotReadyWhenSyncing() throws Exception {
    when(validatorDataProvider.isStoreAvailable()).thenReturn(true);
    when(syncService.getCurrentSyncState()).thenReturn(SyncState.SYNCING);
    handler.handle(context);
    verifyStatusCode(SC_SERVICE_UNAVAILABLE);
  }

  @Test
  public void shouldReturnNotReadyWhenStartingUp() throws Exception {
    when(validatorDataProvider.isStoreAvailable()).thenReturn(true);
    when(syncService.getCurrentSyncState()).thenReturn(SyncState.START_UP);
    handler.handle(context);
    verifyStatusCode(SC_SERVICE_UNAVAILABLE);
  }

  @Test
  public void shouldReturnNotReadyWhenStoreNotReady() throws Exception {
    when(validatorDataProvider.isStoreAvailable()).thenReturn(false);

    handler.handle(context);
    verify(syncService, never()).isSyncActive();
    verifyStatusCode(SC_SERVICE_UNAVAILABLE);
  }
}
