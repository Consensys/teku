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
import java.net.BindException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.Random;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.beaconrestapi.BeaconRestApi;
import tech.pegasys.teku.core.BlockProposalUtil;
import tech.pegasys.teku.core.ForkChoiceAttestationValidator;
import tech.pegasys.teku.core.ForkChoiceBlockTasks;
import tech.pegasys.teku.core.ForkChoiceUtilWrapper;
import tech.pegasys.teku.core.StateTransition;
import tech.pegasys.teku.core.operationsignatureverifiers.ProposerSlashingSignatureVerifier;
import tech.pegasys.teku.core.operationsignatureverifiers.VoluntaryExitSignatureVerifier;
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
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.AsyncRunnerFactory;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.eventthread.AsyncRunnerEventThread;
import tech.pegasys.teku.infrastructure.events.EventChannels;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.infrastructure.version.VersionProvider;
import tech.pegasys.teku.networking.eth2.Eth2P2PNetwork;
import tech.pegasys.teku.networking.eth2.Eth2P2PNetworkBuilder;
import tech.pegasys.teku.networking.eth2.gossip.GossipPublisher;
import tech.pegasys.teku.networking.eth2.gossip.subnets.AllSubnetsSubscriber;
import tech.pegasys.teku.networking.eth2.gossip.subnets.AttestationTopicSubscriber;
import tech.pegasys.teku.networking.eth2.gossip.subnets.StableSubnetSubscriber;
import tech.pegasys.teku.networking.eth2.gossip.subnets.ValidatorBasedStableSubnetSubscriber;
import tech.pegasys.teku.networking.eth2.mock.NoOpEth2P2PNetwork;
import tech.pegasys.teku.pow.api.Eth1EventsChannel;
import tech.pegasys.teku.protoarray.ProtoArrayStorageChannel;
import tech.pegasys.teku.service.serviceutils.Service;
import tech.pegasys.teku.service.serviceutils.ServiceConfig;
import tech.pegasys.teku.spec.SpecProvider;
import tech.pegasys.teku.statetransition.OperationPool;
import tech.pegasys.teku.statetransition.OperationsReOrgManager;
import tech.pegasys.teku.statetransition.attestation.AggregatingAttestationPool;
import tech.pegasys.teku.statetransition.attestation.AttestationManager;
import tech.pegasys.teku.statetransition.block.BlockImportChannel;
import tech.pegasys.teku.statetransition.block.BlockImporter;
import tech.pegasys.teku.statetransition.block.BlockManager;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoice;
import tech.pegasys.teku.statetransition.genesis.GenesisHandler;
import tech.pegasys.teku.statetransition.util.FutureItems;
import tech.pegasys.teku.statetransition.util.PendingPool;
import tech.pegasys.teku.statetransition.validation.AggregateAttestationValidator;
import tech.pegasys.teku.statetransition.validation.AttestationValidator;
import tech.pegasys.teku.statetransition.validation.AttesterSlashingValidator;
import tech.pegasys.teku.statetransition.validation.BlockValidator;
import tech.pegasys.teku.statetransition.validation.ProposerSlashingValidator;
import tech.pegasys.teku.statetransition.validation.ValidationResultCode;
import tech.pegasys.teku.statetransition.validation.VoluntaryExitValidator;
import tech.pegasys.teku.storage.api.ChainHeadChannel;
import tech.pegasys.teku.storage.api.FinalizedCheckpointChannel;
import tech.pegasys.teku.storage.api.StorageQueryChannel;
import tech.pegasys.teku.storage.api.StorageUpdateChannel;
import tech.pegasys.teku.storage.api.VoteUpdateChannel;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.client.StorageBackedRecentChainData;
import tech.pegasys.teku.storage.store.FileKeyValueStore;
import tech.pegasys.teku.storage.store.KeyValueStore;
import tech.pegasys.teku.storage.store.StoreConfig;
import tech.pegasys.teku.storage.store.UpdatableStore.StoreTransaction;
import tech.pegasys.teku.sync.SyncService;
import tech.pegasys.teku.sync.SyncServiceFactory;
import tech.pegasys.teku.sync.events.CoalescingChainHeadChannel;
import tech.pegasys.teku.util.config.InvalidConfigurationException;
import tech.pegasys.teku.util.time.channels.SlotEventsChannel;
import tech.pegasys.teku.util.time.channels.TimeTickChannel;
import tech.pegasys.teku.validator.api.InteropConfig;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.api.ValidatorPerformanceTrackingMode;
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

