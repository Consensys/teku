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

package tech.pegasys.teku.networking.p2p.discovery;

import com.google.common.base.MoreObjects;
import java.net.InetSocketAddress;
import java.util.Objects;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;

public class DiscoveryPeer {
  private final Bytes publicKey;
  private final InetSocketAddress nodeAddress;
  private final Optional<Bytes> enrForkId;

  public DiscoveryPeer(
      final Bytes publicKey, final InetSocketAddress nodeAddress, final Optional<Bytes> enrForkId) {
    this.publicKey = publicKey;
    this.nodeAddress = nodeAddress;
    this.enrForkId = enrForkId;
  }

  public Bytes getPublicKey() {
    return publicKey;
  }

  public InetSocketAddress getNodeAddress() {
    return nodeAddress;
  }

  public Optional<Bytes> getEnrForkId() {
    return enrForkId;
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
    return Objects.equals(publicKey, that.publicKey)
        && Objects.equals(nodeAddress, that.nodeAddress);
  }

  @Override
  public int hashCode() {
    return Objects.hash(publicKey, nodeAddress);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("publicKey", publicKey)
        .add("nodeAddress", nodeAddress)
        .toString();
  }
}
