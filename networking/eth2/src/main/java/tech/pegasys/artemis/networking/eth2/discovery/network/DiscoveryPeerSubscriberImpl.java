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

package tech.pegasys.artemis.networking.eth2.discovery.network;

import com.google.common.eventbus.Subscribe;
import io.libp2p.core.PeerId;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.artemis.networking.p2p.libp2p.LibP2PNodeId;
import tech.pegasys.artemis.networking.p2p.network.P2PNetwork;
import tech.pegasys.artemis.networking.p2p.peer.NodeId;
import tech.pegasys.artemis.util.async.SafeFuture;

public class DiscoveryPeerSubscriberImpl implements DiscoveryPeerSubscriber {

  Logger logger = LogManager.getLogger();

  private final P2PNetwork<?> network;

  public DiscoveryPeerSubscriberImpl(final P2PNetwork<?> network) {
    this.network = network;
  }

  @Subscribe
  @Override
  public void onDiscovery(DiscoveryPeer discoveryPeer) {
    logger.debug("DiscoveryPeer subscriber notified");
//    if (network.getPeer(new LibP2PNodeId(new PeerId(discoveryPeer.getNodeId().toArray()))).isPresent()) {
//      logger.debug("Discovery peer already exists in nodetable");
//      return;
//    }
//    final String connectString =
//        "/ip4/"
//            + discoveryPeer.getAddress().getHostAddress()
//            + "/tcp/"
//            + discoveryPeer.getUdpPort()
//            + "/p2p/"
//            + discoveryPeer.getNodeIdString();
//    final SafeFuture<?> connect = network.connect(connectString);
//    if (connect != null) {
//      connect.finish(
//          r -> {
//            logger.info("discv5 connected to:" + connectString);
//          },
//          t -> {
//            logger.error("discv5 connect failed: " + t.toString());
//          });
//    } else {
//      logger.error(
//          "connect failed with null, is this a mock? If so, the repo check expects a safe future to be handled, before being able to to test the mock");
//    }
  }
}
