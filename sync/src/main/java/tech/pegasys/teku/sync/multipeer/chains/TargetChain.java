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

package tech.pegasys.teku.sync.multipeer.chains;

import com.google.common.base.MoreObjects;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import tech.pegasys.teku.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.networking.p2p.peer.Peer;

public class TargetChain<P extends Peer> {
  private final SlotAndBlockRoot chainHead;
  private final Set<P> peers = new HashSet<>();

  public TargetChain(final SlotAndBlockRoot chainHead) {
    this.chainHead = chainHead;
  }

  void addPeer(final P peer) {
    peers.add(peer);
  }

  void removePeer(final P peer) {
    peers.remove(peer);
  }

  int getPeerCount() {
    return peers.size();
  }

  public Collection<P> getPeers() {
    return Collections.unmodifiableSet(peers);
  }

  public SlotAndBlockRoot getChainHead() {
    return chainHead;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final TargetChain<?> that = (TargetChain<?>) o;
    return Objects.equals(getChainHead(), that.getChainHead())
        && Objects.equals(getPeers(), that.getPeers());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getChainHead(), getPeers());
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("chainHead", chainHead)
        .add("peers", peers)
        .toString();
  }
}
