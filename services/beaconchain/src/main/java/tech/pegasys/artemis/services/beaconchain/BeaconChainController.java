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
import static tech.pegasys.artemis.util.alogger.ALogger.STDOUT;
import static tech.pegasys.artemis.util.config.Constants.DEPOSIT_TEST;
import static tech.pegasys.artemis.util.config.Constants.SECONDS_PER_SLOT;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.common.primitives.UnsignedLong;
import io.libp2p.core.crypto.KeyKt;
import io.libp2p.core.crypto.PrivKey;
import java.util.Date;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.Level;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.artemis.beaconrestapi.BeaconRestApi;
import tech.pegasys.artemis.events.EventChannels;
import tech.pegasys.artemis.metrics.ArtemisMetricCategory;
import tech.pegasys.artemis.metrics.SettableGauge;
import tech.pegasys.artemis.networking.eth2.Eth2Network;
import tech.pegasys.artemis.networking.eth2.Eth2NetworkBuilder;
import tech.pegasys.artemis.networking.p2p.mock.MockP2PNetwork;
import tech.pegasys.artemis.networking.p2p.network.NetworkConfig;
import tech.pegasys.artemis.networking.p2p.network.P2PNetwork;
import tech.pegasys.artemis.pow.api.DepositEventChannel;
import tech.pegasys.artemis.service.serviceutils.NoopService;
import tech.pegasys.artemis.service.serviceutils.Service;
import tech.pegasys.artemis.statetransition.AttestationAggregator;
import tech.pegasys.artemis.statetransition.BlockAttestationsPool;
import tech.pegasys.artemis.statetransition.StateProcessor;
import tech.pegasys.artemis.statetransition.blockimport.BlockImporter;
import tech.pegasys.artemis.statetransition.events.BroadcastAggregatesEvent;
import tech.pegasys.artemis.statetransition.events.BroadcastAttestationEvent;
import tech.pegasys.artemis.statetransition.genesis.PreGenesisDepositHandler;
import tech.pegasys.artemis.statetransition.util.StartupUtil;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.storage.HistoricalChainData;
import tech.pegasys.artemis.storage.Store;
import tech.pegasys.artemis.storage.events.NodeStartEvent;
import tech.pegasys.artemis.storage.events.SlotEvent;
import tech.pegasys.artemis.storage.events.StoreInitializedEvent;
import tech.pegasys.artemis.sync.AttestationManager;
import tech.pegasys.artemis.sync.SyncService;
import tech.pegasys.artemis.util.alogger.ALogger;
import tech.pegasys.artemis.util.config.ArtemisConfiguration;
import tech.pegasys.artemis.util.config.Constants;
import tech.pegasys.artemis.util.time.TimeProvider;
import tech.pegasys.artemis.util.time.Timer;
import tech.pegasys.artemis.validator.coordinator.ValidatorCoordinator;

