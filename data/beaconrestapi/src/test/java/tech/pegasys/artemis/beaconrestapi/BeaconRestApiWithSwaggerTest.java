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

package tech.pegasys.artemis.beaconrestapi;

import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.eventbus.EventBus;
import io.javalin.Javalin;
import io.javalin.core.JavalinServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.api.DataProvider;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.storage.CombinedChainDataClient;
import tech.pegasys.artemis.storage.api.DiskUpdateChannel;
import tech.pegasys.artemis.sync.SyncService;
import tech.pegasys.artemis.util.config.ArtemisConfiguration;

public class BeaconRestApiWithSwaggerTest {
  private final ChainStorageClient storageClient =
      ChainStorageClient.memoryOnlyClient(new EventBus(), mock(DiskUpdateChannel.class));
  private final CombinedChainDataClient combinedChainDataClient =
      mock(CombinedChainDataClient.class);
  private final JavalinServer server = mock(JavalinServer.class);
  private final Javalin app = mock(Javalin.class);
  private final SyncService syncService = mock(SyncService.class);
  private static final Integer THE_PORT = 12345;

  @BeforeEach
  public void setup() {
    ArtemisConfiguration config =
        ArtemisConfiguration.builder().setRestApiPort(THE_PORT).setRestApiDocsEnabled(true).build();
    when(app.server()).thenReturn(server);
    new BeaconRestApi(
        new DataProvider(storageClient, combinedChainDataClient, null, syncService, null, null),
        config,
        app);
  }

  @Test
  public void RestApiShouldHaveCustomNotFoundError() {
    verify(app).error(eq(SC_NOT_FOUND), any());
  }
}
