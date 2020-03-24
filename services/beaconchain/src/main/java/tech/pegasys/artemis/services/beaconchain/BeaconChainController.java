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

package tech.pegasys.artemis.services.beaconchain;

import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;
import static tech.pegasys.artemis.statetransition.util.ForkChoiceUtil.on_tick;
import static tech.pegasys.artemis.util.config.Constants.SECONDS_PER_SLOT;
import static tech.pegasys.artemis.util.config.Constants.SLOTS_PER_EPOCH;
import static tech.pegasys.teku.logging.EventLogger.EVENT_LOG;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.common.primitives.UnsignedLong;
import io.libp2p.core.crypto.KEY_TYPE;
import io.libp2p.core.crypto.KeyKt;
import io.libp2p.core.crypto.PrivKey;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Date;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.artemis.api.DataProvider;
import tech.pegasys.artemis.beaconrestapi.BeaconRestApi;
import tech.pegasys.artemis.events.EventChannels;
import tech.pegasys.artemis.metrics.ArtemisMetricCategory;
import tech.pegasys.artemis.metrics.SettableGauge;
import tech.pegasys.artemis.networking.eth2.Eth2NetworkBuilder;
import tech.pegasys.artemis.networking.eth2.peers.Eth2Peer;
import tech.pegasys.artemis.networking.p2p.connection.TargetPeerRange;
import tech.pegasys.artemis.networking.p2p.mock.MockP2PNetwork;
import tech.pegasys.artemis.networking.p2p.network.NetworkConfig;
import tech.pegasys.artemis.networking.p2p.network.P2PNetwork;
import tech.pegasys.artemis.pow.api.Eth1EventsChannel;
import tech.pegasys.artemis.service.serviceutils.Service;
import tech.pegasys.artemis.statetransition.AttestationAggregator;
import tech.pegasys.artemis.statetransition.BlockAttestationsPool;
import tech.pegasys.artemis.statetransition.BlockProposalUtil;
import tech.pegasys.artemis.statetransition.StateProcessor;
import tech.pegasys.artemis.statetransition.StateTransition;
import tech.pegasys.artemis.statetransition.blockimport.BlockImporter;
import tech.pegasys.artemis.statetransition.events.attestation.BroadcastAggregatesEvent;
import tech.pegasys.artemis.statetransition.events.attestation.BroadcastAttestationEvent;
import tech.pegasys.artemis.statetransition.genesis.GenesisHandler;
import tech.pegasys.artemis.statetransition.util.StartupUtil;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.storage.CombinedChainDataClient;
import tech.pegasys.artemis.storage.HistoricalChainData;
import tech.pegasys.artemis.storage.Store;
import tech.pegasys.artemis.storage.api.FinalizedCheckpointEventChannel;
import tech.pegasys.artemis.storage.events.SlotEvent;
import tech.pegasys.artemis.sync.AttestationManager;
import tech.pegasys.artemis.sync.SyncService;
import tech.pegasys.artemis.sync.util.NoopSyncService;
import tech.pegasys.artemis.util.async.SafeFuture;
import tech.pegasys.artemis.util.config.ArtemisConfiguration;
import tech.pegasys.artemis.util.config.Constants;
import tech.pegasys.artemis.util.time.TimeProvider;
import tech.pegasys.artemis.util.time.Timer;
import tech.pegasys.artemis.validator.api.ValidatorApiChannel;
import tech.pegasys.artemis.validator.coordinator.BlockFactory;
import tech.pegasys.artemis.validator.coordinator.DepositProvider;
import tech.pegasys.artemis.validator.coordinator.Eth1DataCache;
import tech.pegasys.artemis.validator.coordinator.ValidatorApiHandler;
import tech.pegasys.artemis.validator.coordinator.ValidatorCoordinator;

public class BeaconChainController extends Service {
  private static final Logger LOG = LogManager.getLogger();

  private final EventChannels eventChannels;
  private final MetricsSystem metricsSystem;
  private final ArtemisConfiguration config;
  private final TimeProvider timeProvider;
  private final EventBus eventBus;
  private final boolean setupInitialState;

