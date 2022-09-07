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

package tech.pegasys.teku.services.beaconchain;

import static tech.pegasys.teku.infrastructure.logging.EventLogger.EVENT_LOG;
import static tech.pegasys.teku.infrastructure.logging.StatusLogger.STATUS_LOG;
import static tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory.BEACON;
import static tech.pegasys.teku.infrastructure.time.TimeUtilities.millisToSeconds;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;
import static tech.pegasys.teku.spec.config.SpecConfig.GENESIS_SLOT;
import static tech.pegasys.teku.statetransition.attestation.AggregatingAttestationPool.DEFAULT_MAXIMUM_ATTESTATION_COUNT;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Throwables;
import java.io.IOException;
import java.net.BindException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.Optional;
import java.util.Random;
import java.util.function.Function;
import java.util.function.IntSupplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.beacon.sync.DefaultSyncServiceFactory;
import tech.pegasys.teku.beacon.sync.SyncService;
import tech.pegasys.teku.beacon.sync.SyncServiceFactory;
import tech.pegasys.teku.beacon.sync.events.CoalescingChainHeadChannel;
import tech.pegasys.teku.beaconrestapi.BeaconRestApi;
import tech.pegasys.teku.beaconrestapi.JsonTypeDefinitionBeaconRestApi;
import tech.pegasys.teku.beaconrestapi.ReflectionBasedBeaconRestApi;
import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.ethereum.pow.api.Eth1EventsChannel;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.AsyncRunnerFactory;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.eventthread.AsyncRunnerEventThread;
import tech.pegasys.teku.infrastructure.bytes.Eth1Address;
import tech.pegasys.teku.infrastructure.events.EventChannels;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.infrastructure.io.PortAvailability;
import tech.pegasys.teku.infrastructure.metrics.SettableLabelledGauge;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.infrastructure.version.VersionProvider;
import tech.pegasys.teku.networking.eth2.Eth2P2PNetwork;
import tech.pegasys.teku.networking.eth2.Eth2P2PNetworkBuilder;
import tech.pegasys.teku.networking.eth2.P2PConfig;
import tech.pegasys.teku.networking.eth2.gossip.BlockGossipChannel;
import tech.pegasys.teku.networking.eth2.gossip.subnets.AllSubnetsSubscriber;
import tech.pegasys.teku.networking.eth2.gossip.subnets.AllSyncCommitteeSubscriptions;
import tech.pegasys.teku.networking.eth2.gossip.subnets.AttestationTopicSubscriber;
import tech.pegasys.teku.networking.eth2.gossip.subnets.StableSubnetSubscriber;
import tech.pegasys.teku.networking.eth2.gossip.subnets.SyncCommitteeSubscriptionManager;
import tech.pegasys.teku.networking.eth2.gossip.subnets.ValidatorBasedStableSubnetSubscriber;
import tech.pegasys.teku.networking.eth2.mock.NoOpEth2P2PNetwork;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryConfig;
import tech.pegasys.teku.service.serviceutils.Service;
import tech.pegasys.teku.service.serviceutils.ServiceConfig;
import tech.pegasys.teku.services.timer.TimerService;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.attestation.ValidateableAttestation;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodySchema;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.interop.InteropStartupUtil;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.state.AnchorPoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannel;
import tech.pegasys.teku.statetransition.EpochCachePrimer;
import tech.pegasys.teku.statetransition.LocalOperationAcceptedFilter;
import tech.pegasys.teku.statetransition.OperationPool;
import tech.pegasys.teku.statetransition.OperationsReOrgManager;
import tech.pegasys.teku.statetransition.attestation.AggregatingAttestationPool;
import tech.pegasys.teku.statetransition.attestation.AttestationManager;
import tech.pegasys.teku.statetransition.block.BlockImportChannel;
import tech.pegasys.teku.statetransition.block.BlockImportMetrics;
import tech.pegasys.teku.statetransition.block.BlockImportNotifications;
import tech.pegasys.teku.statetransition.block.BlockImporter;
import tech.pegasys.teku.statetransition.block.BlockManager;
import tech.pegasys.teku.statetransition.block.FailedExecutionPool;
import tech.pegasys.teku.statetransition.forkchoice.ActivePandaPrinter;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoice;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoiceNotifier;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoiceNotifierImpl;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoiceTrigger;
import tech.pegasys.teku.statetransition.forkchoice.MergeTransitionBlockValidator;
import tech.pegasys.teku.statetransition.forkchoice.MergeTransitionConfigCheck;
import tech.pegasys.teku.statetransition.forkchoice.PreProposalForkChoiceTrigger;
import tech.pegasys.teku.statetransition.forkchoice.ProposersDataManager;
import tech.pegasys.teku.statetransition.forkchoice.TerminalPowBlockMonitor;
import tech.pegasys.teku.statetransition.forkchoice.TickProcessingPerformance;
import tech.pegasys.teku.statetransition.forkchoice.TickProcessor;
import tech.pegasys.teku.statetransition.genesis.GenesisHandler;
import tech.pegasys.teku.statetransition.synccommittee.SignedContributionAndProofValidator;
import tech.pegasys.teku.statetransition.synccommittee.SyncCommitteeContributionPool;
import tech.pegasys.teku.statetransition.synccommittee.SyncCommitteeMessagePool;
import tech.pegasys.teku.statetransition.synccommittee.SyncCommitteeMessageValidator;
import tech.pegasys.teku.statetransition.synccommittee.SyncCommitteeStateUtils;
import tech.pegasys.teku.statetransition.util.FutureItems;
import tech.pegasys.teku.statetransition.util.PendingPool;
import tech.pegasys.teku.statetransition.util.PendingPoolFactory;
import tech.pegasys.teku.statetransition.validation.AggregateAttestationValidator;
import tech.pegasys.teku.statetransition.validation.AttestationValidator;
import tech.pegasys.teku.statetransition.validation.AttesterSlashingValidator;
import tech.pegasys.teku.statetransition.validation.BlockValidator;
import tech.pegasys.teku.statetransition.validation.ProposerSlashingValidator;
import tech.pegasys.teku.statetransition.validation.VoluntaryExitValidator;
import tech.pegasys.teku.statetransition.validation.signatures.AggregatingSignatureVerificationService;
import tech.pegasys.teku.statetransition.validation.signatures.SignatureVerificationService;
import tech.pegasys.teku.statetransition.validatorcache.ActiveValidatorCache;
import tech.pegasys.teku.statetransition.validatorcache.ActiveValidatorChannel;
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
import tech.pegasys.teku.validator.api.InteropConfig;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.api.ValidatorPerformanceTrackingMode;
import tech.pegasys.teku.validator.coordinator.ActiveValidatorTracker;
import tech.pegasys.teku.validator.coordinator.BlockFactory;
import tech.pegasys.teku.validator.coordinator.BlockOperationSelectorFactory;
import tech.pegasys.teku.validator.coordinator.DepositProvider;
import tech.pegasys.teku.validator.coordinator.DutyMetrics;
import tech.pegasys.teku.validator.coordinator.Eth1DataCache;
import tech.pegasys.teku.validator.coordinator.Eth1DataProvider;
import tech.pegasys.teku.validator.coordinator.Eth1VotingPeriod;
import tech.pegasys.teku.validator.coordinator.ValidatorApiHandler;
import tech.pegasys.teku.validator.coordinator.performance.DefaultPerformanceTracker;
import tech.pegasys.teku.validator.coordinator.performance.NoOpPerformanceTracker;
import tech.pegasys.teku.validator.coordinator.performance.PerformanceTracker;
import tech.pegasys.teku.validator.coordinator.performance.SyncCommitteePerformanceTracker;
import tech.pegasys.teku.validator.coordinator.performance.ValidatorPerformanceMetrics;
import tech.pegasys.teku.weaksubjectivity.WeakSubjectivityValidator;

