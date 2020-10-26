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

import static tech.pegasys.teku.core.ForkChoiceUtil.on_tick;
import static tech.pegasys.teku.infrastructure.logging.EventLogger.EVENT_LOG;
import static tech.pegasys.teku.infrastructure.logging.StatusLogger.STATUS_LOG;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;
import static tech.pegasys.teku.util.config.Constants.SECONDS_PER_SLOT;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Throwables;
import com.google.common.eventbus.EventBus;
import io.libp2p.core.crypto.KEY_TYPE;
import io.libp2p.core.crypto.KeyKt;
import io.libp2p.core.crypto.PrivKey;
import java.io.IOException;
import java.net.BindException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
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
import tech.pegasys.teku.datastructures.interop.InteropStartupUtil;
import tech.pegasys.teku.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.datastructures.state.AnchorPoint;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.datastructures.util.ChainDataLoader;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.AsyncRunnerFactory;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.events.EventChannels;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.Eth2Config;
import tech.pegasys.teku.networking.eth2.Eth2Network;
import tech.pegasys.teku.networking.eth2.Eth2NetworkBuilder;
import tech.pegasys.teku.networking.eth2.gossip.subnets.AttestationTopicSubscriber;
import tech.pegasys.teku.networking.eth2.gossip.subnets.StableSubnetSubscriber;
import tech.pegasys.teku.networking.eth2.mock.NoOpEth2Network;
import tech.pegasys.teku.networking.p2p.connection.TargetPeerRange;
import tech.pegasys.teku.networking.p2p.network.GossipConfig;
import tech.pegasys.teku.networking.p2p.network.NetworkConfig;
import tech.pegasys.teku.networking.p2p.network.WireLogsConfig;
import tech.pegasys.teku.pow.api.Eth1EventsChannel;
import tech.pegasys.teku.protoarray.ProtoArrayStorageChannel;
import tech.pegasys.teku.service.serviceutils.Service;
import tech.pegasys.teku.service.serviceutils.ServiceConfig;
import tech.pegasys.teku.statetransition.OperationPool;
import tech.pegasys.teku.statetransition.OperationsReOrgManager;
import tech.pegasys.teku.statetransition.attestation.AggregatingAttestationPool;
import tech.pegasys.teku.statetransition.attestation.AttestationManager;
import tech.pegasys.teku.statetransition.block.BlockImportChannel;
import tech.pegasys.teku.statetransition.block.BlockImporter;
import tech.pegasys.teku.statetransition.block.BlockManager;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoice;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoiceExecutor;
import tech.pegasys.teku.statetransition.forkchoice.SingleThreadedForkChoiceExecutor;
import tech.pegasys.teku.statetransition.genesis.GenesisHandler;
import tech.pegasys.teku.statetransition.util.FutureItems;
import tech.pegasys.teku.statetransition.util.PendingPool;
import tech.pegasys.teku.storage.api.ChainHeadChannel;
import tech.pegasys.teku.storage.api.FinalizedCheckpointChannel;
import tech.pegasys.teku.storage.api.StorageQueryChannel;
import tech.pegasys.teku.storage.api.StorageUpdateChannel;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.client.StorageBackedRecentChainData;
import tech.pegasys.teku.storage.events.WeakSubjectivityUpdate;
import tech.pegasys.teku.storage.store.FileKeyValueStore;
import tech.pegasys.teku.storage.store.KeyValueStore;
import tech.pegasys.teku.storage.store.StoreConfig;
import tech.pegasys.teku.storage.store.UpdatableStore.StoreTransaction;
import tech.pegasys.teku.sync.CoalescingChainHeadChannel;
import tech.pegasys.teku.sync.SyncService;
import tech.pegasys.teku.sync.SyncStateTracker;
import tech.pegasys.teku.sync.gossip.FetchRecentBlocksService;
import tech.pegasys.teku.sync.gossip.NoopRecentBlockFetcher;
import tech.pegasys.teku.sync.gossip.RecentBlockFetcher;
import tech.pegasys.teku.sync.multipeer.MultipeerSyncService;
import tech.pegasys.teku.sync.noop.NoopSyncService;
import tech.pegasys.teku.sync.singlepeer.SinglePeerSyncServiceFactory;
import tech.pegasys.teku.util.cli.VersionProvider;
import tech.pegasys.teku.util.config.GlobalConfiguration;
import tech.pegasys.teku.util.config.InvalidConfigurationException;
import tech.pegasys.teku.util.time.channels.SlotEventsChannel;
import tech.pegasys.teku.util.time.channels.TimeTickChannel;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.coordinator.ActiveValidatorTracker;
import tech.pegasys.teku.validator.coordinator.BlockFactory;
import tech.pegasys.teku.validator.coordinator.DepositProvider;
import tech.pegasys.teku.validator.coordinator.DutyMetrics;
import tech.pegasys.teku.validator.coordinator.Eth1DataCache;
import tech.pegasys.teku.validator.coordinator.Eth1VotingPeriod;
import tech.pegasys.teku.validator.coordinator.ValidatorApiHandler;
import tech.pegasys.teku.validator.coordinator.performance.DefaultPerformanceTracker;
import tech.pegasys.teku.validator.coordinator.performance.NoOpPerformanceTracker;
import tech.pegasys.teku.validator.coordinator.performance.PerformanceTracker;
import tech.pegasys.teku.validator.coordinator.performance.ValidatorPerformanceMetrics;
import tech.pegasys.teku.weaksubjectivity.WeakSubjectivityValidator;
import tech.pegasys.teku.weaksubjectivity.config.WeakSubjectivityConfig;

