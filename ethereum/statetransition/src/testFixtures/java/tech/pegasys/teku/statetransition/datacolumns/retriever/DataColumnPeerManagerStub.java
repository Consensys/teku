/*
 * Copyright Consensys Software Inc., 2024
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

package tech.pegasys.teku.statetransition.datacolumns.retriever;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.tuweni.units.bigints.UInt256;

public class DataColumnPeerManagerStub implements DataColumnPeerManager {

  private final Set<UInt256> bannedNodes = new HashSet<>();
  private final List<PeerListener> listeners = new ArrayList<>();

  @Override
  public void addPeerListener(PeerListener listener) {
    listeners.add(listener);
  }

  @Override
  public void banNode(UInt256 node) {
    bannedNodes.add(node);
  }

  public Set<UInt256> getBannedNodes() {
    return bannedNodes;
  }

  public void addNode(UInt256 nodeId) {
    listeners.forEach(l -> l.peerConnected(nodeId));
  }

  public void removeNode(UInt256 nodeId) {
    listeners.forEach(l -> l.peerDisconnected(nodeId));
  }
}
