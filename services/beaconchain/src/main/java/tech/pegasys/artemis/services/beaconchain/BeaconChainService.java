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

import com.google.common.eventbus.EventBus;
import com.google.common.primitives.UnsignedLong;
import io.vertx.core.Vertx;
import java.util.Optional;
import org.apache.logging.log4j.Level;
import tech.pegasys.artemis.datastructures.Constants;
import tech.pegasys.artemis.networking.p2p.HobbitsP2PNetwork;
import tech.pegasys.artemis.networking.p2p.JvmLibP2PNetwork;
import tech.pegasys.artemis.networking.p2p.MockP2PNetwork;
import tech.pegasys.artemis.networking.p2p.MothraP2PNetwork;
import tech.pegasys.artemis.networking.p2p.api.P2PNetwork;
import tech.pegasys.artemis.networking.p2p.jvmlibp2p.JvmLibp2pConfig;
import tech.pegasys.artemis.service.serviceutils.ServiceConfig;
import tech.pegasys.artemis.service.serviceutils.ServiceInterface;
import tech.pegasys.artemis.statetransition.StateProcessor;
import tech.pegasys.artemis.statetransition.TimingProcessor;
import tech.pegasys.artemis.storage.ChainStorage;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.storage.events.NodeStartEvent;
import tech.pegasys.artemis.util.alogger.ALogger;
import tech.pegasys.artemis.util.time.Timer;
import tech.pegasys.artemis.util.time.TimerFactory;
import tech.pegasys.artemis.validator.coordinator.ValidatorCoordinator;

public class BeaconChainService implements ServiceInterface {
  private static final ALogger STDOUT = new ALogger(BeaconChainService.class.getName());
  private EventBus eventBus;
  private Timer timer;
  private Vertx vertx;
  private ChainStorageClient store;
  private P2PNetwork p2pNetwork;

  public BeaconChainService() {}

  @Override
  @SuppressWarnings("rawtypes")
  public void init(ServiceConfig config) {
    this.eventBus = config.getEventBus();
    this.eventBus.register(this);
    this.vertx = config.getVertx();
    int timerPeriodInMiliseconds = (int) ((1.0 / Constants.TIME_TICKER_REFRESH_RATE) * 1000);
    try {
      this.timer =
          new TimerFactory()
              .create(
                  config.getConfig().getTimer(),
                  new Object[] {this.eventBus, 0, timerPeriodInMiliseconds},
                  new Class[] {EventBus.class, Integer.class, Integer.class});
    } catch (IllegalArgumentException e) {
      System.exit(1);
    }
    this.store = ChainStorage.Create(ChainStorageClient.class, eventBus);
    this.store.setGenesisTime(UnsignedLong.valueOf(config.getConfig().getInteropGenesisTime()));
    new TimingProcessor(config, store);
    new ValidatorCoordinator(config, store);
    new StateProcessor(config, store);
    if ("mock".equals(config.getConfig().getNetworkMode())) {
      this.p2pNetwork = new MockP2PNetwork(eventBus);
    } else if ("hobbits".equals(config.getConfig().getNetworkMode())) {
      P2PNetwork.GossipProtocol gossipProtocol;
      switch (config.getConfig().getGossipProtocol()) {
        case "floodsub":
          gossipProtocol = P2PNetwork.GossipProtocol.FLOODSUB;
          break;
        case "gossipsub":
          gossipProtocol = P2PNetwork.GossipProtocol.GOSSIPSUB;
          break;
        case "plumtree":
          gossipProtocol = P2PNetwork.GossipProtocol.PLUMTREE;
          break;
        case "none":
          gossipProtocol = P2PNetwork.GossipProtocol.NONE;
          break;
        default:
          gossipProtocol = P2PNetwork.GossipProtocol.PLUMTREE;
      }

      this.p2pNetwork =
          new HobbitsP2PNetwork(
              eventBus,
              vertx,
              store,
              config.getConfig().getPort(),
              config.getConfig().getAdvertisedPort(),
              config.getConfig().getNetworkInterface(),
              config.getConfig().getStaticHobbitsPeers(),
              gossipProtocol);
    } else if ("mothra".equals(config.getConfig().getNetworkMode())) {
      this.p2pNetwork =
          new MothraP2PNetwork(
              eventBus,
              store,
              config.getConfig().getPort(),
              config.getConfig().getNetworkInterface(),
              config.getConfig().getIdentity(),
              config.getConfig().getBootnodes(),
              config.getConfig().isBootnode(),
              config.getConfig().getDiscovery(),
              config.getConfig().getStaticMothraPeers());
      if (config.getConfig().getDiscovery().equals("discv5")) {
        // TODO - issue #827:
        //      Once i have a reliable way to be notified when
        //      libp2p peers are found then this can be removed
        try {
          Thread.sleep(15000);
        } catch (InterruptedException e) {
          STDOUT.log(Level.ERROR, e.getMessage());
        }
      }
    } else if ("jvmlibp2p".equals(config.getConfig().getNetworkMode())) {
      this.p2pNetwork =
          new JvmLibP2PNetwork(
              new JvmLibp2pConfig(
                  Optional.empty(),
                  config.getConfig().getNetworkInterface(),
                  config.getConfig().getPort(),
                  config.getConfig().getAdvertisedPort(),
                  config.getConfig().getStaticMothraPeers(),
                  true,
                  true,
                  true),
              eventBus);
    } else {
      throw new IllegalArgumentException(
          "Unsupported network mode " + config.getConfig().getNetworkMode());
    }

    this.eventBus.post(new NodeStartEvent());
    this.timer.start();
  }

  @Override
  public void run() {
    // Start p2p adapter
    this.p2pNetwork.run();
  }

  @Override
  public void stop() {
    this.p2pNetwork.stop();
    this.timer.stop();
    this.eventBus.unregister(this);
  }

  P2PNetwork p2pNetwork() {
    return p2pNetwork;
  }
}
