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

package tech.pegasys.teku.networking.eth2.gossip.encoding;

import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding.ForkDigestToMilestone;
import tech.pegasys.teku.networking.p2p.gossip.PreparedGossipMessage;
import tech.pegasys.teku.ssz.SszData;
import tech.pegasys.teku.ssz.schema.SszSchema;

public class SnappyPreparedGossipMessageFactory implements Eth2PreparedGossipMessageFactory {

  private final SnappyBlockCompressor snappyCompressor;
  private final ForkDigestToMilestone forkDigestToMilestone;

  public SnappyPreparedGossipMessageFactory(
      final SnappyBlockCompressor snappyCompressor,
      final ForkDigestToMilestone forkDigestToMilestone) {
    this.snappyCompressor = snappyCompressor;
    this.forkDigestToMilestone = forkDigestToMilestone;
  }

  @Override
  public <T extends SszData> PreparedGossipMessage create(
      final String topic, final Bytes data, final SszSchema<T> valueType) {
    return SnappyPreparedGossipMessage.create(
        topic, data, forkDigestToMilestone, valueType, snappyCompressor::uncompress);
  }

  @Override
  public PreparedGossipMessage create(final String topic, final Bytes data) {
    return SnappyPreparedGossipMessage.createUnknown(topic, data, forkDigestToMilestone);
  }
}
