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

package tech.pegasys.teku.services.beaconchain;

import static com.google.common.primitives.UnsignedLong.ZERO;
import static tech.pegasys.teku.core.ForkChoiceUtil.on_tick;
import static tech.pegasys.teku.logging.StatusLogger.STATUS_LOG;
import static tech.pegasys.teku.util.config.Constants.SECONDS_PER_SLOT;

import com.google.common.eventbus.EventBus;
import com.google.common.primitives.UnsignedLong;
import io.libp2p.core.crypto.KEY_TYPE;
import io.libp2p.core.crypto.KeyKt;
import io.libp2p.core.crypto.PrivKey;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Date;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.beaconrestapi.BeaconRestApi;
import tech.pegasys.teku.core.BlockProposalUtil;
import tech.pegasys.teku.core.StateTransition;
import tech.pegasys.teku.core.operationvalidators.AttestationDataStateTransitionValidator;
import tech.pegasys.teku.core.operationvalidators.AttesterSlashingStateTransitionValidator;
import tech.pegasys.teku.core.operationvalidators.ProposerSlashingStateTransitionValidator;
import tech.pegasys.teku.core.operationvalidators.VoluntaryExitStateTransitionValidator;
import tech.pegasys.teku.datastructures.attestation.ValidateableAttestation;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.events.EventChannels;
import tech.pegasys.teku.networking.eth2.Eth2Config;
import tech.pegasys.teku.networking.eth2.Eth2Network;
import tech.pegasys.teku.networking.eth2.Eth2NetworkBuilder;
import tech.pegasys.teku.networking.eth2.gossip.AttestationTopicSubscriber;
import tech.pegasys.teku.networking.eth2.mock.NoOpEth2Network;
import tech.pegasys.teku.networking.p2p.connection.TargetPeerRange;
import tech.pegasys.teku.networking.p2p.network.GossipConfig;
import tech.pegasys.teku.networking.p2p.network.NetworkConfig;
import tech.pegasys.teku.networking.p2p.network.WireLogsConfig;
import tech.pegasys.teku.pow.api.Eth1EventsChannel;
import tech.pegasys.teku.service.serviceutils.Service;
import tech.pegasys.teku.service.serviceutils.ServiceConfig;
import tech.pegasys.teku.statetransition.OperationPool;
import tech.pegasys.teku.statetransition.attestation.AggregatingAttestationPool;
import tech.pegasys.teku.statetransition.attestation.AttestationManager;
import tech.pegasys.teku.statetransition.attestation.ForkChoiceAttestationProcessor;
import tech.pegasys.teku.statetransition.blockimport.BlockImporter;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoice;
import tech.pegasys.teku.statetransition.genesis.GenesisHandler;
import tech.pegasys.teku.statetransition.util.FutureItems;
import tech.pegasys.teku.statetransition.util.PendingPool;
import tech.pegasys.teku.statetransition.util.StartupUtil;
import tech.pegasys.teku.storage.api.FinalizedCheckpointChannel;
import tech.pegasys.teku.storage.api.ReorgEventChannel;
import tech.pegasys.teku.storage.api.StorageQueryChannel;
import tech.pegasys.teku.storage.api.StorageUpdateChannel;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.client.StorageBackedRecentChainData;
import tech.pegasys.teku.storage.store.UpdatableStore.StoreTransaction;
import tech.pegasys.teku.sync.BlockManager;
import tech.pegasys.teku.sync.DefaultSyncService;
import tech.pegasys.teku.sync.FetchRecentBlocksService;
import tech.pegasys.teku.sync.SyncManager;
import tech.pegasys.teku.sync.SyncService;
import tech.pegasys.teku.sync.SyncStateTracker;
import tech.pegasys.teku.sync.util.NoopSyncService;
import tech.pegasys.teku.util.async.AsyncRunner;
import tech.pegasys.teku.util.async.SafeFuture;
import tech.pegasys.teku.util.cli.VersionProvider;
import tech.pegasys.teku.util.config.InvalidConfigurationException;
import tech.pegasys.teku.util.config.TekuConfiguration;
import tech.pegasys.teku.util.time.TimeProvider;
import tech.pegasys.teku.util.time.channels.SlotEventsChannel;
import tech.pegasys.teku.util.time.channels.TimeTickChannel;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.coordinator.BlockFactory;
import tech.pegasys.teku.validator.coordinator.DepositProvider;
import tech.pegasys.teku.validator.coordinator.Eth1DataCache;
import tech.pegasys.teku.validator.coordinator.Eth1VotingPeriod;
import tech.pegasys.teku.validator.coordinator.ValidatorApiHandler;

