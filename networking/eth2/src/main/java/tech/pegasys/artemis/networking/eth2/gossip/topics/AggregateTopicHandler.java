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

package tech.pegasys.artemis.networking.eth2.gossip.topics;

import com.google.common.eventbus.EventBus;
import tech.pegasys.artemis.datastructures.operations.SignedAggregateAndProof;
import tech.pegasys.artemis.datastructures.state.ForkInfo;
import tech.pegasys.artemis.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.artemis.networking.eth2.gossip.topics.validation.SignedAggregateAndProofValidator;
import tech.pegasys.artemis.networking.eth2.gossip.topics.validation.ValidationResult;

public class AggregateTopicHandler extends Eth2TopicHandler<SignedAggregateAndProof> {
  public static String TOPIC_NAME = "beacon_aggregate_and_proof";
  private final SignedAggregateAndProofValidator validator;

  public AggregateTopicHandler(
      final GossipEncoding gossipEncoding,
      final ForkInfo forkInfo,
      final SignedAggregateAndProofValidator validator,
      final EventBus eventBus) {
    super(gossipEncoding, forkInfo, eventBus);
    this.validator = validator;
  }

  @Override
  public String getTopicName() {
    return TOPIC_NAME;
  }

  @Override
  protected Class<SignedAggregateAndProof> getValueType() {
    return SignedAggregateAndProof.class;
  }

  @Override
  protected ValidationResult validateData(final SignedAggregateAndProof aggregateAndProof) {
    return validator.validate(aggregateAndProof);
  }
}
