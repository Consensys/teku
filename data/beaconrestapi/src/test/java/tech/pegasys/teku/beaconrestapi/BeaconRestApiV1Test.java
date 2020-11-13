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
import io.javalin.http.Handler;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.beaconrestapi.handlers.tekuv1.admin.PutLogLevel;
import tech.pegasys.teku.beaconrestapi.handlers.tekuv1.beacon.GetSszState;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetAttestations;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetAttesterSlashings;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetBlock;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetBlockAttestations;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetBlockHeader;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetBlockHeaders;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetBlockRoot;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetGenesis;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetProposerSlashings;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetStateCommittees;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetStateFinalityCheckpoints;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetStateFork;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetStateRoot;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetStateValidator;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetStateValidatorBalances;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetStateValidators;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetVoluntaryExits;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.PostAttestation;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.PostAttesterSlashing;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.PostBlock;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.PostProposerSlashing;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.PostVoluntaryExit;
import tech.pegasys.teku.beaconrestapi.handlers.v1.config.GetDepositContract;
import tech.pegasys.teku.beaconrestapi.handlers.v1.config.GetForkSchedule;
import tech.pegasys.teku.beaconrestapi.handlers.v1.config.GetSpec;
import tech.pegasys.teku.beaconrestapi.handlers.v1.debug.GetState;
import tech.pegasys.teku.beaconrestapi.handlers.v1.events.GetEvents;
import tech.pegasys.teku.beaconrestapi.handlers.v1.node.GetHealth;
import tech.pegasys.teku.beaconrestapi.handlers.v1.node.GetIdentity;
import tech.pegasys.teku.beaconrestapi.handlers.v1.node.GetPeerById;
import tech.pegasys.teku.beaconrestapi.handlers.v1.node.GetPeers;
import tech.pegasys.teku.beaconrestapi.handlers.v1.node.GetSyncing;
import tech.pegasys.teku.beaconrestapi.handlers.v1.node.GetVersion;
import tech.pegasys.teku.beaconrestapi.handlers.v1.validator.GetAggregateAttestation;
import tech.pegasys.teku.beaconrestapi.handlers.v1.validator.GetAttestationData;
import tech.pegasys.teku.beaconrestapi.handlers.v1.validator.GetNewBlock;
import tech.pegasys.teku.beaconrestapi.handlers.v1.validator.GetProposerDuties;
import tech.pegasys.teku.beaconrestapi.handlers.v1.validator.PostAggregateAndProofs;
import tech.pegasys.teku.beaconrestapi.handlers.v1.validator.PostAttesterDuties;
import tech.pegasys.teku.beaconrestapi.handlers.v1.validator.PostSubscribeToBeaconCommitteeSubnet;
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
import tech.pegasys.teku.sync.forward.ForwardSync;
import tech.pegasys.teku.util.config.GlobalConfiguration;

@SuppressWarnings("unchecked")
public class BeaconRestApiV1Test {
  private final RecentChainData storageClient = MemoryOnlyRecentChainData.create(new EventBus());
  private final CombinedChainDataClient combinedChainDataClient =
      mock(CombinedChainDataClient.class);
  private final JavalinServer server = mock(JavalinServer.class);
  private final Javalin app = mock(Javalin.class);
  private final ForwardSync syncService = mock(ForwardSync.class);
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

  @ParameterizedTest(name = "{0}")
  @MethodSource("getParameters")
  void getRouteExists(final String route, final Class<Handler> type) {
    verify(app).get(eq(route), any(type));
  }

  @Test
  void shouldHavePutLogLevel() {
    verify(app).put(eq(PutLogLevel.ROUTE), any(PutLogLevel.class));
  }

