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

package tech.pegasys.artemis.networking.p2p.libp2p.discovery;

import com.google.common.base.MoreObjects;
import io.libp2p.core.multiformats.Multiaddr;
import java.util.Objects;
import tech.pegasys.artemis.networking.p2p.peer.NodeId;

public class DiscoveryPeer {
  private final NodeId nodeId;
  private final Multiaddr nodeAddress;

  public DiscoveryPeer(final NodeId nodeId, final Multiaddr nodeAddress) {
    this.nodeId = nodeId;
    this.nodeAddress = nodeAddress;
  }

  public NodeId getNodeId() {
    return nodeId;
  }

  public Multiaddr getNodeAddress() {
    return nodeAddress;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final DiscoveryPeer that = (DiscoveryPeer) o;
    return Objects.equals(nodeId, that.nodeId) &&
        Objects.equals(nodeAddress, that.nodeAddress);
  }

  @Override
  public int hashCode() {
    return Objects.hash(nodeId, nodeAddress);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("nodeId", nodeId)
        .add("nodeAddress", nodeAddress)
        .toString();
  }
}
