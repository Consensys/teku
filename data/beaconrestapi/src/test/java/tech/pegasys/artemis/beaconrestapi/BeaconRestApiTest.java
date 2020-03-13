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
import tech.pegasys.artemis.api.DataProvider;
import tech.pegasys.artemis.beaconrestapi.handlers.beacon.GetChainHead;
import tech.pegasys.artemis.beaconrestapi.handlers.beacon.GetHead;
import tech.pegasys.artemis.beaconrestapi.handlers.beacon.GetState;
import tech.pegasys.artemis.beaconrestapi.handlers.beacon.GetStateRoot;
import tech.pegasys.artemis.beaconrestapi.handlers.beacon.GetValidators;
import tech.pegasys.artemis.beaconrestapi.handlers.beacon.PostValidators;
import tech.pegasys.artemis.beaconrestapi.handlers.network.GetEthereumNameRecord;
import tech.pegasys.artemis.beaconrestapi.handlers.network.GetListenPort;
import tech.pegasys.artemis.beaconrestapi.handlers.network.GetPeerCount;
import tech.pegasys.artemis.beaconrestapi.handlers.network.GetPeerId;
import tech.pegasys.artemis.beaconrestapi.handlers.network.GetPeers;
import tech.pegasys.artemis.beaconrestapi.handlers.node.GetGenesisTime;
import tech.pegasys.artemis.beaconrestapi.handlers.node.GetSyncing;
import tech.pegasys.artemis.beaconrestapi.handlers.node.GetVersion;
import tech.pegasys.artemis.beaconrestapi.handlers.validator.PostValidatorDuties;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.storage.CombinedChainDataClient;
import tech.pegasys.artemis.sync.SyncService;
import tech.pegasys.artemis.util.config.ArtemisConfiguration;

class BeaconRestApiTest {
  private final ChainStorageClient storageClient =
      ChainStorageClient.memoryOnlyClient(new EventBus());
  private final CombinedChainDataClient combinedChainDataClient =
      mock(CombinedChainDataClient.class);
  private final JavalinServer server = mock(JavalinServer.class);
  private final Javalin app = mock(Javalin.class);
  private final SyncService syncService = mock(SyncService.class);
  private static final Integer THE_PORT = 12345;
  private static final String THE_CONFIG =
      String.format(
          "beaconrestapi.portNumber=%d\nbeaconrestapi.enableSwagger=%s", THE_PORT, "false");

  @BeforeEach
  public void setup() {
    ArtemisConfiguration config = ArtemisConfiguration.fromString(THE_CONFIG);
    when(app.server()).thenReturn(server);
    new BeaconRestApi(
        new DataProvider(storageClient, combinedChainDataClient, null, syncService), config, app);
  }

  @Test
  public void RestApiShouldHaveServerPortSet() {
    verify(server).setServerPort(THE_PORT);
  }

  @Test
  public void RestApiShouldHaveGenesisTimeEndpoint() {
    verify(app).get(eq(GetGenesisTime.ROUTE), any(GetGenesisTime.class));
  }

  @Test
  public void RestApiShouldHaveVersionEndpoint() {
    verify(app).get(eq(GetVersion.ROUTE), any(GetVersion.class));
  }

  @Test
  public void RestApiShouldHavePeerIdEndpoint() {
    verify(app).get(eq(GetPeerId.ROUTE), any(GetPeerId.class));
  }

  @Test
  public void restApiShouldHaveBeaconHeadEndpoint() {
    verify(app).get(eq(GetHead.ROUTE), any(GetHead.class));
  }

  @Test
  public void RestApiShouldHavePeersEndpoint() {
    verify(app).get(eq(GetPeers.ROUTE), any(GetPeers.class));
  }

  @Test
  public void RestApiShouldHaveChainHeadEndpoint() {
    verify(app).get(eq(GetChainHead.ROUTE), any(GetChainHead.class));
  }

  @Test
  public void RestApiShouldHaveBeaconStateEndpoint() {
    verify(app).get(eq(GetState.ROUTE), any(GetState.class));
  }

  @Test
  public void RestApiShouldHaveSyncingEndpoint() {
    verify(app).get(eq(GetSyncing.ROUTE), any(GetSyncing.class));
  }

  @Test
  public void RestApiShouldHaveBeaconValidatorsEndpoint() {
    verify(app).get(eq(GetValidators.ROUTE), any(GetValidators.class));
  }

  @Test
  public void RestApiShouldHaveBeaconStateRootEndpoint() {
    verify(app).get(eq(GetStateRoot.ROUTE), any(GetStateRoot.class));
  }

  @Test
  public void RestApiShouldHaveNetworkEnrEndpoint() {
    verify(app).get(eq(GetEthereumNameRecord.ROUTE), any(GetEthereumNameRecord.class));
  }

  @Test
  public void RestApiShouldHaveNetworkPeerCountEndpoint() {
    verify(app).get(eq(GetPeerCount.ROUTE), any(GetPeerCount.class));
  }

  @Test
  public void RestApiShouldHaveNetworkListenPortEndpoint() {
    verify(app).get(eq(GetListenPort.ROUTE), any(GetListenPort.class));
  }

  @Test
  public void RestApiShouldHaveBeaconValidatorsPostEndpoint() {
    verify(app).post(eq(PostValidators.ROUTE), any(PostValidators.class));
  }

  @Test
  public void RestApiShouldHaveValidatorDutiesEndpoint() {
    verify(app).post(eq(PostValidatorDuties.ROUTE), any(PostValidatorDuties.class));
  }
}
