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

package tech.pegasys.artemis.network.p2p.jvmlibp2p;

import tech.pegasys.artemis.network.p2p.jvmlibp2p.JvmLibP2PNetworkFactory.JvmLibP2PNetworkBuilder;
import tech.pegasys.artemis.networking.p2p.JvmLibP2PNetwork;
import tech.pegasys.artemis.networking.p2p.NetworkConfig;

public class JvmLibP2PNetworkFactory
    extends P2PNetworkFactory<JvmLibP2PNetwork, JvmLibP2PNetworkBuilder> {

  @Override
  public JvmLibP2PNetworkBuilder builder() {
    return new JvmLibP2PNetworkBuilder();
  }

  public class JvmLibP2PNetworkBuilder
      extends P2PNetworkFactory<JvmLibP2PNetwork, JvmLibP2PNetworkBuilder>.NetworkBuilder {

    @Override
    protected JvmLibP2PNetwork buildNetwork(final NetworkConfig config) {
      {
        return new JvmLibP2PNetwork(
            config, eventBus, chainStorageClient, METRICS_SYSTEM, protocols, peerHandlers);
      }
    }
  }
}