public class BeaconChainController extends Service implements TimeTickChannel {
  private static final Logger LOG = LogManager.getLogger();

  private static final String KEY_VALUE_STORE_SUBDIRECTORY = "kvstore";

  private final BeaconChainConfiguration beaconConfig;
  private final SpecProvider specProvider;
  private final EventChannels eventChannels;
  private final MetricsSystem metricsSystem;
  private final AsyncRunner beaconAsyncRunner;
  private final TimeProvider timeProvider;
  private final EventBus eventBus;
  private final SlotEventsChannel slotEventsChannelPublisher;
  private final AsyncRunner networkAsyncRunner;
  private final AsyncRunnerFactory asyncRunnerFactory;
  private final AsyncRunner eventAsyncRunner;
  private final Path beaconDataDirectory;
  private final WeakSubjectivityInitializer wsInitializer = new WeakSubjectivityInitializer();
  private final AsyncRunnerEventThread forkChoiceExecutor;

  private volatile ForkChoice forkChoice;
  private volatile StateTransition stateTransition;
  private volatile BlockImporter blockImporter;
  private volatile RecentChainData recentChainData;
  private volatile Eth2P2PNetwork p2pNetwork;
  private volatile Optional<BeaconRestApi> beaconRestAPI = Optional.empty();
  private volatile AggregatingAttestationPool attestationPool;
  private volatile DepositProvider depositProvider;
  private volatile SyncService syncService;
  private volatile AttestationManager attestationManager;
  private volatile CombinedChainDataClient combinedChainDataClient;
  private volatile Eth1DataCache eth1DataCache;
  private volatile SlotProcessor slotProcessor;
  private volatile OperationPool<AttesterSlashing> attesterSlashingPool;
  private final GossipPublisher<AttesterSlashing> attesterSlashingGossipPublisher =
      new GossipPublisher<>();
  private volatile OperationPool<ProposerSlashing> proposerSlashingPool;
  private final GossipPublisher<ProposerSlashing> proposerSlashingGossipPublisher =
      new GossipPublisher<>();
  private volatile OperationPool<SignedVoluntaryExit> voluntaryExitPool;
  private final GossipPublisher<SignedVoluntaryExit> voluntaryExitGossipPublisher =
      new GossipPublisher<>();
  private volatile OperationsReOrgManager operationsReOrgManager;
  private volatile WeakSubjectivityValidator weakSubjectivityValidator;
  private volatile PerformanceTracker performanceTracker;
  private volatile PendingPool<SignedBeaconBlock> pendingBlocks;
  private volatile CoalescingChainHeadChannel coalescingChainHeadChannel;
  private volatile ActiveValidatorTracker activeValidatorTracker;
  private volatile AttestationTopicSubscriber attestationTopicSubscriber;

  private UInt64 genesisTimeTracker = ZERO;
  private BlockManager blockManager;

