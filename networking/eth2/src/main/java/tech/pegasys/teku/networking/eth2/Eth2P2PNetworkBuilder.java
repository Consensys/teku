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

package tech.pegasys.teku.networking.eth2;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static tech.pegasys.teku.spec.config.Constants.MAX_CHUNK_SIZE;
import static tech.pegasys.teku.spec.config.Constants.MAX_CHUNK_SIZE_BELLATRIX;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.events.EventChannels;
import tech.pegasys.teku.infrastructure.metrics.SettableLabelledGauge;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.forks.GossipForkManager;
import tech.pegasys.teku.networking.eth2.gossip.forks.GossipForkSubscriptions;
import tech.pegasys.teku.networking.eth2.gossip.forks.versions.GossipForkSubscriptionsAltair;
import tech.pegasys.teku.networking.eth2.gossip.forks.versions.GossipForkSubscriptionsBellatrix;
import tech.pegasys.teku.networking.eth2.gossip.forks.versions.GossipForkSubscriptionsCapella;
import tech.pegasys.teku.networking.eth2.gossip.forks.versions.GossipForkSubscriptionsDeneb;
import tech.pegasys.teku.networking.eth2.gossip.forks.versions.GossipForkSubscriptionsPhase0;
import tech.pegasys.teku.networking.eth2.gossip.subnets.AttestationSubnetTopicProvider;
import tech.pegasys.teku.networking.eth2.gossip.subnets.PeerSubnetSubscriptions;
import tech.pegasys.teku.networking.eth2.gossip.subnets.SyncCommitteeSubnetTopicProvider;
import tech.pegasys.teku.networking.eth2.gossip.topics.Eth2GossipTopicFilter;
import tech.pegasys.teku.networking.eth2.gossip.topics.OperationProcessor;
import tech.pegasys.teku.networking.eth2.gossip.topics.ProcessedAttestationSubscriptionProvider;
import tech.pegasys.teku.networking.eth2.peers.Eth2PeerManager;
import tech.pegasys.teku.networking.eth2.peers.Eth2PeerSelectionStrategy;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.StatusMessageFactory;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.RpcEncoding;
import tech.pegasys.teku.networking.p2p.connection.TargetPeerRange;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryConfig;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryNetwork;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryNetworkBuilder;
import tech.pegasys.teku.networking.p2p.gossip.PreparedGossipMessageFactory;
import tech.pegasys.teku.networking.p2p.libp2p.LibP2PNetworkBuilder;
import tech.pegasys.teku.networking.p2p.libp2p.LibP2PPrivateKeyLoader;
import tech.pegasys.teku.networking.p2p.libp2p.gossip.GossipTopicFilter;
import tech.pegasys.teku.networking.p2p.network.P2PNetwork;
import tech.pegasys.teku.networking.p2p.network.PeerHandler;
import tech.pegasys.teku.networking.p2p.network.config.NetworkConfig;
import tech.pegasys.teku.networking.p2p.peer.Peer;
import tech.pegasys.teku.networking.p2p.reputation.ReputationManager;
import tech.pegasys.teku.networking.p2p.rpc.RpcMethod;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.config.Constants;
import tech.pegasys.teku.spec.datastructures.attestation.ValidateableAttestation;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.SignedBlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SignedContributionAndProof;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.ValidateableSyncCommitteeMessage;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.util.ForkAndSpecMilestone;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsSupplier;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.storage.store.KeyValueStore;

/**
 * CAUTION: this API is unstable and primarily intended for debugging and testing purposes this API
 * might be changed in any version in backward incompatible way
 */
public class Eth2P2PNetworkBuilder {

  public static final Duration DEFAULT_ETH2_RPC_PING_INTERVAL = Duration.ofSeconds(10);
  public static final int DEFAULT_ETH2_RPC_OUTSTANDING_PING_THRESHOLD = 2;
  public static final Duration DEFAULT_ETH2_STATUS_UPDATE_INTERVAL = Duration.ofMinutes(5);

