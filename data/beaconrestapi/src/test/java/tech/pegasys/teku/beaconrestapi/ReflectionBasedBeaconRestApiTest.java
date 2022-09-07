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

package tech.pegasys.teku.beaconrestapi;

import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.javalin.Javalin;
import io.javalin.jetty.JettyServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.beacon.sync.SyncService;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.bytes.Eth1Address;
import tech.pegasys.teku.infrastructure.events.EventChannels;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.statetransition.OperationPool;
import tech.pegasys.teku.statetransition.attestation.AggregatingAttestationPool;
import tech.pegasys.teku.statetransition.attestation.AttestationManager;
import tech.pegasys.teku.statetransition.block.BlockManager;
import tech.pegasys.teku.statetransition.forkchoice.ProposersDataManager;
import tech.pegasys.teku.statetransition.synccommittee.SyncCommitteeContributionPool;
import tech.pegasys.teku.statetransition.validatorcache.ActiveValidatorChannel;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.storage.client.MemoryOnlyRecentChainData;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.validator.coordinator.DepositProvider;
import tech.pegasys.teku.validator.coordinator.Eth1DataCache;
import tech.pegasys.teku.validator.coordinator.Eth1DataProvider;

@SuppressWarnings("unchecked")
class ReflectionBasedBeaconRestApiTest {
  private final RecentChainData storageClient = MemoryOnlyRecentChainData.create();
  private final CombinedChainDataClient combinedChainDataClient =
      mock(CombinedChainDataClient.class);
  private final JettyServer server = mock(JettyServer.class);
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
  private final SyncCommitteeContributionPool syncCommitteeContributionPool =
      mock(SyncCommitteeContributionPool.class);
  private final ActiveValidatorChannel activeValidatorChannel = mock(ActiveValidatorChannel.class);
  private final ProposersDataManager proposersDataManager = mock(ProposersDataManager.class);
  private final DepositProvider depositProvider = mock(DepositProvider.class);
  private final Eth1DataCache eth1DataCache = mock(Eth1DataCache.class);

  @BeforeEach
  public void setup() {
    BeaconRestApiConfig beaconRestApiConfig =
        BeaconRestApiConfig.builder()
            .restApiDocsEnabled(false)
            .restApiPort(THE_PORT)
            .eth1DepositContractAddress(Eth1Address.ZERO)
            .build();

    when(app.jettyServer()).thenReturn(server);
    final DataProvider dataProvider =
        DataProvider.builder()
            .spec(TestSpecFactory.createMinimalPhase0())
            .recentChainData(storageClient)
            .combinedChainDataClient(combinedChainDataClient)
            .p2pNetwork(null)
            .syncService(syncService)
            .validatorApiChannel(null)
            .blockManager(blockManager)
            .attestationManager(attestationManager)
            .isLivenessTrackingEnabled(false)
            .activeValidatorChannel(activeValidatorChannel)
            .attestationPool(attestationPool)
            .attesterSlashingPool(attesterSlashingPool)
            .proposerSlashingPool(proposerSlashingPool)
            .voluntaryExitPool(voluntaryExitPool)
            .syncCommitteeContributionPool(syncCommitteeContributionPool)
            .proposersDataManager(proposersDataManager)
            .build();
    final Eth1DataProvider eth1DataProvider = new Eth1DataProvider(eth1DataCache, depositProvider);
    new ReflectionBasedBeaconRestApi(
        dataProvider,
        eth1DataProvider,
        beaconRestApiConfig,
        eventChannels,
        new StubAsyncRunner(),
        StubTimeProvider.withTimeInMillis(1000),
        app,
        storageClient.getSpec());
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
