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

package tech.pegasys.teku.networking.eth2.gossip.subnets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.networking.p2p.discovery.discv5.DiscV5Service.DEFAULT_NODE_RECORD_CONVERTER;

import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntLists;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBitvectorSchema;
import tech.pegasys.teku.networking.eth2.peers.PeerId;
import tech.pegasys.teku.networking.eth2.peers.PeerScorer;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryPeer;
import tech.pegasys.teku.networking.p2p.mock.MockNodeId;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;

class SubnetScorerTest {
  private final Spec spec = TestSpecFactory.createMinimalAltair();
  private final SchemaDefinitions schemaDefinitions = spec.getGenesisSchemaDefinitions();
  private static final int DATA_COLUMN_SIDECAR_SUBNET_COUNT = 128;

  @Test
  void shouldScoreCandidatePeerWithNoSubnetsAsZero() {
    final SubnetScorer scorer =
        SubnetScorer.create(
            PeerSubnetSubscriptions.createEmpty(
                () -> schemaDefinitions,
                SszBitvectorSchema.create(DATA_COLUMN_SIDECAR_SUBNET_COUNT)));
    assertThat(
            scorer.scoreCandidatePeer(
                createDiscoveryPeer(
                    schemaDefinitions.getAttnetsENRFieldSchema().getDefault(),
                    schemaDefinitions.getSyncnetsENRFieldSchema().getDefault()),
                new SubnetScorer.SelectedCandidateSubnetCountChanges()))
        .isZero();
  }

  @Test
  void shouldScoreExistingPeerWithNoSubnetsAsZero() {
    final SubnetScorer scorer =
        SubnetScorer.create(
            PeerSubnetSubscriptions.createEmpty(
                () -> schemaDefinitions,
                SszBitvectorSchema.create(DATA_COLUMN_SIDECAR_SUBNET_COUNT)));
    assertThat(scorer.scoreExistingPeer(PeerId.fromExistingId(new MockNodeId(1)))).isZero();
  }

  @Test
  void shouldScoreExistingPeersOnSubnetsWithFewPeersMoreHighly() {
    final PeerId node1 = PeerId.fromExistingId(new MockNodeId(0));
    final PeerId node2 = PeerId.fromExistingId(new MockNodeId(1));
    final PeerId node3 = PeerId.fromExistingId(new MockNodeId(2));
    final PeerId node4 = PeerId.fromExistingId(new MockNodeId(3));
    final PeerId node5 = PeerId.fromExistingId(new MockNodeId(4));
    final SubnetScorer scorer =
        SubnetScorer.create(
            PeerSubnetSubscriptions.builder(
                    () -> schemaDefinitions,
                    SszBitvectorSchema.create(DATA_COLUMN_SIDECAR_SUBNET_COUNT))
                .attestationSubnetSubscriptions(
                    b ->
                        b.addRelevantSubnet(1)
                            .addRelevantSubnet(2)
                            .addRelevantSubnet(3)
                            .addRelevantSubnet(4)

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

                            // Irrelevant subnet
                            .addSubscriber(5, node2))
                .syncCommitteeSubnetSubscriptions(
                    b ->
                        b.addRelevantSubnet(3)
                            // Subnet 4
                            .addSubscriber(3, node1)
                            .addSubscriber(3, node4)
                            // Irrelevant subnet
                            .addSubscriber(2, node3))
                .build());

    assertExistingPeerScores(
        scorer,
        entry(node1, 265),
        entry(node2, 140),
        entry(node3, 1015),
        entry(node4, 140),
        entry(node5, 0));
  }

