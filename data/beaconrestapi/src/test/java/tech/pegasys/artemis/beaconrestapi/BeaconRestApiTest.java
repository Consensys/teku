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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.beaconrestapi.beaconhandlers.BeaconChainHeadHandler;
import tech.pegasys.artemis.beaconrestapi.beaconhandlers.BeaconHeadHandler;
import tech.pegasys.artemis.beaconrestapi.beaconhandlers.BeaconStateHandler;
import tech.pegasys.artemis.beaconrestapi.beaconhandlers.GenesisTimeHandler;
import tech.pegasys.artemis.beaconrestapi.beaconhandlers.VersionHandler;
import tech.pegasys.artemis.beaconrestapi.networkhandlers.PeerIdHandler;
import tech.pegasys.artemis.beaconrestapi.networkhandlers.PeersHandler;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.storage.CombinedChainDataClient;
import tech.pegasys.artemis.util.config.ArtemisConfiguration;

class BeaconRestApiTest {
  private final ChainStorageClient storageClient =
      ChainStorageClient.memoryOnlyClient(new EventBus());
  private final CombinedChainDataClient combinedChainDataClient =
      mock(CombinedChainDataClient.class);
  private final JavalinServer server = mock(JavalinServer.class);
  private final Javalin app = mock(Javalin.class);
  private static final Integer THE_PORT = 5051;

  @BeforeEach
  public void setup() {
    ArtemisConfiguration config =
        ArtemisConfiguration.fromString(
            "beaconrestapi.portNumber=5051\nbeaconrestapi.enableSwagger=false");
    when(app.server()).thenReturn(server);
    new BeaconRestApi(storageClient, null, null, combinedChainDataClient, config, app);
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
  public void restApiShouldHaveBeaconHeadEndpoint() throws Exception {
    verify(app).get(eq(BeaconHeadHandler.ROUTE), any(BeaconHeadHandler.class));
  }

  @Test
  public void RestApiShouldHavePeersEndpoint() {
    verify(app).get(eq(PeersHandler.ROUTE), any(PeersHandler.class));
  }

  @Test
  public void RestApiShouldHaveChainHeadEndpoint() {
    verify(app).get(eq(BeaconChainHeadHandler.ROUTE), any(BeaconChainHeadHandler.class));
  }

  @Test
  public void RestApiShouldHaveBeaconStateEndpoint() {
    verify(app).get(eq(BeaconStateHandler.ROUTE), any(BeaconStateHandler.class));
  }
}