public class BeaconChainController extends Service implements TimeTickChannel {
  private static final Logger LOG = LogManager.getLogger();

  private static final String KEY_VALUE_STORE_SUBDIRECTORY = "kvstore";
  private static final String GENERATED_NODE_KEY_KEY = "generated-node-key";

  private final BeaconChainConfiguration beaconConfig;
  private final GlobalConfiguration config;
  private final EventChannels eventChannels;
  private final MetricsSystem metricsSystem;
  private final AsyncRunner asyncRunner;
  private final TimeProvider timeProvider;
  private final EventBus eventBus;
  private final SlotEventsChannel slotEventsChannelPublisher;
  private final AsyncRunner networkAsyncRunner;
  private final AsyncRunnerFactory asyncRunnerFactory;
  private final AsyncRunner eventAsyncRunner;
  private final Path beaconDataDirectory;

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
  private volatile OperationsReOrgManager operationsReOrgManager;
  private volatile WeakSubjectivityValidator weakSubjectivityValidator;
  private volatile Optional<AnchorPoint> weakSubjectivityAnchor = Optional.empty();
  private volatile PerformanceTracker performanceTracker;
  private volatile RecentBlockFetcher recentBlockFetcher;
  private volatile PendingPool<SignedBeaconBlock> pendingBlocks;
  private volatile CoalescingChainHeadChannel coalescingChainHeadChannel;

  private SyncStateTracker syncStateTracker;
  private UInt64 genesisTimeTracker = ZERO;
  private ForkChoiceExecutor forkChoiceExecutor;
  private BlockManager blockManager;

