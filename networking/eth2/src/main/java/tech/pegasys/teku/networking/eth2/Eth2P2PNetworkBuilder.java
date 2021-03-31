/*
 * Copyright 2019 ConsenSys AG.
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
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.gossip.GossipPublisher;
import tech.pegasys.teku.networking.eth2.gossip.GossipSubscriptionsPhase0;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.forks.GossipForkManager;
import tech.pegasys.teku.networking.eth2.gossip.subnets.AttestationSubnetTopicProvider;
import tech.pegasys.teku.networking.eth2.gossip.subnets.PeerSubnetSubscriptions;
import tech.pegasys.teku.networking.eth2.gossip.topics.Eth2GossipTopicFilter;
import tech.pegasys.teku.networking.eth2.gossip.topics.OperationProcessor;
import tech.pegasys.teku.networking.eth2.gossip.topics.ProcessedAttestationSubscriptionProvider;
import tech.pegasys.teku.networking.eth2.peers.Eth2PeerManager;
import tech.pegasys.teku.networking.eth2.peers.Eth2PeerSelectionStrategy;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.RpcEncoding;
import tech.pegasys.teku.networking.p2p.connection.TargetPeerRange;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryConfig;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryNetwork;
import tech.pegasys.teku.networking.p2p.gossip.PreparedGossipMessageFactory;
import tech.pegasys.teku.networking.p2p.libp2p.LibP2PNetwork;
import tech.pegasys.teku.networking.p2p.libp2p.LibP2PPrivateKeyLoader;
import tech.pegasys.teku.networking.p2p.libp2p.gossip.GossipTopicFilter;
import tech.pegasys.teku.networking.p2p.network.PeerHandler;
import tech.pegasys.teku.networking.p2p.network.config.NetworkConfig;
import tech.pegasys.teku.networking.p2p.reputation.ReputationManager;
import tech.pegasys.teku.networking.p2p.rpc.RpcMethod;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.attestation.ValidateableAttestation;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.storage.api.StorageQueryChannel;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.store.KeyValueStore;
import tech.pegasys.teku.util.config.Constants;

public class Eth2P2PNetworkBuilder {
  public static final Duration DEFAULT_ETH2_RPC_PING_INTERVAL = Duration.ofSeconds(10);
  public static final int DEFAULT_ETH2_RPC_OUTSTANDING_PING_THRESHOLD = 2;
  public static final Duration DEFAULT_ETH2_STATUS_UPDATE_INTERVAL = Duration.ofMinutes(5);

  private P2PConfig config;
  private EventChannels eventChannels;
  private RecentChainData recentChainData;
  private OperationProcessor<SignedBeaconBlock> gossipedBlockProcessor;
  private OperationProcessor<ValidateableAttestation> gossipedAttestationConsumer;
  private OperationProcessor<ValidateableAttestation> gossipedAggregateProcessor;
  private OperationProcessor<AttesterSlashing> gossipedAttesterSlashingConsumer;
  private GossipPublisher<AttesterSlashing> attesterSlashingGossipPublisher;
  private OperationProcessor<ProposerSlashing> gossipedProposerSlashingConsumer;
  private GossipPublisher<ProposerSlashing> proposerSlashingGossipPublisher;
  private OperationProcessor<SignedVoluntaryExit> gossipedVoluntaryExitConsumer;
  private GossipPublisher<SignedVoluntaryExit> voluntaryExitGossipPublisher;
  private ProcessedAttestationSubscriptionProvider processedAttestationSubscriptionProvider;
  private StorageQueryChannel historicalChainData;
  private MetricsSystem metricsSystem;
  private final List<RpcMethod> rpcMethods = new ArrayList<>();
  private final List<PeerHandler> peerHandlers = new ArrayList<>();
  private TimeProvider timeProvider;
  private AsyncRunner asyncRunner;
  private KeyValueStore<String, Bytes> keyValueStore;
  private Duration eth2RpcPingInterval = DEFAULT_ETH2_RPC_PING_INTERVAL;
  private int eth2RpcOutstandingPingThreshold = DEFAULT_ETH2_RPC_OUTSTANDING_PING_THRESHOLD;
  private final Duration eth2StatusUpdateInterval = DEFAULT_ETH2_STATUS_UPDATE_INTERVAL;
  private Optional<Checkpoint> requiredCheckpoint = Optional.empty();
  private Spec spec;

  private Eth2P2PNetworkBuilder() {}

  public static Eth2P2PNetworkBuilder create() {
    return new Eth2P2PNetworkBuilder();
  }

  public Eth2P2PNetwork build() {
    validate();

    // Setup eth2 handlers
    final AttestationSubnetService attestationSubnetService = new AttestationSubnetService();
    final RpcEncoding rpcEncoding = RpcEncoding.SSZ_SNAPPY;
    final Eth2PeerManager eth2PeerManager =
        Eth2PeerManager.create(
            asyncRunner,
            recentChainData,
            historicalChainData,
            metricsSystem,
            attestationSubnetService,
            rpcEncoding,
            requiredCheckpoint,
            eth2RpcPingInterval,
            eth2RpcOutstandingPingThreshold,
            eth2StatusUpdateInterval,
            timeProvider,
            config.getPeerRateLimit(),
            config.getPeerRequestLimit(),
            spec);
    final Collection<RpcMethod> eth2RpcMethods = eth2PeerManager.getBeaconChainMethods().all();
    rpcMethods.addAll(eth2RpcMethods);
    peerHandlers.add(eth2PeerManager);

    final GossipEncoding gossipEncoding = config.getGossipEncoding();
    // Build core network and inject eth2 handlers
    final DiscoveryNetwork<?> network = buildNetwork(gossipEncoding);

    final GossipForkManager gossipForkManager = buildGossipForkManager(gossipEncoding, network);

    return new ActiveEth2P2PNetwork(
        config.getSpec(),
        asyncRunner,
        network,
        eth2PeerManager,
        gossipForkManager,
        eventChannels,
        recentChainData,
        attestationSubnetService,
        gossipEncoding,
        config.getGossipConfigurator(),
        processedAttestationSubscriptionProvider);
  }

  private GossipForkManager buildGossipForkManager(
      final GossipEncoding gossipEncoding, final DiscoveryNetwork<?> network) {
    final GossipForkManager.Builder gossipForkManagerBuilder =
        GossipForkManager.builder().spec(spec).recentChainData(recentChainData);
    gossipForkManagerBuilder.fork(
        new GossipSubscriptionsPhase0(
            spec.getForkManifest().get(UInt64.ZERO),
            spec,
            asyncRunner,
            metricsSystem,
            network,
            recentChainData,
            gossipEncoding,
            gossipedBlockProcessor,
            gossipedAttestationConsumer,
            gossipedAggregateProcessor,
            gossipedAttesterSlashingConsumer,
            attesterSlashingGossipPublisher,
            gossipedProposerSlashingConsumer,
            proposerSlashingGossipPublisher,
            gossipedVoluntaryExitConsumer,
            voluntaryExitGossipPublisher));
    return gossipForkManagerBuilder.build();
  }

  protected DiscoveryNetwork<?> buildNetwork(final GossipEncoding gossipEncoding) {
    final ReputationManager reputationManager =
        new ReputationManager(metricsSystem, timeProvider, Constants.REPUTATION_MANAGER_CAPACITY);
    PreparedGossipMessageFactory defaultMessageFactory =
        (__, msg) -> gossipEncoding.prepareUnknownMessage(msg);
    final GossipTopicFilter gossipTopicsFilter =
        new Eth2GossipTopicFilter(recentChainData, gossipEncoding, spec);
    final NetworkConfig networkConfig = config.getNetworkConfig();
    final DiscoveryConfig discoConfig = config.getDiscoveryConfig();
    final LibP2PNetwork p2pNetwork =
        new LibP2PNetwork(
            asyncRunner,
            networkConfig,
            new LibP2PPrivateKeyLoader(keyValueStore, networkConfig.getPrivateKeyFile()),
            reputationManager,
            metricsSystem,
            rpcMethods,
            peerHandlers,
            defaultMessageFactory,
            gossipTopicsFilter);
    final AttestationSubnetTopicProvider subnetTopicProvider =
        new AttestationSubnetTopicProvider(recentChainData, gossipEncoding);

    final TargetPeerRange targetPeerRange =
        new TargetPeerRange(
            discoConfig.getMinPeers(),
            discoConfig.getMaxPeers(),
            discoConfig.getMinRandomlySelectedPeers());
    return DiscoveryNetwork.create(
        metricsSystem,
        asyncRunner,
        keyValueStore,
        p2pNetwork,
        new Eth2PeerSelectionStrategy(
            targetPeerRange,
            network ->
                PeerSubnetSubscriptions.create(
                    network, subnetTopicProvider, config.getTargetSubnetSubscriberCount()),
            reputationManager,
            Collections::shuffle),
        discoConfig,
        networkConfig,
        config.getSpec());
  }

  private void validate() {
    assertNotNull("config", config);
    assertNotNull("eventChannels", eventChannels);
    assertNotNull("metricsSystem", metricsSystem);
    assertNotNull("chainStorageClient", recentChainData);
    assertNotNull("keyValueStore", keyValueStore);
    assertNotNull("timeProvider", timeProvider);
    assertNotNull("gossipedBlockProcessor", gossipedBlockProcessor);
    assertNotNull("gossipedAttestationProcessor", gossipedAttestationConsumer);
    assertNotNull("gossipedAggregateProcessor", gossipedAggregateProcessor);
    assertNotNull("gossipedAttesterSlashingProcessor", gossipedAttesterSlashingConsumer);
    assertNotNull("gossipedProposerSlashingProcessor", gossipedProposerSlashingConsumer);
    assertNotNull("gossipedVoluntaryExitProcessor", gossipedVoluntaryExitConsumer);
    assertNotNull("voluntaryExitGossipPublisher", voluntaryExitGossipPublisher);
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

  public Eth2P2PNetworkBuilder historicalChainData(final StorageQueryChannel historicalChainData) {
    checkNotNull(historicalChainData);
    this.historicalChainData = historicalChainData;
    return this;
  }

  public Eth2P2PNetworkBuilder recentChainData(final RecentChainData recentChainData) {
    checkNotNull(recentChainData);
    this.recentChainData = recentChainData;
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

  public Eth2P2PNetworkBuilder voluntaryExitGossipPublisher(
      final GossipPublisher<SignedVoluntaryExit> voluntaryExitGossipPublisher) {
    checkNotNull(voluntaryExitGossipPublisher);
    this.voluntaryExitGossipPublisher = voluntaryExitGossipPublisher;
    return this;
  }

  public Eth2P2PNetworkBuilder attesterSlashingGossipPublisher(
      final GossipPublisher<AttesterSlashing> attesterSlashingGossipPublisher) {
    checkNotNull(attesterSlashingGossipPublisher);
    this.attesterSlashingGossipPublisher = attesterSlashingGossipPublisher;
    return this;
  }

  public Eth2P2PNetworkBuilder proposerSlashingGossipPublisher(
      final GossipPublisher<ProposerSlashing> proposerSlashingGossipPublisher) {
    checkNotNull(proposerSlashingGossipPublisher);
    this.proposerSlashingGossipPublisher = proposerSlashingGossipPublisher;
    return this;
  }

  public Eth2P2PNetworkBuilder gossipedBlockProcessor(
      final OperationProcessor<SignedBeaconBlock> blockProcessor) {
    checkNotNull(blockProcessor);
    this.gossipedBlockProcessor = blockProcessor;
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

  public Eth2P2PNetworkBuilder metricsSystem(final MetricsSystem metricsSystem) {
    checkNotNull(metricsSystem);
    this.metricsSystem = metricsSystem;
    return this;
  }

  public Eth2P2PNetworkBuilder timeProvider(final TimeProvider timeProvider) {
    this.timeProvider = timeProvider;
    return this;
  }

  public Eth2P2PNetworkBuilder rpcMethod(final RpcMethod rpcMethod) {
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
}
