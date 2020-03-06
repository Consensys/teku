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
import static tech.pegasys.artemis.statetransition.util.ForkChoiceUtil.get_head;
import static tech.pegasys.artemis.statetransition.util.ForkChoiceUtil.on_tick;
import static tech.pegasys.artemis.util.config.Constants.DEPOSIT_TEST;
import static tech.pegasys.artemis.util.config.Constants.SECONDS_PER_SLOT;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.common.primitives.UnsignedLong;
import io.libp2p.core.crypto.KEY_TYPE;
import io.libp2p.core.crypto.KeyKt;
import io.libp2p.core.crypto.PrivKey;
import java.util.Date;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.artemis.beaconrestapi.BeaconRestApi;
import tech.pegasys.artemis.events.EventChannels;
import tech.pegasys.artemis.metrics.ArtemisMetricCategory;
import tech.pegasys.artemis.metrics.SettableGauge;
import tech.pegasys.artemis.networking.eth2.Eth2NetworkBuilder;
import tech.pegasys.artemis.networking.eth2.peers.Eth2Peer;
import tech.pegasys.artemis.networking.p2p.mock.MockP2PNetwork;
import tech.pegasys.artemis.networking.p2p.network.NetworkConfig;
import tech.pegasys.artemis.networking.p2p.network.P2PNetwork;
import tech.pegasys.artemis.pow.api.Eth1EventsChannel;
import tech.pegasys.artemis.statetransition.AttestationAggregator;
import tech.pegasys.artemis.statetransition.BlockAttestationsPool;
import tech.pegasys.artemis.statetransition.StateProcessor;
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
import tech.pegasys.artemis.storage.events.NodeStartEvent;
import tech.pegasys.artemis.storage.events.SlotEvent;
import tech.pegasys.artemis.storage.events.StoreInitializedEvent;
import tech.pegasys.artemis.sync.AttestationManager;
import tech.pegasys.artemis.sync.SyncService;
import tech.pegasys.artemis.sync.util.NoopSyncService;
import tech.pegasys.artemis.util.config.ArtemisConfiguration;
import tech.pegasys.artemis.util.config.Constants;
import tech.pegasys.artemis.util.time.TimeProvider;
import tech.pegasys.artemis.util.time.Timer;
import tech.pegasys.artemis.validator.coordinator.DepositProvider;
import tech.pegasys.artemis.validator.coordinator.ValidatorCoordinator;
import tech.pegasys.teku.logging.StatusLogger;

public class BeaconChainController {

  private static final Logger LOG = LogManager.getLogger();
  private static final StatusLogger STATUS_LOG = StatusLogger.getLogger();

