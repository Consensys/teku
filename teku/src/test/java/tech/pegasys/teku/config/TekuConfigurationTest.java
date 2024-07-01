/*
 * Copyright Consensys Software Inc., 2022
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

package tech.pegasys.teku.config;

import static org.assertj.core.api.Assertions.assertThat;

import dagger.Component;
import dagger.Module;
import dagger.Provides;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Singleton;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.BeaconNodeFacade;
import tech.pegasys.teku.TekuFacade;
import tech.pegasys.teku.cli.TempDirUtils;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.events.EventChannels;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.kzg.KZG;
import tech.pegasys.teku.networking.eth2.Eth2P2PNetwork;
import tech.pegasys.teku.networking.eth2.Eth2P2PNetworkBuilder;
import tech.pegasys.teku.networking.eth2.P2PConfig;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryNetwork;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryNetworkBuilder;
import tech.pegasys.teku.networking.p2p.libp2p.LibP2PNetworkBuilder;
import tech.pegasys.teku.networking.p2p.libp2p.gossip.LibP2PGossipNetwork;
import tech.pegasys.teku.networking.p2p.libp2p.gossip.LibP2PGossipNetworkBuilder;
import tech.pegasys.teku.networking.p2p.network.P2PNetwork;
import tech.pegasys.teku.networking.p2p.peer.Peer;
import tech.pegasys.teku.service.serviceutils.layout.DataDirLayout;
import tech.pegasys.teku.services.beaconchain.BeaconChainControllerFacade;
import tech.pegasys.teku.services.beaconchain.BeaconChainControllerFactory;
import tech.pegasys.teku.services.beaconchain.LateInitDelegateBeaconChainController;
import tech.pegasys.teku.services.beaconchain.init.AsyncRunnerModule;
import tech.pegasys.teku.services.beaconchain.init.BeaconConfigModule;
import tech.pegasys.teku.services.beaconchain.init.BeaconModule;
import tech.pegasys.teku.services.beaconchain.init.BlobModule;
import tech.pegasys.teku.services.beaconchain.init.ChannelsModule;
import tech.pegasys.teku.services.beaconchain.init.CryptoModule;
import tech.pegasys.teku.services.beaconchain.init.DataProviderModule;
import tech.pegasys.teku.services.beaconchain.init.ExternalDependenciesModule;
import tech.pegasys.teku.services.beaconchain.init.ForkChoiceModule;
import tech.pegasys.teku.services.beaconchain.init.LoggingModule;
import tech.pegasys.teku.services.beaconchain.init.MainModule;
import tech.pegasys.teku.services.beaconchain.init.MetricsModule;
import tech.pegasys.teku.services.beaconchain.init.NetworkModule;
import tech.pegasys.teku.services.beaconchain.init.PoolAndCachesModule;
import tech.pegasys.teku.services.beaconchain.init.PowModule;
import tech.pegasys.teku.services.beaconchain.init.ServiceConfigModule;
import tech.pegasys.teku.services.beaconchain.init.SpecModule;
import tech.pegasys.teku.services.beaconchain.init.StorageModule;
import tech.pegasys.teku.services.beaconchain.init.SubnetsModule;
import tech.pegasys.teku.services.beaconchain.init.SyncModule;
import tech.pegasys.teku.services.beaconchain.init.ValidatorModule;
import tech.pegasys.teku.services.beaconchain.init.VerifyModule;
import tech.pegasys.teku.services.beaconchain.init.WSModule;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.statetransition.OperationPool;
import tech.pegasys.teku.statetransition.attestation.AttestationManager;
import tech.pegasys.teku.statetransition.blobs.BlobSidecarManager;
import tech.pegasys.teku.statetransition.block.BlockManager;
import tech.pegasys.teku.statetransition.synccommittee.SyncCommitteeContributionPool;
import tech.pegasys.teku.statetransition.synccommittee.SyncCommitteeMessagePool;
import tech.pegasys.teku.statetransition.util.DebugDataDumper;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.storage.store.KeyValueStore;
import tech.pegasys.teku.weaksubjectivity.WeakSubjectivityValidator;

public class TekuConfigurationTest {

  Path tempDir = TempDirUtils.createTempDir();

  @AfterEach
  void cleanup() {
    TempDirUtils.deleteDirLenient(tempDir, 10, true);
  }

  @Test
  void beaconChainControllerFactory_useCustomFactories() {
    AtomicBoolean customDiscoveryBuilderMethodCalled = new AtomicBoolean();
    AtomicBoolean customLibP2PBuilderMethodCalled = new AtomicBoolean();
    AtomicBoolean customGossipNetworkBuilderCalled = new AtomicBoolean();

    DiscoveryNetworkBuilder customDiscoveryNetworkBuilder =
        new DiscoveryNetworkBuilder() {
          @Override
          public DiscoveryNetwork<?> build() {
            customDiscoveryBuilderMethodCalled.set(true);
            return super.build();
          }
        };

    LibP2PGossipNetworkBuilder customGossipNetworkBuilder =
        new LibP2PGossipNetworkBuilder() {
          @Override
          public LibP2PGossipNetwork build() {
            customGossipNetworkBuilderCalled.set(true);
            return super.build();
          }
        };

    LibP2PNetworkBuilder customLibP2PNetworkBuilder =
        new LibP2PNetworkBuilder() {
          @Override
          public P2PNetwork<Peer> build() {
            customLibP2PBuilderMethodCalled.set(true);
            return super.build();
          }

          @Override
          protected LibP2PGossipNetworkBuilder createLibP2PGossipNetworkBuilder() {
            return customGossipNetworkBuilder;
          }
        };

    Eth2P2PNetworkBuilder customEth2P2PNetworkBuilder =
        new Eth2P2PNetworkBuilder() {
          @Override
          protected DiscoveryNetworkBuilder createDiscoveryNetworkBuilder() {
            return customDiscoveryNetworkBuilder;
          }

          @Override
          protected LibP2PNetworkBuilder createLibP2PNetworkBuilder() {
            return customLibP2PNetworkBuilder;
          }
        };

    BeaconChainControllerFactory customControllerFactory =
        LateInitDelegateBeaconChainController.createLateInitFactory(
            (serviceConfig, beaconConfig) ->
                DaggerTekuConfigurationTest_TestBeaconChainControllerComponent.builder()
                    .externalDependenciesModule(
                        new ExternalDependenciesModule(serviceConfig, beaconConfig))
                    .testNetworkModule(new TestNetworkModule(customEth2P2PNetworkBuilder))
                    .build()
                    .beaconChainController());

    TekuConfiguration tekuConfiguration =
        TekuConfiguration.builder()
            .data(b -> b.dataBasePath(tempDir))
            .executionLayer(b -> b.engineEndpoint("unsafe-test-stub"))
            .eth2NetworkConfig(b -> b.ignoreWeakSubjectivityPeriodEnabled(true))
            .beaconChainControllerFactory(customControllerFactory)
            .build();

    try (BeaconNodeFacade beaconNode = TekuFacade.startBeaconNode(tekuConfiguration)) {
      assertThat(beaconNode).isNotNull();
      assertThat(customDiscoveryBuilderMethodCalled).isTrue();
      assertThat(customLibP2PBuilderMethodCalled).isTrue();
      assertThat(customGossipNetworkBuilderCalled).isTrue();
    }
  }

  @Singleton
  @Component(
      modules = {
        AsyncRunnerModule.class,
        BeaconConfigModule.class,
        BeaconModule.class,
        BlobModule.class,
        ChannelsModule.class,
        CryptoModule.class,
        DataProviderModule.class,
        ExternalDependenciesModule.class,
        ForkChoiceModule.class,
        LoggingModule.class,
        MainModule.class,
        MetricsModule.class,
        //          NetworkModule.class,
        TestNetworkModule.class,
        PoolAndCachesModule.class,
        PowModule.class,
        ServiceConfigModule.class,
        SpecModule.class,
        StorageModule.class,
        SubnetsModule.class,
        SyncModule.class,
        ValidatorModule.class,
        VerifyModule.class,
        WSModule.class
      })
  public interface TestBeaconChainControllerComponent {

    BeaconChainControllerFacade beaconChainController();
  }

  @Module
  public static class TestNetworkModule {
    private final Eth2P2PNetworkBuilder eth2P2PNetworkBuilder;

    public TestNetworkModule(final Eth2P2PNetworkBuilder eth2P2PNetworkBuilder) {
      this.eth2P2PNetworkBuilder = eth2P2PNetworkBuilder;
    }

    @Provides
    Eth2P2PNetworkBuilder eth2P2PNetworkBuilder() {
      return eth2P2PNetworkBuilder;
    }

    @Provides
    @Singleton
    static Eth2P2PNetwork eth2P2PNetwork(
        final Spec spec,
        final P2PConfig p2pConfig,
        final MetricsSystem metricsSystem,
        @AsyncRunnerModule.NetworkAsyncRunner final AsyncRunner networkAsyncRunner,
        final TimeProvider timeProvider,
        final EventChannels eventChannels,
        final KeyValueStore<String, Bytes> keyValueStore,
        final Eth2P2PNetworkBuilder eth2P2PNetworkBuilder,
        final CombinedChainDataClient combinedChainDataClient,
        final BlockManager blockManager,
        final BlobSidecarManager blobSidecarManager,
        final AttestationManager attestationManager,
        final OperationPool<AttesterSlashing> attesterSlashingPool,
        final OperationPool<ProposerSlashing> proposerSlashingPool,
        final OperationPool<SignedVoluntaryExit> voluntaryExitPool,
        final SyncCommitteeContributionPool syncCommitteeContributionPool,
        final SyncCommitteeMessagePool syncCommitteeMessagePool,
        final OperationPool<SignedBlsToExecutionChange> blsToExecutionChangePool,
        final KZG kzg,
        final WeakSubjectivityValidator weakSubjectivityValidator,
        final DebugDataDumper p2pDebugDataDumper) {

      return NetworkModule.eth2P2PNetwork(
          spec,
          p2pConfig,
          metricsSystem,
          networkAsyncRunner,
          timeProvider,
          eventChannels,
          keyValueStore,
          eth2P2PNetworkBuilder,
          combinedChainDataClient,
          blockManager,
          blobSidecarManager,
          attestationManager,
          attesterSlashingPool,
          proposerSlashingPool,
          voluntaryExitPool,
          syncCommitteeContributionPool,
          syncCommitteeMessagePool,
          blsToExecutionChangePool,
          kzg,
          weakSubjectivityValidator,
          p2pDebugDataDumper);
    }

    @Provides
    @Singleton
    static DebugDataDumper p2pDebugDataDumper(final DataDirLayout dataDirLayout) {
      return NetworkModule.debugDataDumper(dataDirLayout);
    }
  }
}