  protected P2PConfig config;
  protected EventChannels eventChannels;
  protected CombinedChainDataClient combinedChainDataClient;
  protected OperationProcessor<SignedBeaconBlock> gossipedBlockProcessor;
  protected OperationProcessor<SignedBlobSidecar> gossipedBlobSidecarProcessor;
  protected OperationProcessor<ValidateableAttestation> gossipedAttestationConsumer;
  protected OperationProcessor<ValidateableAttestation> gossipedAggregateProcessor;
  protected OperationProcessor<AttesterSlashing> gossipedAttesterSlashingConsumer;
  protected OperationProcessor<ProposerSlashing> gossipedProposerSlashingConsumer;
  protected OperationProcessor<SignedVoluntaryExit> gossipedVoluntaryExitConsumer;
  protected OperationProcessor<SignedBlsToExecutionChange>
      gossipedSignedBlsToExecutionChangeProcessor;
  protected ProcessedAttestationSubscriptionProvider processedAttestationSubscriptionProvider;
  protected MetricsSystem metricsSystem;
  protected final List<RpcMethod<?, ?, ?>> rpcMethods = new ArrayList<>();
  protected final List<PeerHandler> peerHandlers = new ArrayList<>();
  protected TimeProvider timeProvider;
  protected AsyncRunner asyncRunner;
  protected KeyValueStore<String, Bytes> keyValueStore;
  protected Duration eth2RpcPingInterval = DEFAULT_ETH2_RPC_PING_INTERVAL;
  protected int eth2RpcOutstandingPingThreshold = DEFAULT_ETH2_RPC_OUTSTANDING_PING_THRESHOLD;
  protected final Duration eth2StatusUpdateInterval = DEFAULT_ETH2_STATUS_UPDATE_INTERVAL;
  protected Optional<Checkpoint> requiredCheckpoint = Optional.empty();
  protected Spec spec;
  protected OperationProcessor<SignedContributionAndProof>
      gossipedSignedContributionAndProofProcessor;
  protected OperationProcessor<ValidateableSyncCommitteeMessage>
      gossipedSyncCommitteeMessageProcessor;
  protected StatusMessageFactory statusMessageFactory;

  protected Eth2P2PNetworkBuilder() {}

  public static Eth2P2PNetworkBuilder create() {
    return new Eth2P2PNetworkBuilder();
  }

  public Eth2P2PNetwork build() {
    validate();

    // Setup eth2 handlers
    final SubnetSubscriptionService attestationSubnetService = new SubnetSubscriptionService();
    final SubnetSubscriptionService syncCommitteeSubnetService = new SubnetSubscriptionService();
    final RpcEncoding rpcEncoding =
        RpcEncoding.createSszSnappyEncoding(
            spec.isMilestoneSupported(SpecMilestone.BELLATRIX)
                ? MAX_CHUNK_SIZE_BELLATRIX
                : MAX_CHUNK_SIZE);
    if (statusMessageFactory == null) {
      statusMessageFactory = new StatusMessageFactory(combinedChainDataClient.getRecentChainData());
    }
    final Eth2PeerManager eth2PeerManager =
        Eth2PeerManager.create(
            asyncRunner,
            combinedChainDataClient,
            metricsSystem,
            attestationSubnetService,
            syncCommitteeSubnetService,
            rpcEncoding,
            statusMessageFactory,
            requiredCheckpoint,
            eth2RpcPingInterval,
            eth2RpcOutstandingPingThreshold,
            eth2StatusUpdateInterval,
            timeProvider,
            config.getPeerRateLimit(),
            config.getPeerRequestLimit(),
            spec);
    final Collection<RpcMethod<?, ?, ?>> eth2RpcMethods =
        eth2PeerManager.getBeaconChainMethods().all();
    rpcMethods.addAll(eth2RpcMethods);
    peerHandlers.add(eth2PeerManager);

    final GossipEncoding gossipEncoding = config.getGossipEncoding();
    // Build core network and inject eth2 handlers
    final DiscoveryNetwork<?> network = buildNetwork(gossipEncoding, syncCommitteeSubnetService);

    final GossipForkManager gossipForkManager = buildGossipForkManager(gossipEncoding, network);

    return new ActiveEth2P2PNetwork(
        config.getSpec(),
        asyncRunner,
        network,
        eth2PeerManager,
        gossipForkManager,
        eventChannels,
        combinedChainDataClient.getRecentChainData(),
        attestationSubnetService,
        syncCommitteeSubnetService,
        gossipEncoding,
        config.getGossipConfigurator(),
        processedAttestationSubscriptionProvider);
  }

