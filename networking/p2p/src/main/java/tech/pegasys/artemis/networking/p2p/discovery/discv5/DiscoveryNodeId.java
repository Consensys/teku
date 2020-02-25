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

package tech.pegasys.artemis.networking.p2p.discovery.discv5;

import io.libp2p.etc.encode.Base58;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.artemis.networking.p2p.peer.NodeId;

public class DiscoveryNodeId implements NodeId {
  private final Bytes nodeId;

  public DiscoveryNodeId(final Bytes nodeId) {
    this.nodeId = nodeId;
  }

  @Override
  public Bytes toBytes() {
    return nodeId;
  }

  @Override
  public String toBase58() {
    return Base58.INSTANCE.encode(nodeId.toArrayUnsafe());
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final DiscoveryNodeId that = (DiscoveryNodeId) o;
    return Objects.equals(nodeId, that.nodeId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(nodeId);
  }

  @Override
  public String toString() {
    return nodeId.toHexString();
  }
}