  private volatile Timer timer;
  private volatile ChainStorageClient chainStorageClient;
  private volatile P2PNetwork<Eth2Peer> p2pNetwork;
  private volatile SettableGauge currentSlotGauge;
  private volatile SettableGauge currentEpochGauge;
  private volatile StateProcessor stateProcessor;
  private volatile UnsignedLong nodeSlot = UnsignedLong.ZERO;
  private volatile BeaconRestApi beaconRestAPI;
  private volatile AttestationAggregator attestationAggregator;
  private volatile BlockAttestationsPool blockAttestationsPool;
  private volatile DepositProvider depositProvider;
  private volatile SyncService syncService;
  private volatile AttestationManager attestationManager;
  private volatile ValidatorCoordinator validatorCoordinator;
  private volatile CombinedChainDataClient combinedChainDataClient;
  private volatile Eth1DataCache eth1DataCache;

  public BeaconChainController(
      TimeProvider timeProvider,
      EventBus eventBus,
      EventChannels eventChannels,
      MetricsSystem metricsSystem,
      ArtemisConfiguration config) {
    this.timeProvider = timeProvider;
    this.eventBus = eventBus;
    this.eventChannels = eventChannels;
    this.config = config;
    this.metricsSystem = metricsSystem;
    this.setupInitialState = config.isInteropEnabled() || config.getInteropStartState() != null;
  }

  @Override
  protected SafeFuture<?> doStart() {
    this.eventBus.register(this);
    LOG.debug("Starting {}", this.getClass().getSimpleName());
    return initialize()
        .thenCompose(
            __ ->
                SafeFuture.allOfFailFast(
                    validatorCoordinator.start(),
                    attestationManager.start(),
                    p2pNetwork.start(),
                    syncService.start(),
                    SafeFuture.fromRunnable(timer::start),
                    SafeFuture.fromRunnable(beaconRestAPI::start)));
  }

  @Override
  protected SafeFuture<?> doStop() {
    LOG.debug("Stopping {}", this.getClass().getSimpleName());
    return SafeFuture.allOf(
        SafeFuture.fromRunnable(() -> eventBus.unregister(this)),
        SafeFuture.fromRunnable(beaconRestAPI::stop),
        SafeFuture.fromRunnable(timer::stop),
        validatorCoordinator.stop(),
        syncService.stop(),
        attestationManager.stop(),
        SafeFuture.fromRunnable(p2pNetwork::stop));
  }

  private SafeFuture<?> initialize() {
    return ChainStorageClient.storageBackedClient(eventBus)
        .thenAccept(
            client -> {
              // Setup chain storage
              this.chainStorageClient = client;
              if (setupInitialState && chainStorageClient.getStore() == null) {
                setupInitialState();
              }
              chainStorageClient.subscribeStoreInitialized(this::onStoreInitialized);
              // Init other services
              this.initAll();
            });
  }

  public void initAll() {
    initCombinedChainDataClient();
    initTimer();
    initMetrics();
    initAttestationAggregator();
    initBlockAttestationsPool();
    initDepositProvider();
    initEth1DataCache();
    initValidatorCoordinator();
    initPreGenesisDepositHandler();
    initStateProcessor();
    initAttestationPropagationManager();
    initP2PNetwork();
    initSyncManager();
    initValidatorApiHandler();
    initRestAPI();
  }

  private void initCombinedChainDataClient() {
    LOG.debug("BeaconChainController.initCombinedChainDataClient()");
    HistoricalChainData historicalChainData = new HistoricalChainData(eventBus);
    combinedChainDataClient = new CombinedChainDataClient(chainStorageClient, historicalChainData);
  }

  public void initTimer() {
    LOG.debug("BeaconChainController.initTimer()");
    int timerPeriodInMilliseconds = (int) ((1.0 / Constants.TIME_TICKER_REFRESH_RATE) * 1000);
    try {
      this.timer = new Timer(eventBus, 0, timerPeriodInMilliseconds);
    } catch (IllegalArgumentException e) {
      System.exit(1);
    }
  }