  private GossipForkManager buildGossipForkManager(
      final GossipEncoding gossipEncoding, final DiscoveryNetwork<?> network) {
    final GossipForkManager.Builder gossipForkManagerBuilder =
        GossipForkManager.builder()
            .spec(spec)
            .recentChainData(combinedChainDataClient.getRecentChainData());
    spec.getEnabledMilestones().stream()
        .map(
            forkAndSpecMilestone ->
                createSubscriptions(forkAndSpecMilestone, network, gossipEncoding))
        .forEach(gossipForkManagerBuilder::fork);
    return gossipForkManagerBuilder.build();
  }

  private GossipForkSubscriptions createSubscriptions(
      final ForkAndSpecMilestone forkAndSpecMilestone,
      final DiscoveryNetwork<?> network,
      final GossipEncoding gossipEncoding) {
    switch (forkAndSpecMilestone.getSpecMilestone()) {
      case PHASE0:
        return new GossipForkSubscriptionsPhase0(
            forkAndSpecMilestone.getFork(),
            spec,
            asyncRunner,
            metricsSystem,
            network,
            combinedChainDataClient.getRecentChainData(),
            gossipEncoding,
            gossipedBlockProcessor,
            gossipedAttestationConsumer,
            gossipedAggregateProcessor,
            gossipedAttesterSlashingConsumer,
            gossipedProposerSlashingConsumer,
            gossipedVoluntaryExitConsumer);
      case ALTAIR:
        return new GossipForkSubscriptionsAltair(
            forkAndSpecMilestone.getFork(),
            spec,
            asyncRunner,
            metricsSystem,
            network,
            combinedChainDataClient.getRecentChainData(),
            gossipEncoding,
            gossipedBlockProcessor,
            gossipedAttestationConsumer,
            gossipedAggregateProcessor,
            gossipedAttesterSlashingConsumer,
            gossipedProposerSlashingConsumer,
            gossipedVoluntaryExitConsumer,
            gossipedSignedContributionAndProofProcessor,
            gossipedSyncCommitteeMessageProcessor);
      case BELLATRIX:
        return new GossipForkSubscriptionsBellatrix(
            forkAndSpecMilestone.getFork(),
            spec,
            asyncRunner,
            metricsSystem,
            network,
            combinedChainDataClient.getRecentChainData(),
            gossipEncoding,
            gossipedBlockProcessor,
            gossipedAttestationConsumer,
            gossipedAggregateProcessor,
            gossipedAttesterSlashingConsumer,
            gossipedProposerSlashingConsumer,
            gossipedVoluntaryExitConsumer,
            gossipedSignedContributionAndProofProcessor,
            gossipedSyncCommitteeMessageProcessor);
      case CAPELLA:
        return new GossipForkSubscriptionsCapella(
            forkAndSpecMilestone.getFork(),
            spec,
            config,
            asyncRunner,
            metricsSystem,
            network,
            combinedChainDataClient.getRecentChainData(),
            gossipEncoding,
            gossipedBlockProcessor,
            gossipedAttestationConsumer,
            gossipedAggregateProcessor,
            gossipedAttesterSlashingConsumer,
            gossipedProposerSlashingConsumer,
            gossipedVoluntaryExitConsumer,
            gossipedSignedContributionAndProofProcessor,
            gossipedSyncCommitteeMessageProcessor,
            gossipedSignedBlsToExecutionChangeProcessor);
      case DENEB:
        return new GossipForkSubscriptionsDeneb(
            forkAndSpecMilestone.getFork(),
            spec,
            config,
            asyncRunner,
            metricsSystem,
            network,
            combinedChainDataClient.getRecentChainData(),
            gossipEncoding,
            gossipedBlockProcessor,
            gossipedBlobSidecarProcessor,
            gossipedAttestationConsumer,
            gossipedAggregateProcessor,
            gossipedAttesterSlashingConsumer,
            gossipedProposerSlashingConsumer,
            gossipedVoluntaryExitConsumer,
            gossipedSignedContributionAndProofProcessor,
            gossipedSyncCommitteeMessageProcessor,
            gossipedSignedBlsToExecutionChangeProcessor);
      default:
        throw new UnsupportedOperationException(
            "Gossip not supported for fork " + forkAndSpecMilestone.getSpecMilestone());
    }
  }

