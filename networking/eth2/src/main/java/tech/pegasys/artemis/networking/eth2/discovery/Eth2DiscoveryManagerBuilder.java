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

package tech.pegasys.artemis.networking.eth2.discovery;

import com.google.common.eventbus.EventBus;
import io.libp2p.core.crypto.PrivKey;
import java.util.List;
import java.util.Optional;
import tech.pegasys.artemis.networking.p2p.network.P2PNetwork;

public final class Eth2DiscoveryManagerBuilder {

  // core parameters for discovery service
  private String networkInterface;
  private int port;
  // configuration of discovery service
  private List<String> peers; // for setting boot nodes
  private Optional<PrivKey> privateKey = Optional.empty(); // for generating ENR records
  // the network to potentially affect with discovered peers
  private Optional<P2PNetwork<?>> network = Optional.empty();
  // event bus by which to signal other services
  private EventBus eventBus;

  static final int SEED = 123456789;

  public Eth2DiscoveryManagerBuilder networkInterface(String networkInterface) {
    this.networkInterface = networkInterface;
    return this;
  }

  public Eth2DiscoveryManagerBuilder port(int port) {
    this.port = port;
    return this;
  }

  public Eth2DiscoveryManagerBuilder peers(List<String> peers) {
    this.peers = peers;
    return this;
  }

  public Eth2DiscoveryManagerBuilder privateKey(Optional<PrivKey> privateKey) {
    this.privateKey = privateKey;
    return this;
  }

  public Eth2DiscoveryManagerBuilder network(Optional<P2PNetwork<?>> network) {
    this.network = network;
    return this;
  }

  public Eth2DiscoveryManagerBuilder eventBus(EventBus eventBus) {
    this.eventBus = eventBus;
    return this;
  }

  public Eth2DiscoveryService buildSeeded() {
    Eth2DiscoveryService ret = build();
    ret.setRnd(SEED);
    return ret;
  }

  public Eth2DiscoveryService build() {
    Eth2DiscoveryService eth2DiscoveryService = new Eth2DiscoveryService();
    eth2DiscoveryService.setNetworkInterface(networkInterface);
    eth2DiscoveryService.setPort(port);
    eth2DiscoveryService.setPeers(peers);
    eth2DiscoveryService.setEventBus(eventBus);
    privateKey.ifPresent(eth2DiscoveryService::setPrivateKey);
    network.ifPresent(eth2DiscoveryService::setNetwork);
    return eth2DiscoveryService.build();
  }
}
