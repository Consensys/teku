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
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.eventbus.EventBus;
import io.libp2p.core.crypto.KEY_TYPE;
import io.libp2p.core.crypto.KeyKt;
import java.net.BindException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import tech.pegasys.teku.datastructures.attestation.ProcessedAttestationListener;
import tech.pegasys.teku.datastructures.attestation.ValidateableAttestation;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.DelayedExecutorAsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.Waiter;
import tech.pegasys.teku.infrastructure.subscribers.Subscribers;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.networking.eth2.gossip.GossipPublisher;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.subnets.AttestationSubnetTopicProvider;
import tech.pegasys.teku.networking.eth2.gossip.subnets.PeerSubnetSubscriptions;
import tech.pegasys.teku.networking.eth2.gossip.topics.Eth2GossipTopicFilter;
import tech.pegasys.teku.networking.eth2.gossip.topics.OperationProcessor;
import tech.pegasys.teku.networking.eth2.gossip.topics.ProcessedAttestationSubscriptionProvider;
import tech.pegasys.teku.networking.eth2.gossip.topics.VerifiedBlockAttestationsSubscriptionProvider;
import tech.pegasys.teku.networking.eth2.peers.Eth2PeerManager;
import tech.pegasys.teku.networking.eth2.peers.Eth2PeerSelectionStrategy;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.RpcEncoding;
import tech.pegasys.teku.networking.p2p.DiscoveryNetwork;
import tech.pegasys.teku.networking.p2p.connection.TargetPeerRange;
import tech.pegasys.teku.networking.p2p.libp2p.LibP2PNetwork;
import tech.pegasys.teku.networking.p2p.libp2p.gossip.GossipTopicFilter;
import tech.pegasys.teku.networking.p2p.network.GossipConfig;
import tech.pegasys.teku.networking.p2p.network.NetworkConfig;
import tech.pegasys.teku.networking.p2p.network.P2PNetwork;
import tech.pegasys.teku.networking.p2p.network.PeerHandler;
import tech.pegasys.teku.networking.p2p.network.WireLogsConfig;
import tech.pegasys.teku.networking.p2p.reputation.ReputationManager;
import tech.pegasys.teku.networking.p2p.rpc.RpcMethod;
import tech.pegasys.teku.statetransition.BeaconChainUtil;
import tech.pegasys.teku.statetransition.block.VerifiedBlockOperationsListener;
import tech.pegasys.teku.storage.api.StorageQueryChannel;
import tech.pegasys.teku.storage.api.StubStorageQueryChannel;
import tech.pegasys.teku.storage.client.MemoryOnlyRecentChainData;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.store.KeyValueStore;
import tech.pegasys.teku.storage.store.MemKeyValueStore;
import tech.pegasys.teku.util.config.Constants;

public class Eth2NetworkFactory {

  protected static final Logger LOG = LogManager.getLogger();
  protected static final NoOpMetricsSystem METRICS_SYSTEM = new NoOpMetricsSystem();
  private static final int MIN_PORT = 6000;
  private static final int MAX_PORT = 9000;

  private final List<Eth2Network> networks = new ArrayList<>();

  public Eth2P2PNetworkBuilder builder() {
    return new Eth2P2PNetworkBuilder();
  }

  public void stopAll() throws InterruptedException, ExecutionException, TimeoutException {
    Waiter.waitFor(
        SafeFuture.allOf(networks.stream().map(P2PNetwork::stop).toArray(SafeFuture[]::new)));
  }

  public class Eth2P2PNetworkBuilder {

    protected List<Eth2Network> peers = new ArrayList<>();
    protected AsyncRunner asyncRunner;
    protected EventBus eventBus;
    protected RecentChainData recentChainData;
    protected StorageQueryChannel historicalChainData = new StubStorageQueryChannel();
    protected OperationProcessor<SignedBeaconBlock> gossipedBlockProcessor;
    protected OperationProcessor<ValidateableAttestation> gossipedAttestationProcessor;
    protected OperationProcessor<ValidateableAttestation> gossipedAggregateProcessor;
    protected OperationProcessor<AttesterSlashing> attesterSlashingProcessor;
    private GossipPublisher<AttesterSlashing> attesterSlashingGossipPublisher;
    protected OperationProcessor<ProposerSlashing> proposerSlashingProcessor;
    private GossipPublisher<ProposerSlashing> proposerSlashingGossipPublisher;
    protected OperationProcessor<SignedVoluntaryExit> voluntaryExitProcessor;
    protected GossipPublisher<SignedVoluntaryExit> voluntaryExitPublisher;
    protected ProcessedAttestationSubscriptionProvider processedAttestationSubscriptionProvider;
    protected VerifiedBlockAttestationsSubscriptionProvider
        verifiedBlockAttestationsSubscriptionProvider;
    protected Function<RpcMethod, Stream<RpcMethod>> rpcMethodsModifier = Stream::of;
    protected List<PeerHandler> peerHandlers = new ArrayList<>();
    protected RpcEncoding rpcEncoding = RpcEncoding.SSZ_SNAPPY;
    protected GossipEncoding gossipEncoding = GossipEncoding.SSZ_SNAPPY;
    private Optional<Checkpoint> requiredCheckpoint = Optional.empty();
    protected Duration eth2RpcPingInterval;
    protected Integer eth2RpcOutstandingPingThreshold;
    protected Duration eth2StatusUpdateInterval;