/**
 * The central class which assembles together and initializes Beacon Chain components
 *
 * <p>CAUTION: This class can be overridden by custom implementation to tweak creation and
 * initialization behavior (see {@link BeaconChainControllerFactory}} however this class may change
 * in a backward incompatible manner and either break compilation or runtime behavior
 */
public class BeaconChainController extends Service implements BeaconChainControllerFacade {
  private static final Logger LOG = LogManager.getLogger();

  protected static final String KEY_VALUE_STORE_SUBDIRECTORY = "kvstore";

  protected volatile BeaconChainConfiguration beaconConfig;
  protected volatile Spec spec;
  protected volatile Function<UInt64, BeaconBlockBodySchema<?>> beaconBlockSchemaSupplier;
  protected volatile EventChannels eventChannels;
  protected volatile MetricsSystem metricsSystem;
  protected volatile AsyncRunner beaconAsyncRunner;
  protected volatile TimeProvider timeProvider;
  protected volatile SlotEventsChannel slotEventsChannelPublisher;
  protected volatile AsyncRunner networkAsyncRunner;
  protected volatile AsyncRunnerFactory asyncRunnerFactory;
  protected volatile AsyncRunner eventAsyncRunner;
  protected volatile Path beaconDataDirectory;
  protected volatile WeakSubjectivityInitializer wsInitializer = new WeakSubjectivityInitializer();
  protected volatile AsyncRunnerEventThread forkChoiceExecutor;

  protected volatile ForkChoice forkChoice;
  protected volatile ForkChoiceTrigger forkChoiceTrigger;
  protected volatile BlockImporter blockImporter;
  protected volatile RecentChainData recentChainData;
  protected volatile Eth2P2PNetwork p2pNetwork;
  protected volatile Optional<BeaconRestApi> beaconRestAPI = Optional.empty();
  protected volatile AggregatingAttestationPool attestationPool;
  protected volatile DepositProvider depositProvider;
  protected volatile SyncService syncService;
  protected volatile AttestationManager attestationManager;
  protected volatile SignatureVerificationService signatureVerificationService;
  protected volatile CombinedChainDataClient combinedChainDataClient;
  protected volatile Eth1DataCache eth1DataCache;
  protected volatile SlotProcessor slotProcessor;
  protected volatile OperationPool<AttesterSlashing> attesterSlashingPool;
  protected volatile OperationPool<ProposerSlashing> proposerSlashingPool;
  protected volatile OperationPool<SignedVoluntaryExit> voluntaryExitPool;
  protected volatile SyncCommitteeContributionPool syncCommitteeContributionPool;
  protected volatile SyncCommitteeMessagePool syncCommitteeMessagePool;
  protected volatile WeakSubjectivityValidator weakSubjectivityValidator;
  protected volatile PerformanceTracker performanceTracker;
  protected volatile PendingPool<SignedBeaconBlock> pendingBlocks;
  protected volatile CoalescingChainHeadChannel coalescingChainHeadChannel;
  protected volatile ActiveValidatorTracker activeValidatorTracker;
  protected volatile AttestationTopicSubscriber attestationTopicSubscriber;
  protected volatile ForkChoiceNotifier forkChoiceNotifier;
  protected volatile ExecutionLayerChannel executionLayer;
  protected volatile Optional<TerminalPowBlockMonitor> terminalPowBlockMonitor = Optional.empty();
  protected volatile Optional<MergeTransitionConfigCheck> mergeTransitionConfigCheck =
      Optional.empty();
  protected volatile ProposersDataManager proposersDataManager;
  protected volatile KeyValueStore<String, Bytes> keyValueStore;