  protected DiscoveryNetwork<?> buildNetwork(
      final GossipEncoding gossipEncoding,
      final SubnetSubscriptionService syncCommitteeSubnetService) {
    final ReputationManager reputationManager =
        new ReputationManager(metricsSystem, timeProvider, Constants.REPUTATION_MANAGER_CAPACITY);
    PreparedGossipMessageFactory defaultMessageFactory =
        gossipEncoding.createPreparedGossipMessageFactory(
            combinedChainDataClient.getRecentChainData()::getMilestoneByForkDigest);
    final GossipTopicFilter gossipTopicsFilter =
        new Eth2GossipTopicFilter(
            combinedChainDataClient.getRecentChainData(), gossipEncoding, spec);
    final NetworkConfig networkConfig = config.getNetworkConfig();
    final DiscoveryConfig discoConfig = config.getDiscoveryConfig();

    final P2PNetwork<Peer> p2pNetwork =
        createLibP2PNetworkBuilder()
            .asyncRunner(asyncRunner)
            .metricsSystem(metricsSystem)
            .config(networkConfig)
            .privateKeyProvider(
                new LibP2PPrivateKeyLoader(keyValueStore, networkConfig.getPrivateKeySource()))
            .reputationManager(reputationManager)
            .rpcMethods(rpcMethods)
            .peerHandlers(peerHandlers)
            .preparedGossipMessageFactory(defaultMessageFactory)
            .gossipTopicFilter(gossipTopicsFilter)
            .build();

    final AttestationSubnetTopicProvider attestationSubnetTopicProvider =
        new AttestationSubnetTopicProvider(
            combinedChainDataClient.getRecentChainData(), gossipEncoding);
    final SyncCommitteeSubnetTopicProvider syncCommitteeSubnetTopicProvider =
        new SyncCommitteeSubnetTopicProvider(
            combinedChainDataClient.getRecentChainData(), gossipEncoding);

    final TargetPeerRange targetPeerRange =
        new TargetPeerRange(
            discoConfig.getMinPeers(),
            discoConfig.getMaxPeers(),
            discoConfig.getMinRandomlySelectedPeers());
    final SchemaDefinitionsSupplier currentSchemaDefinitions =
        () -> combinedChainDataClient.getRecentChainData().getCurrentSpec().getSchemaDefinitions();
    final SettableLabelledGauge subnetPeerCountGauge =
        SettableLabelledGauge.create(
            metricsSystem,
            TekuMetricCategory.NETWORK,
            "subnet_peer_count",
            "Number of currently connected peers subscribed to each subnet",
            "subnet");
    return createDiscoveryNetworkBuilder()
        .metricsSystem(metricsSystem)
        .asyncRunner(asyncRunner)
        .kvStore(keyValueStore)
        .p2pNetwork(p2pNetwork)
        .peerSelectionStrategy(
            new Eth2PeerSelectionStrategy(
                targetPeerRange,
                network ->
                    PeerSubnetSubscriptions.create(
                        currentSchemaDefinitions,
                        network,
                        attestationSubnetTopicProvider,
                        syncCommitteeSubnetTopicProvider,
                        syncCommitteeSubnetService,
                        config.getTargetSubnetSubscriberCount(),
                        subnetPeerCountGauge),
                reputationManager,
                Collections::shuffle))
        .discoveryConfig(discoConfig)
        .p2pConfig(networkConfig)
        .spec(config.getSpec())
        .currentSchemaDefinitionsSupplier(currentSchemaDefinitions)
        .build();
  }

  protected DiscoveryNetworkBuilder createDiscoveryNetworkBuilder() {
    return DiscoveryNetworkBuilder.create();
  }

  protected LibP2PNetworkBuilder createLibP2PNetworkBuilder() {
    return LibP2PNetworkBuilder.create();
  }