  public BeaconChainController(
      final ServiceConfig serviceConfig, final BeaconChainConfiguration beaconConfig) {
    this.beaconConfig = beaconConfig;
    this.config = serviceConfig.getConfig();
    this.beaconDataDirectory = serviceConfig.getDataDirLayout().getBeaconDataDirectory();
    this.asyncRunnerFactory = serviceConfig.getAsyncRunnerFactory();
    this.asyncRunner = serviceConfig.createAsyncRunner("beaconchain");
    this.eventAsyncRunner = serviceConfig.createAsyncRunner("events", 10);
    this.networkAsyncRunner = serviceConfig.createAsyncRunner("p2p", 10);
    this.timeProvider = serviceConfig.getTimeProvider();
    this.eventBus = serviceConfig.getEventBus();
    this.eventChannels = serviceConfig.getEventChannels();
    this.metricsSystem = serviceConfig.getMetricsSystem();
    this.slotEventsChannelPublisher = eventChannels.getPublisher(SlotEventsChannel.class);
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
    recentBlockFetcher.subscribeBlockFetched(
        (block) ->
            blockManager
                .importBlock(block)
                .finish(err -> LOG.error("Failed to process recently fetched block.", err)));
    blockManager.subscribeToReceivedBlocks(recentBlockFetcher::cancelRecentBlockRequest);
    SafeFuture.allOfFailFast(
            attestationManager.start(),
            p2pNetwork.start(),
            recentBlockFetcher.start(),
            blockManager.start(),
            syncService.start(),
            syncStateTracker.start())
        .finish(
            error -> {
              Throwable rootCause = Throwables.getRootCause(error);
              if (rootCause instanceof BindException) {
                final String errorWhilePerformingDescription =
                    "starting P2P services on port " + this.p2pNetwork.getListenPort() + ".";
                STATUS_LOG.fatalError(errorWhilePerformingDescription, rootCause);
                System.exit(1);
              } else {
                Thread.currentThread()
                    .getUncaughtExceptionHandler()
                    .uncaughtException(Thread.currentThread(), error);
              }
            });
  }

  @Override
  protected SafeFuture<?> doStop() {
    LOG.debug("Stopping {}", this.getClass().getSimpleName());
    return SafeFuture.allOf(
        SafeFuture.fromRunnable(() -> eventBus.unregister(this)),
        SafeFuture.fromRunnable(() -> beaconRestAPI.ifPresent(BeaconRestApi::stop)),
        SafeFuture.fromRunnable(() -> forkChoiceExecutor.stop()),
        syncStateTracker.stop(),
        syncService.stop(),
        blockManager.stop(),
        attestationManager.stop(),
        p2pNetwork.stop());
  }