public class BeaconChainController extends Service implements TimeTickChannel {
  private static final Logger LOG = LogManager.getLogger();

  private final EventChannels eventChannels;
  private final MetricsSystem metricsSystem;
  private final TekuConfiguration config;
  private final AsyncRunner asyncRunner;
  private final TimeProvider timeProvider;
  private final EventBus eventBus;
  private final boolean setupInitialState;
  private final SlotEventsChannel slotEventsChannelPublisher;
  private final AsyncRunner networkAsyncRunner;

  private volatile ForkChoice forkChoice;
  private volatile StateTransition stateTransition;
  private volatile BlockImporter blockImporter;
  private volatile RecentChainData recentChainData;
  private volatile Eth2Network p2pNetwork;
  private volatile Optional<BeaconRestApi> beaconRestAPI = Optional.empty();
  private volatile AggregatingAttestationPool attestationPool;
  private volatile DepositProvider depositProvider;
  private volatile SyncService syncService;
  private volatile AttestationManager attestationManager;
  private volatile CombinedChainDataClient combinedChainDataClient;
  private volatile Eth1DataCache eth1DataCache;
  private volatile SlotProcessor slotProcessor;
  private volatile OperationPool<AttesterSlashing> attesterSlashingPool;
  private volatile OperationPool<ProposerSlashing> proposerSlashingPool;
  private volatile OperationPool<SignedVoluntaryExit> voluntaryExitPool;

  private SyncStateTracker syncStateTracker;
  private UnsignedLong genesisTimeTracker = ZERO;

  public BeaconChainController(final ServiceConfig serviceConfig) {
    this.asyncRunner = serviceConfig.createAsyncRunner("beaconchain");
    this.networkAsyncRunner = serviceConfig.createAsyncRunner("p2p", 10);
    this.timeProvider = serviceConfig.getTimeProvider();
    this.eventBus = serviceConfig.getEventBus();
    this.eventChannels = serviceConfig.getEventChannels();
    this.config = serviceConfig.getConfig();
    this.metricsSystem = serviceConfig.getMetricsSystem();
    this.slotEventsChannelPublisher = eventChannels.getPublisher(SlotEventsChannel.class);
    this.setupInitialState = config.isInteropEnabled() || config.getInitialState() != null;
  }

  @Override
  protected SafeFuture<?> doStart() {
    this.eventBus.register(this);
    LOG.debug("Starting {}", this.getClass().getSimpleName());
    return initialize()
        .thenCompose(
            (__) -> SafeFuture.fromRunnable(() -> beaconRestAPI.ifPresent(BeaconRestApi::start)));
  }

  private void startServices() {
    SafeFuture.allOfFailFast(
            attestationManager.start(),
            p2pNetwork.start(),
            syncService.start(),
            syncStateTracker.start())
        .reportExceptions();
  }

  @Override
  protected SafeFuture<?> doStop() {
    LOG.debug("Stopping {}", this.getClass().getSimpleName());
    return SafeFuture.allOf(
        SafeFuture.fromRunnable(() -> eventBus.unregister(this)),
        SafeFuture.fromRunnable(() -> beaconRestAPI.ifPresent(BeaconRestApi::stop)),
        syncStateTracker.stop(),
        syncService.stop(),
        attestationManager.stop(),
        SafeFuture.fromRunnable(p2pNetwork::stop));
  }

  private SafeFuture<?> initialize() {
    return StorageBackedRecentChainData.create(
            metricsSystem,
            asyncRunner,
            eventChannels.getPublisher(StorageQueryChannel.class),
            eventChannels.getPublisher(StorageUpdateChannel.class),
            eventChannels.getPublisher(FinalizedCheckpointChannel.class),
            eventChannels.getPublisher(ReorgEventChannel.class),
            eventBus)
        .thenAccept(
            client -> {
              // Setup chain storage
              this.recentChainData = client;
              if (recentChainData.isPreGenesis()) {
                if (setupInitialState) {
                  setupInitialState();
                } else if (config.isEth1Enabled()) {
                  STATUS_LOG.loadingGenesisFromEth1Chain();
                } else {
                  throw new InvalidConfigurationException(
                      "ETH1 is disabled but initial state is unknown. Enable ETH1 or specify an initial state.");
                }
              }
              // Init other services
              this.initAll();
              eventChannels.subscribe(TimeTickChannel.class, this);

              recentChainData.subscribeStoreInitialized(this::onStoreInitialized);
              recentChainData.subscribeBestBlockInitialized(this::startServices);
            });
  }