    public Eth2Network startNetwork() throws Exception {
      setDefaults();
      final Eth2Network network = buildAndStartNetwork();
      networks.add(network);
      return network;
    }

    protected Eth2Network buildAndStartNetwork() throws Exception {
      int attempt = 1;
      while (true) {
        final NetworkConfig config = generateConfig();
        final Eth2Network network = buildNetwork(config);
        try {
          network.start().get(30, TimeUnit.SECONDS);
          networks.add(network);
          Waiter.waitFor(() -> assertThat(network.getPeerCount()).isEqualTo(peers.size()));
          return network;
        } catch (ExecutionException e) {
          if (e.getCause() instanceof BindException) {
            if (attempt > 10) {
              throw new RuntimeException("Failed to find a free port after multiple attempts", e);
            }
            LOG.info(
                "Port conflict detected, retrying with a new port. Original message: {}",
                e.getMessage());
            attempt++;
            Waiter.waitFor(network.stop());
          } else {
            throw e;
          }
        }
      }
    }

    protected Eth2Network buildNetwork(final NetworkConfig config) {
      {
        // Setup eth2 handlers
        final AttestationSubnetService attestationSubnetService = new AttestationSubnetService();
        final Eth2PeerManager eth2PeerManager =
            Eth2PeerManager.create(
                asyncRunner,
                recentChainData,
                historicalChainData,
                METRICS_SYSTEM,
                attestationSubnetService,
                rpcEncoding,
                requiredCheckpoint,
                eth2RpcPingInterval,
                eth2RpcOutstandingPingThreshold,
                eth2StatusUpdateInterval,
                StubTimeProvider.withTimeInSeconds(1000),
                500,
                50);

        List<RpcMethod> rpcMethods =
            eth2PeerManager.getBeaconChainMethods().all().stream()
                .flatMap(rpcMethodsModifier)
                .collect(toList());

        this.peerHandler(eth2PeerManager);

        final NoOpMetricsSystem metricsSystem = new NoOpMetricsSystem();
        final ReputationManager reputationManager =
            new ReputationManager(
                metricsSystem,
                StubTimeProvider.withTimeInSeconds(1000),
                Constants.REPUTATION_MANAGER_CAPACITY);
        final AttestationSubnetTopicProvider subnetTopicProvider =
            new AttestationSubnetTopicProvider(recentChainData, gossipEncoding);
        final GossipTopicFilter gossipTopicsFilter =
            new Eth2GossipTopicFilter(recentChainData, gossipEncoding);
        final KeyValueStore<String, Bytes> keyValueStore = new MemKeyValueStore<>();
        final DiscoveryNetwork<?> network =
            DiscoveryNetwork.create(
                metricsSystem,
                asyncRunner,
                keyValueStore,
                new LibP2PNetwork(
                    asyncRunner,
                    config,
                    reputationManager,
                    METRICS_SYSTEM,
                    new ArrayList<>(rpcMethods),
                    peerHandlers,
                    (__, msg) -> gossipEncoding.prepareUnknownMessage(msg),
                    gossipTopicsFilter),
                new Eth2PeerSelectionStrategy(
                    config.getTargetPeerRange(),
                    gossipNetwork ->
                        PeerSubnetSubscriptions.create(
                            gossipNetwork,
                            subnetTopicProvider,
                            config.getTargetSubnetSubscriberCount()),
                    reputationManager,
                    Collections::shuffle),
                config);

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
            gossipedAttestationProcessor,
            gossipedAggregateProcessor,
            attesterSlashingProcessor,
            attesterSlashingGossipPublisher,
            proposerSlashingProcessor,
            proposerSlashingGossipPublisher,
            voluntaryExitProcessor,
            voluntaryExitPublisher,
            processedAttestationSubscriptionProvider);
      }
    }

    private NetworkConfig generateConfig() {
      final List<String> peerAddresses =
          peers.stream().map(P2PNetwork::getNodeAddress).collect(toList());

      final Random random = new Random();
      final int port = MIN_PORT + random.nextInt(MAX_PORT - MIN_PORT);

      return new NetworkConfig(
          KeyKt.generateKeyPair(KEY_TYPE.SECP256K1).component1(),
          "127.0.0.1",
          Optional.empty(),
          port,
          OptionalInt.empty(),
          peerAddresses,
          false,
          emptyList(),
          new TargetPeerRange(20, 30, 0),
          2,
          GossipConfig.DEFAULT_CONFIG,
          new WireLogsConfig(false, false, true, false));
    }

