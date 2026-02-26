/*
 * Copyright Consensys Software Inc., 2026
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

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBitvectorSchema;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryPeer;

public class StubPeerScorer implements PeerScorer {

  record SubnetSubscriptionsKey(
      SszBitvector attestationSubscriptions,
      SszBitvector syncCommitteeSubnetSubscriptions,
      SszBitvector dataColumnSidecarSubnetSubscriptions) {}

  private final Object2IntMap<PeerId> peerScores = new Object2IntOpenHashMap<>();
  private final Object2IntMap<SubnetSubscriptionsKey> candidateScores =
      new Object2IntOpenHashMap<>();

  public void setScore(final PeerId peerId, final int score) {
    peerScores.put(peerId, score);
  }

  public void setScore(
      final SszBitvector attestationSubscriptions,
      final SszBitvector syncCommitteeSubnetSubscriptions,
      final int score) {
    candidateScores.put(
        new SubnetSubscriptionsKey(
            attestationSubscriptions,
            syncCommitteeSubnetSubscriptions,
            SszBitvectorSchema.create(128).getDefault()),
        score);
  }

  @Override
  public int scoreExistingPeer(final PeerId peerId) {
    return peerScores.getOrDefault(peerId, 0);
  }

  @Override
  public List<DiscoveryPeer> selectCandidatePeers(
      final List<DiscoveryPeer> candidates, final int maxToSelect) {
    return candidates.stream()
        .sorted(
            Comparator.comparing(
                    (DiscoveryPeer candidate) -> scoreCandidatePeer(candidate, Map.of()))
                .reversed())
        .limit(maxToSelect)
        .toList();
  }

  public int scoreCandidatePeer(
      final DiscoveryPeer candidate, final Map<Integer, Integer> selectedDataColumnSidecarSubnets) {
    return candidateScores.getOrDefault(
        new StubPeerScorer.SubnetSubscriptionsKey(
            candidate.getPersistentAttestationSubnets(),
            candidate.getSyncCommitteeSubnets(),
            SszBitvectorSchema.create(128).getDefault()),
        0);
  }
}
