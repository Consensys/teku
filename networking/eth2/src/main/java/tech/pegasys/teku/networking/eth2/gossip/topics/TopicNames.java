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

package tech.pegasys.teku.networking.eth2.gossip.topics;

import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.ssz.SSZTypes.Bytes4;

public class TopicNames {

  public static String getTopic(
      final Bytes4 forkDigest, final String topicName, final GossipEncoding gossipEncoding) {
    return "/eth2/"
        + forkDigest.toUnprefixedHexString()
        + "/"
        + topicName
        + "/"
        + gossipEncoding.getName();
  }

  public static String getAttestationSubnetTopic(
      final Bytes4 forkDigest, final int subnetId, final GossipEncoding gossipEncoding) {
    return getTopic(forkDigest, getAttestationSubnetTopicName(subnetId), gossipEncoding);
  }

  public static String getAttestationSubnetTopicName(final int subnetId) {
    return "beacon_attestation_" + subnetId;
  }
}