  @Test
  void shouldScoreCandidatePeersOnSubnetsWithFewPeersMoreHighly() {
    final PeerId node1 = PeerId.fromExistingId(new MockNodeId(0));
    final PeerId node2 = PeerId.fromExistingId(new MockNodeId(1));
    final PeerId node3 = PeerId.fromExistingId(new MockNodeId(2));
    final SubnetScorer scorer =
        SubnetScorer.create(
            PeerSubnetSubscriptions.builder(
                    () -> schemaDefinitions,
                    SszBitvectorSchema.create(DATA_COLUMN_SIDECAR_SUBNET_COUNT))
                .attestationSubnetSubscriptions(
                    b ->
                        b.addRelevantSubnet(1)
                            .addRelevantSubnet(2)
                            .addRelevantSubnet(3)
                            .addRelevantSubnet(5)

                            // Subnet 1
                            .addSubscriber(1, node2)

                            // No subscribers for subnet 2

                            // Subnet 3
                            .addSubscriber(3, node3))
                .syncCommitteeSubnetSubscriptions(
                    b ->
                        b
                            // Tracked subnets
                            .addRelevantSubnet(1)
                            .addRelevantSubnet(2)

                            // Subnet 1
                            .addSubscriber(1, node1)
                            .addSubscriber(1, node2)
                            .addSubscriber(1, node3))
                .nodeIdToDataColumnSidecarSubnetsCalculator(
                    NodeIdToDataColumnSidecarSubnetsCalculator.NOOP)
                .build());

    assertCandidatePeerScores(
        scorer,
        entry(candidateWithSubnets(IntList.of(1, 3), IntList.of(1)), 265),
        entry(candidateWithSubnets(IntList.of(1), IntList.of(1)), 140),
        entry(candidateWithSubnets(IntList.of(2), IntList.of(1)), 1015),
        entry(candidateWithSubnets(IntList.of(3), IntList.of(1)), 140),
        entry(candidateWithSubnets(IntLists.emptyList(), IntLists.emptyList()), 0),
        entry(candidateWithSubnets(IntList.of(5), IntLists.emptyList()), 1000),
        entry(candidateWithSubnets(IntList.of(4), IntLists.emptyList()), 0),
        entry(candidateWithSubnets(IntLists.emptyList(), IntList.of(2)), 1000),
        entry(candidateWithSubnets(IntLists.emptyList(), IntList.of(3)), 0));
  }

  @Test
  void scoreCandidatePeer_shouldReturnZeroForPeerWithNoRelevantSubnets() {
    final SubnetScorer scorer =
        SubnetScorer.create(
            PeerSubnetSubscriptions.builder(
                    () -> schemaDefinitions,
                    SszBitvectorSchema.create(DATA_COLUMN_SIDECAR_SUBNET_COUNT))
                .attestationSubnetSubscriptions(b -> b.addRelevantSubnet(1).addRelevantSubnet(2))
                .syncCommitteeSubnetSubscriptions(b -> b.addRelevantSubnet(1))
                .nodeIdToDataColumnSidecarSubnetsCalculator(
                    NodeIdToDataColumnSidecarSubnetsCalculator.NOOP)
                .build());

    // Candidate with subnets that are not relevant
    final DiscoveryPeer candidate =
        createDiscoveryPeer(
            schemaDefinitions.getAttnetsENRFieldSchema().ofBits(IntList.of(3, 4)),
            schemaDefinitions.getSyncnetsENRFieldSchema().ofBits(IntList.of(2)));

    assertThat(
            scorer.scoreCandidatePeer(
                candidate, new SubnetScorer.SelectedCandidateSubnetCountChanges()))
        .isZero();
  }

  @Test
  void scoreCandidatePeer_shouldScoreHigherForUniqueSubnets() {
    final SubnetScorer scorer =
        SubnetScorer.create(
            PeerSubnetSubscriptions.builder(
                    () -> schemaDefinitions,
                    SszBitvectorSchema.create(DATA_COLUMN_SIDECAR_SUBNET_COUNT))
                .attestationSubnetSubscriptions(
                    b -> b.addRelevantSubnet(1).addRelevantSubnet(2).addRelevantSubnet(3))
                .syncCommitteeSubnetSubscriptions(b -> b.addRelevantSubnet(1))
                .nodeIdToDataColumnSidecarSubnetsCalculator(
                    NodeIdToDataColumnSidecarSubnetsCalculator.NOOP)
                .build());

    // Candidate with unique attestation subnets (no existing subscribers)
    final DiscoveryPeer candidateWithUniqueSubnet =
        createDiscoveryPeer(
            schemaDefinitions.getAttnetsENRFieldSchema().ofBits(IntList.of(1)),
            schemaDefinitions.getSyncnetsENRFieldSchema().getDefault());

    // Candidate with multiple unique attestation subnets
    final DiscoveryPeer candidateWithMultipleUniqueSubnets =
        createDiscoveryPeer(
            schemaDefinitions.getAttnetsENRFieldSchema().ofBits(IntList.of(1, 2, 3)),
            schemaDefinitions.getSyncnetsENRFieldSchema().getDefault());

    final int singleSubnetScore =
        scorer.scoreCandidatePeer(
            candidateWithUniqueSubnet, new SubnetScorer.SelectedCandidateSubnetCountChanges());
    final int multipleSubnetsScore =
        scorer.scoreCandidatePeer(
            candidateWithMultipleUniqueSubnets,
            new SubnetScorer.SelectedCandidateSubnetCountChanges());

    assertThat(multipleSubnetsScore).isGreaterThan(singleSubnetScore);
    assertThat(singleSubnetScore).isEqualTo(1000); // MAX_SUBNET_SCORE / (1^3)
    assertThat(multipleSubnetsScore).isEqualTo(3000); // 3 * 1000
  }