  public void initAll() {
    initStateTransition();
    initForkChoice();
    initBlockImporter();
    initCombinedChainDataClient();
    initAttestationPool();
    initAttesterSlashingPool();
    initProposerSlashingPool();
    initVoluntaryExitPool();
    initEth1DataCache();
    initDepositProvider();
    initGenesisHandler();
    initAttestationManager();
    initP2PNetwork();
    initSyncManager();
    initSlotProcessor();
    initMetrics();
    initSyncStateTracker();
    initValidatorApiHandler();
    initRestAPI();
  }

  private void initAttesterSlashingPool() {
    LOG.debug("BeaconChainController.initAttesterSlashingPool()");
    attesterSlashingPool =
        new OperationPool<>(AttesterSlashing.class, new AttesterSlashingStateTransitionValidator());
    blockImporter.subscribeToVerifiedBlockAttesterSlashings(attesterSlashingPool::removeAll);
  }

  private void initProposerSlashingPool() {
    LOG.debug("BeaconChainController.initProposerSlashingPool()");
    proposerSlashingPool =
        new OperationPool<>(ProposerSlashing.class, new ProposerSlashingStateTransitionValidator());
    blockImporter.subscribeToVerifiedBlockProposerSlashings(proposerSlashingPool::removeAll);
  }

  private void initVoluntaryExitPool() {
    LOG.debug("BeaconChainController.initVoluntaryExitPool()");
    voluntaryExitPool =
        new OperationPool<>(SignedVoluntaryExit.class, new VoluntaryExitStateTransitionValidator());
    blockImporter.subscribeToVerifiedBlockVoluntaryExits(voluntaryExitPool::removeAll);
  }

  private void initCombinedChainDataClient() {
    LOG.debug("BeaconChainController.initCombinedChainDataClient()");
    combinedChainDataClient =
        new CombinedChainDataClient(
            recentChainData, eventChannels.getPublisher(StorageQueryChannel.class));
  }

  private void initStateTransition() {
    LOG.debug("BeaconChainController.initStateTransition()");
    stateTransition = new StateTransition();
  }

  private void initForkChoice() {
    LOG.debug("BeaconChainController.initForkChoice()");
    forkChoice = new ForkChoice(recentChainData, stateTransition);
  }

  public void initMetrics() {
    LOG.debug("BeaconChainController.initMetrics()");
    eventChannels.subscribe(
        SlotEventsChannel.class,
        new BeaconChainMetrics(
            recentChainData, slotProcessor.getNodeSlot(), metricsSystem, p2pNetwork));
  }

  public void initDepositProvider() {
    LOG.debug("BeaconChainController.initDepositProvider()");
    depositProvider = new DepositProvider(recentChainData, eth1DataCache);
    eventChannels
        .subscribe(Eth1EventsChannel.class, depositProvider)
        .subscribe(FinalizedCheckpointChannel.class, depositProvider);
  }

  private void initEth1DataCache() {
    LOG.debug("BeaconChainController.initEth1DataCache");
    eth1DataCache = new Eth1DataCache(new Eth1VotingPeriod());
  }

  private void initSyncStateTracker() {
    LOG.debug("BeaconChainController.initSyncStateTracker");
    syncStateTracker =
        new SyncStateTracker(
            asyncRunner,
            syncService,
            p2pNetwork,
            config.getStartupTargetPeerCount(),
            Duration.ofSeconds(config.getStartupTimeoutSeconds()));
  }

  public void initValidatorApiHandler() {
    LOG.debug("BeaconChainController.initValidatorApiHandler()");
    final BlockFactory blockFactory =
        new BlockFactory(
            new BlockProposalUtil(stateTransition),
            stateTransition,
            attestationPool,
            attesterSlashingPool,
            proposerSlashingPool,
            voluntaryExitPool,
            depositProvider,
            eth1DataCache,
            VersionProvider.getDefaultGraffiti());
    final AttestationTopicSubscriber attestationTopicSubscriber =
        new AttestationTopicSubscriber(p2pNetwork);
    final ValidatorApiHandler validatorApiHandler =
        new ValidatorApiHandler(
            combinedChainDataClient,
            syncStateTracker,
            stateTransition,
            blockFactory,
            attestationPool,
            attestationManager,
            attestationTopicSubscriber,
            eventBus);
    eventChannels
        .subscribe(SlotEventsChannel.class, attestationTopicSubscriber)
        .subscribe(ValidatorApiChannel.class, validatorApiHandler);
  }