  public BeaconChainController(
      final ServiceConfig serviceConfig, final BeaconChainConfiguration beaconConfig) {
    this.beaconConfig = beaconConfig;
    this.specProvider = beaconConfig.getSpecProvider();
    this.beaconDataDirectory = serviceConfig.getDataDirLayout().getBeaconDataDirectory();
    this.asyncRunnerFactory = serviceConfig.getAsyncRunnerFactory();
    this.beaconAsyncRunner = serviceConfig.createAsyncRunner("beaconchain");
    this.eventAsyncRunner = serviceConfig.createAsyncRunner("events", 10);
    this.networkAsyncRunner = serviceConfig.createAsyncRunner("p2p", 10);
    this.timeProvider = serviceConfig.getTimeProvider();
    this.eventBus = serviceConfig.getEventBus();
    this.eventChannels = serviceConfig.getEventChannels();
    this.metricsSystem = serviceConfig.getMetricsSystem();
    this.slotEventsChannelPublisher = eventChannels.getPublisher(SlotEventsChannel.class);
    this.forkChoiceExecutor = new AsyncRunnerEventThread("forkchoice", asyncRunnerFactory);
  }

  @Override
  protected SafeFuture<?> doStart() {
    LOG.debug("Starting {}", this.getClass().getSimpleName());
    forkChoiceExecutor.start();
    this.eventBus.register(this);
    return initialize()
        .thenCompose(
            (__) -> SafeFuture.fromRunnable(() -> beaconRestAPI.ifPresent(BeaconRestApi::start)));
  }