  private final ExecutorService networkExecutor = Executors.newSingleThreadExecutor();
  private final EventChannels eventChannels;
  private final ArtemisConfiguration config;
  private final TimeProvider timeProvider;
  private EventBus eventBus;
  private Timer timer;
  private ChainStorageClient chainStorageClient;
  private P2PNetwork<Eth2Peer> p2pNetwork;
  private final MetricsSystem metricsSystem;
  private SettableGauge currentSlotGauge;
  private SettableGauge currentEpochGauge;
  private StateProcessor stateProcessor;
  private UnsignedLong nodeSlot = UnsignedLong.ZERO;
  private BeaconRestApi beaconRestAPI;
  private AttestationAggregator attestationAggregator;
  private BlockAttestationsPool blockAttestationsPool;
  private DepositProvider depositProvider;
  private SyncService syncService;
  private boolean testMode;
  private AttestationManager attestationManager;

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
    this.testMode = config.getDepositMode().equals(DEPOSIT_TEST);
    this.eventBus.register(this);
  }

  public void initAll() {
    initTimer();
    initStorage();
    initMetrics();
    initAttestationAggregator();
    initBlockAttestationsPool();
    initDepositProvider();
    initValidatorCoordinator();
    initPreGenesisDepositHandler();
    initStateProcessor();
    initAttestationPropagationManager();
    initP2PNetwork();
    initSyncManager();
    initRestAPI();
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

  public void initStorage() {
    this.chainStorageClient = ChainStorageClient.storageBackedClient(eventBus);
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

  public void initValidatorCoordinator() {
    LOG.debug("BeaconChainController.initValidatorCoordinator()");
    new ValidatorCoordinator(
        timeProvider,
        eventBus,
        chainStorageClient,
        attestationAggregator,
        blockAttestationsPool,
        depositProvider,
        config);
  }

  public void initStateProcessor() {
    LOG.debug("BeaconChainController.initStateProcessor()");
    this.stateProcessor = new StateProcessor(eventBus, chainStorageClient);
  }

  private void initPreGenesisDepositHandler() {
    LOG.debug("BeaconChainController.initPreGenesisDepositHandler()");
    eventChannels.subscribe(Eth1EventsChannel.class, new GenesisHandler(chainStorageClient));
  }

  private void initAttestationPropagationManager() {
    attestationManager = AttestationManager.create(eventBus, chainStorageClient);
  }

  public void initP2PNetwork() {
    LOG.debug("BeaconChainController.initP2PNetwork()");
    if ("mock".equals(config.getNetworkMode())) {
      this.p2pNetwork = new MockP2PNetwork<>(eventBus);
    } else if ("jvmlibp2p".equals(config.getNetworkMode())) {
      Bytes bytes = Bytes.fromHexString(config.getInteropPrivateKey());
      PrivKey pk =
          bytes.isEmpty()
              ? KeyKt.generateKeyPair(KEY_TYPE.SECP256K1).component1()
              : KeyKt.unmarshalPrivateKey(bytes.toArrayUnsafe());
      NetworkConfig p2pConfig =
          new NetworkConfig(
              pk,
              config.getNetworkInterface(),
              config.getPort(),
              config.getAdvertisedPort(),
              config.getStaticPeers(),
              config.getDiscovery(),
              config.getBootnodes(),
              true,
              true,
              true);
      this.p2pNetwork =
          Eth2NetworkBuilder.create()
              .config(p2pConfig)
              .eventBus(eventBus)
              .chainStorageClient(chainStorageClient)
              .metricsSystem(metricsSystem)
              .build();
    } else {
      throw new IllegalArgumentException("Unsupported network mode " + config.getNetworkMode());
    }
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
    HistoricalChainData historicalChainData = new HistoricalChainData(eventBus);
    CombinedChainDataClient combinedChainDataClient =
        new CombinedChainDataClient(chainStorageClient, historicalChainData);
    beaconRestAPI =
        new BeaconRestApi(
            chainStorageClient, p2pNetwork, combinedChainDataClient, syncService, config);
  }

  public void initSyncManager() {
    LOG.debug("BeaconChainController.initSyncManager()");
    if ("mock".equals(config.getNetworkMode())) {
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

  public void start() {
    LOG.debug("BeaconChainController.start(): starting AttestationPropagationManager");
    attestationManager.start().reportExceptions();
    LOG.debug("BeaconChainController.start(): starting p2pNetwork");
    this.p2pNetwork.start().reportExceptions();
    LOG.debug("BeaconChainController.start(): emit NodeStartEvent");
    this.eventBus.post(new NodeStartEvent());
    LOG.debug("BeaconChainController.start(): starting timer");
    this.timer.start();
    LOG.debug("BeaconChainController.start(): starting BeaconRestAPI");
    this.beaconRestAPI.start();

    if (testMode && !config.startFromDisk()) {
      generateTestModeGenesis();
    }

    syncService.start().reportExceptions();
  }

  private void generateTestModeGenesis() {
    StartupUtil.setupInitialState(
        chainStorageClient,
        config.getGenesisTime(),
        config.getStartState(),
        config.getNumValidators());
  }

  public void stop() {
    LOG.debug("BeaconChainController.stop()");
    syncService.stop().reportExceptions();
    attestationManager.stop().reportExceptions();
    if (!Objects.isNull(p2pNetwork)) {
      this.p2pNetwork.stop();
    }
    networkExecutor.shutdown();
    try {
      if (!networkExecutor.awaitTermination(250, TimeUnit.MILLISECONDS)) {
        networkExecutor.shutdownNow();
      }
    } catch (InterruptedException e) {
      networkExecutor.shutdownNow();
    }
    this.timer.stop();
    this.beaconRestAPI.stop();
    this.eventBus.unregister(this);
  }

  @Subscribe
  @SuppressWarnings("unused")
  private void onStoreInitializedEvent(final StoreInitializedEvent event) {
    UnsignedLong genesisTime = chainStorageClient.getGenesisTime();
    UnsignedLong currentTime = UnsignedLong.valueOf(System.currentTimeMillis() / 1000);
    UnsignedLong currentSlot = UnsignedLong.ZERO;
    if (currentTime.compareTo(genesisTime) > 0) {
      UnsignedLong deltaTime = currentTime.minus(genesisTime);
      currentSlot = deltaTime.dividedBy(UnsignedLong.valueOf(SECONDS_PER_SLOT));
    } else {
      UnsignedLong timeUntilGenesis = genesisTime.minus(currentTime);
      LOG.info(" {}seconds until genesis.", timeUntilGenesis);
    }
    nodeSlot = currentSlot;
  }

  @Subscribe
  @SuppressWarnings("unused")
  private void onTick(Date date) {
    if (chainStorageClient.isPreGenesis()) {
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
      this.eventBus.post(new SlotEvent(nodeSlot));
      this.currentSlotGauge.set(nodeSlot.longValue());
      this.currentEpochGauge.set(compute_epoch_at_slot(nodeSlot).longValue());
      Thread.sleep(SECONDS_PER_SLOT * 1000 / 3);
      Bytes32 headBlockRoot = this.stateProcessor.processHead();
      STATUS_LOG.slotEvent(
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

  @Subscribe
  public void setNodeSlotAccordingToDBStore(Store store) {
    Bytes32 headBlockRoot = get_head(store);
    chainStorageClient.initializeFromStore(store, headBlockRoot);
    LOG.info("Node being started from database.");
  }
}
