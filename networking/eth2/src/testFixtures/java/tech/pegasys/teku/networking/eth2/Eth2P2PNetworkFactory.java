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
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.spec.config.Constants.MAX_CHUNK_SIZE;

import java.net.BindException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
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
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.DelayedExecutorAsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.Waiter;
import tech.pegasys.teku.infrastructure.events.EventChannels;
import tech.pegasys.teku.infrastructure.metrics.SettableLabelledGauge;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.subscribers.Subscribers;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.network.p2p.jvmlibp2p.PrivateKeyGenerator;
import tech.pegasys.teku.networking.eth2.gossip.config.GossipConfigurator;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.forks.GossipForkManager;
import tech.pegasys.teku.networking.eth2.gossip.forks.versions.GossipForkSubscriptionsCapella;
import tech.pegasys.teku.networking.eth2.gossip.forks.versions.GossipForkSubscriptionsPhase0;
import tech.pegasys.teku.networking.eth2.gossip.subnets.AttestationSubnetTopicProvider;
import tech.pegasys.teku.networking.eth2.gossip.subnets.PeerSubnetSubscriptions;
import tech.pegasys.teku.networking.eth2.gossip.subnets.SyncCommitteeSubnetTopicProvider;
import tech.pegasys.teku.networking.eth2.gossip.topics.Eth2GossipTopicFilter;
import tech.pegasys.teku.networking.eth2.gossip.topics.OperationProcessor;
import tech.pegasys.teku.networking.eth2.gossip.topics.ProcessedAttestationSubscriptionProvider;
import tech.pegasys.teku.networking.eth2.gossip.topics.VerifiedBlockAttestationsSubscriptionProvider;
import tech.pegasys.teku.networking.eth2.peers.Eth2PeerManager;
import tech.pegasys.teku.networking.eth2.peers.Eth2PeerSelectionStrategy;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.RpcEncoding;
import tech.pegasys.teku.networking.p2p.connection.TargetPeerRange;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryConfig;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryNetwork;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryNetworkBuilder;
import tech.pegasys.teku.networking.p2p.libp2p.LibP2PNetworkBuilder;
import tech.pegasys.teku.networking.p2p.libp2p.gossip.GossipTopicFilter;
import tech.pegasys.teku.networking.p2p.network.P2PNetwork;
import tech.pegasys.teku.networking.p2p.network.PeerHandler;
import tech.pegasys.teku.networking.p2p.reputation.ReputationManager;
import tech.pegasys.teku.networking.p2p.rpc.RpcMethod;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.Constants;
import tech.pegasys.teku.spec.datastructures.attestation.ProcessedAttestationListener;
import tech.pegasys.teku.spec.datastructures.attestation.ValidateableAttestation;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SignedContributionAndProof;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.ValidateableSyncCommitteeMessage;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsSupplier;
import tech.pegasys.teku.statetransition.BeaconChainUtil;
import tech.pegasys.teku.statetransition.block.VerifiedBlockOperationsListener;
import tech.pegasys.teku.storage.api.StorageQueryChannel;
import tech.pegasys.teku.storage.api.StubStorageQueryChannel;
import tech.pegasys.teku.storage.client.MemoryOnlyRecentChainData;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.store.KeyValueStore;
import tech.pegasys.teku.storage.store.MemKeyValueStore;

public class Eth2P2PNetworkFactory {

  private static final Logger LOG = LogManager.getLogger();
  protected static final NoOpMetricsSystem METRICS_SYSTEM = new NoOpMetricsSystem();
  private static final int MIN_PORT = 6000;
  private static final int MAX_PORT = 9000;

  private final List<Eth2P2PNetwork> networks = new ArrayList<>();

  public Eth2P2PNetworkBuilder builder() {
    return new Eth2P2PNetworkBuilder();
  }

  public void stopAll() throws InterruptedException, ExecutionException, TimeoutException {
    Waiter.waitFor(
        SafeFuture.allOf(networks.stream().map(P2PNetwork::stop).toArray(SafeFuture[]::new)));
  }

  public class Eth2P2PNetworkBuilder {