  @Test
  void scoreCandidatePeer_shouldConsiderSubnetCountChanges() {
    final PeerId node1 = PeerId.fromExistingId(new MockNodeId(0));
    final SubnetScorer scorer =
        SubnetScorer.create(
            PeerSubnetSubscriptions.builder(
                    () -> schemaDefinitions,
                    SszBitvectorSchema.create(DATA_COLUMN_SIDECAR_SUBNET_COUNT))
                .attestationSubnetSubscriptions(b -> b.addRelevantSubnet(1).addSubscriber(1, node1))
                .syncCommitteeSubnetSubscriptions(b -> b.addRelevantSubnet(1))
                .nodeIdToDataColumnSidecarSubnetsCalculator(
                    NodeIdToDataColumnSidecarSubnetsCalculator.NOOP)
                .build());

    final DiscoveryPeer candidate =
        createDiscoveryPeer(
            schemaDefinitions.getAttnetsENRFieldSchema().ofBits(IntList.of(1)),
            schemaDefinitions.getSyncnetsENRFieldSchema().getDefault());

    // Score without subnet changes (1 existing subscriber on subnet 1)
    final int scoreWithoutChanges =
        scorer.scoreCandidatePeer(
            candidate, new SubnetScorer.SelectedCandidateSubnetCountChanges());

    // Score with subnet changes (simulate 2 additional subscribers on subnet 1)
    final SubnetScorer.SelectedCandidateSubnetCountChanges changes =
        new SubnetScorer.SelectedCandidateSubnetCountChanges();
    changes.increment(SubnetScorer.SubnetType.ATTESTATION, 1);
    changes.increment(SubnetScorer.SubnetType.ATTESTATION, 1);
    final int scoreWithChanges = scorer.scoreCandidatePeer(candidate, changes);

    // Score should be lower with more subscribers (less valuable)
    assertThat(scoreWithChanges).isLessThan(scoreWithoutChanges);
    assertThat(scoreWithoutChanges).isEqualTo(125); // MAX_SUBNET_SCORE / (2^3)
    assertThat(scoreWithChanges).isEqualTo(15); // MAX_SUBNET_SCORE / (4^3) = 1000 / 64 = 15
  }