  public void initMetrics() {
    LOG.debug("BeaconChainController.initMetrics()");
    currentSlotGauge =
        SettableGauge.create(
            metricsSystem,
            ArtemisMetricCategory.BEACONCHAIN,
            "current_slot",
            "Latest slot recorded by the beacon chain");
    currentEpochGauge =
        SettableGauge.create(
            metricsSystem,
            ArtemisMetricCategory.BEACONCHAIN,
            "current_epoch",
            "Latest epoch recorded by the beacon chain");
  }

  public void initDepositProvider() {
    LOG.debug("BeaconChainController.initDepositProvider()");
    depositProvider = new DepositProvider(chainStorageClient);
    eventChannels
        .subscribe(Eth1EventsChannel.class, depositProvider)
        .subscribe(FinalizedCheckpointEventChannel.class, depositProvider);
  }

  private void initEth1DataCache() {
    LOG.debug("BeaconChainController.initEth1DataCache");
    eth1DataCache = new Eth1DataCache(eventBus, timeProvider);
  }

  public void initValidatorCoordinator() {
    LOG.debug("BeaconChainController.initValidatorCoordinator()");
    this.validatorCoordinator =
        new ValidatorCoordinator(
            timeProvider,
            eventBus,
            eventChannels.getPublisher(ValidatorApiChannel.class),
            chainStorageClient,
            attestationAggregator,
            blockAttestationsPool,
            eth1DataCache,
            config);
  }

  public void initValidatorApiHandler() {
    LOG.debug("BeaconChainController.initValidatorApiHandler()");
    final StateTransition stateTransition = new StateTransition();
    final BlockFactory blockFactory =
        new BlockFactory(
            new BlockProposalUtil(stateTransition),
            stateTransition,
            blockAttestationsPool,
            depositProvider,
            eth1DataCache);
    eventChannels.subscribe(
        ValidatorApiChannel.class, new ValidatorApiHandler(combinedChainDataClient, blockFactory));
  }

  public void initStateProcessor() {
    LOG.debug("BeaconChainController.initStateProcessor()");
    this.stateProcessor = new StateProcessor(eventBus, chainStorageClient);
  }

  private void initPreGenesisDepositHandler() {
    if (setupInitialState) {
      return;
    }
    LOG.debug("BeaconChainController.initPreGenesisDepositHandler()");
    eventChannels.subscribe(Eth1EventsChannel.class, new GenesisHandler(chainStorageClient));
  }

  private void initAttestationPropagationManager() {
    attestationManager = AttestationManager.create(eventBus, chainStorageClient);
  }

