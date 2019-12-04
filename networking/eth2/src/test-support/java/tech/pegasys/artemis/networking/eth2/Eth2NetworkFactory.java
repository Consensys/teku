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

package tech.pegasys.artemis.networking.eth2;

import java.util.Collection;
import tech.pegasys.artemis.network.p2p.jvmlibp2p.P2PNetworkFactory;
import tech.pegasys.artemis.networking.eth2.Eth2NetworkFactory.Eth2P2PNetworkBuilder;
import tech.pegasys.artemis.networking.eth2.peers.Eth2PeerManager;
import tech.pegasys.artemis.networking.p2p.JvmLibP2PNetwork;
import tech.pegasys.artemis.networking.p2p.NetworkConfig;
import tech.pegasys.artemis.networking.p2p.api.P2PNetwork;
import tech.pegasys.artemis.networking.p2p.jvmlibp2p.Protocol;

public class Eth2NetworkFactory extends P2PNetworkFactory<Eth2Network, Eth2P2PNetworkBuilder> {

  @Override
  public Eth2P2PNetworkBuilder builder() {
    return new Eth2P2PNetworkBuilder();
  }

  public class Eth2P2PNetworkBuilder
      extends P2PNetworkFactory<Eth2Network, Eth2P2PNetworkBuilder>.NetworkBuilder {

    @Override
    protected Eth2Network buildNetwork(final NetworkConfig config) {
      {
        // Setup eth2 handlers
        final Eth2PeerManager eth2PeerManager =
            new Eth2PeerManager(chainStorageClient, METRICS_SYSTEM);
        final Collection<? extends Protocol<?>> eth2Protocols =
            eth2PeerManager.getRpcMethods().all();
        protocols.addAll(eth2Protocols);
        peerHandlers.add(eth2PeerManager);

        final P2PNetwork network =
            new JvmLibP2PNetwork(
                config, eventBus, chainStorageClient, METRICS_SYSTEM, protocols, peerHandlers);

        return new Eth2Network(network, eth2PeerManager);
      }
    }
  }
}
