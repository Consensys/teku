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
import com.google.common.base.Objects;
import java.net.InetSocketAddress;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.datastructures.networking.libp2p.rpc.EnrForkId;
import tech.pegasys.teku.ssz.SSZTypes.Bitvector;

public class DiscoveryPeer {
  private final Bytes publicKey;
  private final InetSocketAddress nodeAddress;
  private final Optional<EnrForkId> enrForkId;
  private final Bitvector persistentSubnets;

  public DiscoveryPeer(
      final Bytes publicKey,
      final InetSocketAddress nodeAddress,
      final Optional<EnrForkId> enrForkId,
      final Bitvector persistentSubnets) {
    this.publicKey = publicKey;
    this.nodeAddress = nodeAddress;
    this.enrForkId = enrForkId;
    this.persistentSubnets = persistentSubnets;
  }

  public Bytes getPublicKey() {
    return publicKey;
  }

  public InetSocketAddress getNodeAddress() {
    return nodeAddress;
  }

  public Optional<EnrForkId> getEnrForkId() {
    return enrForkId;
  }

  public Bitvector getPersistentSubnets() {
    return persistentSubnets;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof DiscoveryPeer)) return false;
    DiscoveryPeer that = (DiscoveryPeer) o;
    return Objects.equal(getPublicKey(), that.getPublicKey())
        && Objects.equal(getNodeAddress(), that.getNodeAddress())
        && Objects.equal(getEnrForkId(), that.getEnrForkId())
        && Objects.equal(getPersistentSubnets(), that.getPersistentSubnets());
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(
        getPublicKey(), getNodeAddress(), getEnrForkId(), getPersistentSubnets());
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("publicKey", publicKey)
        .add("nodeAddress", nodeAddress)
        .add("enrForkId", enrForkId)
        .add("persistentSubnets", persistentSubnets)
        .toString();
  }
}
