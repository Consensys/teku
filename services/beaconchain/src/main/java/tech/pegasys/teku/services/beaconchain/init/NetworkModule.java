package tech.pegasys.teku.services.beaconchain.init;

import dagger.Module;
import dagger.Provides;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.events.EventChannels;
import tech.pegasys.teku.infrastructure.io.PortAvailability;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.kzg.KZG;
import tech.pegasys.teku.networking.eth2.Eth2P2PNetwork;
import tech.pegasys.teku.networking.eth2.Eth2P2PNetworkBuilder;
import tech.pegasys.teku.networking.eth2.P2PConfig;
import tech.pegasys.teku.networking.eth2.mock.NoOpEth2P2PNetwork;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryConfig;
import tech.pegasys.teku.services.beaconchain.init.AsyncRunnerModule.NetworkAsyncRunner;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.statetransition.LocalOperationAcceptedFilter;
import tech.pegasys.teku.statetransition.OperationPool;
import tech.pegasys.teku.statetransition.attestation.AttestationManager;
import tech.pegasys.teku.statetransition.blobs.BlobSidecarManager;
import tech.pegasys.teku.statetransition.block.BlockManager;
import tech.pegasys.teku.statetransition.synccommittee.SyncCommitteeContributionPool;
import tech.pegasys.teku.statetransition.synccommittee.SyncCommitteeMessagePool;
import tech.pegasys.teku.statetransition.util.P2PDebugDataDumper;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.storage.store.KeyValueStore;
import tech.pegasys.teku.weaksubjectivity.WeakSubjectivityValidator;

import javax.inject.Singleton;
import java.util.Optional;

@Module
public interface NetworkModule {

  @Provides
  static Eth2P2PNetworkBuilder eth2P2PNetworkBuilder() {
    return Eth2P2PNetworkBuilder.create();
  }

  @Provides
  @Singleton
  static Eth2P2PNetwork eth2P2PNetwork(
      Spec spec,
      P2PConfig p2pConfig,
      MetricsSystem metricsSystem,
      @NetworkAsyncRunner AsyncRunner networkAsyncRunner,
      TimeProvider timeProvider,
      EventChannels eventChannels,
      KeyValueStore<String, Bytes> keyValueStore,
      Eth2P2PNetworkBuilder eth2P2PNetworkBuilder,
      CombinedChainDataClient combinedChainDataClient,
      BlockManager blockManager,
      BlobSidecarManager blobSidecarManager,
      AttestationManager attestationManager,
      OperationPool<AttesterSlashing> attesterSlashingPool,
      OperationPool<ProposerSlashing> proposerSlashingPool,
      OperationPool<SignedVoluntaryExit> voluntaryExitPool,
      SyncCommitteeContributionPool syncCommitteeContributionPool,
      SyncCommitteeMessagePool syncCommitteeMessagePool,
      OperationPool<SignedBlsToExecutionChange> blsToExecutionChangePool,
      KZG kzg,
      WeakSubjectivityValidator weakSubjectivityValidator,
      P2PDebugDataDumper p2pDebugDataDumper
  ) {
    if (!p2pConfig.getNetworkConfig().isEnabled()) {
      return new NoOpEth2P2PNetwork(spec);
    }

    DiscoveryConfig discoveryConfig = p2pConfig.getDiscoveryConfig();
    final Optional<Integer> maybeUdpPort =
        discoveryConfig.isDiscoveryEnabled()
            ? Optional.of(discoveryConfig.getListenUdpPort())
            : Optional.empty();

    PortAvailability.checkPortsAvailable(
        p2pConfig.getNetworkConfig().getListenPort(), maybeUdpPort);

    // TODO adopt Dagger instead of eth2P2PNetworkBuilder()
    Eth2P2PNetwork p2pNetwork = eth2P2PNetworkBuilder
        .config(p2pConfig)
        .eventChannels(eventChannels)
        .combinedChainDataClient(combinedChainDataClient)
        .gossipedBlockProcessor(blockManager::validateAndImportBlock)
        .gossipedBlobSidecarProcessor(blobSidecarManager::validateAndPrepareForBlockImport)
        .gossipedAttestationProcessor(attestationManager::addAttestation)
        .gossipedAggregateProcessor(attestationManager::addAggregate)
        .gossipedAttesterSlashingProcessor(attesterSlashingPool::addRemote)
        .gossipedProposerSlashingProcessor(proposerSlashingPool::addRemote)
        .gossipedVoluntaryExitProcessor(voluntaryExitPool::addRemote)
        .gossipedSignedContributionAndProofProcessor(syncCommitteeContributionPool::addRemote)
        .gossipedSyncCommitteeMessageProcessor(syncCommitteeMessagePool::addRemote)
        .gossipedSignedBlsToExecutionChangeProcessor(blsToExecutionChangePool::addRemote)
        .processedAttestationSubscriptionProvider(
            attestationManager::subscribeToAttestationsToSend)
        .metricsSystem(metricsSystem)
        .timeProvider(timeProvider)
        .asyncRunner(networkAsyncRunner)
        .keyValueStore(keyValueStore)
        .requiredCheckpoint(weakSubjectivityValidator.getWSCheckpoint())
        .specProvider(spec)
        .kzg(kzg)
        .recordMessageArrival(true)
        .p2pDebugDataDumper(p2pDebugDataDumper)
        .build();

    syncCommitteeMessagePool.subscribeOperationAdded(
        new LocalOperationAcceptedFilter<>(p2pNetwork::publishSyncCommitteeMessage));
    syncCommitteeContributionPool.subscribeOperationAdded(
        new LocalOperationAcceptedFilter<>(p2pNetwork::publishSyncCommitteeContribution));
    proposerSlashingPool.subscribeOperationAdded(
        new LocalOperationAcceptedFilter<>(p2pNetwork::publishProposerSlashing));
    attesterSlashingPool.subscribeOperationAdded(
        new LocalOperationAcceptedFilter<>(p2pNetwork::publishAttesterSlashing));
    voluntaryExitPool.subscribeOperationAdded(
        new LocalOperationAcceptedFilter<>(p2pNetwork::publishVoluntaryExit));
    blsToExecutionChangePool.subscribeOperationAdded(
        new LocalOperationAcceptedFilter<>(p2pNetwork::publishSignedBlsToExecutionChange));

    return p2pNetwork;
  }
}