  private SafeFuture<?> initialize() {
    final StoreConfig storeConfig =
        StoreConfig.builder()
            .hotStatePersistenceFrequencyInEpochs(config.getHotStatePersistenceFrequencyInEpochs())
            .disableBlockProcessingAtStartup(config.isBlockProcessingAtStartupDisabled())
            .build();
    coalescingChainHeadChannel =
        new CoalescingChainHeadChannel(eventChannels.getPublisher(ChainHeadChannel.class));

    return initWeakSubjectivity()
        .thenCompose(
            __ ->
                StorageBackedRecentChainData.create(
                    metricsSystem,
                    storeConfig,
                    asyncRunner,
                    eventChannels.getPublisher(StorageQueryChannel.class, asyncRunner),
                    eventChannels.getPublisher(StorageUpdateChannel.class, asyncRunner),
                    eventChannels.getPublisher(ProtoArrayStorageChannel.class, asyncRunner),
                    eventChannels.getPublisher(FinalizedCheckpointChannel.class, asyncRunner),
                    coalescingChainHeadChannel,
                    eventBus))
        .thenApply(
            client -> {
              // Setup chain storage
              this.recentChainData = client;
              if (recentChainData.isPreGenesis()) {
                // Set up initial state
                if (weakSubjectivityAnchor.isPresent()) {
                  client.initializeFromAnchorPoint(weakSubjectivityAnchor.get());
                } else if (config.getInitialState() != null) {
                  setupGenesisState();
                } else if (config.isInteropEnabled()) {
                  setupInteropState();
                } else if (config.isEth1Enabled()) {
                  STATUS_LOG.loadingGenesisFromEth1Chain();
                } else {
                  throw new InvalidConfigurationException(
                      "ETH1 is disabled but initial state is unknown. Enable ETH1 or specify an initial state.");
                }
              } else if (weakSubjectivityAnchor.isPresent()) {
                // We already have an existing database, throw for now
                throw new IllegalStateException(
                    "Cannot set weak subjectivity state for an existing database.");
              }
              return client;
            })
        .thenAccept(
            client -> {
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
    initPendingBlocks();
    initBlockManager();
    initP2PNetwork();
    initRecentBlockFetcher();
    initSyncManager();
    initSlotProcessor();
    initMetrics();
    initSyncStateTracker();
    initPerformanceTracker();
    initValidatorApiHandler();
    initRestAPI();
    initOperationsReOrgManager();
  }

  private void initPendingBlocks() {
    LOG.debug("BeaconChainController.initPendingBlocks()");
    pendingBlocks = PendingPool.createForBlocks();
    eventChannels.subscribe(FinalizedCheckpointChannel.class, pendingBlocks);
  }

  private void initRecentBlockFetcher() {
    LOG.debug("BeaconChainController.initRecentBlockFetcher()");
    if (!config.isP2pEnabled()) {
      recentBlockFetcher = new NoopRecentBlockFetcher();
    } else {
      recentBlockFetcher = FetchRecentBlocksService.create(asyncRunner, p2pNetwork, pendingBlocks);
    }
  }

  private void initPerformanceTracker() {
    LOG.debug("BeaconChainController.initPerformanceTracker()");
    if (beaconConfig.validatorConfig().isValidatorPerformanceTrackingEnabled()) {
      performanceTracker =
          new DefaultPerformanceTracker(
              combinedChainDataClient, STATUS_LOG, new ValidatorPerformanceMetrics(metricsSystem));
      eventChannels.subscribe(SlotEventsChannel.class, performanceTracker);
    } else {
      performanceTracker = new NoOpPerformanceTracker();
    }
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
            recentChainData,
            eventChannels.getPublisher(StorageQueryChannel.class, asyncRunner),
            stateTransition);
  }

  @VisibleForTesting
  SafeFuture<Void> initWeakSubjectivity() {
    return initWeakSubjectivity(loadWeakSubjectivityInitialAnchorPoint());
  }

  @VisibleForTesting
  SafeFuture<Void> initWeakSubjectivity(Optional<AnchorPoint> wsAnchor) {
    this.weakSubjectivityAnchor = wsAnchor;
    StorageQueryChannel storageQueryChannel =
        eventChannels.getPublisher(StorageQueryChannel.class, asyncRunner);
    StorageUpdateChannel storageUpdateChannel =
        eventChannels.getPublisher(StorageUpdateChannel.class, asyncRunner);

    return storageQueryChannel
        .getWeakSubjectivityState()
        .thenCompose(
            storedState -> {
              WeakSubjectivityConfig wsConfig = beaconConfig.weakSubjectivity();
              final Optional<Checkpoint> storedWsCheckpoint = storedState.getCheckpoint();
              Optional<Checkpoint> newWsCheckpoint = wsConfig.getWeakSubjectivityCheckpoint();

              // Reconcile supplied config with stored configuration
              Optional<WeakSubjectivityConfig> configToPersist = Optional.empty();
              if (newWsCheckpoint.isPresent()
                  && !Objects.equals(storedWsCheckpoint, newWsCheckpoint)) {
                // We have a new ws checkpoint, so we need to persist it
                configToPersist = Optional.of(wsConfig);
              } else if (storedState.getCheckpoint().isPresent()) {
                // We haven't supplied a new ws checkpoint, so use the stored value
                wsConfig =
                    wsConfig.updated(
                        b -> b.weakSubjectivityCheckpoint(storedState.getCheckpoint()));
              }

              // Reconcile ws checkpoint with ws state
              boolean shouldClearStoredState = false;
              final Optional<UInt64> wsAnchorEpoch =
                  weakSubjectivityAnchor.map(AnchorPoint::getEpoch);
              final Optional<UInt64> wsCheckpointEpoch =
                  wsConfig.getWeakSubjectivityCheckpoint().map(Checkpoint::getEpoch);
              if (wsAnchorEpoch.isPresent()
                  && wsCheckpointEpoch.isPresent()
                  && wsAnchorEpoch.get().isGreaterThanOrEqualTo(wsCheckpointEpoch.get())) {
                // The ws checkpoint is prior to our new anchor, so clear it out
                wsConfig = wsConfig.updated(b -> b.weakSubjectivityCheckpoint(Optional.empty()));
                configToPersist = Optional.empty();
                if (newWsCheckpoint.isPresent()) {
                  LOG.info(
                      "Ignoring configured weak subjectivity checkpoint which is prior to supplied weak subjectivity state");
                }
                if (storedWsCheckpoint.isPresent()) {
                  shouldClearStoredState = true;
                }
              }

              weakSubjectivityValidator = WeakSubjectivityValidator.moderate(wsConfig);

              // Persist changes as necessary
              if (shouldClearStoredState) {
                // Clear out stored checkpoint
                LOG.info("Clearing stored weak subjectivity checkpoint");
                WeakSubjectivityUpdate update =
                    WeakSubjectivityUpdate.clearWeakSubjectivityCheckpoint();
                return storageUpdateChannel.onWeakSubjectivityUpdate(update);
              } else if (configToPersist.isPresent()) {
                final Checkpoint updatedCheckpoint =
                    configToPersist.get().getWeakSubjectivityCheckpoint().orElseThrow();

                // Persist changes
                LOG.info("Update stored weak subjectivity checkpoint to: {}", updatedCheckpoint);
                WeakSubjectivityUpdate update =
                    WeakSubjectivityUpdate.setWeakSubjectivityCheckpoint(updatedCheckpoint);
                return storageUpdateChannel.onWeakSubjectivityUpdate(update);
              }

              return SafeFuture.COMPLETE;
            });
  }

  private void initStateTransition() {
    LOG.debug("BeaconChainController.initStateTransition()");
    stateTransition = new StateTransition();
  }

  private void initForkChoice() {
    LOG.debug("BeaconChainController.initForkChoice()");
    forkChoiceExecutor = SingleThreadedForkChoiceExecutor.create();
    forkChoice = new ForkChoice(forkChoiceExecutor, recentChainData, stateTransition);
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
    final ActiveValidatorTracker activeValidatorTracker =
        new ActiveValidatorTracker(
            new StableSubnetSubscriber(attestationTopicSubscriber, new Random()));
    final BlockImportChannel blockImportChannel =
        eventChannels.getPublisher(BlockImportChannel.class, asyncRunner);
    final ValidatorApiHandler validatorApiHandler =
        new ValidatorApiHandler(
            combinedChainDataClient,
            syncStateTracker,
            stateTransition,
            blockFactory,
            blockImportChannel,
            attestationPool,
            attestationManager,
            attestationTopicSubscriber,
            activeValidatorTracker,
            eventBus,
            DutyMetrics.create(metricsSystem, timeProvider, recentChainData),
            performanceTracker);
    eventChannels
        .subscribe(SlotEventsChannel.class, attestationTopicSubscriber)
        .subscribe(SlotEventsChannel.class, activeValidatorTracker)
        .subscribe(ValidatorApiChannel.class, validatorApiHandler);
  }

  private void initGenesisHandler() {
    if (config.isInteropEnabled() || config.getInitialState() != null) {
      // We're manually setting genesis, so don't spin up the genesis handler
      return;
    }
    LOG.debug("BeaconChainController.initPreGenesisDepositHandler()");
    eventChannels.subscribe(Eth1EventsChannel.class, new GenesisHandler(recentChainData));
  }

  private void initAttestationManager() {
    final PendingPool<ValidateableAttestation> pendingAttestations =
        PendingPool.createForAttestations();
    final FutureItems<ValidateableAttestation> futureAttestations =
        FutureItems.create(
            ValidateableAttestation::getEarliestSlotForForkChoiceProcessing, UInt64.valueOf(3));
    attestationManager =
        AttestationManager.create(
            eventBus, pendingAttestations, futureAttestations, forkChoice, attestationPool);
    eventChannels
        .subscribe(SlotEventsChannel.class, attestationManager)
        .subscribe(FinalizedCheckpointChannel.class, pendingAttestations);
  }

  public void initP2PNetwork() {
    LOG.debug("BeaconChainController.initP2PNetwork()");
    if (!config.isP2pEnabled()) {
      this.p2pNetwork = new NoOpEth2Network();
    } else {
      final KeyValueStore<String, Bytes> keyValueStore =
          new FileKeyValueStore(beaconDataDirectory.resolve(KEY_VALUE_STORE_SUBDIRECTORY));
      final PrivKey pk =
          KeyKt.unmarshalPrivateKey(getP2pPrivateKeyBytes(keyValueStore).toArrayUnsafe());
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
              new TargetPeerRange(
                  config.getP2pPeerLowerBound(),
                  config.getP2pPeerUpperBound(),
                  config.getMinimumRandomlySelectedPeerCount()),
              config.getTargetSubnetSubscriberCount(),
              GossipConfig.DEFAULT_CONFIG,
              new WireLogsConfig(
                  config.isLogWireCipher(),
                  config.isLogWirePlain(),
                  config.isLogWireMuxFrames(),
                  config.isLogWireGossip()));

      p2pConfig.validateListenPortAvailable();
      final Eth2Config eth2Config = new Eth2Config(weakSubjectivityValidator.getWSCheckpoint());

      this.p2pNetwork =
          Eth2NetworkBuilder.create()
              .config(p2pConfig)
              .eth2Config(eth2Config)
              .eventBus(eventBus)
              .recentChainData(recentChainData)
              .gossipedBlockConsumer(
                  block ->
                      blockManager
                          .importBlock(block)
                          .finish(err -> LOG.error("Failed to process gossiped block.", err)))
              .gossipedAttestationConsumer(
                  attestation ->
                      attestationManager
                          .onAttestation(attestation)
                          .finish(
                              result ->
                                  result.ifInvalid(
                                      reason ->
                                          LOG.debug("Rejected gossiped attestation: " + reason)),
                              err -> LOG.error("Failed to process gossiped attestation.", err)))
              .gossipedAttesterSlashingConsumer(attesterSlashingPool::add)
              .gossipedProposerSlashingConsumer(proposerSlashingPool::add)
              .gossipedVoluntaryExitConsumer(voluntaryExitPool::add)
              .processedAttestationSubscriptionProvider(
                  attestationManager::subscribeToProcessedAttestations)
              .verifiedBlockAttestationsProvider(
                  blockImporter::subscribeToVerifiedBlockAttestations)
              .historicalChainData(
                  eventChannels.getPublisher(StorageQueryChannel.class, asyncRunner))
              .metricsSystem(metricsSystem)
              .timeProvider(timeProvider)
              .asyncRunner(networkAsyncRunner)
              .keyValueStore(keyValueStore)
              .peerRateLimit(config.getPeerRateLimit())
              .peerRequestLimit(config.getPeerRequestLimit())
              .build();
    }
  }

