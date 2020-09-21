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

package tech.pegasys.teku.beaconrestapi;

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
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetGenesis;
import tech.pegasys.teku.beaconrestapi.handlers.v1.node.GetHealth;
import tech.pegasys.teku.beaconrestapi.handlers.v1.node.GetIdentity;
import tech.pegasys.teku.beaconrestapi.handlers.v1.node.GetPeerById;
import tech.pegasys.teku.beaconrestapi.handlers.v1.node.GetPeers;
import tech.pegasys.teku.beaconrestapi.handlers.v1.node.GetSyncing;
import tech.pegasys.teku.beaconrestapi.handlers.v1.node.GetVersion;
import tech.pegasys.teku.beaconrestapi.handlers.v1.validator.GetAttesterDuties;
import tech.pegasys.teku.beaconrestapi.handlers.v1.validator.GetProposerDuties;
import tech.pegasys.teku.statetransition.attestation.AggregatingAttestationPool;
import tech.pegasys.teku.statetransition.blockimport.BlockImporter;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.storage.client.MemoryOnlyRecentChainData;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.sync.SyncService;
import tech.pegasys.teku.util.config.GlobalConfiguration;

public class BeaconRestApiV1Test {
  private final RecentChainData storageClient = MemoryOnlyRecentChainData.create(new EventBus());
  private final CombinedChainDataClient combinedChainDataClient =
      mock(CombinedChainDataClient.class);
  private final JavalinServer server = mock(JavalinServer.class);
  private final Javalin app = mock(Javalin.class);
  private final SyncService syncService = mock(SyncService.class);
  private final BlockImporter blockImporter = mock(BlockImporter.class);
  private static final Integer THE_PORT = 12345;
  private AggregatingAttestationPool attestationPool = mock(AggregatingAttestationPool.class);

  @BeforeEach
  public void setup() {
    GlobalConfiguration config =
        GlobalConfiguration.builder().setRestApiPort(THE_PORT).setRestApiDocsEnabled(false).build();
    when(app.server()).thenReturn(server);
    new BeaconRestApi(
        new DataProvider(
            storageClient,
            combinedChainDataClient,
            null,
            syncService,
            null,
            blockImporter,
            attestationPool),
        config,
        app);
  }

  @Test
  public void shouldHaveVersionEndpoint() {
    verify(app).get(eq(GetVersion.ROUTE), any(GetVersion.class));
  }

  @Test
  public void shouldHaveIdentityEndpoint() {
    verify(app).get(eq(GetIdentity.ROUTE), any(GetIdentity.class));
  }

  @Test
  public void shouldHaveHealthEndpoint() {
    verify(app).get(eq(GetHealth.ROUTE), any(GetHealth.class));
  }

  @Test
  public void shouldHaveSyncingEndpoint() {
    verify(app).get(eq(GetSyncing.ROUTE), any(GetSyncing.class));
  }

  @Test
  public void shouldHavePeersEndpoint() {
    verify(app).get(eq(GetPeers.ROUTE), any(GetPeers.class));
  }

  @Test
  public void shouldHavePeerByIdEndpoint() {
    verify(app).get(eq(GetPeerById.ROUTE), any(GetPeerById.class));
  }

  @Test
  public void shouldHaveGetAttesterDutiesEndpoint() {
    verify(app).get(eq(GetAttesterDuties.ROUTE), any(GetAttesterDuties.class));
  }

  @Test
  public void shouldHaveGetProposerDutiesEndpoint() {
    verify(app).get(eq(GetProposerDuties.ROUTE), any(GetProposerDuties.class));
  }

  @Test
  public void shouldHaveGetGenesisEndpoint() {
    verify(app).get(eq(GetGenesis.ROUTE), any(GetGenesis.class));
  }
}
