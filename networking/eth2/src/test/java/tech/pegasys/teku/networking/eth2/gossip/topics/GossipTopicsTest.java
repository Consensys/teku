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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Comparator;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.p2p.libp2p.gossip.LibP2PGossipNetworkBuilder;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;

public class GossipTopicsTest {
  private final GossipEncoding gossipEncoding = GossipEncoding.SSZ_SNAPPY;

  @Test
  public void getTopic() {
    final Bytes4 forkDigest = Bytes4.fromHexString("0x01020304");
    final String topicName = "test";

    final String actual = GossipTopics.getTopic(forkDigest, topicName, gossipEncoding);
    assertThat(actual).isEqualTo("/eth2/01020304/test/ssz_snappy");
  }

  @Test
  public void extractForkDigest_valid() {
    final Bytes4 result = GossipTopics.extractForkDigest("/eth2/01020304/test/ssz_snappy");
    assertThat(result).isEqualTo(Bytes4.fromHexString("01020304"));
  }

  @Test
  public void extractForkDigest_invalid() {
    final String topic = "/eth2/wrong/test/ssz_snappy";
    assertThatThrownBy(() -> GossipTopics.extractForkDigest(topic))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void maxSubscribedTopicsConstantIsLargeEnough() {
    final SpecMilestone latestMilestone = SpecMilestone.values()[SpecMilestone.values().length - 1];
    final Spec spec = TestSpecFactory.createMainnet(latestMilestone);

    final StorageSystem storageSystem = InMemoryStorageSystemBuilder.buildDefault(spec);
    storageSystem.chainUpdater().initializeGenesis();
    final Bytes32 genesisValidatorsRoot =
        storageSystem.recentChainData().getGenesisData().orElseThrow().getGenesisValidatorsRoot();

    // sum of all topics for highest fork + (fork-1) + (fork-2)
    int exactMaxSubscribedTopics =
        SpecMilestone.getMilestonesUpTo(latestMilestone).stream()
            .sorted(Comparator.reverseOrder())
            .limit(3)
            .mapToInt(
                milestone -> {
                  final Fork fork = spec.getForkSchedule().getFork(milestone);
                  final Bytes4 forkDigest =
                      spec.forMilestone(milestone)
                          .miscHelpers()
                          .computeForkDigest(fork.getCurrentVersion(), genesisValidatorsRoot);
                  return GossipTopics.getAllTopics(gossipEncoding, forkDigest, spec, milestone)
                      .size();
                })
            .sum();

    assertThat(exactMaxSubscribedTopics)
        .isLessThanOrEqualTo(LibP2PGossipNetworkBuilder.MAX_SUBSCRIBED_TOPICS);
  }
}