    private void setDefaults() {
      if (eventBus == null) {
        eventBus = new EventBus();
      }
      if (asyncRunner == null) {
        asyncRunner = DelayedExecutorAsyncRunner.create();
      }
      if (eth2RpcPingInterval == null) {
        eth2RpcPingInterval = Eth2NetworkBuilder.DEFAULT_ETH2_RPC_PING_INTERVAL;
      }
      if (eth2StatusUpdateInterval == null) {
        eth2StatusUpdateInterval = Eth2NetworkBuilder.DEFAULT_ETH2_STATUS_UPDATE_INTERVAL;
      }
      if (eth2RpcOutstandingPingThreshold == null) {
        eth2RpcOutstandingPingThreshold =
            Eth2NetworkBuilder.DEFAULT_ETH2_RPC_OUTSTANDING_PING_THRESHOLD;
      }
      if (recentChainData == null) {
        recentChainData = MemoryOnlyRecentChainData.create(eventBus);
        BeaconChainUtil.create(0, recentChainData).initializeStorage();
      }
      if (processedAttestationSubscriptionProvider == null) {
        Subscribers<ProcessedAttestationListener> subscribers = Subscribers.create(false);
        processedAttestationSubscriptionProvider = subscribers::subscribe;
      }
      if (verifiedBlockAttestationsSubscriptionProvider == null) {
        Subscribers<VerifiedBlockOperationsListener<Attestation>> subscribers =
            Subscribers.create(false);
        verifiedBlockAttestationsSubscriptionProvider = subscribers::subscribe;
      }
      if (gossipedBlockProcessor == null) {
        gossipedBlockProcessor = OperationProcessor.noop();
      }
      if (gossipedAttestationProcessor == null) {
        gossipedAttestationProcessor = OperationProcessor.noop();
      }
      if (gossipedAggregateProcessor == null) {
        gossipedAggregateProcessor = OperationProcessor.noop();
      }
      if (attesterSlashingProcessor == null) {
        attesterSlashingProcessor = OperationProcessor.noop();
      }
      if (proposerSlashingProcessor == null) {
        proposerSlashingProcessor = OperationProcessor.noop();
      }
      if (voluntaryExitProcessor == null) {
        voluntaryExitProcessor = OperationProcessor.noop();
      }
      if (voluntaryExitPublisher == null) {
        voluntaryExitPublisher = new GossipPublisher<>();
      }
      if (proposerSlashingGossipPublisher == null) {
        proposerSlashingGossipPublisher = new GossipPublisher<>();
      }
      if (attesterSlashingGossipPublisher == null) {
        attesterSlashingGossipPublisher = new GossipPublisher<>();
      }
    }

    public Eth2P2PNetworkBuilder rpcEncoding(final RpcEncoding rpcEncoding) {
      checkNotNull(rpcEncoding);
      this.rpcEncoding = rpcEncoding;
      return this;
    }

    public Eth2P2PNetworkBuilder gossipEncoding(final GossipEncoding gossipEncoding) {
      checkNotNull(gossipEncoding);
      this.gossipEncoding = gossipEncoding;
      return this;
    }

    public Eth2P2PNetworkBuilder setRequiredCheckpoint(
        final Optional<Checkpoint> requiredCheckpoint) {
      this.requiredCheckpoint = requiredCheckpoint;
      return this;
    }

    public Eth2P2PNetworkBuilder peer(final Eth2Network peer) {
      this.peers.add(peer);
      return this;
    }

    public Eth2P2PNetworkBuilder eventBus(final EventBus eventBus) {
      checkNotNull(eventBus);
      this.eventBus = eventBus;
      return this;
    }

    public Eth2P2PNetworkBuilder recentChainData(final RecentChainData recentChainData) {
      checkNotNull(recentChainData);
      this.recentChainData = recentChainData;
      return this;
    }

    public Eth2P2PNetworkBuilder historicalChainData(
        final StorageQueryChannel historicalChainData) {
      checkNotNull(historicalChainData);
      this.historicalChainData = historicalChainData;
      return this;
    }

    public Eth2P2PNetworkBuilder gossipedBlockProcessor(
        final OperationProcessor<SignedBeaconBlock> gossipedBlockProcessor) {
      checkNotNull(gossipedBlockProcessor);
      this.gossipedBlockProcessor = gossipedBlockProcessor;
      return this;
    }

    public Eth2P2PNetworkBuilder gossipedAggregateProcessor(
        final OperationProcessor<ValidateableAttestation> gossipedAggregateProcessor) {
      checkNotNull(gossipedAggregateProcessor);
      this.gossipedAggregateProcessor = gossipedAggregateProcessor;
      return this;
    }

