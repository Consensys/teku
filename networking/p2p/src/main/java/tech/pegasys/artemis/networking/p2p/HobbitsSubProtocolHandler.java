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

package tech.pegasys.artemis.networking.p2p;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import io.vertx.core.buffer.Buffer;
import java.net.URI;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.consensys.cava.bytes.Bytes;
import net.consensys.cava.bytes.Bytes32;
import net.consensys.cava.concurrent.AsyncCompletion;
import net.consensys.cava.rlpx.RLPxService;
import net.consensys.cava.rlpx.wire.DisconnectReason;
import net.consensys.cava.rlpx.wire.SubProtocolHandler;
import tech.pegasys.artemis.data.TimeSeriesRecord;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.networking.p2p.hobbits.GossipMethod;
import tech.pegasys.artemis.networking.p2p.hobbits.HobbitsSocketHandler;
import tech.pegasys.artemis.networking.p2p.hobbits.Peer;

final class HobbitsSubProtocolHandler implements SubProtocolHandler {

  private final Map<String, HobbitsSocketHandler> handlerMap = new ConcurrentHashMap<>();
  private final RLPxService service;
  private final EventBus eventBus;
  private final String userAgent;
  private final TimeSeriesRecord chainData;

  HobbitsSubProtocolHandler(
      RLPxService service, EventBus eventBus, String userAgent, TimeSeriesRecord chainData) {
    this.service = service;
    this.eventBus = eventBus;
    this.userAgent = userAgent;
    this.chainData = chainData;
    eventBus.register(this);
  }

  @Override
  public AsyncCompletion handle(String connectionId, int messageType, Bytes message) {
    HobbitsSocketHandler handler = handlerMap.get(connectionId);
    handler.handleMessage(Buffer.buffer(message.toArrayUnsafe()));
    return AsyncCompletion.completed();
  }

  @Override
  public AsyncCompletion handleNewPeerConnection(String connectionId) {
    Peer peer = new Peer(URI.create("hob+rlpx://" + connectionId));
    handlerMap.computeIfAbsent(
        connectionId,
        (id) ->
            new HobbitsSocketHandler(
                eventBus,
                userAgent,
                peer,
                chainData,
                bytes -> service.send(HobbitsSubProtocol.BEACON_ID, 1, id, bytes),
                () -> service.disconnect(id, DisconnectReason.CLIENT_QUITTING)));
    return AsyncCompletion.completed();
  }

  @Override
  public AsyncCompletion stop() {
    return AsyncCompletion.completed();
  }

  @Subscribe
  public void onNewUnprocessedBlock(BeaconBlock block) {
    for (HobbitsSocketHandler handler : handlerMap.values()) {
      // TODO: implement messageHash and signature
      handler.gossipMessage(
          GossipMethod.GOSSIP, Bytes32.random(), Bytes32.random(), block.toBytes());
    }
  }

  Collection<HobbitsSocketHandler> handlers() {
    return handlerMap.values();
  }
}