  protected UInt64 genesisTimeTracker = ZERO;
  protected BlockManager blockManager;
  protected TimerService timerService;
  protected PendingPoolFactory pendingPoolFactory;
  protected SettableLabelledGauge futureItemsMetric;
  protected IntSupplier rejectedExecutionCountSupplier;

  public BeaconChainController(
      final ServiceConfig serviceConfig, final BeaconChainConfiguration beaconConfig) {
    this.beaconConfig = beaconConfig;
    this.spec = beaconConfig.getSpec();
    this.beaconBlockSchemaSupplier =
        slot -> spec.atSlot(slot).getSchemaDefinitions().getBeaconBlockBodySchema();
    this.beaconDataDirectory = serviceConfig.getDataDirLayout().getBeaconDataDirectory();
    this.asyncRunnerFactory = serviceConfig.getAsyncRunnerFactory();
    this.beaconAsyncRunner = serviceConfig.createAsyncRunner("beaconchain");
    this.eventAsyncRunner = serviceConfig.createAsyncRunner("events", 10);
    this.networkAsyncRunner = serviceConfig.createAsyncRunner("p2p", 10);
    this.timeProvider = serviceConfig.getTimeProvider();
    this.eventChannels = serviceConfig.getEventChannels();
    this.metricsSystem = serviceConfig.getMetricsSystem();
    this.pendingPoolFactory = new PendingPoolFactory(this.metricsSystem);
    this.rejectedExecutionCountSupplier = serviceConfig.getRejectedExecutionsSupplier();
    this.slotEventsChannelPublisher = eventChannels.getPublisher(SlotEventsChannel.class);
    this.forkChoiceExecutor = new AsyncRunnerEventThread("forkchoice", asyncRunnerFactory);
    this.futureItemsMetric =
        SettableLabelledGauge.create(
            metricsSystem,
            BEACON,
            "future_items_size",
            "Current number of items held for future slots, labelled by type",
            "type");
  }

  @Override
  protected SafeFuture<?> doStart() {
    LOG.debug("Starting {}", this.getClass().getSimpleName());
    forkChoiceExecutor.start();
    return initialize()
        .thenCompose(
            (__) ->
                beaconRestAPI.map(BeaconRestApi::start).orElse(SafeFuture.completedFuture(null)));
  }