  public void initP2PNetwork() {
    LOG.debug("BeaconChainController.initP2PNetwork()");
    if (!config.isP2pEnabled()) {
      this.p2pNetwork = new MockP2PNetwork<>(eventBus);
    } else {
      final Optional<Bytes> bytes = getP2pPrivateKeyBytes();
      PrivKey pk =
          bytes.isEmpty()
              ? KeyKt.generateKeyPair(KEY_TYPE.SECP256K1).component1()
              : KeyKt.unmarshalPrivateKey(bytes.get().toArrayUnsafe());
      NetworkConfig p2pConfig =
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
              true,
              true,
              true);
      this.p2pNetwork =
          Eth2NetworkBuilder.create()
              .config(p2pConfig)
              .eventBus(eventBus)
              .chainStorageClient(chainStorageClient)
              .metricsSystem(metricsSystem)
              .timeProvider(timeProvider)
              .build();
    }
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
      LOG.info("Private key file not supplied. A private key will be generated");
      bytes = Optional.empty();
    }
    return bytes;
  }

  public void initBlockAttestationsPool() {
    LOG.debug("BeaconChainController.initBlockAttestationsPool()");
    blockAttestationsPool = new BlockAttestationsPool();
  }

  public void initAttestationAggregator() {
    LOG.debug("BeaconChainController.initAttestationAggregator()");
    attestationAggregator = new AttestationAggregator();
  }

  public void initRestAPI() {
    LOG.debug("BeaconChainController.initRestAPI()");
    DataProvider dataProvider =
        new DataProvider(
            chainStorageClient,
            combinedChainDataClient,
            p2pNetwork,
            syncService,
            eventChannels.getPublisher(ValidatorApiChannel.class),
            validatorCoordinator);
    beaconRestAPI = new BeaconRestApi(dataProvider, config);
  }

  public void initSyncManager() {
    LOG.debug("BeaconChainController.initSyncManager()");
    if (!config.isP2pEnabled()) {
      syncService = new NoopSyncService(null, null, null, null);
    } else {
      syncService =
          new SyncService(
              eventBus,
              p2pNetwork,
              chainStorageClient,
              new BlockImporter(chainStorageClient, eventBus));
    }
  }

  private void setupInitialState() {
    StartupUtil.setupInitialState(
        chainStorageClient,
        config.getInteropGenesisTime(),
        config.getInteropStartState(),
        config.getInteropNumberOfValidators());
  }

  private void onStoreInitialized() {
    UnsignedLong genesisTime = chainStorageClient.getGenesisTime();
    UnsignedLong currentTime = UnsignedLong.valueOf(System.currentTimeMillis() / 1000);
    UnsignedLong currentSlot = UnsignedLong.ZERO;
    if (currentTime.compareTo(genesisTime) > 0) {
      UnsignedLong deltaTime = currentTime.minus(genesisTime);
      currentSlot = deltaTime.dividedBy(UnsignedLong.valueOf(SECONDS_PER_SLOT));
    } else {
      UnsignedLong timeUntilGenesis = genesisTime.minus(currentTime);
      LOG.info("{} seconds until genesis.", timeUntilGenesis);
    }
    nodeSlot = currentSlot;
  }

  @Subscribe
  @SuppressWarnings("unused")
  private void onTick(Date date) {
    if (chainStorageClient.isPreGenesis() || syncService.getSyncStatus().isSyncing()) {
      return;
    }
    final UnsignedLong currentTime = UnsignedLong.valueOf(date.getTime() / 1000);
    final Store.Transaction transaction = chainStorageClient.startStoreTransaction();
    on_tick(transaction, currentTime);
    transaction.commit().join();
    final UnsignedLong nextSlotStartTime =
        chainStorageClient
            .getGenesisTime()
            .plus(nodeSlot.times(UnsignedLong.valueOf(SECONDS_PER_SLOT)));
    if (chainStorageClient.getStore().getTime().compareTo(nextSlotStartTime) >= 0) {
      processSlot();
    }
  }

  private void processSlot() {
    try {
      if (isFirstSlotOfNewEpoch(nodeSlot)) {
        EVENT_LOG.epochEvent();
      }

      this.eventBus.post(new SlotEvent(nodeSlot));
      this.currentSlotGauge.set(nodeSlot.longValue());
      this.currentEpochGauge.set(compute_epoch_at_slot(nodeSlot).longValue());
      Thread.sleep(SECONDS_PER_SLOT * 1000 / 3);
      Bytes32 headBlockRoot = this.stateProcessor.processHead();
      EVENT_LOG.slotEvent(
          nodeSlot,
          chainStorageClient.getBestSlot(),
          chainStorageClient.getStore().getJustifiedCheckpoint().getEpoch(),
          chainStorageClient.getStore().getFinalizedCheckpoint().getEpoch());
      this.eventBus.post(new BroadcastAttestationEvent(headBlockRoot, nodeSlot));
      Thread.sleep(SECONDS_PER_SLOT * 1000 / 3);
      this.eventBus.post(new BroadcastAggregatesEvent());
      nodeSlot = nodeSlot.plus(UnsignedLong.ONE);
    } catch (InterruptedException e) {
      LOG.fatal("onTick: {}", e.toString(), e);
    }
  }

  private boolean isFirstSlotOfNewEpoch(final UnsignedLong slot) {
    return slot.plus(UnsignedLong.ONE)
        .mod(UnsignedLong.valueOf(SLOTS_PER_EPOCH))
        .equals(UnsignedLong.ZERO);
  }
}
