/*
 * Copyright 2021 ConsenSys AG.
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

import java.util.HashSet;
import java.util.Set;
import tech.pegasys.teku.infrastructure.ssz.type.Bytes4;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.spec.constants.NetworkConstants;
import tech.pegasys.teku.util.config.Constants;

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

  public static Set<String> getAllTopics(
      final GossipEncoding gossipEncoding, final Bytes4 forkDigest) {
    final Set<String> topics = new HashSet<>();

    for (int i = 0; i < Constants.ATTESTATION_SUBNET_COUNT; i++) {
      topics.add(getAttestationSubnetTopic(forkDigest, i, gossipEncoding));
    }
    for (int i = 0; i < NetworkConstants.SYNC_COMMITTEE_SUBNET_COUNT; i++) {
      topics.add(getSyncCommitteeSubnetTopic(forkDigest, i, gossipEncoding));
    }
    for (GossipTopicName topicName : GossipTopicName.values()) {
      topics.add(GossipTopics.getTopic(forkDigest, topicName, gossipEncoding));
    }

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
