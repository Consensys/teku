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

package tech.pegasys.artemis.networking.p2p.hobbits;

import com.google.common.eventbus.EventBus;
import io.vertx.core.net.NetSocket;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.plumtree.MessageSender;
import org.apache.tuweni.plumtree.State;
import tech.pegasys.artemis.networking.p2p.hobbits.gossip.GossipMessage;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.util.alogger.ALogger;

/** TCP persistent connection handler for hobbits messages. */
public final class FloodsubSocketHandler extends AbstractSocketHandler {
  private static final ALogger STDOUT = new ALogger("stdout");

  public FloodsubSocketHandler(
      EventBus eventBus,
      NetSocket netSocket,
      String userAgent,
      Peer peer,
      ChainStorageClient store,
      State p2pState,
      ConcurrentHashMap<String, Boolean> receivedMessages) {
    super(eventBus, netSocket, userAgent, peer, store, p2pState, receivedMessages);
  }

  @Override
  @SuppressWarnings("StringSplitter")
  protected void handleGossipMessage(GossipMessage gossipMessage) {
    if (MessageSender.Verb.GOSSIP.ordinal() == gossipMessage.method()) {
      String key = gossipMessage.body().toHexString();
      if (!receivedMessages.containsKey(key)) {
        peer.setPeerGossip(gossipMessage.body());
        String[] attributes = gossipMessage.getAttributes().split(",");
        if (attributes[0].equalsIgnoreCase("ATTESTATION")) {
          Bytes32 root = Bytes32.wrap(gossipMessage.body());
          this.sendGetAttestations(root);
        } else if (attributes[0].equalsIgnoreCase("BLOCK")) {
          Bytes32 root = Bytes32.wrap(gossipMessage.body());
          this.sendGetBlockBodies(root);
        }
        receivedMessages.put(key, true);
      }
    }
  }
}