  @Test
  void scoreCandidatePeer_shouldScoreAttestationAndSyncCommitteeSubnetsCombined() {
    final PeerId node1 = PeerId.fromExistingId(new MockNodeId(0));
    final SubnetScorer scorer =
        SubnetScorer.create(
            PeerSubnetSubscriptions.builder(
                    () -> schemaDefinitions,
                    SszBitvectorSchema.create(DATA_COLUMN_SIDECAR_SUBNET_COUNT))
                .attestationSubnetSubscriptions(b -> b.addRelevantSubnet(1).addSubscriber(1, node1))
                .syncCommitteeSubnetSubscriptions(
                    b -> b.addRelevantSubnet(2).addSubscriber(2, node1))
                .nodeIdToDataColumnSidecarSubnetsCalculator(
                    NodeIdToDataColumnSidecarSubnetsCalculator.NOOP)
                .build());

    final DiscoveryPeer candidateWithBoth =
        createDiscoveryPeer(
            schemaDefinitions.getAttnetsENRFieldSchema().ofBits(IntList.of(1)),
            schemaDefinitions.getSyncnetsENRFieldSchema().ofBits(IntList.of(2)));

    final DiscoveryPeer candidateWithAttestationOnly =
        createDiscoveryPeer(
            schemaDefinitions.getAttnetsENRFieldSchema().ofBits(IntList.of(1)),
            schemaDefinitions.getSyncnetsENRFieldSchema().getDefault());

    final DiscoveryPeer candidateWithSyncCommitteeOnly =
        createDiscoveryPeer(
            schemaDefinitions.getAttnetsENRFieldSchema().getDefault(),
            schemaDefinitions.getSyncnetsENRFieldSchema().ofBits(IntList.of(2)));

    final int scoreBoth =
        scorer.scoreCandidatePeer(
            candidateWithBoth, new SubnetScorer.SelectedCandidateSubnetCountChanges());
    final int scoreAttestationOnly =
        scorer.scoreCandidatePeer(
            candidateWithAttestationOnly, new SubnetScorer.SelectedCandidateSubnetCountChanges());
    final int scoreSyncCommitteeOnly =
        scorer.scoreCandidatePeer(
            candidateWithSyncCommitteeOnly, new SubnetScorer.SelectedCandidateSubnetCountChanges());

    // Combined score should equal sum of individual scores
    assertThat(scoreBoth).isEqualTo(scoreAttestationOnly + scoreSyncCommitteeOnly);
    assertThat(scoreBoth).isEqualTo(250); // 125 + 125
  }

  @Test
  void scoreCandidatePeer_shouldUseOverloadedMethodWithSszBitvectors() {
    final SubnetScorer scorer =
        SubnetScorer.create(
            PeerSubnetSubscriptions.builder(
                    () -> schemaDefinitions,
                    SszBitvectorSchema.create(DATA_COLUMN_SIDECAR_SUBNET_COUNT))
                .attestationSubnetSubscriptions(b -> b.addRelevantSubnet(1).addRelevantSubnet(2))
                .syncCommitteeSubnetSubscriptions(b -> b.addRelevantSubnet(1))
                .nodeIdToDataColumnSidecarSubnetsCalculator(
                    NodeIdToDataColumnSidecarSubnetsCalculator.NOOP)
                .build());

    final SszBitvector attSubnets =
        schemaDefinitions.getAttnetsENRFieldSchema().ofBits(IntList.of(1, 2));
    final SszBitvector syncSubnets =
        schemaDefinitions.getSyncnetsENRFieldSchema().ofBits(IntList.of(1));
    final SszBitvector dataColumnSubnets =
        SszBitvectorSchema.create(DATA_COLUMN_SIDECAR_SUBNET_COUNT).getDefault();

    final int score =
        scorer.scoreCandidatePeer(
            attSubnets,
            syncSubnets,
            dataColumnSubnets,
            new SubnetScorer.SelectedCandidateSubnetCountChanges());

    // Expected score: 2 attestation subnets (1000 each) + 1 sync committee subnet (1000)
    assertThat(score).isEqualTo(3000);
  }