  private void validate() {
    assertNotNull("config", config);
    assertNotNull("eventChannels", eventChannels);
    assertNotNull("metricsSystem", metricsSystem);
    assertNotNull("combinedChainDataClient", combinedChainDataClient);
    assertNotNull("keyValueStore", keyValueStore);
    assertNotNull("timeProvider", timeProvider);
    assertNotNull("gossipedBlockProcessor", gossipedBlockProcessor);
    assertNotNull("gossipedBlobSidecarProcessor", gossipedBlobSidecarProcessor);
    assertNotNull("gossipedAttestationProcessor", gossipedAttestationConsumer);
    assertNotNull("gossipedAggregateProcessor", gossipedAggregateProcessor);
    assertNotNull("gossipedAttesterSlashingProcessor", gossipedAttesterSlashingConsumer);
    assertNotNull("gossipedProposerSlashingProcessor", gossipedProposerSlashingConsumer);
    assertNotNull("gossipedVoluntaryExitProcessor", gossipedVoluntaryExitConsumer);
    assertNotNull(
        "gossipedSignedContributionAndProofProcessor", gossipedSignedContributionAndProofProcessor);
    assertNotNull("gossipedSyncCommitteeMessageProcessor", gossipedSyncCommitteeMessageProcessor);
    assertNotNull(
        "gossipedSignedBlsToExecutionChangeProcessor", gossipedSignedBlsToExecutionChangeProcessor);
  }

  private void assertNotNull(String fieldName, Object fieldValue) {
    checkState(fieldValue != null, "Field " + fieldName + " must be set.");
  }

  public Eth2P2PNetworkBuilder config(final P2PConfig config) {
    checkNotNull(config);
    this.config = config;
    return this;
  }

  public Eth2P2PNetworkBuilder eventChannels(final EventChannels eventChannels) {
    checkNotNull(eventChannels);
    this.eventChannels = eventChannels;
    return this;
  }

  public Eth2P2PNetworkBuilder combinedChainDataClient(
      final CombinedChainDataClient combinedChainDataClient) {
    checkNotNull(combinedChainDataClient);
    this.combinedChainDataClient = combinedChainDataClient;
    return this;
  }

  public Eth2P2PNetworkBuilder keyValueStore(final KeyValueStore<String, Bytes> kvStore) {
    checkNotNull(kvStore);
    this.keyValueStore = kvStore;
    return this;
  }

  public Eth2P2PNetworkBuilder processedAttestationSubscriptionProvider(
      final ProcessedAttestationSubscriptionProvider processedAttestationSubscriptionProvider) {
    checkNotNull(processedAttestationSubscriptionProvider);
    this.processedAttestationSubscriptionProvider = processedAttestationSubscriptionProvider;
    return this;
  }

  public Eth2P2PNetworkBuilder gossipedBlockProcessor(
      final OperationProcessor<SignedBeaconBlock> blockProcessor) {
    checkNotNull(blockProcessor);
    this.gossipedBlockProcessor = blockProcessor;
    return this;
  }

  public Eth2P2PNetworkBuilder gossipedBlobSidecarProcessor(
      final OperationProcessor<SignedBlobSidecar> blobSidecarProcessor) {
    checkNotNull(blobSidecarProcessor);
    this.gossipedBlobSidecarProcessor = blobSidecarProcessor;
    return this;
  }

  public Eth2P2PNetworkBuilder gossipedAttestationProcessor(
      final OperationProcessor<ValidateableAttestation> gossipedAttestationProcessor) {
    checkNotNull(gossipedAttestationProcessor);
    this.gossipedAttestationConsumer = gossipedAttestationProcessor;
    return this;
  }

  public Eth2P2PNetworkBuilder gossipedAggregateProcessor(
      final OperationProcessor<ValidateableAttestation> gossipedAggregateProcessor) {
    checkNotNull(gossipedAggregateProcessor);
    this.gossipedAggregateProcessor = gossipedAggregateProcessor;
    return this;
  }

  public Eth2P2PNetworkBuilder gossipedAttesterSlashingProcessor(
      final OperationProcessor<AttesterSlashing> gossipedAttesterSlashingProcessor) {
    checkNotNull(gossipedAttesterSlashingProcessor);
    this.gossipedAttesterSlashingConsumer = gossipedAttesterSlashingProcessor;
    return this;
  }

