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
import org.apache.tuweni.plumtree.MessageSender;
import org.apache.tuweni.plumtree.State;
import tech.pegasys.artemis.networking.p2p.hobbits.gossip.GossipMessage;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.util.alogger.ALogger;

/** TCP persistent connection handler for hobbits messages. */
public class PlumtreeSocketHandler extends SocketHandler {
  private static final ALogger STDOUT = new ALogger("stdout");

  public PlumtreeSocketHandler(
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
  protected void handleGossipMessage(GossipMessage gossipMessage) {
    if (MessageSender.Verb.GOSSIP.ordinal() == gossipMessage.method()) {
      // String key = gossipMessage.body().toHexString();
      // if (!receivedMessages.containsKey(key)) {
      peer.setPeerGossip(gossipMessage.body());
      p2pState.receiveGossipMessage(
          peer, gossipMessage.getAttributes(), gossipMessage.body(), gossipMessage.messageHash());
      // receivedMessages.put(key, true);
      // }
    } else if (MessageSender.Verb.PRUNE.ordinal() == gossipMessage.method()) {
      p2pState.receivePruneMessage(peer);
    } else if (MessageSender.Verb.GRAFT.ordinal() == gossipMessage.method()) {
      p2pState.receiveGraftMessage(peer, gossipMessage.messageHash());
    } else if (MessageSender.Verb.IHAVE.ordinal() == gossipMessage.method()) {
      p2pState.receiveIHaveMessage(peer, gossipMessage.messageHash());
    } else {
      throw new UnsupportedOperationException(gossipMessage.method() + " is not supported");
    }
  }
}