  @Test
  void scoreCandidatePeer_shouldScoreLowerWithMoreExistingSubscribers() {
    final PeerId node1 = PeerId.fromExistingId(new MockNodeId(0));
    final PeerId node2 = PeerId.fromExistingId(new MockNodeId(1));
    final PeerId node3 = PeerId.fromExistingId(new MockNodeId(2));
    final SubnetScorer scorer =
        SubnetScorer.create(
            PeerSubnetSubscriptions.builder(
                    () -> schemaDefinitions,
                    SszBitvectorSchema.create(DATA_COLUMN_SIDECAR_SUBNET_COUNT))
                .attestationSubnetSubscriptions(
                    b ->
                        b.addRelevantSubnet(1)
                            .addRelevantSubnet(2)
                            .addSubscriber(2, node1)
                            .addSubscriber(2, node2)
                            .addSubscriber(2, node3))
                .syncCommitteeSubnetSubscriptions(b -> {})
                .nodeIdToDataColumnSidecarSubnetsCalculator(
                    NodeIdToDataColumnSidecarSubnetsCalculator.NOOP)
                .build());

    final DiscoveryPeer candidateForSubnet1 =
        createDiscoveryPeer(
            schemaDefinitions.getAttnetsENRFieldSchema().ofBits(IntList.of(1)),
            schemaDefinitions.getSyncnetsENRFieldSchema().getDefault());

    final DiscoveryPeer candidateForSubnet2 =
        createDiscoveryPeer(
            schemaDefinitions.getAttnetsENRFieldSchema().ofBits(IntList.of(2)),
            schemaDefinitions.getSyncnetsENRFieldSchema().getDefault());

    final int scoreForSubnet1WithNoSubscribers =
        scorer.scoreCandidatePeer(
            candidateForSubnet1, new SubnetScorer.SelectedCandidateSubnetCountChanges());
    final int scoreForSubnet2WithThreeSubscribers =
        scorer.scoreCandidatePeer(
            candidateForSubnet2, new SubnetScorer.SelectedCandidateSubnetCountChanges());

    // Subnet 1 (no existing subscribers): 1000 / (1^3) = 1000
    // Subnet 2 (3 existing subscribers): 1000 / (4^3) = 15
    assertThat(scoreForSubnet1WithNoSubscribers).isEqualTo(1000);
    assertThat(scoreForSubnet2WithThreeSubscribers).isEqualTo(15);
    assertThat(scoreForSubnet1WithNoSubscribers).isGreaterThan(scoreForSubnet2WithThreeSubscribers);
  }

  @Test
  void shouldScoreDataColumnSidecarSubnets() {
    final PeerId existingPeer = PeerId.fromExistingId(new MockNodeId(0));
    final SubnetScorer scorer =
        SubnetScorer.create(
            PeerSubnetSubscriptions.builder(
                    () -> schemaDefinitions,
                    SszBitvectorSchema.create(DATA_COLUMN_SIDECAR_SUBNET_COUNT))
                .dataColumnSidecarSubnetSubscriptions(
                    b -> b.addRelevantSubnet(0).addRelevantSubnet(1).addSubscriber(1, existingPeer))
                .nodeIdToDataColumnSidecarSubnetsCalculator(
                    (peerId, custodySubnetCount) ->
                        Optional.of(
                            SszBitvectorSchema.create(DATA_COLUMN_SIDECAR_SUBNET_COUNT)
                                .ofBits(IntList.of(0))))
                .build());

    final DiscoveryPeer candidate =
        createDiscoveryPeer(
            schemaDefinitions.getAttnetsENRFieldSchema().getDefault(),
            schemaDefinitions.getSyncnetsENRFieldSchema().getDefault());

    final int score =
        scorer.scoreCandidatePeer(
            candidate, new SubnetScorer.SelectedCandidateSubnetCountChanges());

    // Should score for data column subnet 0 (no existing subscribers): 1000
    assertThat(score).isEqualTo(1000);
  }

  @Test
  void selectCandidatePeers_shouldSelectHighestScoringPeers() {
    final SubnetScorer scorer =
        SubnetScorer.create(
            PeerSubnetSubscriptions.builder(
                    () -> schemaDefinitions,
                    SszBitvectorSchema.create(DATA_COLUMN_SIDECAR_SUBNET_COUNT))
                .attestationSubnetSubscriptions(b -> b.addRelevantSubnet(1))
                .nodeIdToDataColumnSidecarSubnetsCalculator(
                    NodeIdToDataColumnSidecarSubnetsCalculator.NOOP)
                .build());

    final List<DiscoveryPeer> candidates =
        List.of(
            createDiscoveryPeer(
                schemaDefinitions
                    .getAttnetsENRFieldSchema()
                    .getDefault(), // No relevant subnets - low score
                schemaDefinitions.getSyncnetsENRFieldSchema().getDefault()),
            createDiscoveryPeer(
                schemaDefinitions
                    .getAttnetsENRFieldSchema()
                    .ofBits(IntList.of(1)), // Has relevant subnet - high score
                schemaDefinitions.getSyncnetsENRFieldSchema().getDefault()));

    final List<DiscoveryPeer> selected = scorer.selectCandidatePeers(candidates, 1);

    assertThat(selected).hasSize(1);
    assertThat(selected.getFirst())
        .isEqualTo(candidates.get(1)); // Should select the higher-scoring peer
  }

