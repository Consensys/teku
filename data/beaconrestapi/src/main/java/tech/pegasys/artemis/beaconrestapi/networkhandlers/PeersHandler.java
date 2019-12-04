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

package tech.pegasys.artemis.beaconrestapi.networkhandlers;

import java.util.stream.Collectors;
import tech.pegasys.artemis.beaconrestapi.handlerinterfaces.BeaconRestApiHandler;
import tech.pegasys.artemis.networking.p2p.api.P2PNetwork;
import tech.pegasys.artemis.networking.p2p.peer.NodeId;
import tech.pegasys.artemis.networking.p2p.peer.Peer;

public class PeersHandler implements BeaconRestApiHandler {

  private final P2PNetwork network;

  public PeersHandler(P2PNetwork network) {
    this.network = network;
  }

  @Override
  public String getPath() {
    return "/network/peers";
  }

  @Override
  public Object handleRequest(RequestParams param) {
    return network
        .streamPeers()
        .map(Peer::getId)
        .map(NodeId::toBase58)
        .collect(Collectors.toList());
  }
}