  protected void startServices() {
    syncService
        .getRecentBlockFetcher()
        .subscribeBlockFetched(
            (block) ->
                blockManager
                    .importBlock(block)
                    .finish(err -> LOG.error("Failed to process recently fetched block.", err)));
    blockManager.subscribeToReceivedBlocks(
        (block, executionOptimistic) ->
            syncService.getRecentBlockFetcher().cancelRecentBlockRequest(block.getRoot()));
    SafeFuture.allOfFailFast(
            attestationManager.start(),
            p2pNetwork.start(),
            blockManager.start(),
            syncService.start(),
            SafeFuture.fromRunnable(
                () -> terminalPowBlockMonitor.ifPresent(TerminalPowBlockMonitor::start)),
            mergeTransitionConfigCheck
                .map(MergeTransitionConfigCheck::start)
                .orElse(SafeFuture.completedFuture(null)))
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
            beaconRestAPI.map(BeaconRestApi::stop).orElse(SafeFuture.completedFuture(null)),
            syncService.stop(),
            blockManager.stop(),
            attestationManager.stop(),
            p2pNetwork.stop(),
            timerService.stop(),
            SafeFuture.fromRunnable(
                () -> terminalPowBlockMonitor.ifPresent(TerminalPowBlockMonitor::stop)),
            mergeTransitionConfigCheck
                .map(MergeTransitionConfigCheck::stop)
                .orElse(SafeFuture.completedFuture(null)))
        .thenRun(forkChoiceExecutor::stop);
  }

  protected SafeFuture<?> initialize() {
    final StoreConfig storeConfig = beaconConfig.storeConfig();
    coalescingChainHeadChannel =
        new CoalescingChainHeadChannel(
            eventChannels.getPublisher(ChainHeadChannel.class), EVENT_LOG);
    timerService = new TimerService(this::onTick);

    StorageQueryChannel storageQueryChannel =
        eventChannels.getPublisher(StorageQueryChannel.class, beaconAsyncRunner);
    StorageUpdateChannel storageUpdateChannel =
        eventChannels.getPublisher(StorageUpdateChannel.class, beaconAsyncRunner);
    final VoteUpdateChannel voteUpdateChannel = eventChannels.getPublisher(VoteUpdateChannel.class);
    // Init other services
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
                    eventChannels.getPublisher(FinalizedCheckpointChannel.class, beaconAsyncRunner),
                    coalescingChainHeadChannel,
                    spec))
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
        // Init other services
        .thenRun(this::initAll)
        .thenRun(
            () -> {
              recentChainData.subscribeStoreInitialized(this::onStoreInitialized);
              recentChainData.subscribeBestBlockInitialized(this::startServices);
            })
        .thenCompose(__ -> timerService.start());
  }

  public void initAll() {
    initKeyValueStore();
    initExecutionLayer();
    initForkChoiceNotifier();
    initMergeMonitors();
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
    initSignatureVerificationService();
    initAttestationManager();
    initPendingBlocks();
    initBlockManager();
    initSyncCommitteePools();
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

  private void initKeyValueStore() {
    keyValueStore =
        new FileKeyValueStore(beaconDataDirectory.resolve(KEY_VALUE_STORE_SUBDIRECTORY));
  }

  protected void initExecutionLayer() {
    executionLayer = eventChannels.getPublisher(ExecutionLayerChannel.class, beaconAsyncRunner);
  }

  protected void initMergeMonitors() {
    if (spec.isMilestoneSupported(SpecMilestone.BELLATRIX)) {
      terminalPowBlockMonitor =
          Optional.of(
              new TerminalPowBlockMonitor(
                  executionLayer,
                  spec,
                  recentChainData,
                  forkChoiceNotifier,
                  beaconAsyncRunner,
                  EVENT_LOG,
                  timeProvider));

      mergeTransitionConfigCheck =
          Optional.of(
              new MergeTransitionConfigCheck(EVENT_LOG, spec, executionLayer, beaconAsyncRunner));
    }
  }

  protected void initPendingBlocks() {
    LOG.debug("BeaconChainController.initPendingBlocks()");
    pendingBlocks = pendingPoolFactory.createForBlocks(spec);
    eventChannels.subscribe(FinalizedCheckpointChannel.class, pendingBlocks);
  }

  protected void initPerformanceTracker() {
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
              new SyncCommitteePerformanceTracker(spec, combinedChainDataClient),
              spec);
      eventChannels.subscribe(SlotEventsChannel.class, performanceTracker);
    } else {
      performanceTracker = new NoOpPerformanceTracker();
    }
  }

  protected void initAttesterSlashingPool() {
    LOG.debug("BeaconChainController.initAttesterSlashingPool()");
    attesterSlashingPool =
        new OperationPool<>(
            "AttesterSlashingPool",
            metricsSystem,
            beaconBlockSchemaSupplier.andThen(BeaconBlockBodySchema::getAttesterSlashingsSchema),
            new AttesterSlashingValidator(recentChainData, spec),
            // Prioritise slashings that include more validators at a time
            Comparator.<AttesterSlashing>comparingInt(
                    slashing -> slashing.getIntersectingValidatorIndices().size())
                .reversed());
    blockImporter.subscribeToVerifiedBlockAttesterSlashings(attesterSlashingPool::removeAll);
    attesterSlashingPool.subscribeOperationAdded(forkChoice::onAttesterSlashing);
  }

  protected void initProposerSlashingPool() {
    LOG.debug("BeaconChainController.initProposerSlashingPool()");
    ProposerSlashingValidator validator = new ProposerSlashingValidator(spec, recentChainData);
    proposerSlashingPool =
        new OperationPool<>(
            "ProposerSlashingPool",
            metricsSystem,
            beaconBlockSchemaSupplier.andThen(BeaconBlockBodySchema::getProposerSlashingsSchema),
            validator);
    blockImporter.subscribeToVerifiedBlockProposerSlashings(proposerSlashingPool::removeAll);
  }

  protected void initVoluntaryExitPool() {
    LOG.debug("BeaconChainController.initVoluntaryExitPool()");
    VoluntaryExitValidator validator = new VoluntaryExitValidator(spec, recentChainData);
    voluntaryExitPool =
        new OperationPool<>(
            "VoluntaryExitPool",
            metricsSystem,
            beaconBlockSchemaSupplier.andThen(BeaconBlockBodySchema::getVoluntaryExitsSchema),
            validator);
    blockImporter.subscribeToVerifiedBlockVoluntaryExits(voluntaryExitPool::removeAll);
  }

  protected void initCombinedChainDataClient() {
    LOG.debug("BeaconChainController.initCombinedChainDataClient()");
    combinedChainDataClient =
        new CombinedChainDataClient(
            recentChainData,
            eventChannels.getPublisher(StorageQueryChannel.class, beaconAsyncRunner),
            spec);
  }

  protected SafeFuture<Void> initWeakSubjectivity(
      final StorageQueryChannel queryChannel, final StorageUpdateChannel updateChannel) {
    return wsInitializer
        .finalizeAndStoreConfig(beaconConfig.weakSubjectivity(), queryChannel, updateChannel)
        .thenAccept(
            finalConfig ->
                this.weakSubjectivityValidator = WeakSubjectivityValidator.moderate(finalConfig));
  }

  protected void initForkChoice() {
    LOG.debug("BeaconChainController.initForkChoice()");
    final boolean proposerBoostEnabled = beaconConfig.eth2NetworkConfig().isProposerBoostEnabled();
    final boolean equivocatingIndicesEnabled =
        beaconConfig.eth2NetworkConfig().isEquivocatingIndicesEnabled();
    forkChoice =
        new ForkChoice(
            spec,
            forkChoiceExecutor,
            recentChainData,
            forkChoiceNotifier,
            new TickProcessor(spec, recentChainData),
            new MergeTransitionBlockValidator(spec, recentChainData, executionLayer),
            new ActivePandaPrinter(keyValueStore, STATUS_LOG),
            proposerBoostEnabled,
            equivocatingIndicesEnabled);
    forkChoiceTrigger =
        beaconConfig.eth2NetworkConfig().isForkChoiceBeforeProposingEnabled()
            ? new PreProposalForkChoiceTrigger(forkChoice)
            : new ForkChoiceTrigger(forkChoice);
  }

  public void initMetrics() {
    LOG.debug("BeaconChainController.initMetrics()");
    final SyncCommitteeMetrics syncCommitteeMetrics =
        new SyncCommitteeMetrics(spec, recentChainData, metricsSystem);
    final BeaconChainMetrics beaconChainMetrics =
        new BeaconChainMetrics(
            spec,
            recentChainData,
            slotProcessor.getNodeSlot(),
            metricsSystem,
            p2pNetwork,
            eth1DataCache);
    eventChannels
        .subscribe(SlotEventsChannel.class, beaconChainMetrics)
        .subscribe(SlotEventsChannel.class, syncCommitteeMetrics)
        .subscribe(ChainHeadChannel.class, syncCommitteeMetrics);
  }

  public void initDepositProvider() {
    LOG.debug("BeaconChainController.initDepositProvider()");
    depositProvider =
        new DepositProvider(
            metricsSystem,
            recentChainData,
            eth1DataCache,
            spec,
            EVENT_LOG,
            beaconConfig.powchainConfig().useMissingDepositEventLogging());
    eventChannels
        .subscribe(Eth1EventsChannel.class, depositProvider)
        .subscribe(FinalizedCheckpointChannel.class, depositProvider)
        .subscribe(SlotEventsChannel.class, depositProvider);
  }

  protected void initEth1DataCache() {
    LOG.debug("BeaconChainController.initEth1DataCache");
    eth1DataCache = new Eth1DataCache(metricsSystem, new Eth1VotingPeriod(spec));
  }

  protected void initAttestationTopicSubscriber() {
    LOG.debug("BeaconChainController.initAttestationTopicSubscriber");
    this.attestationTopicSubscriber = new AttestationTopicSubscriber(spec, p2pNetwork);
  }

  protected void initActiveValidatorTracker() {
    LOG.debug("BeaconChainController.initActiveValidatorTracker");
    final StableSubnetSubscriber stableSubnetSubscriber =
        beaconConfig.p2pConfig().isSubscribeAllSubnetsEnabled()
            ? AllSubnetsSubscriber.create(attestationTopicSubscriber)
            : new ValidatorBasedStableSubnetSubscriber(
                attestationTopicSubscriber, new Random(), spec);
    this.activeValidatorTracker = new ActiveValidatorTracker(stableSubnetSubscriber, spec);
  }

  public void initValidatorApiHandler() {
    LOG.debug("BeaconChainController.initValidatorApiHandler()");
    final BlockFactory blockFactory =
        new BlockFactory(
            spec,
            new BlockOperationSelectorFactory(
                spec,
                attestationPool,
                attesterSlashingPool,
                proposerSlashingPool,
                voluntaryExitPool,
                syncCommitteeContributionPool,
                depositProvider,
                eth1DataCache,
                VersionProvider.getDefaultGraffiti(),
                forkChoiceNotifier,
                executionLayer));
    SyncCommitteeSubscriptionManager syncCommitteeSubscriptionManager =
        beaconConfig.p2pConfig().isSubscribeAllSubnetsEnabled()
            ? new AllSyncCommitteeSubscriptions(p2pNetwork, spec)
            : new SyncCommitteeSubscriptionManager(p2pNetwork);
    final BlockImportChannel blockImportChannel =
        eventChannels.getPublisher(BlockImportChannel.class, beaconAsyncRunner);
    final BlockGossipChannel blockGossipChannel =
        eventChannels.getPublisher(BlockGossipChannel.class);
    final ValidatorApiHandler validatorApiHandler =
        new ValidatorApiHandler(
            new ChainDataProvider(spec, recentChainData, combinedChainDataClient),
            combinedChainDataClient,
            syncService,
            blockFactory,
            blockImportChannel,
            blockGossipChannel,
            attestationPool,
            attestationManager,
            attestationTopicSubscriber,
            activeValidatorTracker,
            DutyMetrics.create(metricsSystem, timeProvider, recentChainData, spec),
            performanceTracker,
            spec,
            forkChoiceTrigger,
            proposersDataManager,
            syncCommitteeMessagePool,
            syncCommitteeContributionPool,
            syncCommitteeSubscriptionManager);
    eventChannels
        .subscribe(SlotEventsChannel.class, activeValidatorTracker)
        .subscribeMultithreaded(
            ValidatorApiChannel.class,
            validatorApiHandler,
            beaconConfig.beaconRestApiConfig().getValidatorThreads());

    // if subscribeAllSubnets is set, the slot events in these handlers are empty,
    // so don't subscribe.
    if (!beaconConfig.p2pConfig().isSubscribeAllSubnetsEnabled()) {
      eventChannels
          .subscribe(SlotEventsChannel.class, attestationTopicSubscriber)
          .subscribe(SlotEventsChannel.class, syncCommitteeSubscriptionManager);
    }
  }

  protected void initGenesisHandler() {
    if (!recentChainData.isPreGenesis()) {
      // We already have a genesis block - no need for a genesis handler
      return;
    } else if (!beaconConfig.powchainConfig().isEnabled()) {
      // We're pre-genesis but no eth1 endpoint is set
      throw new IllegalStateException("ETH1 is disabled, but no initial state is set.");
    }
    STATUS_LOG.loadingGenesisFromEth1Chain();
    eventChannels.subscribe(
        Eth1EventsChannel.class, new GenesisHandler(recentChainData, timeProvider, spec));
  }

  protected void initSignatureVerificationService() {
    final P2PConfig p2PConfig = beaconConfig.p2pConfig();
    signatureVerificationService =
        p2PConfig.batchVerifyAttestationSignatures()
            ? new AggregatingSignatureVerificationService(
                metricsSystem,
                asyncRunnerFactory,
                beaconAsyncRunner,
                p2PConfig.getBatchVerifyMaxThreads(),
                p2PConfig.getBatchVerifyQueueCapacity(),
                p2PConfig.getBatchVerifyMaxBatchSize(),
                p2PConfig.isBatchVerifyStrictThreadLimitEnabled())
            : SignatureVerificationService.createSimple();
  }

  protected void initAttestationManager() {
    final PendingPool<ValidateableAttestation> pendingAttestations =
        pendingPoolFactory.createForAttestations(spec);
    final FutureItems<ValidateableAttestation> futureAttestations =
        FutureItems.create(
            ValidateableAttestation::getEarliestSlotForForkChoiceProcessing,
            UInt64.valueOf(3),
            futureItemsMetric,
            "attestations");
    AttestationValidator attestationValidator =
        new AttestationValidator(spec, recentChainData, signatureVerificationService);
    AggregateAttestationValidator aggregateValidator =
        new AggregateAttestationValidator(spec, attestationValidator, signatureVerificationService);
    blockImporter.subscribeToVerifiedBlockAttestations(
        (slot, attestations) ->
            attestations.forEach(
                attestation ->
                    aggregateValidator.addSeenAggregate(
                        ValidateableAttestation.from(spec, attestation))));
    attestationManager =
        AttestationManager.create(
            pendingAttestations,
            futureAttestations,
            forkChoice,
            attestationPool,
            attestationValidator,
            aggregateValidator,
            signatureVerificationService,
            eventChannels.getPublisher(ActiveValidatorChannel.class, beaconAsyncRunner));

    eventChannels
        .subscribe(SlotEventsChannel.class, attestationManager)
        .subscribe(FinalizedCheckpointChannel.class, pendingAttestations)
        .subscribe(BlockImportNotifications.class, attestationManager);
  }

  protected void initSyncCommitteePools() {
    final SyncCommitteeStateUtils syncCommitteeStateUtils =
        new SyncCommitteeStateUtils(spec, recentChainData);
    syncCommitteeContributionPool =
        new SyncCommitteeContributionPool(
            spec,
            new SignedContributionAndProofValidator(
                spec,
                recentChainData,
                syncCommitteeStateUtils,
                timeProvider,
                signatureVerificationService));

    syncCommitteeMessagePool =
        new SyncCommitteeMessagePool(
            spec,
            new SyncCommitteeMessageValidator(
                spec,
                recentChainData,
                syncCommitteeStateUtils,
                signatureVerificationService,
                timeProvider));
    eventChannels
        .subscribe(SlotEventsChannel.class, syncCommitteeContributionPool)
        .subscribe(SlotEventsChannel.class, syncCommitteeMessagePool);
  }

  protected void initP2PNetwork() {
    LOG.debug("BeaconChainController.initP2PNetwork()");
    if (!beaconConfig.p2pConfig().getNetworkConfig().isEnabled()) {
      this.p2pNetwork = new NoOpEth2P2PNetwork(spec);
      return;
    }

    DiscoveryConfig discoveryConfig = beaconConfig.p2pConfig().getDiscoveryConfig();
    final Optional<Integer> maybeUdpPort =
        discoveryConfig.isDiscoveryEnabled()
            ? Optional.of(discoveryConfig.getListenUdpPort())
            : Optional.empty();

    PortAvailability.checkPortsAvailable(
        beaconConfig.p2pConfig().getNetworkConfig().getListenPort(), maybeUdpPort);

    this.p2pNetwork =
        createEth2P2PNetworkBuilder()
            .config(beaconConfig.p2pConfig())
            .eventChannels(eventChannels)
            .recentChainData(recentChainData)
            .gossipedBlockProcessor(blockManager::validateAndImportBlock)
            .gossipedAttestationProcessor(attestationManager::addAttestation)
            .gossipedAggregateProcessor(attestationManager::addAggregate)
            .gossipedAttesterSlashingProcessor(attesterSlashingPool::addRemote)
            .gossipedProposerSlashingProcessor(proposerSlashingPool::addRemote)
            .gossipedVoluntaryExitProcessor(voluntaryExitPool::addRemote)
            .gossipedSignedContributionAndProofProcessor(syncCommitteeContributionPool::addRemote)
            .gossipedSyncCommitteeMessageProcessor(syncCommitteeMessagePool::addRemote)
            .processedAttestationSubscriptionProvider(
                attestationManager::subscribeToAttestationsToSend)
            .historicalChainData(
                eventChannels.getPublisher(StorageQueryChannel.class, beaconAsyncRunner))
            .metricsSystem(metricsSystem)
            .timeProvider(timeProvider)
            .asyncRunner(networkAsyncRunner)
            .keyValueStore(keyValueStore)
            .requiredCheckpoint(weakSubjectivityValidator.getWSCheckpoint())
            .specProvider(spec)
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
  }

  protected Eth2P2PNetworkBuilder createEth2P2PNetworkBuilder() {
    return Eth2P2PNetworkBuilder.create();
  }

  protected void initSlotProcessor() {
    slotProcessor =
        new SlotProcessor(
            spec,
            recentChainData,
            syncService,
            forkChoiceTrigger,
            forkChoiceNotifier,
            p2pNetwork,
            slotEventsChannelPublisher,
            new EpochCachePrimer(spec, recentChainData));
  }

  public void initAttestationPool() {
    LOG.debug("BeaconChainController.initAttestationPool()");
    attestationPool =
        new AggregatingAttestationPool(spec, metricsSystem, DEFAULT_MAXIMUM_ATTESTATION_COUNT);
    eventChannels.subscribe(SlotEventsChannel.class, attestationPool);
    blockImporter.subscribeToVerifiedBlockAttestations(
        attestationPool::onAttestationsIncludedInBlock);
  }

  public void initRestAPI() {
    LOG.debug("BeaconChainController.initRestAPI()");
    if (!beaconConfig.beaconRestApiConfig().isRestApiEnabled()) {
      LOG.info("rest-api-enabled is false, not starting rest api.");
      return;
    }
    final DataProvider dataProvider =
        DataProvider.builder()
            .spec(spec)
            .recentChainData(recentChainData)
            .combinedChainDataClient(combinedChainDataClient)
            .p2pNetwork(p2pNetwork)
            .syncService(syncService)
            .validatorApiChannel(
                eventChannels.getPublisher(ValidatorApiChannel.class, beaconAsyncRunner))
            .attestationPool(attestationPool)
            .blockManager(blockManager)
            .attestationManager(attestationManager)
            .isLivenessTrackingEnabled(
                beaconConfig.beaconRestApiConfig().isBeaconLivenessTrackingEnabled())
            .activeValidatorChannel(
                eventChannels.getPublisher(ActiveValidatorChannel.class, beaconAsyncRunner))
            .attesterSlashingPool(attesterSlashingPool)
            .proposerSlashingPool(proposerSlashingPool)
            .voluntaryExitPool(voluntaryExitPool)
            .syncCommitteeContributionPool(syncCommitteeContributionPool)
            .proposersDataManager(proposersDataManager)
            .rejectedExecutionSupplier(rejectedExecutionCountSupplier)
            .build();
    final Eth1DataProvider eth1DataProvider = new Eth1DataProvider(eth1DataCache, depositProvider);

    final BeaconRestApi api =
        beaconConfig.beaconRestApiConfig().isEnableMigratedRestApi()
            ? new JsonTypeDefinitionBeaconRestApi(
                dataProvider,
                eth1DataProvider,
                beaconConfig.beaconRestApiConfig(),
                eventChannels,
                eventAsyncRunner,
                timeProvider,
                spec)
            : new ReflectionBasedBeaconRestApi(
                dataProvider,
                eth1DataProvider,
                beaconConfig.beaconRestApiConfig(),
                eventChannels,
                eventAsyncRunner,
                timeProvider,
                spec);
    beaconRestAPI = Optional.of(api);

    if (beaconConfig.beaconRestApiConfig().isBeaconLivenessTrackingEnabled()) {
      final int initialValidatorsCount =
          spec.getGenesisSpec().getConfig().getMinGenesisActiveValidatorCount();
      eventChannels.subscribe(
          ActiveValidatorChannel.class, new ActiveValidatorCache(spec, initialValidatorsCount));
    }
  }

  public void initBlockImporter() {
    LOG.debug("BeaconChainController.initBlockImporter()");
    blockImporter =
        new BlockImporter(
            spec,
            eventChannels.getPublisher(BlockImportNotifications.class),
            recentChainData,
            forkChoice,
            weakSubjectivityValidator,
            executionLayer);
  }

  public void initBlockManager() {
    LOG.debug("BeaconChainController.initBlockManager()");
    final FutureItems<SignedBeaconBlock> futureBlocks =
        FutureItems.create(SignedBeaconBlock::getSlot, futureItemsMetric, "blocks");
    final BlockValidator blockValidator = new BlockValidator(spec, recentChainData);
    final Optional<BlockImportMetrics> importMetrics =
        beaconConfig.getMetricsConfig().isBlockPerformanceEnabled()
            ? Optional.of(BlockImportMetrics.create(metricsSystem))
            : Optional.empty();

    blockManager =
        new BlockManager(
            recentChainData,
            blockImporter,
            pendingBlocks,
            futureBlocks,
            blockValidator,
            timeProvider,
            EVENT_LOG,
            importMetrics);
    if (spec.isMilestoneSupported(SpecMilestone.BELLATRIX)) {
      final FailedExecutionPool failedExecutionPool =
          new FailedExecutionPool(blockManager, beaconAsyncRunner);
      blockManager.subscribeFailedPayloadExecution(failedExecutionPool::addFailedBlock);
    }
    eventChannels
        .subscribe(SlotEventsChannel.class, blockManager)
        .subscribe(BlockImportChannel.class, blockManager)
        .subscribe(BlockImportNotifications.class, blockManager);
  }

  protected SyncServiceFactory createSyncServiceFactory() {
    return new DefaultSyncServiceFactory(
        beaconConfig.syncConfig(),
        beaconConfig.eth2NetworkConfig().getGenesisState(),
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
        signatureVerificationService,
        Duration.ofSeconds(beaconConfig.eth2NetworkConfig().getStartupTimeoutSeconds()),
        spec);
  }

  public void initSyncService() {
    LOG.debug("BeaconChainController.initSyncService()");
    syncService = createSyncServiceFactory().create();

    // chainHeadChannel subscription
    syncService.getForwardSync().subscribeToSyncChanges(coalescingChainHeadChannel);

    // forkChoiceNotifier subscription
    syncService.subscribeToSyncStateChangesAndUpdate(
        syncState -> forkChoiceNotifier.onSyncingStatusChanged(syncState.isInSync()));

    // depositProvider subscription
    syncService.subscribeToSyncStateChangesAndUpdate(
        syncState -> depositProvider.onSyncingStatusChanged(syncState.isInSync()));

    // forkChoice subscription
    forkChoice.subscribeToOptimisticHeadChangesAndUpdate(syncService.getOptimisticSyncSubscriber());

    // terminalPowBlockMonitor subscription
    terminalPowBlockMonitor.ifPresent(
        monitor ->
            syncService.subscribeToSyncStateChangesAndUpdate(
                syncState -> monitor.onNodeSyncStateChanged(syncState.isInSync())));

    // p2pNetwork subscription so gossip can be enabled and disabled appropriately
    syncService.subscribeToSyncStateChangesAndUpdate(
        state -> p2pNetwork.onSyncStateChanged(state.isInSync(), state.isOptimistic()));
  }

  protected void initOperationsReOrgManager() {
    LOG.debug("BeaconChainController.initOperationsReOrgManager()");
    OperationsReOrgManager operationsReOrgManager =
        new OperationsReOrgManager(
            proposerSlashingPool,
            attesterSlashingPool,
            voluntaryExitPool,
            attestationPool,
            attestationManager,
            recentChainData);
    eventChannels.subscribe(ChainHeadChannel.class, operationsReOrgManager);
  }

  protected void initForkChoiceNotifier() {
    LOG.debug("BeaconChainController.initForkChoiceNotifier()");
    final AsyncRunnerEventThread eventThread =
        new AsyncRunnerEventThread("forkChoiceNotifier", asyncRunnerFactory);
    eventThread.start();
    proposersDataManager =
        new ProposersDataManager(
            eventThread,
            spec,
            metricsSystem,
            executionLayer,
            recentChainData,
            getProposerDefaultFeeRecipient());
    eventChannels.subscribe(SlotEventsChannel.class, proposersDataManager);
    forkChoiceNotifier =
        new ForkChoiceNotifierImpl(
            eventThread, timeProvider, spec, executionLayer, recentChainData, proposersDataManager);
  }

  private Optional<Eth1Address> getProposerDefaultFeeRecipient() {
    if (!spec.isMilestoneSupported(SpecMilestone.BELLATRIX)) {
      return Optional.of(Eth1Address.ZERO);
    }

    final Optional<Eth1Address> defaultFeeRecipient =
        beaconConfig.validatorConfig().getProposerDefaultFeeRecipient();

    if (defaultFeeRecipient.isEmpty() && beaconConfig.beaconRestApiConfig().isRestApiEnabled()) {
      STATUS_LOG.warnMissingProposerDefaultFeeRecipientWithRestAPIEnabled();
    }

    return defaultFeeRecipient;
  }

  protected void setupInitialState(final RecentChainData client) {
    final Optional<AnchorPoint> initialAnchor =
        wsInitializer.loadInitialAnchorPoint(
            spec, beaconConfig.eth2NetworkConfig().getInitialState());
    // Validate
    initialAnchor.ifPresent(
        anchor -> {
          final UInt64 currentSlot = getCurrentSlot(anchor.getState().getGenesisTime());
          wsInitializer.validateInitialAnchor(anchor, currentSlot, spec);
        });

    if (initialAnchor.isPresent()) {
      final AnchorPoint anchor = initialAnchor.get();
      client.initializeFromAnchorPoint(anchor, timeProvider.getTimeInSeconds());
      if (anchor.isGenesis()) {
        EVENT_LOG.genesisEvent(
            anchor.getStateRoot(),
            recentChainData.getBestBlockRoot().orElseThrow(),
            anchor.getState().getGenesisTime());
      }
    } else if (beaconConfig.interopConfig().isInteropEnabled()) {
      setupInteropState();
    } else if (!beaconConfig.powchainConfig().isEnabled()) {
      throw new InvalidConfigurationException(
          "ETH1 is disabled but initial state is unknown. Enable ETH1 or specify an initial state.");
    }
  }

  protected void setupInteropState() {
    final InteropConfig config = beaconConfig.interopConfig();
    STATUS_LOG.generatingMockStartGenesis(
        config.getInteropGenesisTime(), config.getInteropNumberOfValidators());

    Optional<ExecutionPayloadHeader> executionPayloadHeader = Optional.empty();
    if (config.getInteropGenesisPayloadHeader().isPresent()) {
      try {
        executionPayloadHeader =
            Optional.of(
                spec.deserializeJsonExecutionPayloadHeader(
                    new ObjectMapper(),
                    config.getInteropGenesisPayloadHeader().get().toFile(),
                    GENESIS_SLOT));
      } catch (IOException e) {
        throw new RuntimeException(
            "Unable to load payload header from " + config.getInteropGenesisPayloadHeader().get(),
            e);
      }
    }

    final BeaconState genesisState =
        InteropStartupUtil.createMockedStartInitialBeaconState(
            spec,
            config.getInteropGenesisTime(),
            config.getInteropNumberOfValidators(),
            executionPayloadHeader);

    recentChainData.initializeFromGenesis(genesisState, timeProvider.getTimeInSeconds());

    EVENT_LOG.genesisEvent(
        genesisState.hashTreeRoot(),
        recentChainData.getBestBlockRoot().orElseThrow(),
        genesisState.getGenesisTime());
  }

  protected void onStoreInitialized() {
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

  protected UInt64 getCurrentSlot(final UInt64 genesisTime) {
    return getCurrentSlot(genesisTime, timeProvider.getTimeInSeconds());
  }

  protected UInt64 getCurrentSlot(final UInt64 genesisTime, final UInt64 currentTime) {
    return spec.getCurrentSlot(currentTime, genesisTime);
  }

  protected void validateChain(final UInt64 currentSlot) {
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

  private void onTick() {
    if (recentChainData.isPreGenesis()) {
      return;
    }

    final UInt64 currentTimeMillis = timeProvider.getTimeInMillis();
    final UInt64 currentTimeSeconds = millisToSeconds(currentTimeMillis);
    final Optional<TickProcessingPerformance> performanceRecord =
        beaconConfig.getMetricsConfig().isTickPerformanceEnabled()
            ? Optional.of(new TickProcessingPerformance(timeProvider, currentTimeMillis))
            : Optional.empty();

    forkChoice.onTick(currentTimeMillis, performanceRecord);

    final UInt64 genesisTime = recentChainData.getGenesisTime();
    if (genesisTime.isGreaterThan(currentTimeSeconds)) {
      // notify every 10 minutes
      if (genesisTimeTracker.plus(600L).isLessThanOrEqualTo(currentTimeSeconds)) {
        genesisTimeTracker = currentTimeSeconds;
        STATUS_LOG.timeUntilGenesis(
            genesisTime.minus(currentTimeSeconds).longValue(), p2pNetwork.getPeerCount());
      }
    }

    slotProcessor.onTick(currentTimeMillis, performanceRecord);
    performanceRecord.ifPresent(TickProcessingPerformance::complete);
  }

  @Override
  public Spec getSpec() {
    return spec;
  }

  @Override
  public TimeProvider getTimeProvider() {
    return timeProvider;
  }

  @Override
  public AsyncRunnerFactory getAsyncRunnerFactory() {
    return asyncRunnerFactory;
  }

  @Override
  public SignatureVerificationService getSignatureVerificationService() {
    return signatureVerificationService;
  }

  @Override
  public RecentChainData getRecentChainData() {
    return recentChainData;
  }

  @Override
  public CombinedChainDataClient getCombinedChainDataClient() {
    return combinedChainDataClient;
  }

  @Override
  public Eth2P2PNetwork getP2pNetwork() {
    return p2pNetwork;
  }

  @Override
  public Optional<BeaconRestApi> getBeaconRestAPI() {
    return beaconRestAPI;
  }

  @Override
  public SyncService getSyncService() {
    return syncService;
  }

  @Override
  public ForkChoice getForkChoice() {
    return forkChoice;
  }
}
