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

package tech.pegasys.teku.networking.p2p.connection;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import tech.pegasys.teku.networking.p2p.peer.NodeId;

public class PeerSources {
  private final Map<NodeId, PeerSource> knownSources = new ConcurrentHashMap<>();

  public void recordPeerSource(final NodeId nodeId, final PeerSource peerSource) {
    knownSources.put(nodeId, peerSource);
  }

  public void forgetPeer(final NodeId nodeId) {
    knownSources.remove(nodeId);
  }

  public PeerSource getSource(final NodeId nodeId) {
    return knownSources.getOrDefault(nodeId, PeerSource.SELECTED_BY_SCORE);
  }

  public enum PeerSource {
    SELECTED_BY_SCORE,
    RANDOMLY_SELECTED,
    STATIC_PEER
  }
}
