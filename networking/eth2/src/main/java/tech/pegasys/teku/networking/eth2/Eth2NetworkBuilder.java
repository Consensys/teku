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

import com.google.common.eventbus.EventBus;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.datastructures.attestation.ValidateableAttestation;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.networking.eth2.gossip.GossipPublisher;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.subnets.AttestationSubnetTopicProvider;
import tech.pegasys.teku.networking.eth2.gossip.subnets.PeerSubnetSubscriptions;
import tech.pegasys.teku.networking.eth2.gossip.topics.Eth2GossipTopicFilter;
import tech.pegasys.teku.networking.eth2.gossip.topics.OperationProcessor;
import tech.pegasys.teku.networking.eth2.gossip.topics.ProcessedAttestationSubscriptionProvider;
import tech.pegasys.teku.networking.eth2.peers.Eth2PeerManager;
import tech.pegasys.teku.networking.eth2.peers.Eth2PeerSelectionStrategy;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.RpcEncoding;
import tech.pegasys.teku.networking.p2p.DiscoveryNetwork;
import tech.pegasys.teku.networking.p2p.gossip.PreparedGossipMessageFactory;
import tech.pegasys.teku.networking.p2p.libp2p.LibP2PNetwork;
import tech.pegasys.teku.networking.p2p.libp2p.gossip.GossipTopicFilter;
import tech.pegasys.teku.networking.p2p.network.NetworkConfig;
import tech.pegasys.teku.networking.p2p.network.PeerHandler;
import tech.pegasys.teku.networking.p2p.reputation.ReputationManager;
import tech.pegasys.teku.networking.p2p.rpc.RpcMethod;
import tech.pegasys.teku.storage.api.StorageQueryChannel;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.store.KeyValueStore;
import tech.pegasys.teku.util.config.Constants;

public class Eth2NetworkBuilder {
  public static final Duration DEFAULT_ETH2_RPC_PING_INTERVAL = Duration.ofSeconds(10);
  public static final int DEFAULT_ETH2_RPC_OUTSTANDING_PING_THRESHOLD = 2;
  public static final Duration DEFAULT_ETH2_STATUS_UPDATE_INTERVAL = Duration.ofMinutes(5);

  private NetworkConfig config;
  private Eth2Config eth2Config;
  private EventBus eventBus;
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
  private int peerRateLimit = Constants.MAX_BLOCKS_PER_MINUTE;
  private int peerRequestLimit = 50;

  private Eth2NetworkBuilder() {}

  public static Eth2NetworkBuilder create() {
    return new Eth2NetworkBuilder();
  }

  public Eth2Network build() {
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
            eth2Config.getRequiredCheckpoint(),
            eth2RpcPingInterval,
            eth2RpcOutstandingPingThreshold,
            eth2StatusUpdateInterval,
            timeProvider,
            peerRateLimit,
            peerRequestLimit);
    final Collection<RpcMethod> eth2RpcMethods = eth2PeerManager.getBeaconChainMethods().all();
    rpcMethods.addAll(eth2RpcMethods);
    peerHandlers.add(eth2PeerManager);

    final GossipEncoding gossipEncoding = GossipEncoding.SSZ_SNAPPY;
    // Build core network and inject eth2 handlers
    final DiscoveryNetwork<?> network = buildNetwork(gossipEncoding);

