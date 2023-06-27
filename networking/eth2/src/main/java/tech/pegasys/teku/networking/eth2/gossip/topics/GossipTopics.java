/*
 * Copyright ConsenSys Software Inc., 2022
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

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.IntStream;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.constants.NetworkConstants;

/**
 * Helpers for getting the full topic strings formatted like: /eth2/ForkDigestValue/Name/Encoding
 */
public class GossipTopics {
  private static final String DOMAIN_PREFIX = "eth2";

  public static String getTopic(
      final Bytes4 forkDigest,
      final GossipTopicName topicName,
      final GossipEncoding gossipEncoding) {
    return getTopic(forkDigest, topicName.toString(), gossipEncoding);
  }

  public static String getTopic(
      final Bytes4 forkDigest, final String topicName, final GossipEncoding gossipEncoding) {
    return "/"
        + DOMAIN_PREFIX
        + "/"
        + forkDigest.toUnprefixedHexString()
        + "/"
        + topicName
        + "/"
        + gossipEncoding.getName();
  }

  public static String getAttestationSubnetTopic(
      final Bytes4 forkDigest, final int subnetId, final GossipEncoding gossipEncoding) {
    return getTopic(
        forkDigest, GossipTopicName.getAttestationSubnetTopicName(subnetId), gossipEncoding);
  }

  public static String getSyncCommitteeSubnetTopic(
      final Bytes4 forkDigest, final int subnetId, final GossipEncoding gossipEncoding) {
    return getTopic(
        forkDigest, GossipTopicName.getSyncCommitteeSubnetTopicName(subnetId), gossipEncoding);
  }

  public static String getBlobSidecarSubnetTopic(
      final Bytes4 forkDigest, final int subnetId, final GossipEncoding gossipEncoding) {
    return getTopic(
        forkDigest, GossipTopicName.getBlobSidecarSubnetTopicName(subnetId), gossipEncoding);
  }

  public static Set<String> getAllTopics(
      final GossipEncoding gossipEncoding, final Bytes4 forkDigest, final Spec spec) {
    final Set<String> topics = new HashSet<>();

    IntStream.range(0, spec.getNetworkingConfig().getAttestationSubnetCount())
        .forEach(
            attestationSubnetIndex ->
                topics.add(
                    getAttestationSubnetTopic(forkDigest, attestationSubnetIndex, gossipEncoding)));
    IntStream.range(0, NetworkConstants.SYNC_COMMITTEE_SUBNET_COUNT)
        .forEach(
            syncCommitteeSubnetIndex ->
                topics.add(
                    getSyncCommitteeSubnetTopic(
                        forkDigest, syncCommitteeSubnetIndex, gossipEncoding)));
    spec.getNetworkingConfigDeneb()
        .ifPresent(
            networkingSpecConfigDeneb ->
                IntStream.range(0, networkingSpecConfigDeneb.getBlobSidecarSubnetCount())
                    .forEach(
                        blobSidecarSubnetIndex ->
                            topics.add(
                                getBlobSidecarSubnetTopic(
                                    forkDigest, blobSidecarSubnetIndex, gossipEncoding))));
    Arrays.stream(GossipTopicName.values())
        .forEach(
            topicName -> topics.add(GossipTopics.getTopic(forkDigest, topicName, gossipEncoding)));

    return topics;
  }

  /**
   * @param topic The topic string
   * @return The forkDigest embedded in the topic string
   * @throws IllegalArgumentException Throws if the topic string is not formatted as expected
   */
  public static Bytes4 extractForkDigest(final String topic) throws IllegalArgumentException {
    // Fork digest starts after domain prefix + slash separators
    final int beginIndex = DOMAIN_PREFIX.length() + 2;
    final int endIndex = topic.indexOf("/", beginIndex);
    final String forkDigest = topic.substring(beginIndex, endIndex);

    return Bytes4.fromHexString(forkDigest);
  }
}
