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

package tech.pegasys.teku.networking.eth2.gossip.topics.topichandlers;

import io.libp2p.core.pubsub.ValidationResult;
import java.util.concurrent.RejectedExecutionException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.exceptions.ExceptionUtil;
import tech.pegasys.teku.networking.eth2.gossip.encoding.DecodingException;
import tech.pegasys.teku.networking.eth2.gossip.encoding.Eth2PreparedGossipMessageFactory;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.topics.GossipSubValidationUtil;
import tech.pegasys.teku.networking.eth2.gossip.topics.GossipTopicName;
import tech.pegasys.teku.networking.eth2.gossip.topics.GossipTopics;
import tech.pegasys.teku.networking.eth2.gossip.topics.OperationProcessor;
import tech.pegasys.teku.networking.p2p.gossip.PreparedGossipMessage;
import tech.pegasys.teku.networking.p2p.gossip.TopicHandler;
import tech.pegasys.teku.ssz.SszData;
import tech.pegasys.teku.ssz.schema.SszSchema;
import tech.pegasys.teku.ssz.type.Bytes4;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.storage.client.RecentChainData;

public class Eth2TopicHandler<MessageT extends SszData> implements TopicHandler {
  private static final Logger LOG = LogManager.getLogger();
  private final AsyncRunner asyncRunner;
  private final OperationProcessor<MessageT> processor;
  private final GossipEncoding gossipEncoding;
  private final Bytes4 forkDigest;
  private final String topicName;
  private final SszSchema<MessageT> messageType;
  private final Eth2PreparedGossipMessageFactory preparedGossipMessageFactory;

  public Eth2TopicHandler(
      final RecentChainData recentChainData,
      AsyncRunner asyncRunner,
      OperationProcessor<MessageT> processor,
      GossipEncoding gossipEncoding,
      Bytes4 forkDigest,
      String topicName,
      SszSchema<MessageT> messageType) {
    this.asyncRunner = asyncRunner;
    this.processor = processor;
    this.gossipEncoding = gossipEncoding;
    this.forkDigest = forkDigest;
    this.topicName = topicName;
    this.messageType = messageType;

    this.preparedGossipMessageFactory =
        gossipEncoding.createPreparedGossipMessageFactory(
            recentChainData::getMilestoneByForkDigest);
  }

  public Eth2TopicHandler(
      final RecentChainData recentChainData,
      AsyncRunner asyncRunner,
      OperationProcessor<MessageT> processor,
      GossipEncoding gossipEncoding,
      Bytes4 forkDigest,
      GossipTopicName topicName,
      SszSchema<MessageT> messageType) {
    this(
        recentChainData,
        asyncRunner,
        processor,
        gossipEncoding,
        forkDigest,
        topicName.toString(),
        messageType);
  }

  @Override
  public SafeFuture<ValidationResult> handleMessage(PreparedGossipMessage message) {
    return SafeFuture.of(() -> deserialize(message))
        .thenCompose(
            deserialized ->
                asyncRunner.runAsync(
                    () ->
                        processor
                            .process(deserialized)
                            .thenApply(
                                internalValidation -> {
                                  processMessage(internalValidation);
                                  return GossipSubValidationUtil.fromInternalValidationResult(
                                      internalValidation);
                                })))
        .exceptionally(this::handleMessageProcessingError);
  }

  private void processMessage(final InternalValidationResult internalValidationResult) {
    switch (internalValidationResult.code()) {
      case REJECT:
      case IGNORE:
        LOG.trace("Received invalid message for topic: {}", this::getTopic);
        break;
      case SAVE_FOR_FUTURE:
        LOG.trace("Deferring message for topic: {}", this::getTopic);
        break;
      case ACCEPT:
        break;
      default:
        throw new UnsupportedOperationException(
            "Unexpected validation result: " + internalValidationResult);
    }
  }

  private String getTopicName() {
    return topicName;
  }

  private SszSchema<MessageT> getMessageType() {
    return messageType;
  }

  protected ValidationResult handleMessageProcessingError(Throwable err) {
    final ValidationResult response;
    if (ExceptionUtil.getCause(err, DecodingException.class).isPresent()) {
      LOG.debug("Received malformed gossip message on {}", getTopic());
      response = ValidationResult.Invalid;
    } else if (ExceptionUtil.getCause(err, RejectedExecutionException.class).isPresent()) {
      LOG.warn(
          "Discarding gossip message for topic {} because the executor queue is full", getTopic());
      response = ValidationResult.Ignore;
    } else {
      LOG.warn("Encountered exception while processing message for topic {}", getTopic(), err);
      response = ValidationResult.Invalid;
    }

    return response;
  }

  @Override
  public PreparedGossipMessage prepareMessage(Bytes payload) {
    return preparedGossipMessageFactory.create(getTopic(), payload, getMessageType());
  }

  protected MessageT deserialize(PreparedGossipMessage message) throws DecodingException {
    return getGossipEncoding().decodeMessage(message, getMessageType());
  }

  public String getTopic() {
    return GossipTopics.getTopic(getForkDigest(), getTopicName(), getGossipEncoding());
  }

  public GossipEncoding getGossipEncoding() {
    return gossipEncoding;
  }

  public Bytes4 getForkDigest() {
    return forkDigest;
  }
}
