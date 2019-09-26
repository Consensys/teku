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

import static tech.pegasys.artemis.datastructures.Constants.SECONDS_PER_SLOT;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.compute_epoch_of_slot;
import static tech.pegasys.artemis.statetransition.util.ForkChoiceUtil.on_tick;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.common.primitives.UnsignedLong;
import io.libp2p.core.crypto.KeyKt;
import io.libp2p.core.crypto.PrivKey;
import io.vertx.core.Vertx;
import java.util.Date;
import java.util.Optional;
import org.apache.logging.log4j.Level;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.artemis.datastructures.Constants;
import tech.pegasys.artemis.metrics.ArtemisMetricCategory;
import tech.pegasys.artemis.metrics.SettableGauge;
import tech.pegasys.artemis.networking.p2p.JvmLibP2PNetwork;
import tech.pegasys.artemis.networking.p2p.MockP2PNetwork;
import tech.pegasys.artemis.networking.p2p.api.P2PNetwork;
import tech.pegasys.artemis.networking.p2p.jvmlibp2p.Config;
import tech.pegasys.artemis.statetransition.StateProcessor;
import tech.pegasys.artemis.statetransition.events.GenesisEvent;
import tech.pegasys.artemis.statetransition.events.ValidatorAssignmentEvent;
import tech.pegasys.artemis.storage.ChainStorage;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.storage.Store;
import tech.pegasys.artemis.storage.events.DBStoreValidEvent;
import tech.pegasys.artemis.storage.events.NodeStartEvent;
import tech.pegasys.artemis.storage.events.SlotEvent;
import tech.pegasys.artemis.util.alogger.ALogger;
import tech.pegasys.artemis.util.config.ArtemisConfiguration;
import tech.pegasys.artemis.util.time.Timer;
import tech.pegasys.artemis.util.time.TimerFactory;
import tech.pegasys.artemis.validator.coordinator.ValidatorCoordinator;
import tech.pegasys.pantheon.metrics.MetricsSystem;

public class BeaconChainController {
  private static final ALogger STDOUT = new ALogger("stdout");
  private final ArtemisConfiguration config;
  private EventBus eventBus;
  private Vertx vertx;
  private Timer timer;
  private ChainStorageClient chainStorageClient;
  private P2PNetwork p2pNetwork;
  private final MetricsSystem metricsSystem;
  private SettableGauge currentSlotGauge;
  private SettableGauge currentEpochGauge;
  private ValidatorCoordinator validatorCoordinator;
  private StateProcessor stateProcessor;

  private UnsignedLong nodeSlot = UnsignedLong.ZERO;

  public BeaconChainController(
      EventBus eventBus, Vertx vertx, MetricsSystem metricsSystem, ArtemisConfiguration config) {
    this.eventBus = eventBus;
    this.vertx = vertx;
    this.config = config;
    this.metricsSystem = metricsSystem;
    this.eventBus.register(this);
  }

  @SuppressWarnings("rawtypes")
  public void initTimer() {
    STDOUT.log(Level.DEBUG, "BeaconChainController.initTimer()");
    int timerPeriodInMiliseconds = (int) ((1.0 / Constants.TIME_TICKER_REFRESH_RATE) * 1000);
    try {
      this.timer =
          new TimerFactory()
              .create(
                  config.getTimer(),
                  new Object[] {this.eventBus, 0, timerPeriodInMiliseconds},
                  new Class[] {EventBus.class, Integer.class, Integer.class});
    } catch (IllegalArgumentException e) {
      System.exit(1);
    }
  }

  public void initStorage() {
    STDOUT.log(Level.DEBUG, "BeaconChainController.initStorage()");
    this.chainStorageClient = ChainStorage.Create(ChainStorageClient.class, eventBus);
    this.chainStorageClient.setGenesisTime(UnsignedLong.valueOf(config.getGenesisTime()));
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
    this.validatorCoordinator = new ValidatorCoordinator(eventBus, chainStorageClient, config);
  }

  public void initStateProcessor() {
    STDOUT.log(Level.DEBUG, "BeaconChainController.initStateProcessor()");
    this.stateProcessor = new StateProcessor(eventBus, chainStorageClient, metricsSystem, config);
  }

  public void initP2PNetwork() {
    STDOUT.log(Level.DEBUG, "BeaconChainController.initP2PNetwork()");
    if ("mock".equals(config.getNetworkMode())) {
      this.p2pNetwork = new MockP2PNetwork(eventBus);
    } else if ("jvmlibp2p".equals(config.getNetworkMode())) {
      Bytes bytes = Bytes.fromHexString(config.getInteropPrivateKey());
      PrivKey pk = KeyKt.unmarshalPrivateKey(bytes.toArrayUnsafe());

      this.p2pNetwork =
          new JvmLibP2PNetwork(
              new Config(
                  Optional.of(pk),
                  config.getNetworkInterface(),
                  config.getPort(),
                  config.getAdvertisedPort(),
                  config.getStaticPeers(),
                  true,
                  true,
                  true),
              eventBus);
    } else {
      throw new IllegalArgumentException("Unsupported network mode " + config.getNetworkMode());
    }
  }

  public void start() {
    STDOUT.log(Level.DEBUG, "BeaconChainController.start(): starting p2pNetwork");
    this.p2pNetwork.run();
    STDOUT.log(Level.DEBUG, "BeaconChainController.start(): emit NodeStartEvent");
    this.eventBus.post(new NodeStartEvent());
    STDOUT.log(Level.DEBUG, "BeaconChainController.start(): starting timer");
    this.timer.start();
  }

  public void stop() {
    this.p2pNetwork.stop();
    this.timer.stop();
    this.eventBus.unregister(this);
  }

  @Subscribe
  private void onDBStoreValidEvent(DBStoreValidEvent event) {
    final UnsignedLong slot = event.getNodeSlot();
    if (slot.compareTo(UnsignedLong.ZERO) > 0) {
      STDOUT.log(Level.DEBUG, "Restoring nodeSlot to: " + slot);
      this.nodeSlot = slot;
    } else {
      this.eventBus.post(new GenesisEvent(event.getBeaconState()));
    }
  }

  @Subscribe
  private void onTick(Date date) {
    try {
      final UnsignedLong currentTime = UnsignedLong.valueOf(date.getTime() / 1000);
      if (chainStorageClient.getStore() != null) {
        final Store.Transaction transaction = chainStorageClient.getStore().startTransaction();
        on_tick(transaction, currentTime);
        transaction.commit();
        if (chainStorageClient
                .getStore()
                .getTime()
                .compareTo(
                    chainStorageClient
                        .getGenesisTime()
                        .plus(nodeSlot.times(UnsignedLong.valueOf(SECONDS_PER_SLOT))))
            >= 0) {
          this.eventBus.post(new SlotEvent(nodeSlot));
          this.currentSlotGauge.set(nodeSlot.longValue());
          this.currentEpochGauge.set(compute_epoch_of_slot(nodeSlot).longValue());
          STDOUT.log(Level.INFO, "******* Slot Event *******", ALogger.Color.WHITE);
          STDOUT.log(Level.INFO, "Node slot:                             " + nodeSlot);
          Thread.sleep(SECONDS_PER_SLOT * 1000 / 2);
          this.eventBus.post(new ValidatorAssignmentEvent(nodeSlot));
          nodeSlot = nodeSlot.plus(UnsignedLong.ONE);
        }
      }
    } catch (InterruptedException e) {
      STDOUT.log(Level.FATAL, "onTick: " + e.toString());
    }
  }
}