  private void initSlotProcessor() {
    slotProcessor =
        new SlotProcessor(
            recentChainData, syncService, forkChoice, p2pNetwork, slotEventsChannelPublisher);
  }

  @VisibleForTesting
  Bytes getP2pPrivateKeyBytes(KeyValueStore<String, Bytes> keyValueStore) {
    final Bytes privateKey;
    final String p2pPrivateKeyFile = config.getP2pPrivateKeyFile();
    if (p2pPrivateKeyFile != null) {
      try {
        privateKey = Bytes.fromHexString(Files.readString(Paths.get(p2pPrivateKeyFile)));
      } catch (IOException e) {
        throw new RuntimeException("p2p private key file not found - " + p2pPrivateKeyFile);
      }
    } else {
      final Optional<Bytes> generatedKeyBytes = keyValueStore.get(GENERATED_NODE_KEY_KEY);
      if (generatedKeyBytes.isEmpty()) {
        final PrivKey privKey = KeyKt.generateKeyPair(KEY_TYPE.SECP256K1).component1();
        privateKey = Bytes.wrap(KeyKt.marshalPrivateKey(privKey));
        keyValueStore.put(GENERATED_NODE_KEY_KEY, privateKey);
        STATUS_LOG.usingGeneratedP2pPrivateKey(GENERATED_NODE_KEY_KEY, true);
      } else {
        privateKey = generatedKeyBytes.get();
        STATUS_LOG.usingGeneratedP2pPrivateKey(GENERATED_NODE_KEY_KEY, false);
      }
    }

    return privateKey;
  }