  private void startServices() {
    syncService
        .getRecentBlockFetcher()
        .subscribeBlockFetched(
            (block) ->
                blockManager
                    .importBlock(block)
                    .finish(err -> LOG.error("Failed to process recently fetched block.", err)));
    blockManager.subscribeToReceivedBlocks(
        (block) -> syncService.getRecentBlockFetcher().cancelRecentBlockRequest(block.getRoot()));
    SafeFuture.allOfFailFast(
            attestationManager.start(),
            p2pNetwork.start(),
            blockManager.start(),
            syncService.start())
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
            syncService.stop(),
            blockManager.stop(),
            attestationManager.stop(),
            p2pNetwork.stop())
        .thenRun(forkChoiceExecutor::stop);
  }

  private SafeFuture<?> initialize() {
    final StoreConfig storeConfig = beaconConfig.storeConfig();
    coalescingChainHeadChannel =
        new CoalescingChainHeadChannel(eventChannels.getPublisher(ChainHeadChannel.class));

    StorageQueryChannel storageQueryChannel =
        eventChannels.getPublisher(StorageQueryChannel.class, beaconAsyncRunner);
    StorageUpdateChannel storageUpdateChannel =
        eventChannels.getPublisher(StorageUpdateChannel.class, beaconAsyncRunner);
    final VoteUpdateChannel voteUpdateChannel = eventChannels.getPublisher(VoteUpdateChannel.class);
    return initWeakSubjectivity(storageQueryChannel, storageUpdateChannel)
        .thenCompose(
            __ ->
                StorageBackedRecentChainData.create(
                    metricsSystem,
                    storeConfig,
                    beaconAsyncRunner,
                    storageQueryChannel,
                    storageUpdateChannel,
                    voteUpdateChannel,
                    eventChannels.getPublisher(ProtoArrayStorageChannel.class, beaconAsyncRunner),
                    eventChannels.getPublisher(FinalizedCheckpointChannel.class, beaconAsyncRunner),
                    coalescingChainHeadChannel,
                    eventBus,
                    specProvider))
        .thenCompose(
            client -> {
              // Setup chain storage
              this.recentChainData = client;
              if (recentChainData.isPreGenesis()) {
                setupInitialState(client);
              } else if (beaconConfig.eth2NetworkConfig().isUsingCustomInitialState()) {
                STATUS_LOG.warnInitialStateIgnored();
              }
              return SafeFuture.completedFuture(client);
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
    initSyncService();
    initSlotProcessor();
    initMetrics();
    initAttestationTopicSubscriber();
    initActiveValidatorTracker();
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

  private void initPerformanceTracker() {
    LOG.debug("BeaconChainController.initPerformanceTracker()");
    ValidatorPerformanceTrackingMode mode =
        beaconConfig.validatorConfig().getValidatorPerformanceTrackingMode();
    if (mode.isEnabled()) {
      performanceTracker =
          new DefaultPerformanceTracker(
              combinedChainDataClient,
              STATUS_LOG,
              new ValidatorPerformanceMetrics(metricsSystem),
              beaconConfig.validatorConfig().getValidatorPerformanceTrackingMode(),
              activeValidatorTracker,
              specProvider);
      eventChannels.subscribe(SlotEventsChannel.class, performanceTracker);
    } else {
      performanceTracker = new NoOpPerformanceTracker();
    }
  }

  private void initAttesterSlashingPool() {
    LOG.debug("BeaconChainController.initAttesterSlashingPool()");
    AttesterSlashingValidator validator =
        new AttesterSlashingValidator(
            recentChainData, new AttesterSlashingStateTransitionValidator());
    attesterSlashingPool = new OperationPool<>(AttesterSlashing.class, validator);
    blockImporter.subscribeToVerifiedBlockAttesterSlashings(attesterSlashingPool::removeAll);
  }

  private void initProposerSlashingPool() {
    LOG.debug("BeaconChainController.initProposerSlashingPool()");
    ProposerSlashingValidator validator =
        new ProposerSlashingValidator(
            recentChainData,
            new ProposerSlashingStateTransitionValidator(),
            new ProposerSlashingSignatureVerifier());
    proposerSlashingPool = new OperationPool<>(ProposerSlashing.class, validator);
    blockImporter.subscribeToVerifiedBlockProposerSlashings(proposerSlashingPool::removeAll);
  }

  private void initVoluntaryExitPool() {
    LOG.debug("BeaconChainController.initVoluntaryExitPool()");
    VoluntaryExitValidator validator =
        new VoluntaryExitValidator(
            recentChainData,
            new VoluntaryExitStateTransitionValidator(),
            new VoluntaryExitSignatureVerifier());
    voluntaryExitPool = new OperationPool<>(SignedVoluntaryExit.class, validator);
    blockImporter.subscribeToVerifiedBlockVoluntaryExits(voluntaryExitPool::removeAll);
  }

  private void initCombinedChainDataClient() {
    LOG.debug("BeaconChainController.initCombinedChainDataClient()");
    combinedChainDataClient =
        new CombinedChainDataClient(
            recentChainData,
            eventChannels.getPublisher(StorageQueryChannel.class, beaconAsyncRunner),
            specProvider);
  }

  @VisibleForTesting
  SafeFuture<Void> initWeakSubjectivity(
      final StorageQueryChannel queryChannel, final StorageUpdateChannel updateChannel) {
    return wsInitializer
        .finalizeAndStoreConfig(beaconConfig.weakSubjectivity(), queryChannel, updateChannel)
        .thenAccept(
            finalConfig -> {
              this.weakSubjectivityValidator = WeakSubjectivityValidator.moderate(finalConfig);
            });
  }

  private void initStateTransition() {
    LOG.debug("BeaconChainController.initStateTransition()");
    stateTransition = new StateTransition();
  }

  private void initForkChoice() {
    LOG.debug("BeaconChainController.initForkChoice()");
    forkChoice =
        new ForkChoice(
            new ForkChoiceAttestationValidator(),
            new ForkChoiceBlockTasks(),
            forkChoiceExecutor,
            recentChainData,
            stateTransition);
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
    depositProvider =
        new DepositProvider(metricsSystem, recentChainData, eth1DataCache, specProvider);
    eventChannels
        .subscribe(Eth1EventsChannel.class, depositProvider)
        .subscribe(FinalizedCheckpointChannel.class, depositProvider);
  }

  private void initEth1DataCache() {
    LOG.debug("BeaconChainController.initEth1DataCache");
    eth1DataCache = new Eth1DataCache(new Eth1VotingPeriod(specProvider));
  }

  private void initAttestationTopicSubscriber() {
    LOG.debug("BeaconChainController.initAttestationTopicSubscriber");
    this.attestationTopicSubscriber = new AttestationTopicSubscriber(p2pNetwork);
  }

  private void initActiveValidatorTracker() {
    LOG.debug("BeaconChainController.initActiveValidatorTracker");
    final StableSubnetSubscriber stableSubnetSubscriber =
        beaconConfig.p2pConfig().isSubscribeAllSubnetsEnabled()
            ? AllSubnetsSubscriber.create(attestationTopicSubscriber)
            : new ValidatorBasedStableSubnetSubscriber(
                attestationTopicSubscriber, new Random(), specProvider);
    this.activeValidatorTracker = new ActiveValidatorTracker(stableSubnetSubscriber, specProvider);
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
            VersionProvider.getDefaultGraffiti(),
            specProvider);
    final BlockImportChannel blockImportChannel =
        eventChannels.getPublisher(BlockImportChannel.class, beaconAsyncRunner);
    final ValidatorApiHandler validatorApiHandler =
        new ValidatorApiHandler(
            new ChainDataProvider(specProvider, recentChainData, combinedChainDataClient),
            combinedChainDataClient,
            syncService,
            blockFactory,
            blockImportChannel,
            attestationPool,
            attestationManager,
            attestationTopicSubscriber,
            activeValidatorTracker,
            eventBus,
            DutyMetrics.create(metricsSystem, timeProvider, recentChainData, specProvider),
            performanceTracker,
            specProvider);
    eventChannels
        .subscribe(SlotEventsChannel.class, attestationTopicSubscriber)
        .subscribe(SlotEventsChannel.class, activeValidatorTracker)
        .subscribe(ValidatorApiChannel.class, validatorApiHandler);
  }

  private void initGenesisHandler() {
    if (!recentChainData.isPreGenesis()) {
      // We already have a genesis block - no need for a genesis handler
      return;
    } else if (!beaconConfig.powchainConfig().isEnabled()) {
      // We're pre-genesis but no eth1 endpoint is set
      throw new IllegalStateException("ETH1 is disabled, but no initial state is set.");
    }
    STATUS_LOG.loadingGenesisFromEth1Chain();
    eventChannels.subscribe(
        Eth1EventsChannel.class, new GenesisHandler(recentChainData, timeProvider, specProvider));
  }

  private void initAttestationManager() {
    final PendingPool<ValidateableAttestation> pendingAttestations =
        PendingPool.createForAttestations();
    final FutureItems<ValidateableAttestation> futureAttestations =
        FutureItems.create(
            ValidateableAttestation::getEarliestSlotForForkChoiceProcessing, UInt64.valueOf(3));
    AttestationValidator attestationValidator =
        new AttestationValidator(recentChainData, new ForkChoiceUtilWrapper());
    AggregateAttestationValidator aggregateValidator =
        new AggregateAttestationValidator(recentChainData, attestationValidator, specProvider);
    blockImporter.subscribeToVerifiedBlockAttestations(
        (attestations) ->
            attestations.forEach(
                attestation ->
                    aggregateValidator.addSeenAggregate(
                        ValidateableAttestation.from(attestation))));
    attestationManager =
        AttestationManager.create(
            eventBus,
            pendingAttestations,
            futureAttestations,
            forkChoice,
            attestationPool,
            attestationValidator,
            aggregateValidator);
    eventChannels
        .subscribe(SlotEventsChannel.class, attestationManager)
        .subscribe(FinalizedCheckpointChannel.class, pendingAttestations);
  }

  public void initP2PNetwork() {
    LOG.debug("BeaconChainController.initP2PNetwork()");
    if (!beaconConfig.p2pConfig().getNetworkConfig().isEnabled()) {
      this.p2pNetwork = new NoOpEth2P2PNetwork();
      return;
    }

    beaconConfig.p2pConfig().getNetworkConfig().validateListenPortAvailable();

    // Set up gossip for voluntary exits
    voluntaryExitPool.subscribeOperationAdded(
        (item, result) -> {
          if (result.code().equals(ValidationResultCode.ACCEPT)) {
            voluntaryExitGossipPublisher.publish(item);
          }
        });
    // Set up gossip for attester slashings
    attesterSlashingPool.subscribeOperationAdded(
        (item, result) -> {
          if (result.code().equals(ValidationResultCode.ACCEPT)) {
            attesterSlashingGossipPublisher.publish(item);
          }
        });
    // Set up gossip for proposer slashings
    proposerSlashingPool.subscribeOperationAdded(
        (item, result) -> {
          if (result.code().equals(ValidationResultCode.ACCEPT)) {
            proposerSlashingGossipPublisher.publish(item);
          }
        });

    final KeyValueStore<String, Bytes> keyValueStore =
        new FileKeyValueStore(beaconDataDirectory.resolve(KEY_VALUE_STORE_SUBDIRECTORY));

    this.p2pNetwork =
        Eth2P2PNetworkBuilder.create()
            .config(beaconConfig.p2pConfig())
            .eventBus(eventBus)
            .recentChainData(recentChainData)
            .gossipedBlockProcessor(blockManager::validateAndImportBlock)
            .gossipedAttestationProcessor(attestationManager::addAttestation)
            .gossipedAggregateProcessor(attestationManager::addAggregate)
            .gossipedAttesterSlashingProcessor(attesterSlashingPool::add)
            .attesterSlashingGossipPublisher(attesterSlashingGossipPublisher)
            .gossipedProposerSlashingProcessor(proposerSlashingPool::add)
            .proposerSlashingGossipPublisher(proposerSlashingGossipPublisher)
            .gossipedVoluntaryExitProcessor(voluntaryExitPool::add)
            .voluntaryExitGossipPublisher(voluntaryExitGossipPublisher)
            .processedAttestationSubscriptionProvider(
                attestationManager::subscribeToAttestationsToSend)
            .historicalChainData(
                eventChannels.getPublisher(StorageQueryChannel.class, beaconAsyncRunner))
            .metricsSystem(metricsSystem)
            .timeProvider(timeProvider)
            .asyncRunner(networkAsyncRunner)
            .keyValueStore(keyValueStore)
            .requiredCheckpoint(weakSubjectivityValidator.getWSCheckpoint())
            .specProvider(specProvider)
            .build();
  }

  private void initSlotProcessor() {
    slotProcessor =
        new SlotProcessor(
            recentChainData,
            syncService.getForwardSync(),
            forkChoice,
            p2pNetwork,
            slotEventsChannelPublisher);
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
            specProvider,
            recentChainData,
            combinedChainDataClient,
            p2pNetwork,
            syncService,
            eventChannels.getPublisher(ValidatorApiChannel.class, beaconAsyncRunner),
            attestationPool,
            blockManager,
            attestationManager,
            attesterSlashingPool,
            proposerSlashingPool,
            voluntaryExitPool);
    if (beaconConfig.beaconRestApiConfig().isRestApiEnabled()) {

      beaconRestAPI =
          Optional.of(
              new BeaconRestApi(
                  dataProvider,
                  beaconConfig.beaconRestApiConfig(),
                  eventChannels,
                  eventAsyncRunner));
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
    BlockValidator blockValidator = new BlockValidator(recentChainData);
    blockManager =
        BlockManager.create(
            eventBus, pendingBlocks, futureBlocks, recentChainData, blockImporter, blockValidator);
    eventChannels
        .subscribe(SlotEventsChannel.class, blockManager)
        .subscribe(BlockImportChannel.class, blockManager);
  }

  public void initSyncService() {
    LOG.debug("BeaconChainController.initSyncService()");
    syncService =
        SyncServiceFactory.createSyncService(
            beaconConfig.syncConfig(),
            metricsSystem,
            asyncRunnerFactory,
            beaconAsyncRunner,
            timeProvider,
            recentChainData,
            combinedChainDataClient,
            eventChannels.getPublisher(StorageUpdateChannel.class, beaconAsyncRunner),
            p2pNetwork,
            blockImporter,
            pendingBlocks,
            beaconConfig.eth2NetworkConfig().getStartupTargetPeerCount(),
            Duration.ofSeconds(beaconConfig.eth2NetworkConfig().getStartupTimeoutSeconds()));

    syncService.getForwardSync().subscribeToSyncChanges(coalescingChainHeadChannel);
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

  private void setupInitialState(final RecentChainData client) {
    final Optional<AnchorPoint> initialAnchor =
        wsInitializer.loadInitialAnchorPoint(beaconConfig.eth2NetworkConfig().getInitialState());
    // Validate
    initialAnchor.ifPresent(
        anchor -> {
          final UInt64 currentSlot = getCurrentSlot(anchor.getState().getGenesis_time());
          wsInitializer.validateInitialAnchor(anchor, currentSlot);
        });

    if (initialAnchor.isPresent()) {
      final AnchorPoint anchor = initialAnchor.get();
      client.initializeFromAnchorPoint(anchor, timeProvider.getTimeInSeconds());
      if (anchor.isGenesis()) {
        EVENT_LOG.genesisEvent(
            anchor.getStateRoot(),
            recentChainData.getBestBlockRoot().orElseThrow(),
            anchor.getState().getGenesis_time());
      }
    } else if (beaconConfig.interopConfig().isInteropEnabled()) {
      setupInteropState();
    } else if (!beaconConfig.powchainConfig().isEnabled()) {
      throw new InvalidConfigurationException(
          "ETH1 is disabled but initial state is unknown. Enable ETH1 or specify an initial state.");
    }
  }

  private void setupInteropState() {
    final InteropConfig config = beaconConfig.interopConfig();
    STATUS_LOG.generatingMockStartGenesis(
        config.getInteropGenesisTime(), config.getInteropNumberOfValidators());
    final BeaconState genesisState =
        InteropStartupUtil.createMockedStartInitialBeaconState(
            config.getInteropGenesisTime(), config.getInteropNumberOfValidators());

    recentChainData.initializeFromGenesis(genesisState, timeProvider.getTimeInSeconds());

    EVENT_LOG.genesisEvent(
        genesisState.hashTreeRoot(),
        recentChainData.getBestBlockRoot().orElseThrow(),
        genesisState.getGenesis_time());
  }

  private void onStoreInitialized() {
    UInt64 genesisTime = recentChainData.getGenesisTime();
    UInt64 currentTime = timeProvider.getTimeInSeconds();
    final UInt64 currentSlot = getCurrentSlot(genesisTime, currentTime);
    if (currentTime.compareTo(genesisTime) >= 0) {
      // Validate that we're running within the weak subjectivity period
      validateChain(currentSlot);
    } else {
      UInt64 timeUntilGenesis = genesisTime.minus(currentTime);
      genesisTimeTracker = currentTime;
      STATUS_LOG.timeUntilGenesis(timeUntilGenesis.longValue(), p2pNetwork.getPeerCount());
    }
    slotProcessor.setCurrentSlot(currentSlot);
    performanceTracker.start(currentSlot);
  }

  private UInt64 getCurrentSlot(final UInt64 genesisTime) {
    return getCurrentSlot(genesisTime, timeProvider.getTimeInSeconds());
  }

  private UInt64 getCurrentSlot(final UInt64 genesisTime, final UInt64 currentTime) {
    final UInt64 currentSlot;
    if (currentTime.compareTo(genesisTime) >= 0) {
      UInt64 deltaTime = currentTime.minus(genesisTime);
      currentSlot = deltaTime.dividedBy(SECONDS_PER_SLOT);
    } else {
      currentSlot = ZERO;
    }
    return currentSlot;
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