  public Eth2P2PNetworkBuilder gossipedProposerSlashingProcessor(
      final OperationProcessor<ProposerSlashing> gossipedProposerSlashingProcessor) {
    checkNotNull(gossipedProposerSlashingProcessor);
    this.gossipedProposerSlashingConsumer = gossipedProposerSlashingProcessor;
    return this;
  }

  public Eth2P2PNetworkBuilder gossipedVoluntaryExitProcessor(
      final OperationProcessor<SignedVoluntaryExit> gossipedVoluntaryExitProcessor) {
    checkNotNull(gossipedVoluntaryExitProcessor);
    this.gossipedVoluntaryExitConsumer = gossipedVoluntaryExitProcessor;
    return this;
  }

  public Eth2P2PNetworkBuilder gossipedSignedContributionAndProofProcessor(
      final OperationProcessor<SignedContributionAndProof>
          gossipedSignedContributionAndProofProcessor) {
    checkNotNull(gossipedSignedContributionAndProofProcessor);
    this.gossipedSignedContributionAndProofProcessor = gossipedSignedContributionAndProofProcessor;
    return this;
  }

  public Eth2P2PNetworkBuilder gossipedSyncCommitteeMessageProcessor(
      final OperationProcessor<ValidateableSyncCommitteeMessage>
          gossipedSyncCommitteeMessageProcessor) {
    checkNotNull(gossipedSyncCommitteeMessageProcessor);
    this.gossipedSyncCommitteeMessageProcessor = gossipedSyncCommitteeMessageProcessor;
    return this;
  }

  public Eth2P2PNetworkBuilder gossipedSignedBlsToExecutionChangeProcessor(
      final OperationProcessor<SignedBlsToExecutionChange>
          gossipedSignedBlsToExecutionChangeProcessor) {
    checkNotNull(gossipedSignedBlsToExecutionChangeProcessor);
    this.gossipedSignedBlsToExecutionChangeProcessor = gossipedSignedBlsToExecutionChangeProcessor;
    return this;
  }

  public Eth2P2PNetworkBuilder metricsSystem(final MetricsSystem metricsSystem) {
    checkNotNull(metricsSystem);
    this.metricsSystem = metricsSystem;
    return this;
  }

  public Eth2P2PNetworkBuilder timeProvider(final TimeProvider timeProvider) {
    this.timeProvider = timeProvider;
    return this;
  }

  public Eth2P2PNetworkBuilder rpcMethod(final RpcMethod<?, ?, ?> rpcMethod) {
    checkNotNull(rpcMethod);
    rpcMethods.add(rpcMethod);
    return this;
  }

  public Eth2P2PNetworkBuilder peerHandler(final PeerHandler peerHandler) {
    checkNotNull(peerHandler);
    peerHandlers.add(peerHandler);
    return this;
  }

  public Eth2P2PNetworkBuilder asyncRunner(final AsyncRunner asyncRunner) {
    checkNotNull(asyncRunner);
    this.asyncRunner = asyncRunner;
    return this;
  }

  public Eth2P2PNetworkBuilder eth2RpcPingInterval(final Duration eth2RpcPingInterval) {
    checkNotNull(eth2RpcPingInterval);
    this.eth2RpcPingInterval = eth2RpcPingInterval;
    return this;
  }

  public Eth2P2PNetworkBuilder eth2RpcOutstandingPingThreshold(
      final int eth2RpcOutstandingPingThreshold) {
    checkArgument(eth2RpcOutstandingPingThreshold > 0);
    this.eth2RpcOutstandingPingThreshold = eth2RpcOutstandingPingThreshold;
    return this;
  }

  public Eth2P2PNetworkBuilder requiredCheckpoint(final Optional<Checkpoint> requiredCheckpoint) {
    checkNotNull(requiredCheckpoint);
    this.requiredCheckpoint = requiredCheckpoint;
    return this;
  }

  public Eth2P2PNetworkBuilder specProvider(final Spec spec) {
    checkNotNull(spec);
    this.spec = spec;
    return this;
  }

  public Eth2P2PNetworkBuilder statusMessageFactory(
      final StatusMessageFactory statusMessageFactory) {
    checkNotNull(statusMessageFactory);
    this.statusMessageFactory = statusMessageFactory;
    return this;
  }
}