  static Stream<Arguments> getParameters() {
    Stream.Builder<Arguments> builder = Stream.builder();

    // beacon
    builder
        .add(Arguments.of(GetBlockHeader.ROUTE, GetBlockHeader.class))
        .add(Arguments.of(GetBlockHeaders.ROUTE, GetBlockHeaders.class))
        .add(Arguments.of(GetBlock.ROUTE, GetBlock.class))
        .add(Arguments.of(GetBlockRoot.ROUTE, GetBlockRoot.class))
        .add(Arguments.of(GetBlockAttestations.ROUTE, GetBlockAttestations.class))
        .add(Arguments.of(GetGenesis.ROUTE, GetGenesis.class))
        .add(Arguments.of(GetStateFork.ROUTE, GetStateFork.class))
        .add(Arguments.of(GetStateRoot.ROUTE, GetStateRoot.class))
        .add(Arguments.of(GetStateValidator.ROUTE, GetStateValidator.class))
        .add(Arguments.of(GetStateValidators.ROUTE, GetStateValidators.class))
        .add(Arguments.of(GetStateFinalityCheckpoints.ROUTE, GetStateFinalityCheckpoints.class))
        .add(Arguments.of(GetStateValidatorBalances.ROUTE, GetStateValidatorBalances.class))
        .add(Arguments.of(GetStateCommittees.ROUTE, GetStateCommittees.class))
        .add(Arguments.of(GetAttestations.ROUTE, GetAttestations.class))
        .add(Arguments.of(GetAttesterSlashings.ROUTE, GetAttesterSlashings.class))
        .add(Arguments.of(GetProposerSlashings.ROUTE, GetProposerSlashings.class))
        .add(Arguments.of(GetVoluntaryExits.ROUTE, GetVoluntaryExits.class));

    // events
    builder.add(Arguments.of(GetEvents.ROUTE, GetEvents.class));

    // node
    builder
        .add(Arguments.of(GetHealth.ROUTE, GetHealth.class))
        .add(Arguments.of(GetIdentity.ROUTE, GetIdentity.class))
        .add(Arguments.of(GetPeerById.ROUTE, GetPeerById.class))
        .add(Arguments.of(GetPeers.ROUTE, GetPeers.class))
        .add(Arguments.of(GetSyncing.ROUTE, GetSyncing.class))
        .add(Arguments.of(GetVersion.ROUTE, GetVersion.class));

    // validator
    builder
        .add(Arguments.of(GetAggregateAttestation.ROUTE, GetAggregateAttestation.class))
        .add(Arguments.of(GetAttestationData.ROUTE, GetAttestationData.class))
        .add(Arguments.of(GetNewBlock.ROUTE, GetNewBlock.class))
        .add(Arguments.of(GetProposerDuties.ROUTE, GetProposerDuties.class));

    // config
    builder
        .add(Arguments.of(GetSpec.ROUTE, GetSpec.class))
        .add(Arguments.of(GetForkSchedule.ROUTE, GetForkSchedule.class))
        .add(Arguments.of(GetDepositContract.ROUTE, GetDepositContract.class));

    // DEBUG
    builder.add(Arguments.of(GetState.ROUTE, GetState.class));

    // TEKU
    builder.add(Arguments.of(GetSszState.ROUTE, GetSszState.class));

    return builder.build();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("postParameters")
  void postRouteExists(final String route, final Class<Handler> type) {
    verify(app).post(eq(route), any(type));
  }

  static Stream<Arguments> postParameters() {
    Stream.Builder<Arguments> builder = Stream.builder();

    // beacon
    builder
        .add(Arguments.of(PostAttesterDuties.ROUTE, PostAttesterDuties.class))
        .add(Arguments.of(PostAttesterSlashing.ROUTE, PostAttesterSlashing.class))
        .add(Arguments.of(PostProposerSlashing.ROUTE, PostProposerSlashing.class))
        .add(Arguments.of(PostVoluntaryExit.ROUTE, PostVoluntaryExit.class))
        .add(Arguments.of(PostBlock.ROUTE, PostBlock.class));

    // validator
    builder
        .add(Arguments.of(PostAggregateAndProofs.ROUTE, PostAggregateAndProofs.class))
        .add(Arguments.of(PostAttesterDuties.ROUTE, PostAttesterDuties.class))
        .add(Arguments.of(PostAttestation.ROUTE, PostAttestation.class))
        .add(
            Arguments.of(
                PostSubscribeToBeaconCommitteeSubnet.ROUTE,
                PostSubscribeToBeaconCommitteeSubnet.class));

    return builder.build();
  }
}