  @Test
  void selectCandidatePeers_shouldRespectMaxToSelectLimit() {
    final SubnetScorer scorer =
        SubnetScorer.create(
            PeerSubnetSubscriptions.createEmpty(
                () -> schemaDefinitions,
                SszBitvectorSchema.create(DATA_COLUMN_SIDECAR_SUBNET_COUNT)));

    final List<DiscoveryPeer> candidates =
        List.of(
            createDiscoveryPeer(
                schemaDefinitions.getAttnetsENRFieldSchema().getDefault(),
                schemaDefinitions.getSyncnetsENRFieldSchema().getDefault()),
            createDiscoveryPeer(
                schemaDefinitions.getAttnetsENRFieldSchema().getDefault(),
                schemaDefinitions.getSyncnetsENRFieldSchema().getDefault()));

    final List<DiscoveryPeer> selected = scorer.selectCandidatePeers(candidates, 1);
    assertThat(selected).hasSize(1);
  }

  @Test
  void scoreCandidatePeer_shouldHandleEmptySubnets() {
    final SubnetScorer scorer =
        SubnetScorer.create(
            PeerSubnetSubscriptions.createEmpty(
                () -> schemaDefinitions,
                SszBitvectorSchema.create(DATA_COLUMN_SIDECAR_SUBNET_COUNT)));

    final SszBitvector emptyAttSubnets = schemaDefinitions.getAttnetsENRFieldSchema().getDefault();
    final SszBitvector emptySyncSubnets =
        schemaDefinitions.getSyncnetsENRFieldSchema().getDefault();
    final SszBitvector emptyDataColumnSubnets =
        SszBitvectorSchema.create(DATA_COLUMN_SIDECAR_SUBNET_COUNT).getDefault();

    assertThat(
            scorer.scoreCandidatePeer(
                emptyAttSubnets,
                emptySyncSubnets,
                emptyDataColumnSubnets,
                new SubnetScorer.SelectedCandidateSubnetCountChanges()))
        .isZero();
  }

  @Test
  void selectCandidatePeers_shouldHandleEmptyList() {
    final SubnetScorer scorer =
        SubnetScorer.create(
            PeerSubnetSubscriptions.createEmpty(
                () -> schemaDefinitions,
                SszBitvectorSchema.create(DATA_COLUMN_SIDECAR_SUBNET_COUNT)));

    final List<DiscoveryPeer> selected = scorer.selectCandidatePeers(List.of(), 5);
    assertThat(selected).isEmpty();
  }

  @Test
  void shouldCacheDataColumnSubnetCalculations() {
    final NodeIdToDataColumnSidecarSubnetsCalculator calculator =
        mock(NodeIdToDataColumnSidecarSubnetsCalculator.class);

    final SszBitvector expectedSubnets =
        SszBitvectorSchema.create(DATA_COLUMN_SIDECAR_SUBNET_COUNT).ofBits(IntList.of(1));

    when(calculator.calculateSubnets(any(), any())).thenReturn(Optional.of(expectedSubnets));

    final SubnetScorer scorer =
        SubnetScorer.create(
            PeerSubnetSubscriptions.builder(
                    () -> schemaDefinitions,
                    SszBitvectorSchema.create(DATA_COLUMN_SIDECAR_SUBNET_COUNT))
                .nodeIdToDataColumnSidecarSubnetsCalculator(calculator)
                .build());

    final DiscoveryPeer candidate =
        createDiscoveryPeer(
            schemaDefinitions.getAttnetsENRFieldSchema().getDefault(),
            schemaDefinitions.getSyncnetsENRFieldSchema().getDefault());

    // Score multiple times - should only calculate subnets for a peer once due to caching
    scorer.scoreCandidatePeer(candidate, new SubnetScorer.SelectedCandidateSubnetCountChanges());
    scorer.scoreCandidatePeer(candidate, new SubnetScorer.SelectedCandidateSubnetCountChanges());

    verify(calculator, times(1)).calculateSubnets(any(), any());
  }

