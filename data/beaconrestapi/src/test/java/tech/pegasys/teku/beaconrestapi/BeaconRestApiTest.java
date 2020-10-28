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
import tech.pegasys.teku.beaconrestapi.handlers.beacon.GetChainHead;
import tech.pegasys.teku.beaconrestapi.handlers.beacon.GetHead;
import tech.pegasys.teku.beaconrestapi.handlers.beacon.GetState;
import tech.pegasys.teku.beaconrestapi.handlers.beacon.GetStateRoot;
import tech.pegasys.teku.beaconrestapi.handlers.beacon.GetValidators;
import tech.pegasys.teku.beaconrestapi.handlers.beacon.PostValidators;
import tech.pegasys.teku.beaconrestapi.handlers.network.GetEthereumNameRecord;
import tech.pegasys.teku.beaconrestapi.handlers.network.GetListenAddresses;
import tech.pegasys.teku.beaconrestapi.handlers.network.GetListenPort;
import tech.pegasys.teku.beaconrestapi.handlers.network.GetPeerCount;
import tech.pegasys.teku.beaconrestapi.handlers.network.GetPeerId;
import tech.pegasys.teku.beaconrestapi.handlers.network.GetPeers;
import tech.pegasys.teku.beaconrestapi.handlers.node.GetAttestationsInPoolCount;
import tech.pegasys.teku.beaconrestapi.handlers.node.GetFork;
import tech.pegasys.teku.beaconrestapi.handlers.node.GetGenesisTime;
import tech.pegasys.teku.beaconrestapi.handlers.node.GetSyncing;
import tech.pegasys.teku.beaconrestapi.handlers.node.GetVersion;
import tech.pegasys.teku.beaconrestapi.handlers.validator.GetAggregate;
import tech.pegasys.teku.beaconrestapi.handlers.validator.PostAggregateAndProof;
import tech.pegasys.teku.beaconrestapi.handlers.validator.PostBlock;
import tech.pegasys.teku.beaconrestapi.handlers.validator.PostDuties;
import tech.pegasys.teku.beaconrestapi.handlers.validator.PostSubscribeToBeaconCommittee;
import tech.pegasys.teku.beaconrestapi.handlers.validator.PostSubscribeToPersistentSubnets;
import tech.pegasys.teku.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.events.EventChannels;
import tech.pegasys.teku.statetransition.OperationPool;
import tech.pegasys.teku.statetransition.attestation.AggregatingAttestationPool;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.storage.client.MemoryOnlyRecentChainData;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.sync.SyncService;
import tech.pegasys.teku.util.config.GlobalConfiguration;

@SuppressWarnings({"unchecked"})
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
  private final OperationPool<AttesterSlashing> attesterSlashingPool = mock(OperationPool.class);
  private final OperationPool<ProposerSlashing> proposerSlashingPool = mock(OperationPool.class);
  private final OperationPool<SignedVoluntaryExit> voluntaryExitPool = mock(OperationPool.class);

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
            attestationPool,
            attesterSlashingPool,
            proposerSlashingPool,
            voluntaryExitPool),
        config,
        eventChannels,
        new StubAsyncRunner(),
        app);
  }

  @Test
  public void shouldHaveServerPortSet() {
    verify(server).setServerPort(THE_PORT);
  }

  @Test
  public void shouldHaveGenesisTimeEndpoint() {
    verify(app).get(eq(GetGenesisTime.ROUTE), any(GetGenesisTime.class));
  }

  @Test
  public void shouldHaveVersionEndpoint() {
    verify(app).get(eq(GetVersion.ROUTE), any(GetVersion.class));
  }

  @Test
  public void shouldHavePeerIdEndpoint() {
    verify(app).get(eq(GetPeerId.ROUTE), any(GetPeerId.class));
  }

  @Test
  public void shouldHaveBeaconHeadEndpoint() {
    verify(app).get(eq(GetHead.ROUTE), any(GetHead.class));
  }

  @Test
  public void shouldHavePeersEndpoint() {
    verify(app).get(eq(GetPeers.ROUTE), any(GetPeers.class));
  }

  @Test
  public void shouldHaveChainHeadEndpoint() {
    verify(app).get(eq(GetChainHead.ROUTE), any(GetChainHead.class));
  }

  @Test
  public void shouldHaveBeaconStateEndpoint() {
    verify(app).get(eq(GetState.ROUTE), any(GetState.class));
  }

  @Test
  public void shouldHaveSyncingEndpoint() {
    verify(app).get(eq(GetSyncing.ROUTE), any(GetSyncing.class));
  }

  @Test
  public void shouldHaveBeaconValidatorsEndpoint() {
    verify(app).get(eq(GetValidators.ROUTE), any(GetValidators.class));
  }

  @Test
  public void shouldHaveBeaconStateRootEndpoint() {
    verify(app).get(eq(GetStateRoot.ROUTE), any(GetStateRoot.class));
  }

  @Test
  public void shouldHaveForkEndpoint() {
    verify(app).get(eq(GetFork.ROUTE), any(GetFork.class));
  }

  @Test
  public void shouldHaveNetworkEnrEndpoint() {
    verify(app).get(eq(GetEthereumNameRecord.ROUTE), any(GetEthereumNameRecord.class));
  }

  @Test
  public void shouldHaveNetworkListenAddressesEndpoint() {
    verify(app).get(eq(GetListenAddresses.ROUTE), any(GetListenAddresses.class));
  }

  @Test
  public void shouldHaveNetworkPeerCountEndpoint() {
    verify(app).get(eq(GetPeerCount.ROUTE), any(GetPeerCount.class));
  }

  @Test
  public void shouldHaveNetworkListenPortEndpoint() {
    verify(app).get(eq(GetListenPort.ROUTE), any(GetListenPort.class));
  }

  @Test
  public void shouldHaveBeaconValidatorsPostEndpoint() {
    verify(app).post(eq(PostValidators.ROUTE), any(PostValidators.class));
  }

  @Test
  public void shouldHaveValidatorBlockEndpoint() {
    verify(app).post(eq(PostBlock.ROUTE), any(PostBlock.class));
  }

  @Test
  public void shouldHaveValidatorDutiesEndpoint() {
    verify(app).post(eq(PostDuties.ROUTE), any(PostDuties.class));
  }

  @Test
  public void shouldHaveValidatorCreateAggregateEndpoint() {
    verify(app).get(eq(GetAggregate.ROUTE), any(GetAggregate.class));
  }

  @Test
  public void shouldHaveValidatorPostAggregateAndProofEndpoint() {
    verify(app).post(eq(PostAggregateAndProof.ROUTE), any(PostAggregateAndProof.class));
  }

  @Test
  public void shouldHaveAttestationsInPoolCountEndpoint() {
    verify(app).get(eq(GetAttestationsInPoolCount.ROUTE), any(GetAttestationsInPoolCount.class));
  }

  @Test
  public void shouldHaveSubscribeToBeaconCommitteeEndpoint() {
    verify(app)
        .post(eq(PostSubscribeToBeaconCommittee.ROUTE), any(PostSubscribeToBeaconCommittee.class));
  }

  @Test
  public void shouldHaveSubscribeToPersistentSubnetsEndpoint() {
    verify(app)
        .post(
            eq(PostSubscribeToPersistentSubnets.ROUTE),
            any(PostSubscribeToPersistentSubnets.class));
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
