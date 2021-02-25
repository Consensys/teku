/*
 * Copyright 2019 ConsenSys AG.
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

package tech.pegasys.teku.networking.eth2.gossip;

import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.topics.OperationProcessor;
import tech.pegasys.teku.networking.p2p.gossip.GossipNetwork;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.ssz.backing.schema.SszSchema;

public class AttesterSlashingGossipManager extends AbstractGossipManager<AttesterSlashing> {
  public static String TOPIC_NAME = "attester_slashing";

  public AttesterSlashingGossipManager(
      final AsyncRunner asyncRunner,
      final GossipNetwork gossipNetwork,
      final GossipEncoding gossipEncoding,
      final ForkInfo forkInfo,
      final OperationProcessor<AttesterSlashing> processor,
      final GossipPublisher<AttesterSlashing> publisher) {
    super(TOPIC_NAME, asyncRunner, gossipNetwork, gossipEncoding, forkInfo, processor, publisher);
  }

  @Override
  protected SszSchema<AttesterSlashing> getGossipType() {
    return AttesterSlashing.SSZ_SCHEMA;
  }
}
