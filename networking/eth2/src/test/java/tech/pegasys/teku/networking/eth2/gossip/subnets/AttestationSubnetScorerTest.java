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

package tech.pegasys.teku.networking.eth2.gossip.subnets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.networking.eth2.peers.PeerScorer;
import tech.pegasys.teku.networking.p2p.mock.MockNodeId;
import tech.pegasys.teku.networking.p2p.peer.NodeId;
import tech.pegasys.teku.ssz.SSZTypes.Bitvector;
import tech.pegasys.teku.util.config.Constants;

class AttestationSubnetScorerTest {
  @Test
  void shouldScoreCandidatePeerWithNoSubnetsAsZero() {
    final AttestationSubnetScorer scorer =
        AttestationSubnetScorer.create(new PeerSubnetSubscriptions.Builder().build());
    assertThat(scorer.scoreCandidatePeer(new Bitvector(Constants.ATTESTATION_SUBNET_COUNT)))
        .isZero();
  }

  @Test
  void shouldScoreExistingPeerWithNoSubnetsAsZero() {
    final AttestationSubnetScorer scorer =
        AttestationSubnetScorer.create(new PeerSubnetSubscriptions.Builder().build());
    assertThat(scorer.scoreExistingPeer(new MockNodeId(1))).isZero();
  }

  @Test
  void shouldScoreExistingPeersOnSubnetsWithFewPeersMoreHighly() {
    final MockNodeId node1 = new MockNodeId(0);
    final MockNodeId node2 = new MockNodeId(1);
    final MockNodeId node3 = new MockNodeId(2);
    final MockNodeId node4 = new MockNodeId(3);
    final MockNodeId node5 = new MockNodeId(4);
    final AttestationSubnetScorer scorer =
        AttestationSubnetScorer.create(
            new PeerSubnetSubscriptions.Builder()
                // Subnet 1
                .addSubscriber(1, node1)
                .addSubscriber(1, node2)
                .addSubscriber(1, node3)
                .addSubscriber(1, node4)

                // Subnet 2
                .addSubscriber(2, node1)
                .addSubscriber(2, node2)

                // Subnet 3
                .addSubscriber(3, node3)

                // Subnet 4
                .addSubscriber(4, node1)
                .addSubscriber(4, node4)
                .build());

    assertExistingPeerScores(
        scorer,
        entry(node1, 562),
        entry(node2, 312),
        entry(node3, 1062),
        entry(node4, 312),
        entry(node5, 0));
  }

  @Test
  void shouldScoreCandidatePeersOnSubnetsWithFewPeersMoreHighly() {
    final MockNodeId node1 = new MockNodeId(0);
    final MockNodeId node2 = new MockNodeId(1);
    final MockNodeId node3 = new MockNodeId(2);
    final AttestationSubnetScorer scorer =
        AttestationSubnetScorer.create(
            new PeerSubnetSubscriptions.Builder()
                // Subnet 1
                .addSubscriber(1, node1)
                .addSubscriber(1, node2)
                .addSubscriber(1, node3)

                // Subnet 2
                .addSubscriber(2, node2)

                // No subscribers for subnet 3

                // Subnet 4
                .addSubscriber(4, node3)
                .build());

    assertCandidatePeerScores(
        scorer,
        entry(candidateWithSubnets(1, 2, 4), 562),
        entry(candidateWithSubnets(1, 2), 312),
        entry(candidateWithSubnets(1, 3), 1062),
        entry(candidateWithSubnets(1, 4), 312),
        entry(candidateWithSubnets(), 0),
        entry(candidateWithSubnets(5), 1000));
  }

  @SafeVarargs
  private void assertExistingPeerScores(
      final PeerScorer scorer, final Map.Entry<NodeId, Integer>... expected) {
    final Map<NodeId, Integer> actual =
        Stream.of(expected)
            .map(Map.Entry::getKey)
            .collect(Collectors.toMap(Function.identity(), scorer::scoreExistingPeer));
    assertThat(actual).contains(expected);
  }

  @SafeVarargs
  private void assertCandidatePeerScores(
      final PeerScorer scorer, final Map.Entry<Bitvector, Integer>... expected) {
    final Map<Bitvector, Integer> actual =
        Stream.of(expected)
            .map(Map.Entry::getKey)
            .collect(Collectors.toMap(Function.identity(), scorer::scoreCandidatePeer));
    assertThat(actual).contains(expected);
  }

  private Bitvector candidateWithSubnets(final int... subnetIds) {
    final Bitvector persistentSubnets = new Bitvector(Constants.ATTESTATION_SUBNET_COUNT);
    IntStream.of(subnetIds).forEach(persistentSubnets::setBit);
    return persistentSubnets;
  }
}
