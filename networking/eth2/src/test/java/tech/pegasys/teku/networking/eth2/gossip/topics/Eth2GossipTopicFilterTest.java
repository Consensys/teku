/*
 * Copyright Consensys Software Inc., 2022
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

package tech.pegasys.teku.networking.eth2.gossip.topics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding.SSZ_SNAPPY;
import static tech.pegasys.teku.networking.eth2.gossip.topics.GossipTopicName.getAttestationSubnetTopicName;
import static tech.pegasys.teku.networking.eth2.gossip.topics.GossipTopicName.getBlobSidecarSubnetTopicName;
import static tech.pegasys.teku.networking.eth2.gossip.topics.GossipTopicName.getSyncCommitteeSubnetTopicName;
import static tech.pegasys.teku.spec.constants.NetworkConstants.SYNC_COMMITTEE_SUBNET_COUNT;

import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigDeneb;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.client.RecentChainData;

@TestSpecContext(milestone = {SpecMilestone.DENEB, SpecMilestone.ELECTRA})
class Eth2GossipTopicFilterTest {

  private final RecentChainData recentChainData = mock(RecentChainData.class);
  private final UInt64 currentForkEpoch = UInt64.valueOf(10);
  private Spec spec;
  private SpecMilestone specMilestone;
  private DataStructureUtil dataStructureUtil;
  private ForkInfo forkInfo;
  private Eth2GossipTopicFilter filter;
  private Bytes4 currentForkDigest;
  private Bytes4 nextForkDigest;

  @BeforeEach
  void setUp(final TestSpecInvocationContextProvider.SpecContext specContext) {
    specMilestone = specContext.getSpecMilestone();
    spec =
        switch (specContext.getSpecMilestone()) {
          case PHASE0 -> throw new IllegalArgumentException("Phase0 is an unsupported milestone");
          case ALTAIR -> throw new IllegalArgumentException("Altair is an unsupported milestone");
          case BELLATRIX ->
              throw new IllegalArgumentException("Bellatrix is an unsupported milestone");
          case CAPELLA -> throw new IllegalArgumentException("Capella is an unsupported milestone");
          case DENEB -> TestSpecFactory.createMinimalWithDenebForkEpoch(currentForkEpoch);
          case ELECTRA -> TestSpecFactory.createMinimalWithElectraForkEpoch(currentForkEpoch);
        };
    dataStructureUtil = new DataStructureUtil(spec);
    filter = new Eth2GossipTopicFilter(recentChainData, SSZ_SNAPPY, spec);

    final Bytes32 genesisValidatorsRoot = dataStructureUtil.randomBytes32();
    final List<Fork> forks = spec.getForkSchedule().getForks();
    forkInfo = new ForkInfo(forks.get(0), genesisValidatorsRoot);
    currentForkDigest = forkInfo.getForkDigest(spec);

    final Fork nextFork = forks.get(1);
    nextForkDigest =
        spec.atEpoch(nextFork.getEpoch())
            .miscHelpers()
            .computeForkDigest(nextFork.getCurrentVersion(), genesisValidatorsRoot);

    when(recentChainData.getCurrentForkInfo()).thenReturn(Optional.of(forkInfo));
    when(recentChainData.getNextFork(forkInfo.getFork())).thenReturn(Optional.of(nextFork));
    when(recentChainData.getMilestoneByForkDigest(currentForkDigest))
        .thenReturn(Optional.of(specMilestone));
  }

  @TestTemplate
  void shouldNotAllowIrrelevantTopics() {
    assertThat(filter.isRelevantTopic("abc")).isFalse();
  }

  @TestTemplate
  void shouldNotRequireNextForkToBePresent() {
    when(recentChainData.getNextFork(any())).thenReturn(Optional.empty());
    assertThat(filter.isRelevantTopic(getTopicName(GossipTopicName.BEACON_BLOCK))).isTrue();
  }

  @TestTemplate
  void shouldConsiderTopicsForNextForkRelevant() {
    assertThat(filter.isRelevantTopic(getNextForkTopicName(GossipTopicName.BEACON_BLOCK))).isTrue();
  }

  @TestTemplate
  void shouldConsiderTopicsForSignedContributionAndProofRelevant() {
    assertThat(
            filter.isRelevantTopic(
                getNextForkTopicName(GossipTopicName.SYNC_COMMITTEE_CONTRIBUTION_AND_PROOF)))
        .isTrue();
  }

  @TestTemplate
  void shouldConsiderAllAttestationSubnetsRelevant() {
    for (int i = 0; i < spec.getNetworkingConfig().getAttestationSubnetCount(); i++) {
      assertThat(filter.isRelevantTopic(getTopicName(getAttestationSubnetTopicName(i)))).isTrue();
      assertThat(filter.isRelevantTopic(getNextForkTopicName(getAttestationSubnetTopicName(i))))
          .isTrue();
    }
  }

  @TestTemplate
  void shouldConsiderAllSyncCommitteeSubnetsRelevant() {
    for (int i = 0; i < SYNC_COMMITTEE_SUBNET_COUNT; i++) {
      assertThat(filter.isRelevantTopic(getTopicName(getSyncCommitteeSubnetTopicName(i)))).isTrue();
    }
  }

  @TestTemplate
  void shouldConsiderAllBlobSidecarSubnetsRelevant() {
    final SpecConfig config = spec.forMilestone(SpecMilestone.DENEB).getConfig();
    final SpecConfigDeneb specConfigDeneb = SpecConfigDeneb.required(config);
    for (int i = 0; i < specConfigDeneb.getBlobSidecarSubnetCount(); i++) {
      assertThat(filter.isRelevantTopic(getTopicName(getBlobSidecarSubnetTopicName(i)))).isTrue();
    }
  }

  @TestTemplate
  void shouldNotConsiderBlobSidecarWithIncorrectSubnetIdRelevant() {
    final int blobSidecarSubnetCount =
        spec.forMilestone(specMilestone).miscHelpers().getBlobSidecarSubnetCount().orElseThrow();
    assertThat(
            filter.isRelevantTopic(
                getTopicName(getBlobSidecarSubnetTopicName(blobSidecarSubnetCount + 1))))
        .isFalse();
  }

  @TestTemplate
  void shouldNotConsiderBlobSidecarWithoutIndexTopicRelevant() {
    assertThat(filter.isRelevantTopic(getTopicName("blob_sidecar"))).isFalse();
  }

  @TestTemplate
  void shouldNotAllowTopicsWithUnknownForkDigest() {
    final String irrelevantTopic =
        GossipTopics.getTopic(
            Bytes4.fromHexString("0x11223344"), GossipTopicName.BEACON_BLOCK, SSZ_SNAPPY);
    assertThat(filter.isRelevantTopic(irrelevantTopic)).isFalse();
  }

  private String getTopicName(final GossipTopicName name) {
    return GossipTopics.getTopic(forkInfo.getForkDigest(spec), name, SSZ_SNAPPY);
  }

  private String getTopicName(final String name) {
    return GossipTopics.getTopic(forkInfo.getForkDigest(spec), name, SSZ_SNAPPY);
  }

  private String getNextForkTopicName(final GossipTopicName name) {
    return GossipTopics.getTopic(nextForkDigest, name, SSZ_SNAPPY);
  }

  private String getNextForkTopicName(final String name) {
    return GossipTopics.getTopic(nextForkDigest, name, SSZ_SNAPPY);
  }
}
