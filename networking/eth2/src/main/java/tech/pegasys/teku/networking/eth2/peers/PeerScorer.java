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

package tech.pegasys.teku.networking.eth2.peers;

import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryPeer;
import tech.pegasys.teku.networking.p2p.peer.NodeId;
import tech.pegasys.teku.networking.p2p.peer.Peer;

public interface PeerScorer {

  int scoreExistingPeer(NodeId peerId);

  default int scoreExistingPeer(final Peer peer) {
    return scoreExistingPeer(peer.getId());
  }

  int scoreCandidatePeer(
      final SszBitvector attSubnetSubscriptions,
      final SszBitvector syncCommitteeSubnetSubscriptions);

  default int scoreCandidatePeer(final DiscoveryPeer candidate) {
    return scoreCandidatePeer(
        candidate.getPersistentAttestationSubnets(), candidate.getSyncCommitteeSubnets());
  }
}