public class BeaconChainController {
  private final ExecutorService networkExecutor = Executors.newSingleThreadExecutor();
  private Runnable networkTask;
  private final EventChannels eventChannels;
  private final ArtemisConfiguration config;
  private final TimeProvider timeProvider;
  private EventBus eventBus;
  private Timer timer;
  private ChainStorageClient chainStorageClient;
  private P2PNetwork<?> p2pNetwork;
  private final MetricsSystem metricsSystem;
  private SettableGauge currentSlotGauge;
  private SettableGauge currentEpochGauge;
  private StateProcessor stateProcessor;
  private UnsignedLong nodeSlot = UnsignedLong.ZERO;
  private BeaconRestApi beaconRestAPI;
  private AttestationAggregator attestationAggregator;
  private BlockAttestationsPool blockAttestationsPool;
  private Service syncService;
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
    initValidatorCoordinator();
    initDepositHandler();
    initStateProcessor();
    initAttestationPropagationManager();
    initP2PNetwork();
    initSyncManager();
    initRestAPI();
  }

  public void initTimer() {
    STDOUT.log(Level.DEBUG, "BeaconChainController.initTimer()");
    int timerPeriodInMiliseconds = (int) ((1.0 / Constants.TIME_TICKER_REFRESH_RATE) * 1000);
    try {
      this.timer = new Timer(eventBus, 0, timerPeriodInMiliseconds);
    } catch (IllegalArgumentException e) {
      System.exit(1);
    }
  }

  public void initStorage() {
    this.chainStorageClient = ChainStorageClient.storageBackedClient(eventBus);
  }

  public void initMetrics() {
    STDOUT.log(Level.DEBUG, "BeaconChainController.initMetrics()");
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

  public void initValidatorCoordinator() {
    STDOUT.log(Level.DEBUG, "BeaconChainController.initValidatorCoordinator()");
    new ValidatorCoordinator(
        timeProvider,
        eventBus,
        chainStorageClient,
        attestationAggregator,
        blockAttestationsPool,
        config);
  }

  public void initStateProcessor() {
    STDOUT.log(Level.DEBUG, "BeaconChainController.initStateProcessor()");
    this.stateProcessor = new StateProcessor(eventBus, chainStorageClient);
  }

  private void initDepositHandler() {
    STDOUT.log(Level.DEBUG, "BeaconChainController.initDepositHandler()");
    eventChannels.subscribe(
        DepositEventChannel.class, new PreGenesisDepositHandler(config, chainStorageClient));
  }

  private void initAttestationPropagationManager() {
    attestationManager = AttestationManager.create(eventBus, chainStorageClient);
  }

  public void initP2PNetwork() {
    STDOUT.log(Level.DEBUG, "BeaconChainController.initP2PNetwork()");
    if ("mock".equals(config.getNetworkMode())) {
      this.p2pNetwork = new MockP2PNetwork(eventBus);
      this.networkTask = () -> this.p2pNetwork.start().reportExceptions();
    } else if ("jvmlibp2p".equals(config.getNetworkMode())) {
      Bytes bytes = Bytes.fromHexString(config.getInteropPrivateKey());
      Optional<PrivKey> pk =
          bytes.isEmpty()
              ? Optional.empty()
              : Optional.of(KeyKt.unmarshalPrivateKey(bytes.toArrayUnsafe()));
      NetworkConfig p2pConfig =
          new NetworkConfig(
              pk,
              config.getNetworkInterface(),
              config.getPort(),
              config.getAdvertisedPort(),
              config.getStaticPeers(),
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
      this.networkTask = () -> this.p2pNetwork.start().reportExceptions();
    } else {
      throw new IllegalArgumentException("Unsupported network mode " + config.getNetworkMode());
    }
  }

  public void initBlockAttestationsPool() {
    STDOUT.log(Level.DEBUG, "BeaconChainController.initBlockAttestationsPool()");
    blockAttestationsPool = new BlockAttestationsPool();
  }

  public void initAttestationAggregator() {
    STDOUT.log(Level.DEBUG, "BeaconChainController.initAttestationAggregator()");
    attestationAggregator = new AttestationAggregator();
  }

  public void initRestAPI() {
    STDOUT.log(Level.DEBUG, "BeaconChainController.initRestAPI()");
    beaconRestAPI =
        new BeaconRestApi(
            chainStorageClient,
            p2pNetwork,
            new HistoricalChainData(eventBus),
            config.getBeaconRestAPIPortNumber());
  }

  public void initSyncManager() {
    STDOUT.log(Level.DEBUG, "BeaconChainController.initSyncManager()");
    if ("mock".equals(config.getNetworkMode())) {
      syncService = new NoopService();
    } else {
      syncService =
          new SyncService(
              eventBus,
              (Eth2Network) p2pNetwork,
              chainStorageClient,
              new BlockImporter(chainStorageClient, eventBus));
    }
  }

  public void start() {
    STDOUT.log(
        Level.DEBUG, "BeaconChainController.start(): starting AttestationPropagationManager");
    attestationManager.start().reportExceptions();
    STDOUT.log(Level.DEBUG, "BeaconChainController.start(): starting p2pNetwork");
    networkExecutor.execute(networkTask);
    STDOUT.log(Level.DEBUG, "BeaconChainController.start(): emit NodeStartEvent");
    this.eventBus.post(new NodeStartEvent());
    STDOUT.log(Level.DEBUG, "BeaconChainController.start(): starting timer");
    this.timer.start();
    STDOUT.log(Level.DEBUG, "BeaconChainController.start(): starting BeaconRestAPI");
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
    STDOUT.log(Level.DEBUG, "BeaconChainController.stop()");
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
      STDOUT.log(Level.INFO, timeUntilGenesis + " seconds until genesis.", ALogger.Color.GREEN);
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
      STDOUT.log(Level.INFO, "******* Slot Event *******", ALogger.Color.WHITE);
      STDOUT.log(Level.INFO, "Node slot:                             " + nodeSlot);
      Thread.sleep(SECONDS_PER_SLOT * 1000 / 3);
      Bytes32 headBlockRoot = this.stateProcessor.processHead();
      // Logging
      STDOUT.log(
          Level.INFO,
          "Head block slot:" + "                       " + chainStorageClient.getBestSlot());
      STDOUT.log(
          Level.INFO,
          "Justified epoch:"
              + "                       "
              + chainStorageClient.getStore().getJustifiedCheckpoint().getEpoch());
      STDOUT.log(
          Level.INFO,
          "Finalized epoch:"
              + "                       "
              + chainStorageClient.getStore().getFinalizedCheckpoint().getEpoch());

      this.eventBus.post(new BroadcastAttestationEvent(headBlockRoot, nodeSlot));
      Thread.sleep(SECONDS_PER_SLOT * 1000 / 3);
      this.eventBus.post(new BroadcastAggregatesEvent());
      nodeSlot = nodeSlot.plus(UnsignedLong.ONE);
    } catch (InterruptedException e) {
      STDOUT.log(Level.FATAL, "onTick: " + e.toString());
    }
  }

  @Subscribe
  public void setNodeSlotAccordingToDBStore(Store store) {
    Bytes32 headBlockRoot = get_head(store);
    chainStorageClient.initializeFromStore(store, headBlockRoot);
    STDOUT.log(Level.INFO, "Node being started from database.", ALogger.Color.GREEN);
  }
}