    protected List<Eth2P2PNetwork> peers = new ArrayList<>();
    protected AsyncRunner asyncRunner;
    protected EventChannels eventChannels;
    protected RecentChainData recentChainData;
    protected StorageQueryChannel historicalChainData = new StubStorageQueryChannel();
    protected OperationProcessor<SignedBeaconBlock> gossipedBlockProcessor;
    protected OperationProcessor<ValidateableAttestation> gossipedAttestationProcessor;
    protected OperationProcessor<ValidateableAttestation> gossipedAggregateProcessor;
    protected OperationProcessor<AttesterSlashing> attesterSlashingProcessor;
    protected OperationProcessor<ProposerSlashing> proposerSlashingProcessor;
    protected OperationProcessor<SignedVoluntaryExit> voluntaryExitProcessor;
    protected OperationProcessor<SignedContributionAndProof> signedContributionAndProofProcessor;
    protected OperationProcessor<ValidateableSyncCommitteeMessage> syncCommitteeMessageProcessor;
    protected OperationProcessor<SignedBlsToExecutionChange> signedBlsToExecutionChangeProcessor;
    protected ProcessedAttestationSubscriptionProvider processedAttestationSubscriptionProvider;
    protected VerifiedBlockAttestationsSubscriptionProvider
        verifiedBlockAttestationsSubscriptionProvider;
    protected Function<RpcMethod<?, ?, ?>, Stream<RpcMethod<?, ?, ?>>> rpcMethodsModifier =
        Stream::of;
    protected List<PeerHandler> peerHandlers = new ArrayList<>();
    protected RpcEncoding rpcEncoding = RpcEncoding.createSszSnappyEncoding(MAX_CHUNK_SIZE);
    protected GossipEncoding gossipEncoding = GossipEncoding.SSZ_SNAPPY;
    private Optional<Checkpoint> requiredCheckpoint = Optional.empty();
    protected Duration eth2RpcPingInterval;
    protected Integer eth2RpcOutstandingPingThreshold;
    protected Duration eth2StatusUpdateInterval;
    protected Spec spec = TestSpecFactory.createMinimalPhase0();

    public Eth2P2PNetwork startNetwork() throws Exception {
      setDefaults();
      final Eth2P2PNetwork network = buildAndStartNetwork();
      networks.add(network);
      return network;
    }

