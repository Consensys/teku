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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.eventbus.EventBus;
import io.javalin.Javalin;
import io.javalin.core.JavalinServer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.beaconrestapi.beaconhandlers.BeaconChainHeadHandler;
import tech.pegasys.artemis.beaconrestapi.beaconhandlers.FinalizedCheckpointHandler;
import tech.pegasys.artemis.beaconrestapi.beaconhandlers.GenesisTimeHandler;
import tech.pegasys.artemis.beaconrestapi.beaconhandlers.VersionHandler;
import tech.pegasys.artemis.beaconrestapi.networkhandlers.PeerIdHandler;
import tech.pegasys.artemis.beaconrestapi.networkhandlers.PeersHandler;
import tech.pegasys.artemis.storage.ChainStorageClient;

class BeaconRestApiTest {
  private static final ChainStorageClient storageClient =
      ChainStorageClient.memoryOnlyClient(new EventBus());
  private static final JavalinServer server = mock(JavalinServer.class);
  private static final Javalin app = mock(Javalin.class);
  private static final Integer THE_PORT = 12345;

  @BeforeAll
  public static void setup() {

    when(app.server()).thenReturn(server);
    new BeaconRestApi(storageClient, null, null, THE_PORT, app);
  }

  @Test
  public void RestApiShouldHaveServerPortSet() {
    verify(server).setServerPort(THE_PORT);
  }

  @Test
  public void RestApiShouldHaveGenesisTimeEndpoint() throws Exception {
    verify(app).get(eq(GenesisTimeHandler.ROUTE), any(GenesisTimeHandler.class));
  }

  @Test
  public void RestApiShouldHaveVersionEndpoint() throws Exception {
    verify(app).get(eq(VersionHandler.ROUTE), any(VersionHandler.class));
  }

  @Test
  public void RestApiShouldHavePeerIdEndpoint() {
    verify(app).get(eq(PeerIdHandler.ROUTE), any(PeerIdHandler.class));
  }

  @Test
  public void RestApiShouldHaveFinalizedCheckpointEndpoint() {
    verify(app).get(eq(FinalizedCheckpointHandler.ROUTE), any(FinalizedCheckpointHandler.class));
  }

  @Test
  public void RestApiShouldHavePeersEndpoint() {
    verify(app).get(eq(PeersHandler.ROUTE), any(PeersHandler.class));
  }

  @Test
  public void RestApiShouldHaveChainHeadEndpoint() {
    verify(app).get(eq(BeaconChainHeadHandler.ROUTE), any(BeaconChainHeadHandler.class));
  }
}