  @VisibleForTesting
  WeakSubjectivityValidator getWeakSubjectivityValidator() {
    return weakSubjectivityValidator;
  }

  public void initAttestationPool() {
    LOG.debug("BeaconChainController.initAttestationPool()");
    attestationPool =
        new AggregatingAttestationPool(
            new AttestationDataStateTransitionValidator(), metricsSystem);
    eventChannels.subscribe(SlotEventsChannel.class, attestationPool);
    blockImporter.subscribeToVerifiedBlockAttestations(attestationPool::removeAll);
  }

  public void initRestAPI() {
    LOG.debug("BeaconChainController.initRestAPI()");
    DataProvider dataProvider =
        new DataProvider(
            recentChainData,
            combinedChainDataClient,
            p2pNetwork,
            syncService,
            eventChannels.getPublisher(ValidatorApiChannel.class, asyncRunner),
            attestationPool);
    if (config.isRestApiEnabled()) {

      beaconRestAPI =
          Optional.of(new BeaconRestApi(dataProvider, config, eventChannels, eventAsyncRunner));
    } else {
      LOG.info("rest-api-enabled is false, not starting rest api.");
    }
  }

  public void initBlockImporter() {
    LOG.debug("BeaconChainController.initBlockImporter()");
    blockImporter =
        new BlockImporter(recentChainData, forkChoice, weakSubjectivityValidator, eventBus);
  }