  @Test
  void subnetCounts_shouldTrackCorrectly() {
    final SubnetScorer.SubnetCounts counts = new SubnetScorer.SubnetCounts();

    assertThat(counts.get(1)).isZero();
    counts.increment(1);
    assertThat(counts.get(1)).isEqualTo(1);
    counts.increment(1);
    assertThat(counts.get(1)).isEqualTo(2);
  }

  @Test
  void selectedCandidateSubnetCountChanges_shouldTrackAllSubnetTypes() {
    final SubnetScorer.SelectedCandidateSubnetCountChanges changes =
        new SubnetScorer.SelectedCandidateSubnetCountChanges();

    changes.increment(SubnetScorer.SubnetType.ATTESTATION, 1);
    changes.increment(SubnetScorer.SubnetType.SYNC_COMMITTEE, 2);
    changes.increment(SubnetScorer.SubnetType.DATA_COLUMN_SIDECAR, 3);

    assertThat(changes.get(SubnetScorer.SubnetType.ATTESTATION, 1)).isEqualTo(1);
    assertThat(changes.get(SubnetScorer.SubnetType.SYNC_COMMITTEE, 2)).isEqualTo(1);
    assertThat(changes.get(SubnetScorer.SubnetType.DATA_COLUMN_SIDECAR, 3)).isEqualTo(1);
  }

  @Test
  void shouldScoreWithAllSubnetTypesAndChanges() {
    final PeerId existingPeer = PeerId.fromExistingId(new MockNodeId(0));
    final SubnetScorer scorer =
        SubnetScorer.create(
            PeerSubnetSubscriptions.builder(
                    () -> schemaDefinitions,
                    SszBitvectorSchema.create(DATA_COLUMN_SIDECAR_SUBNET_COUNT))
                .attestationSubnetSubscriptions(
                    b -> b.addRelevantSubnet(1).addSubscriber(1, existingPeer))
                .syncCommitteeSubnetSubscriptions(
                    b -> b.addRelevantSubnet(1).addSubscriber(1, existingPeer))
                .dataColumnSidecarSubnetSubscriptions(
                    b -> b.addRelevantSubnet(1).addSubscriber(1, existingPeer))
                .build());

    final SubnetScorer.SelectedCandidateSubnetCountChanges changes =
        new SubnetScorer.SelectedCandidateSubnetCountChanges();
    changes.increment(SubnetScorer.SubnetType.ATTESTATION, 1);
    changes.increment(SubnetScorer.SubnetType.SYNC_COMMITTEE, 1);
    changes.increment(SubnetScorer.SubnetType.DATA_COLUMN_SIDECAR, 1);

    final int score =
        scorer.scoreCandidatePeer(
            schemaDefinitions.getAttnetsENRFieldSchema().ofBits(IntList.of(1)),
            schemaDefinitions.getSyncnetsENRFieldSchema().ofBits(IntList.of(1)),
            SszBitvectorSchema.create(DATA_COLUMN_SIDECAR_SUBNET_COUNT).ofBits(IntList.of(1)),
            changes);

    // With 1 existing + 1 change = 2 total subscribers per subnet per type
    // Score should be 3 * (1000 / (3^3)) = 3 * 37 = 111
    assertThat(score).isEqualTo(111);
  }

