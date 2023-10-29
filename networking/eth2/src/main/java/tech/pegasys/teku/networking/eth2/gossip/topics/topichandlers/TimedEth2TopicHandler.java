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

package tech.pegasys.teku.networking.eth2.gossip.topics.topichandlers;

import io.libp2p.core.pubsub.ValidationResult;
import java.util.Optional;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.topics.GossipSubValidationUtil;
import tech.pegasys.teku.networking.eth2.gossip.topics.GossipTopicName;
import tech.pegasys.teku.networking.eth2.gossip.topics.OperationMilestoneValidator;
import tech.pegasys.teku.networking.eth2.gossip.topics.TimedOperationProcessor;
import tech.pegasys.teku.networking.p2p.gossip.PreparedGossipMessage;
import tech.pegasys.teku.spec.config.NetworkingSpecConfig;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.storage.client.RecentChainData;

public class TimedEth2TopicHandler<MessageT extends SszData> extends Eth2TopicHandler<MessageT> {
  private final TimedOperationProcessor<MessageT> processor;

  public TimedEth2TopicHandler(
      final RecentChainData recentChainData,
      final AsyncRunner asyncRunner,
      final TimedOperationProcessor<MessageT> processor,
      final GossipEncoding gossipEncoding,
      final Bytes4 forkDigest,
      final String topicName,
      final OperationMilestoneValidator<MessageT> forkValidator,
      final SszSchema<MessageT> messageType,
      final NetworkingSpecConfig networkingConfig) {
    super(
        recentChainData,
        asyncRunner,
        operation -> processor.process(operation, Optional.empty()),
        gossipEncoding,
        forkDigest,
        topicName,
        forkValidator,
        messageType,
        networkingConfig);
    this.processor = processor;
  }

  public TimedEth2TopicHandler(
      final RecentChainData recentChainData,
      final AsyncRunner asyncRunner,
      final TimedOperationProcessor<MessageT> processor,
      final GossipEncoding gossipEncoding,
      final Bytes4 forkDigest,
      final GossipTopicName topicName,
      final OperationMilestoneValidator<MessageT> forkValidator,
      final SszSchema<MessageT> messageType,
      final NetworkingSpecConfig networkingConfig) {
    this(
        recentChainData,
        asyncRunner,
        processor,
        gossipEncoding,
        forkDigest,
        topicName.toString(),
        forkValidator,
        messageType,
        networkingConfig);
  }

  @Override
  public SafeFuture<ValidationResult> handleMessage(PreparedGossipMessage message) {
    return SafeFuture.of(() -> deserialize(message))
        .thenCompose(
            deserialized -> {
              if (!forkValidator.isValid(deserialized)) {
                return SafeFuture.completedFuture(
                    GossipSubValidationUtil.fromInternalValidationResult(
                        InternalValidationResult.reject("Incorrect spec milestone")));
              }
              return asyncRunner.runAsync(
                  () ->
                      processor
                          .process(deserialized, message.getArrivalTimestamp())
                          .thenApply(
                              internalValidation -> {
                                processMessage(internalValidation, message);
                                return GossipSubValidationUtil.fromInternalValidationResult(
                                    internalValidation);
                              }));
            })
        .exceptionally(error -> handleMessageProcessingError(message, error));
  }
}