  private void initGenesisHandler() {
    if (setupInitialState) {
      return;
    }
    LOG.debug("BeaconChainController.initPreGenesisDepositHandler()");
    eventChannels.subscribe(Eth1EventsChannel.class, new GenesisHandler(recentChainData));
  }

  private void initAttestationManager() {
    final PendingPool<ValidateableAttestation> pendingAttestations =
        PendingPool.createForAttestations();
    final FutureItems<ValidateableAttestation> futureAttestations =
        new FutureItems<>(ValidateableAttestation::getEarliestSlotForForkChoiceProcessing);
    final ForkChoiceAttestationProcessor forkChoiceAttestationProcessor =
        new ForkChoiceAttestationProcessor(recentChainData, forkChoice);
    attestationManager =
        AttestationManager.create(
            eventBus,
            pendingAttestations,
            futureAttestations,
            forkChoiceAttestationProcessor,
            attestationPool);
    eventChannels
        .subscribe(SlotEventsChannel.class, attestationManager)
        .subscribe(FinalizedCheckpointChannel.class, pendingAttestations);
  }

  public void initP2PNetwork() {
    LOG.debug("BeaconChainController.initP2PNetwork()");
    if (!config.isP2pEnabled()) {
      this.p2pNetwork = new NoOpEth2Network();
    } else {
      final Optional<Bytes> bytes = getP2pPrivateKeyBytes();
      final PrivKey pk =
          bytes.isEmpty()
              ? KeyKt.generateKeyPair(KEY_TYPE.SECP256K1).component1()
              : KeyKt.unmarshalPrivateKey(bytes.get().toArrayUnsafe());
      final NetworkConfig p2pConfig =
          new NetworkConfig(
              pk,
              config.getP2pInterface(),
              config.getP2pAdvertisedIp(),
              config.getP2pPort(),
              config.getP2pAdvertisedPort(),
              config.getP2pStaticPeers(),
              config.isP2pDiscoveryEnabled(),
              config.getP2pDiscoveryBootnodes(),
              new TargetPeerRange(config.getP2pPeerLowerBound(), config.getP2pPeerUpperBound()),
              GossipConfig.DEFAULT_CONFIG,
              new WireLogsConfig(
                  config.isLogWireCipher(),
                  config.isLogWirePlain(),
                  config.isLogWireMuxFrames(),
                  config.isLogWireGossip()));
      final Eth2Config eth2Config = new Eth2Config(config.isP2pSnappyEnabled());

      this.p2pNetwork =
          Eth2NetworkBuilder.create()
              .config(p2pConfig)
              .eth2Config(eth2Config)
              .eventBus(eventBus)
              .recentChainData(recentChainData)
              .gossipedAttestationConsumer(
                  attestation ->
                      attestationManager
                          .onAttestation(attestation)
                          .ifInvalid(
                              reason -> LOG.debug("Rejected gossiped attestation: " + reason)))
              .gossipedAttesterSlashingConsumer(attesterSlashingPool::add)
              .gossipedProposerSlashingConsumer(proposerSlashingPool::add)
              .gossipedVoluntaryExitConsumer(voluntaryExitPool::add)
              .processedAttestationSubscriptionProvider(
                  attestationManager::subscribeToProcessedAttestations)
              .verifiedBlockAttestationsProvider(
                  blockImporter::subscribeToVerifiedBlockAttestations)
              .historicalChainData(eventChannels.getPublisher(StorageQueryChannel.class))
              .metricsSystem(metricsSystem)
              .timeProvider(timeProvider)
              .asyncRunner(networkAsyncRunner)
              .build();
    }
  }

  private void initSlotProcessor() {
    slotProcessor =
        new SlotProcessor(
            recentChainData,
            syncService,
            forkChoice,
            p2pNetwork,
            slotEventsChannelPublisher,
            eventBus);
  }

