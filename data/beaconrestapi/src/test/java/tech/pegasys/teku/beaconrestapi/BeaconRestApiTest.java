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

import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.eventbus.EventBus;
import io.javalin.Javalin;
import io.javalin.core.JavalinServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.datastructures.eth1.Eth1Address;
import tech.pegasys.teku.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.events.EventChannels;
import tech.pegasys.teku.networks.SpecProviderFactory;
import tech.pegasys.teku.statetransition.OperationPool;
import tech.pegasys.teku.statetransition.attestation.AggregatingAttestationPool;
import tech.pegasys.teku.statetransition.attestation.AttestationManager;
import tech.pegasys.teku.statetransition.block.BlockManager;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.storage.client.MemoryOnlyRecentChainData;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.sync.SyncService;

@SuppressWarnings("unchecked")
class BeaconRestApiTest {

  private final RecentChainData storageClient = MemoryOnlyRecentChainData.create(new EventBus());
  private final CombinedChainDataClient combinedChainDataClient =
      mock(CombinedChainDataClient.class);
  private final JavalinServer server = mock(JavalinServer.class);
  private final Javalin app = mock(Javalin.class);
  private final SyncService syncService = mock(SyncService.class);
  private final EventChannels eventChannels = mock(EventChannels.class);
  private static final Integer THE_PORT = 12345;
  private final AggregatingAttestationPool attestationPool = mock(AggregatingAttestationPool.class);
  private final BlockManager blockManager = mock(BlockManager.class);
  private final AttestationManager attestationManager = mock(AttestationManager.class);
  private final OperationPool<AttesterSlashing> attesterSlashingPool = mock(OperationPool.class);
  private final OperationPool<ProposerSlashing> proposerSlashingPool = mock(OperationPool.class);
  private final OperationPool<SignedVoluntaryExit> voluntaryExitPool = mock(OperationPool.class);

  @BeforeEach
  public void setup() {
    BeaconRestApiConfig beaconRestApiConfig =
        BeaconRestApiConfig.builder()
            .restApiDocsEnabled(false)
            .restApiPort(THE_PORT)
            .eth1DepositContractAddress(Eth1Address.ZERO)
            .build();

    when(app.server()).thenReturn(server);
    new BeaconRestApi(
        new DataProvider(
            SpecProviderFactory.createMinimal(),
            storageClient,
            combinedChainDataClient,
            null,
            syncService,
            null,
            attestationPool,
            blockManager,
            attestationManager,
            attesterSlashingPool,
            proposerSlashingPool,
            voluntaryExitPool),
        beaconRestApiConfig,
        eventChannels,
        new StubAsyncRunner(),
        app);
  }

  @Test
  public void shouldHaveServerPortSet() {
    verify(server).setServerPort(THE_PORT);
  }

  @Test
  public void shouldHaveCustomNotFoundError() {
    verify(app, never()).error(eq(SC_NOT_FOUND), any());
  }

  @Test
  public void shouldHaveBeforeHandler() {
    verify(app).before(any());
  }
}