  public void initBlockManager() {
    LOG.debug("BeaconChainController.initBlockManager()");
    final FutureItems<SignedBeaconBlock> futureBlocks =
        FutureItems.create(SignedBeaconBlock::getSlot);
    blockManager =
        BlockManager.create(eventBus, pendingBlocks, futureBlocks, recentChainData, blockImporter);
    eventChannels
        .subscribe(SlotEventsChannel.class, blockManager)
        .subscribe(BlockImportChannel.class, blockManager);
  }

  public void initSyncManager() {
    LOG.debug("BeaconChainController.initSyncManager()");
    if (!config.isP2pEnabled()) {
      syncService = new NoopSyncService();
    } else if (config.isMultiPeerSyncEnabled()) {
      syncService =
          MultipeerSyncService.create(
              asyncRunnerFactory,
              asyncRunner,
              timeProvider,
              recentChainData,
              p2pNetwork,
              blockImporter);
    } else {
      syncService =
          SinglePeerSyncServiceFactory.create(
              metricsSystem, asyncRunner, p2pNetwork, recentChainData, blockImporter);
    }
    syncService.subscribeToSyncChanges(coalescingChainHeadChannel);
  }

  private void initOperationsReOrgManager() {
    LOG.debug("BeaconChainController.initOperationsReOrgManager()");
    operationsReOrgManager =
        new OperationsReOrgManager(
            proposerSlashingPool,
            attesterSlashingPool,
            voluntaryExitPool,
            attestationPool,
            attestationManager,
            recentChainData);
    eventChannels.subscribe(ChainHeadChannel.class, operationsReOrgManager);
  }

  private void setupInteropState() {
    STATUS_LOG.generatingMockStartGenesis(
        config.getInteropGenesisTime(), config.getInteropNumberOfValidators());
    final BeaconState interopState =
        InteropStartupUtil.createMockedStartInitialBeaconState(
            config.getInteropGenesisTime(), config.getInteropNumberOfValidators());
    initializeGenesis(interopState);
  }

  private void setupGenesisState() {
    try {
      STATUS_LOG.loadingGenesisResource(config.getInitialState());
      final BeaconState genesisState = ChainDataLoader.loadState(config.getInitialState());
      initializeGenesis(genesisState);
    } catch (final IOException e) {
      throw new IllegalStateException("Failed to load genesis state", e);
    }
  }