  private Optional<Bytes> getP2pPrivateKeyBytes() {
    final Optional<Bytes> bytes;
    final String p2pPrivateKeyFile = config.getP2pPrivateKeyFile();
    if (p2pPrivateKeyFile != null) {
      try {
        bytes = Optional.of(Bytes.fromHexString(Files.readString(Paths.get(p2pPrivateKeyFile))));
      } catch (IOException e) {
        throw new RuntimeException("p2p private key file not found - " + p2pPrivateKeyFile);
      }
    } else {
      LOG.info("ENR key file not found. A new ENR will be generated.");
      bytes = Optional.empty();
    }
    return bytes;
  }

  public void initAttestationPool() {
    LOG.debug("BeaconChainController.initAttestationPool()");
    attestationPool = new AggregatingAttestationPool(new AttestationDataStateTransitionValidator());
    eventChannels.subscribe(SlotEventsChannel.class, attestationPool);
  }

  public void initRestAPI() {
    LOG.debug("BeaconChainController.initRestAPI()");
    DataProvider dataProvider =
        new DataProvider(
            recentChainData,
            combinedChainDataClient,
            p2pNetwork,
            syncService,
            eventChannels.getPublisher(ValidatorApiChannel.class),
            blockImporter);
    if (config.isRestApiEnabled()) {
      beaconRestAPI = Optional.of(new BeaconRestApi(dataProvider, config));
    } else {
      LOG.info("rest-api-enabled is false, not starting rest api.");
    }
  }

  public void initBlockImporter() {
    LOG.debug("BeaconChainController.initBlockImporter()");
    blockImporter = new BlockImporter(recentChainData, forkChoice, eventBus);
  }

  public void initSyncManager() {
    LOG.debug("BeaconChainController.initSyncManager()");
    if (!config.isP2pEnabled()) {
      syncService = new NoopSyncService();
    } else {
      final PendingPool<SignedBeaconBlock> pendingBlocks = PendingPool.createForBlocks();
      final FutureItems<SignedBeaconBlock> futureBlocks =
          new FutureItems<>(SignedBeaconBlock::getSlot);
      final FetchRecentBlocksService recentBlockFetcher =
          FetchRecentBlocksService.create(asyncRunner, p2pNetwork, pendingBlocks);
      BlockManager blockManager =
          BlockManager.create(
              eventBus,
              pendingBlocks,
              futureBlocks,
              recentBlockFetcher,
              recentChainData,
              blockImporter);
      SyncManager syncManager =
          SyncManager.create(asyncRunner, p2pNetwork, recentChainData, blockImporter);
      syncService = new DefaultSyncService(blockManager, syncManager, recentChainData);
      eventChannels
          .subscribe(SlotEventsChannel.class, blockManager)
          .subscribe(FinalizedCheckpointChannel.class, pendingBlocks);
    }
  }

  private void setupInitialState() {
    StartupUtil.setupInitialState(
        recentChainData,
        config.getInteropGenesisTime(),
        config.getInitialState(),
        config.getInteropNumberOfValidators());
  }

  private void onStoreInitialized() {
    UnsignedLong genesisTime = recentChainData.getGenesisTime();
    UnsignedLong currentTime = UnsignedLong.valueOf(System.currentTimeMillis() / 1000);
    UnsignedLong currentSlot = ZERO;
    if (currentTime.compareTo(genesisTime) >= 0) {
      UnsignedLong deltaTime = currentTime.minus(genesisTime);
      currentSlot = deltaTime.dividedBy(UnsignedLong.valueOf(SECONDS_PER_SLOT));
    } else {
      UnsignedLong timeUntilGenesis = genesisTime.minus(currentTime);
      genesisTimeTracker = currentTime;
      STATUS_LOG.timeUntilGenesis(timeUntilGenesis.longValue());
    }
    slotProcessor.setCurrentSlot(currentSlot);
  }

  @Override
  public void onTick(Date date) {
    if (recentChainData.isPreGenesis()) {
      return;
    }
    final UnsignedLong currentTime = UnsignedLong.valueOf(date.getTime() / 1000);

    final StoreTransaction transaction = recentChainData.startStoreTransaction();
    on_tick(transaction, currentTime);
    transaction.commit().join();

    final UnsignedLong genesisTime = recentChainData.getGenesisTime();
    if (genesisTime.compareTo(currentTime) > 0) {
      // notify every 10 minutes
      if (genesisTimeTracker.plus(UnsignedLong.valueOf(600L)).compareTo(currentTime) <= 0) {
        genesisTimeTracker = currentTime;
        STATUS_LOG.timeUntilGenesis(genesisTime.minus(currentTime).longValue());
      }
    }

    slotProcessor.onTick(currentTime);
  }
}