    protected Eth2P2PNetwork buildAndStartNetwork() throws Exception {
      int attempt = 1;
      while (true) {
        final P2PConfig config = generateConfig();
        final Eth2P2PNetwork network = buildNetwork(config);
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

    protected Eth2P2PNetwork buildNetwork(final P2PConfig config) {
      {
        // Setup eth2 handlers
        final SubnetSubscriptionService attestationSubnetService = new SubnetSubscriptionService();
        final SubnetSubscriptionService syncCommitteeSubnetService =
            new SubnetSubscriptionService();
        final Eth2PeerManager eth2PeerManager =
            Eth2PeerManager.create(
                asyncRunner,
                recentChainData,
                historicalChainData,
                METRICS_SYSTEM,
                attestationSubnetService,
                syncCommitteeSubnetService,
                rpcEncoding,
                requiredCheckpoint,
                eth2RpcPingInterval,
                eth2RpcOutstandingPingThreshold,
                eth2StatusUpdateInterval,
                StubTimeProvider.withTimeInSeconds(1000),
                500,
                50,
                spec);

        List<RpcMethod<?, ?, ?>> rpcMethods =
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
        final AttestationSubnetTopicProvider attestationSubnetTopicProvider =
            new AttestationSubnetTopicProvider(recentChainData, gossipEncoding);
        final SyncCommitteeSubnetTopicProvider syncCommitteeTopicProvider =
            new SyncCommitteeSubnetTopicProvider(recentChainData, gossipEncoding);
        final GossipTopicFilter gossipTopicsFilter =
            new Eth2GossipTopicFilter(recentChainData, gossipEncoding, spec);
        final KeyValueStore<String, Bytes> keyValueStore = new MemKeyValueStore<>();
        final DiscoveryConfig discoConfig = config.getDiscoveryConfig();
        final TargetPeerRange targetPeerRange =
            new TargetPeerRange(
                discoConfig.getMinPeers(),
                discoConfig.getMaxPeers(),
                discoConfig.getMinRandomlySelectedPeers());
        final SchemaDefinitionsSupplier currentSchemaDefinitions =
            () -> config.getSpec().getGenesisSchemaDefinitions();
        final SettableLabelledGauge subnetPeerCountGauge =
            SettableLabelledGauge.create(
                metricsSystem,
                TekuMetricCategory.NETWORK,
                "subnet_peer_count",
                "Number of currently connected peers subscribed to each subnet",
                "subnet");
        final DiscoveryNetwork<?> network =
            DiscoveryNetworkBuilder.create()
                .metricsSystem(metricsSystem)
                .asyncRunner(asyncRunner)
                .kvStore(keyValueStore)
                .p2pNetwork(
                    LibP2PNetworkBuilder.create()
                        .asyncRunner(DelayedExecutorAsyncRunner.create())
                        .config(config.getNetworkConfig())
                        .privateKeyProvider(PrivateKeyGenerator::generate)
                        .reputationManager(reputationManager)
                        .metricsSystem(METRICS_SYSTEM)
                        .rpcMethods(new ArrayList<>(rpcMethods))
                        .peerHandlers(peerHandlers)
                        .preparedGossipMessageFactory(
                            gossipEncoding.createPreparedGossipMessageFactory(
                                recentChainData::getMilestoneByForkDigest))
                        .gossipTopicFilter(gossipTopicsFilter)
                        .build())
                .peerSelectionStrategy(
                    new Eth2PeerSelectionStrategy(
                        targetPeerRange,
                        gossipNetwork ->
                            PeerSubnetSubscriptions.create(
                                currentSchemaDefinitions,
                                gossipNetwork,
                                attestationSubnetTopicProvider,
                                syncCommitteeTopicProvider,
                                syncCommitteeSubnetService,
                                config.getTargetSubnetSubscriberCount(),
                                subnetPeerCountGauge),
                        reputationManager,
                        Collections::shuffle))
                .discoveryConfig(config.getDiscoveryConfig())
                .p2pConfig(config.getNetworkConfig())
                .spec(config.getSpec())
                .currentSchemaDefinitionsSupplier(currentSchemaDefinitions)
                .build();

        final GossipForkManager.Builder gossipForkManagerBuilder =
            GossipForkManager.builder().spec(spec).recentChainData(recentChainData);

        if (spec.getForkSchedule().getHighestSupportedMilestone().equals(SpecMilestone.CAPELLA)) {
          gossipForkManagerBuilder.fork(
              new GossipForkSubscriptionsCapella(
                  spec.getForkSchedule().getFork(UInt64.ZERO),
                  spec,
                  asyncRunner,
                  metricsSystem,
                  network,
                  recentChainData,
                  gossipEncoding,
                  gossipedBlockProcessor,
                  gossipedAttestationProcessor,
                  gossipedAggregateProcessor,
                  attesterSlashingProcessor,
                  proposerSlashingProcessor,
                  voluntaryExitProcessor,
                  signedContributionAndProofProcessor,
                  syncCommitteeMessageProcessor,
                  signedBlsToExecutionChangeProcessor));
        } else {
          gossipForkManagerBuilder.fork(
              new GossipForkSubscriptionsPhase0(
                  spec.getForkSchedule().getFork(UInt64.ZERO),
                  spec,
                  asyncRunner,
                  metricsSystem,
                  network,
                  recentChainData,
                  gossipEncoding,
                  gossipedBlockProcessor,
                  gossipedAttestationProcessor,
                  gossipedAggregateProcessor,
                  attesterSlashingProcessor,
                  proposerSlashingProcessor,
                  voluntaryExitProcessor));
        }
        final GossipForkManager gossipForkManager = gossipForkManagerBuilder.build();

        return new ActiveEth2P2PNetwork(
            spec,
            asyncRunner,
            network,
            eth2PeerManager,
            gossipForkManager,
            eventChannels,
            recentChainData,
            attestationSubnetService,
            syncCommitteeSubnetService,
            gossipEncoding,
            GossipConfigurator.NOOP,
            processedAttestationSubscriptionProvider);
      }
    }

    private P2PConfig generateConfig() {
      final List<String> peerAddresses =
          peers.stream().map(P2PNetwork::getNodeAddress).collect(toList());

      final Random random = new Random();
      final int port = MIN_PORT + random.nextInt(MAX_PORT - MIN_PORT);

      return P2PConfig.builder()
          .specProvider(spec)
          .targetSubnetSubscriberCount(2)
          .network(b -> b.listenPort(port).wireLogs(w -> w.logWireMuxFrames(true)))
          .discovery(
              d ->
                  d.isDiscoveryEnabled(false)
                      .staticPeers(peerAddresses)
                      .minPeers(20)
                      .maxPeers(30)
                      .minRandomlySelectedPeers(0))
          .build();
    }

    private void setDefaults() {
      if (eventChannels == null) {
        eventChannels =
            EventChannels.createSyncChannels(
                (error, subscriber, invokedMethod, args) -> {
                  throw new RuntimeException(error);
                },
                new NoOpMetricsSystem());
      }
      if (asyncRunner == null) {
        asyncRunner = DelayedExecutorAsyncRunner.create();
      }
      if (eth2RpcPingInterval == null) {
        eth2RpcPingInterval =
            tech.pegasys.teku.networking.eth2.Eth2P2PNetworkBuilder.DEFAULT_ETH2_RPC_PING_INTERVAL;
      }
      if (eth2StatusUpdateInterval == null) {
        eth2StatusUpdateInterval =
            tech.pegasys.teku.networking.eth2.Eth2P2PNetworkBuilder
                .DEFAULT_ETH2_STATUS_UPDATE_INTERVAL;
      }
      if (eth2RpcOutstandingPingThreshold == null) {
        eth2RpcOutstandingPingThreshold =
            tech.pegasys.teku.networking.eth2.Eth2P2PNetworkBuilder
                .DEFAULT_ETH2_RPC_OUTSTANDING_PING_THRESHOLD;
      }
      if (recentChainData == null) {
        recentChainData = MemoryOnlyRecentChainData.create();
        BeaconChainUtil.create(spec, 0, recentChainData).initializeStorage();
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
      if (signedContributionAndProofProcessor == null) {
        syncCommitteeMessageProcessor = OperationProcessor.noop();
      }
      if (voluntaryExitProcessor == null) {
        voluntaryExitProcessor = OperationProcessor.noop();
      }
      if (signedBlsToExecutionChangeProcessor == null) {
        signedBlsToExecutionChangeProcessor = OperationProcessor.noop();
      }
    }

    public Eth2P2PNetworkBuilder spec(final Spec spec) {
      checkNotNull(spec);
      this.spec = spec;
      return this;
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

    public Eth2P2PNetworkBuilder peer(final Eth2P2PNetwork peer) {
      this.peers.add(peer);
      return this;
    }

    public Eth2P2PNetworkBuilder eventChannels(final EventChannels eventChannels) {
      checkNotNull(eventChannels);
      this.eventChannels = eventChannels;
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

    public Eth2P2PNetworkBuilder gossipedProposerSlashingProcessor(
        final OperationProcessor<ProposerSlashing> gossipedProposerSlashingProcessor) {
      checkNotNull(gossipedProposerSlashingProcessor);
      this.proposerSlashingProcessor = gossipedProposerSlashingProcessor;
      return this;
    }

    public Eth2P2PNetworkBuilder gossipedVoluntaryExitProcessor(
        final OperationProcessor<SignedVoluntaryExit> gossipedVoluntaryExitProcessor) {
      checkNotNull(gossipedVoluntaryExitProcessor);
      this.voluntaryExitProcessor = gossipedVoluntaryExitProcessor;
      return this;
    }

    public Eth2P2PNetworkBuilder gossipedSignedContributionAndProofProcessor(
        final OperationProcessor<SignedContributionAndProof>
            gossipedSignedContributionAndProofProcessor) {
      checkNotNull(gossipedSignedContributionAndProofProcessor);
      this.signedContributionAndProofProcessor = gossipedSignedContributionAndProofProcessor;
      return this;
    }

    public Eth2P2PNetworkBuilder gossipedSyncCommitteeMessageProcessor(
        final OperationProcessor<ValidateableSyncCommitteeMessage> gossipedSyncCommitteeProcessor) {
      checkNotNull(gossipedSyncCommitteeProcessor);
      this.syncCommitteeMessageProcessor = gossipedSyncCommitteeProcessor;
      return this;
    }

    public Eth2P2PNetworkBuilder gossipedSignedBlsToExecutionChangeProcessor(
        final OperationProcessor<SignedBlsToExecutionChange>
            gossipedSignedBlsToExecutionChangeProcessor) {
      checkNotNull(gossipedSignedBlsToExecutionChangeProcessor);
      this.signedBlsToExecutionChangeProcessor = gossipedSignedBlsToExecutionChangeProcessor;
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
        Function<RpcMethod<?, ?, ?>, Stream<RpcMethod<?, ?, ?>>> rpcMethodsModifier) {
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