  private Optional<AnchorPoint> loadWeakSubjectivityInitialAnchorPoint() {
    return beaconConfig
        .weakSubjectivity()
        .getWeakSubjectivityStateResource()
        .map(
            wsStateResource -> {
              try {
                final String wsBlockResource =
                    beaconConfig
                        .weakSubjectivity()
                        .getWeakSubjectivityBlockResource()
                        .orElseThrow(
                            () ->
                                new IllegalArgumentException(
                                    "Weak subjectivity block must be supplied with state"));
                STATUS_LOG.loadingWeakSubjectivityStateResources(wsStateResource, wsBlockResource);
                final BeaconState state = ChainDataLoader.loadState(wsStateResource);
                final SignedBeaconBlock block = ChainDataLoader.loadBlock(wsBlockResource);
                STATUS_LOG.loadedWeakSubjectivityStateResources(
                    state.hashTreeRoot(), block.getRoot(), state.getSlot());
                return AnchorPoint.fromInitialBlockAndState(block, state);
              } catch (IOException e) {
                throw new IllegalStateException(
                    "Failed to load weak subjectivity initial state data", e);
              }
            });
  }

  private void initializeGenesis(final BeaconState genesisState) {
    recentChainData.initializeFromGenesis(genesisState);

    EVENT_LOG.genesisEvent(
        genesisState.hashTreeRoot(),
        recentChainData.getBestBlockRoot().orElseThrow(),
        genesisState.getGenesis_time());
  }

  private void onStoreInitialized() {
    UInt64 genesisTime = recentChainData.getGenesisTime();
    UInt64 currentTime = timeProvider.getTimeInSeconds();
    final UInt64 currentSlot;
    if (currentTime.compareTo(genesisTime) >= 0) {
      UInt64 deltaTime = currentTime.minus(genesisTime);
      currentSlot = deltaTime.dividedBy(SECONDS_PER_SLOT);
      // Validate that we're running within the weak subjectivity period
      validateChain(currentSlot);
    } else {
      currentSlot = ZERO;
      UInt64 timeUntilGenesis = genesisTime.minus(currentTime);
      genesisTimeTracker = currentTime;
      STATUS_LOG.timeUntilGenesis(timeUntilGenesis.longValue(), p2pNetwork.getPeerCount());
    }
    slotProcessor.setCurrentSlot(currentSlot);
    performanceTracker.start(currentSlot);
  }

  private void validateChain(final UInt64 currentSlot) {
    weakSubjectivityValidator
        .validateChainIsConsistentWithWSCheckpoint(combinedChainDataClient)
        .thenCompose(
            __ ->
                SafeFuture.of(
                    () -> recentChainData.getStore().retrieveFinalizedCheckpointAndState()))
        .thenAccept(
            finalizedCheckpointState -> {
              final UInt64 slot = currentSlot.max(recentChainData.getCurrentSlot().orElse(ZERO));
              weakSubjectivityValidator.validateLatestFinalizedCheckpoint(
                  finalizedCheckpointState, slot);
            })
        .finish(
            err -> {
              weakSubjectivityValidator.handleValidationFailure(
                  "Encountered an error while trying to validate latest finalized checkpoint", err);
              throw new RuntimeException(err);
            });
  }

  @Override
  public void onTick() {
    if (recentChainData.isPreGenesis()) {
      return;
    }
    final UInt64 currentTime = timeProvider.getTimeInSeconds();
    final StoreTransaction transaction = recentChainData.startStoreTransaction();
    on_tick(transaction, currentTime);
    transaction.commit().join();

    final UInt64 genesisTime = recentChainData.getGenesisTime();
    if (genesisTime.isGreaterThan(currentTime)) {
      // notify every 10 minutes
      if (genesisTimeTracker.plus(600L).isLessThanOrEqualTo(currentTime)) {
        genesisTimeTracker = currentTime;
        STATUS_LOG.timeUntilGenesis(
            genesisTime.minus(currentTime).longValue(), p2pNetwork.getPeerCount());
      }
    }

    slotProcessor.onTick(currentTime);
  }
}
