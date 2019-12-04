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

package tech.pegasys.artemis.networking.p2p.jvmlibp2p;

import io.libp2p.core.PeerId;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.artemis.networking.p2p.peer.NodeId;

public class LibP2PNodeId implements NodeId {
  private final PeerId peerId;

  public LibP2PNodeId(final PeerId peerId) {
    this.peerId = peerId;
  }

  @Override
  public Bytes toBytes() {
    return Bytes.wrap(peerId.getBytes());
  }

  @Override
  public String toBase58() {
    return peerId.toBase58();
  }

  @Override
  public String toString() {
    return toBase58();
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof LibP2PNodeId)) {
      return false;
    }
    final LibP2PNodeId that = (LibP2PNodeId) o;
    return Objects.equals(peerId, that.peerId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(peerId);
  }
}