    public Eth2P2PNetworkBuilder gossipedAttestationProcessor(
        final OperationProcessor<ValidateableAttestation> gossipedAttestationProcessor) {
      checkNotNull(gossipedAttestationProcessor);
      this.gossipedAttestationProcessor = gossipedAttestationProcessor;
      return this;
    }

    public Eth2P2PNetworkBuilder gossipedAttesterSlashingProcessor(
        final OperationProcessor<AttesterSlashing> gossipedAttesterSlashingProcessor) {
      checkNotNull(gossipedAttesterSlashingProcessor);
      this.attesterSlashingProcessor = gossipedAttesterSlashingProcessor;
      return this;
    }

    public Eth2P2PNetworkBuilder attesterSlashingGossipPublisher(
        final GossipPublisher<AttesterSlashing> attesterSlashingGossipPublisher) {
      checkNotNull(attesterSlashingGossipPublisher);
      this.attesterSlashingGossipPublisher = attesterSlashingGossipPublisher;
      return this;
    }

    public Eth2P2PNetworkBuilder gossipedProposerSlashingProcessor(
        final OperationProcessor<ProposerSlashing> gossipedProposerSlashingProcessor) {
      checkNotNull(gossipedProposerSlashingProcessor);
      this.proposerSlashingProcessor = gossipedProposerSlashingProcessor;
      return this;
    }

    public Eth2P2PNetworkBuilder proposerSlashingGossipPublisher(
        final GossipPublisher<ProposerSlashing> proposerSlashingGossipPublisher) {
      checkNotNull(proposerSlashingGossipPublisher);
      this.proposerSlashingGossipPublisher = proposerSlashingGossipPublisher;
      return this;
    }

    public Eth2P2PNetworkBuilder gossipedVoluntaryExitProcessor(
        final OperationProcessor<SignedVoluntaryExit> gossipedVoluntaryExitProcessor) {
      checkNotNull(gossipedVoluntaryExitProcessor);
      this.voluntaryExitProcessor = gossipedVoluntaryExitProcessor;
      return this;
    }

    public Eth2P2PNetworkBuilder voluntaryExitPublisher(
        final GossipPublisher<SignedVoluntaryExit> publisher) {
      checkNotNull(publisher);
      this.voluntaryExitPublisher = publisher;
      return this;
    }

    public Eth2P2PNetworkBuilder processedAttestationSubscriptionProvider(
        final ProcessedAttestationSubscriptionProvider processedAttestationSubscriptionProvider) {
      checkNotNull(processedAttestationSubscriptionProvider);
      this.processedAttestationSubscriptionProvider = processedAttestationSubscriptionProvider;
      return this;
    }

    public Eth2P2PNetworkBuilder verifiedBlockAttestationsSubscriptionProvider(
        final VerifiedBlockAttestationsSubscriptionProvider
            verifiedBlockAttestationsSubscriptionProvider) {
      checkNotNull(verifiedBlockAttestationsSubscriptionProvider);
      this.verifiedBlockAttestationsSubscriptionProvider =
          verifiedBlockAttestationsSubscriptionProvider;
      return this;
    }

    public Eth2P2PNetworkBuilder rpcMethodsModifier(
        Function<RpcMethod, Stream<RpcMethod>> rpcMethodsModifier) {
      checkNotNull(rpcMethodsModifier);
      this.rpcMethodsModifier = rpcMethodsModifier;
      return this;
    }

    public Eth2P2PNetworkBuilder peerHandler(final PeerHandler peerHandler) {
      checkNotNull(peerHandler);
      peerHandlers.add(peerHandler);
      return this;
    }

    public Eth2P2PNetworkBuilder asyncRunner(AsyncRunner asyncRunner) {
      checkNotNull(asyncRunner);
      this.asyncRunner = asyncRunner;
      return this;
    }

    public Eth2P2PNetworkBuilder eth2RpcPingInterval(Duration eth2RpcPingInterval) {
      checkNotNull(eth2RpcPingInterval);
      this.eth2RpcPingInterval = eth2RpcPingInterval;
      return this;
    }

    public Eth2P2PNetworkBuilder eth2RpcOutstandingPingThreshold(
        int eth2RpcOutstandingPingThreshold) {
      checkArgument(eth2RpcOutstandingPingThreshold > 0);
      this.eth2RpcOutstandingPingThreshold = eth2RpcOutstandingPingThreshold;
      return this;
    }

    public Eth2P2PNetworkBuilder eth2StatusUpdateInterval(Duration eth2StatusUpdateInterval) {
      checkNotNull(eth2StatusUpdateInterval);
      this.eth2StatusUpdateInterval = eth2StatusUpdateInterval;
      return this;
    }
  }
}