    return new ActiveEth2Network(
        asyncRunner,
        metricsSystem,
        network,
        eth2PeerManager,
        eventBus,
        recentChainData,
        gossipEncoding,
        attestationSubnetService,
        gossipedBlockProcessor,
        gossipedAttestationConsumer,
        gossipedAggregateProcessor,
        gossipedAttesterSlashingConsumer,
        attesterSlashingGossipPublisher,
        gossipedProposerSlashingConsumer,
        proposerSlashingGossipPublisher,
        gossipedVoluntaryExitConsumer,
        voluntaryExitGossipPublisher,
        processedAttestationSubscriptionProvider);
  }

  protected DiscoveryNetwork<?> buildNetwork(final GossipEncoding gossipEncoding) {
    final ReputationManager reputationManager =
        new ReputationManager(metricsSystem, timeProvider, Constants.REPUTATION_MANAGER_CAPACITY);
    PreparedGossipMessageFactory defaultMessageFactory =
        (__, msg) -> gossipEncoding.prepareUnknownMessage(msg);
    final GossipTopicFilter gossipTopicsFilter =
        new Eth2GossipTopicFilter(recentChainData, gossipEncoding);
    final LibP2PNetwork p2pNetwork =
        new LibP2PNetwork(
            asyncRunner,
            config,
            reputationManager,
            metricsSystem,
            rpcMethods,
            peerHandlers,
            defaultMessageFactory,
            gossipTopicsFilter);
    final AttestationSubnetTopicProvider subnetTopicProvider =
        new AttestationSubnetTopicProvider(recentChainData, gossipEncoding);
    return DiscoveryNetwork.create(
        metricsSystem,
        asyncRunner,
        keyValueStore,
        p2pNetwork,
        new Eth2PeerSelectionStrategy(
            config.getTargetPeerRange(),
            network ->
                PeerSubnetSubscriptions.create(
                    network, subnetTopicProvider, config.getTargetSubnetSubscriberCount()),
            reputationManager,
            Collections::shuffle),
        config);
  }

  private void validate() {
    assertNotNull("config", config);
    assertNotNull("eth2Config", eth2Config);
    assertNotNull("eventBus", eventBus);
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

  public Eth2NetworkBuilder config(final NetworkConfig config) {
    checkNotNull(config);
    this.config = config;
    return this;
  }

  public Eth2NetworkBuilder peerRateLimit(final int peerRateLimit) {
    this.peerRateLimit = peerRateLimit;
    return this;
  }

  public Eth2NetworkBuilder peerRequestLimit(final int peerRequestLimit) {
    this.peerRequestLimit = peerRequestLimit;
    return this;
  }

  public Eth2NetworkBuilder eth2Config(final Eth2Config eth2Config) {
    checkNotNull(eth2Config);
    this.eth2Config = eth2Config;
    return this;
  }

  public Eth2NetworkBuilder eventBus(final EventBus eventBus) {
    checkNotNull(eventBus);
    this.eventBus = eventBus;
    return this;
  }

  public Eth2NetworkBuilder historicalChainData(final StorageQueryChannel historicalChainData) {
    checkNotNull(historicalChainData);
    this.historicalChainData = historicalChainData;
    return this;
  }

  public Eth2NetworkBuilder recentChainData(final RecentChainData recentChainData) {
    checkNotNull(recentChainData);
    this.recentChainData = recentChainData;
    return this;
  }

  public Eth2NetworkBuilder keyValueStore(final KeyValueStore<String, Bytes> kvStore) {
    checkNotNull(kvStore);
    this.keyValueStore = kvStore;
    return this;
  }

  public Eth2NetworkBuilder processedAttestationSubscriptionProvider(
      final ProcessedAttestationSubscriptionProvider processedAttestationSubscriptionProvider) {
    checkNotNull(processedAttestationSubscriptionProvider);
    this.processedAttestationSubscriptionProvider = processedAttestationSubscriptionProvider;
    return this;
  }

  public Eth2NetworkBuilder voluntaryExitGossipPublisher(
      final GossipPublisher<SignedVoluntaryExit> voluntaryExitGossipPublisher) {
    checkNotNull(voluntaryExitGossipPublisher);
    this.voluntaryExitGossipPublisher = voluntaryExitGossipPublisher;
    return this;
  }

  public Eth2NetworkBuilder attesterSlashingGossipPublisher(
      final GossipPublisher<AttesterSlashing> attesterSlashingGossipPublisher) {
    checkNotNull(attesterSlashingGossipPublisher);
    this.attesterSlashingGossipPublisher = attesterSlashingGossipPublisher;
    return this;
  }

  public Eth2NetworkBuilder proposerSlashingGossipPublisher(
      final GossipPublisher<ProposerSlashing> proposerSlashingGossipPublisher) {
    checkNotNull(proposerSlashingGossipPublisher);
    this.proposerSlashingGossipPublisher = proposerSlashingGossipPublisher;
    return this;
  }

  public Eth2NetworkBuilder gossipedBlockProcessor(
      final OperationProcessor<SignedBeaconBlock> blockProcessor) {
    checkNotNull(blockProcessor);
    this.gossipedBlockProcessor = blockProcessor;
    return this;
  }

  public Eth2NetworkBuilder gossipedAttestationProcessor(
      final OperationProcessor<ValidateableAttestation> gossipedAttestationProcessor) {
    checkNotNull(gossipedAttestationProcessor);
    this.gossipedAttestationConsumer = gossipedAttestationProcessor;
    return this;
  }

  public Eth2NetworkBuilder gossipedAggregateProcessor(
      final OperationProcessor<ValidateableAttestation> gossipedAggregateProcessor) {
    checkNotNull(gossipedAggregateProcessor);
    this.gossipedAggregateProcessor = gossipedAggregateProcessor;
    return this;
  }

  public Eth2NetworkBuilder gossipedAttesterSlashingProcessor(
      final OperationProcessor<AttesterSlashing> gossipedAttesterSlashingProcessor) {
    checkNotNull(gossipedAttesterSlashingProcessor);
    this.gossipedAttesterSlashingConsumer = gossipedAttesterSlashingProcessor;
    return this;
  }

  public Eth2NetworkBuilder gossipedProposerSlashingProcessor(
      final OperationProcessor<ProposerSlashing> gossipedProposerSlashingProcessor) {
    checkNotNull(gossipedProposerSlashingProcessor);
    this.gossipedProposerSlashingConsumer = gossipedProposerSlashingProcessor;
    return this;
  }

  public Eth2NetworkBuilder gossipedVoluntaryExitProcessor(
      final OperationProcessor<SignedVoluntaryExit> gossipedVoluntaryExitProcessor) {
    checkNotNull(gossipedVoluntaryExitProcessor);
    this.gossipedVoluntaryExitConsumer = gossipedVoluntaryExitProcessor;
    return this;
  }

  public Eth2NetworkBuilder metricsSystem(final MetricsSystem metricsSystem) {
    checkNotNull(metricsSystem);
    this.metricsSystem = metricsSystem;
    return this;
  }

  public Eth2NetworkBuilder timeProvider(final TimeProvider timeProvider) {
    this.timeProvider = timeProvider;
    return this;
  }

  public Eth2NetworkBuilder rpcMethod(final RpcMethod rpcMethod) {
    checkNotNull(rpcMethod);
    rpcMethods.add(rpcMethod);
    return this;
  }

  public Eth2NetworkBuilder peerHandler(final PeerHandler peerHandler) {
    checkNotNull(peerHandler);
    peerHandlers.add(peerHandler);
    return this;
  }

  public Eth2NetworkBuilder asyncRunner(final AsyncRunner asyncRunner) {
    checkNotNull(asyncRunner);
    this.asyncRunner = asyncRunner;
    return this;
  }

  public Eth2NetworkBuilder eth2RpcPingInterval(final Duration eth2RpcPingInterval) {
    checkNotNull(eth2RpcPingInterval);
    this.eth2RpcPingInterval = eth2RpcPingInterval;
    return this;
  }

  public Eth2NetworkBuilder eth2RpcOutstandingPingThreshold(
      final int eth2RpcOutstandingPingThreshold) {
    checkArgument(eth2RpcOutstandingPingThreshold > 0);
    this.eth2RpcOutstandingPingThreshold = eth2RpcOutstandingPingThreshold;
    return this;
  }
}
