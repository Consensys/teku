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

import java.util.HashMap;
import java.util.Map;
import org.apache.commons.lang3.tuple.Pair;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.networking.p2p.peer.NodeId;

public class StubPeerScorer implements PeerScorer {
  private final Map<NodeId, Integer> peerScores = new HashMap<>();
  private final Map<Pair<SszBitvector, SszBitvector>, Integer> candidateScores = new HashMap<>();

  public void setScore(final NodeId peerId, final int score) {
    peerScores.put(peerId, score);
  }

  public void setScore(
      final SszBitvector attestationSubscriptions,
      final SszBitvector syncCommitteeSubnetSubscriptions,
      final int score) {
    candidateScores.put(Pair.of(attestationSubscriptions, syncCommitteeSubnetSubscriptions), score);
  }

  @Override
  public int scoreExistingPeer(final NodeId peerId) {
    return peerScores.getOrDefault(peerId, 0);
  }

  @Override
  public int scoreCandidatePeer(
      final SszBitvector attestationSubscriptions,
      final SszBitvector syncCommitteeSubnetSubscriptions) {
    return candidateScores.getOrDefault(
        Pair.of(attestationSubscriptions, syncCommitteeSubnetSubscriptions), 0);
  }
}