  @Test
  void selectCandidatePeers_shouldUpdateSubnetChangesCorrectly() {
    final SubnetScorer scorer =
        SubnetScorer.create(
            PeerSubnetSubscriptions.builder(
                    () -> schemaDefinitions,
                    SszBitvectorSchema.create(DATA_COLUMN_SIDECAR_SUBNET_COUNT))
                .attestationSubnetSubscriptions(b -> b.addRelevantSubnet(1).addRelevantSubnet(2))
                .syncCommitteeSubnetSubscriptions(b -> b.addRelevantSubnet(1))
                .nodeIdToDataColumnSidecarSubnetsCalculator(
                    NodeIdToDataColumnSidecarSubnetsCalculator.NOOP)
                .build());

    // First peer has attestation subnet 1, sync committee subnet 1
    // Second peer has attestation subnet 2 only
    final List<DiscoveryPeer> candidates =
        List.of(
            createDiscoveryPeer(
                schemaDefinitions.getAttnetsENRFieldSchema().ofBits(IntList.of(1)),
                schemaDefinitions.getSyncnetsENRFieldSchema().ofBits(IntList.of(1))),
            createDiscoveryPeer(
                schemaDefinitions.getAttnetsENRFieldSchema().ofBits(IntList.of(2)),
                schemaDefinitions.getSyncnetsENRFieldSchema().getDefault()));

    final List<DiscoveryPeer> selected = scorer.selectCandidatePeers(candidates, 2);

    // Should select both peers
    assertThat(selected).hasSize(2);
    // First peer should be selected first (higher score due to multiple subnets)
    assertThat(selected.get(0)).isEqualTo(candidates.get(0));
    assertThat(selected.get(1)).isEqualTo(candidates.get(1));
  }

  @Test
  void selectCandidatePeers_shouldStopWhenNoMoreCandidatesAvailable() {
    final SubnetScorer scorer =
        SubnetScorer.create(
            PeerSubnetSubscriptions.createEmpty(
                () -> schemaDefinitions,
                SszBitvectorSchema.create(DATA_COLUMN_SIDECAR_SUBNET_COUNT)));

    final List<DiscoveryPeer> candidates =
        List.of(
            createDiscoveryPeer(
                schemaDefinitions.getAttnetsENRFieldSchema().getDefault(),
                schemaDefinitions.getSyncnetsENRFieldSchema().getDefault()));

    // Ask for more peers than available
    final List<DiscoveryPeer> selected = scorer.selectCandidatePeers(candidates, 5);

    // Should only return available candidates
    assertThat(selected).hasSize(1);
    assertThat(selected.getFirst()).isEqualTo(candidates.getFirst());
  }

  @SafeVarargs
  private void assertExistingPeerScores(
      final PeerScorer scorer, final Map.Entry<PeerId, Integer>... expected) {
    final Map<PeerId, Integer> actual =
        Stream.of(expected)
            .map(Map.Entry::getKey)
            .collect(Collectors.toMap(Function.identity(), scorer::scoreExistingPeer));
    assertThat(actual).contains(expected);
  }

  @SafeVarargs
  private void assertCandidatePeerScores(
      final SubnetScorer scorer,
      final Map.Entry<Pair<SszBitvector, SszBitvector>, Integer>... expected) {
    final Map<Pair<SszBitvector, SszBitvector>, Integer> actual =
        Stream.of(expected)
            .map(Map.Entry::getKey)
            .collect(
                Collectors.toMap(
                    Function.identity(),
                    (subscriptions) ->
                        scorer.scoreCandidatePeer(
                            createDiscoveryPeer(subscriptions.getLeft(), subscriptions.getRight()),
                            new SubnetScorer.SelectedCandidateSubnetCountChanges())));
    assertThat(actual).contains(expected);
  }

  private DiscoveryPeer createDiscoveryPeer(
      final SszBitvector attSubnets, final SszBitvector syncSubnets) {
    try {
      Bytes pubKey =
          Bytes.fromHexString(
              "0x03B86ED9F747A7FA99963F39E3B176B45E9E863108A2D145EA3A4E76D8D0935194");
      return new DiscoveryPeer(
          pubKey,
          DEFAULT_NODE_RECORD_CONVERTER.convertPublicKeyToNodeId(pubKey),
          new InetSocketAddress(InetAddress.getByAddress(new byte[] {127, 0, 0, 1}), 9000),
          Optional.empty(),
          attSubnets,
          syncSubnets,
          Optional.empty(),
          Optional.empty());
    } catch (UnknownHostException e) {
      throw new RuntimeException(e);
    }
  }

  private Pair<SszBitvector, SszBitvector> candidateWithSubnets(
      final List<Integer> attnets, final List<Integer> syncnets) {
    return Pair.of(
        schemaDefinitions.getAttnetsENRFieldSchema().ofBits(attnets),
        schemaDefinitions.getSyncnetsENRFieldSchema().ofBits(syncnets));
  }
}
