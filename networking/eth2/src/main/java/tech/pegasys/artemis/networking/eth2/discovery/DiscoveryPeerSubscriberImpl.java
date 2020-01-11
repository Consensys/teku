/*
 * Copyright 2020 ConsenSys AG.
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

import com.google.common.eventbus.Subscribe;
import io.libp2p.etc.encode.Base58;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.artemis.networking.p2p.network.P2PNetwork;
import tech.pegasys.artemis.util.async.SafeFuture;

public class DiscoveryPeerSubscriberImpl implements DiscoveryPeerSubscriber {

  Logger logger = LogManager.getLogger();

  private P2PNetwork<?> network;

  public DiscoveryPeerSubscriberImpl(P2PNetwork<?> network) {
    this.network = network;
  }

  @Subscribe
  @Override
  public void onDiscovery(DiscoveryPeer discoveryPeer) {
    String d = Base58.INSTANCE.encode(discoveryPeer.getNodeId().toArray());
    String connectString =
        "/ip4/"
            + discoveryPeer.getAddress().getHostAddress()
            + "/tcp/"
            + (discoveryPeer.getUdpPort().isPresent() ? discoveryPeer.getUdpPort().get() : "0")
            + "/p2p/"
            + d;
    SafeFuture<?> connect = network.connect(connectString);
    if (connect != null) {
      connect.finish(
          r -> {
            logger.info("discv5 connected to:" + connectString);
          },
          t -> {
            logger.error("discv5 connect failed: " + t.toString());
          });
    } else {
      logger.error(
          "connect failed with null, is this a mock? If so, the repo check expects a safe future to be handled, before being able to to test the mock");
    }
  }
}
