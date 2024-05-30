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

import static tech.pegasys.teku.infrastructure.logging.P2PLogger.P2P_LOG;

import io.libp2p.core.pubsub.ValidationResult;
import java.util.Optional;
import java.util.concurrent.RejectedExecutionException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.exceptions.ExceptionUtil;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.gossip.encoding.DecodingException;
import tech.pegasys.teku.networking.eth2.gossip.encoding.Eth2PreparedGossipMessageFactory;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.topics.GossipSubValidationUtil;
import tech.pegasys.teku.networking.eth2.gossip.topics.GossipTopicName;
import tech.pegasys.teku.networking.eth2.gossip.topics.GossipTopics;
import tech.pegasys.teku.networking.eth2.gossip.topics.OperationMilestoneValidator;
import tech.pegasys.teku.networking.eth2.gossip.topics.OperationProcessor;
import tech.pegasys.teku.networking.p2p.gossip.PreparedGossipMessage;
import tech.pegasys.teku.networking.p2p.gossip.TopicHandler;
import tech.pegasys.teku.service.serviceutils.ServiceCapacityExceededException;
import tech.pegasys.teku.spec.config.NetworkingSpecConfig;
import tech.pegasys.teku.statetransition.util.P2PDebugDataDumper;
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
  private final OperationMilestoneValidator<MessageT> forkValidator;
  private final NetworkingSpecConfig networkingConfig;
  private final P2PDebugDataDumper p2pDebugDataDumper;

  public Eth2TopicHandler(
      final RecentChainData recentChainData,
      final AsyncRunner asyncRunner,
      final OperationProcessor<MessageT> processor,
      final GossipEncoding gossipEncoding,
      final Bytes4 forkDigest,
      final String topicName,
      final OperationMilestoneValidator<MessageT> forkValidator,
      final SszSchema<MessageT> messageType,
      final NetworkingSpecConfig networkingConfig,
      final P2PDebugDataDumper p2pDebugDataDumper) {
    this.asyncRunner = asyncRunner;
    this.processor = processor;
    this.gossipEncoding = gossipEncoding;
    this.forkDigest = forkDigest;
    this.topicName = topicName;
    this.messageType = messageType;
    this.forkValidator = forkValidator;
    this.networkingConfig = networkingConfig;
    this.preparedGossipMessageFactory =
        gossipEncoding.createPreparedGossipMessageFactory(
            recentChainData::getMilestoneByForkDigest);
    this.p2pDebugDataDumper = p2pDebugDataDumper;
  }

  public Eth2TopicHandler(
      final RecentChainData recentChainData,
      final AsyncRunner asyncRunner,
      final OperationProcessor<MessageT> processor,
      final GossipEncoding gossipEncoding,
      final Bytes4 forkDigest,
      final GossipTopicName topicName,
      final OperationMilestoneValidator<MessageT> forkValidator,
      final SszSchema<MessageT> messageType,
      final NetworkingSpecConfig networkingConfig,
      final P2PDebugDataDumper p2pDebugDataDumper) {
    this(
        recentChainData,
        asyncRunner,
        processor,
        gossipEncoding,
        forkDigest,
        topicName.toString(),
        forkValidator,
        messageType,
        networkingConfig,
        p2pDebugDataDumper);
  }

  @Override
  public SafeFuture<ValidationResult> handleMessage(final PreparedGossipMessage message) {
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

  private void processMessage(
      final InternalValidationResult internalValidationResult,
      final PreparedGossipMessage message) {
    switch (internalValidationResult.code()) {
      case REJECT:
        p2pDebugDataDumper.saveGossipRejectedMessageToFile(
            getTopic(),
            message.getArrivalTimestamp(),
            () -> message.getDecodedMessage().getDecodedMessage().orElse(Bytes.EMPTY),
            internalValidationResult.getDescription());
        P2P_LOG.onGossipRejected(
            getTopic(),
            message.getDecodedMessage().getDecodedMessage().orElse(Bytes.EMPTY),
            internalValidationResult.getDescription());
        break;
      case IGNORE:
        LOG.trace("Ignoring message for topic: {}", this::getTopic);
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

  protected ValidationResult handleMessageProcessingError(
      final PreparedGossipMessage message, final Throwable err) {
    final ValidationResult response;
    if (ExceptionUtil.hasCause(err, DecodingException.class)) {

      p2pDebugDataDumper.saveGossipMessageDecodingError(
          getTopic(), message.getArrivalTimestamp(), message::getOriginalMessage, err);
      P2P_LOG.onGossipMessageDecodingError(getTopic(), message.getOriginalMessage(), err);
      response = ValidationResult.Invalid;
    } else if (ExceptionUtil.hasCause(err, RejectedExecutionException.class)) {
      LOG.warn(
          "Discarding gossip message for topic {} because the executor queue is full", getTopic());
      response = ValidationResult.Ignore;
    } else if (ExceptionUtil.hasCause(err, ServiceCapacityExceededException.class)) {
      LOG.warn(
          "Discarding gossip message for topic {} because the signature verification queue is full",
          getTopic());
      response = ValidationResult.Ignore;
    } else {
      LOG.warn("Encountered exception while processing message for topic {}", getTopic(), err);
      response = ValidationResult.Invalid;
    }

    return response;
  }

  @Override
  public PreparedGossipMessage prepareMessage(
      final Bytes payload, final Optional<UInt64> arrivalTimestamp) {
    return preparedGossipMessageFactory.create(
        getTopic(), payload, getMessageType(), networkingConfig, arrivalTimestamp);
  }

  @Override
  public int getMaxMessageSize() {
    return networkingConfig.getGossipMaxSize();
  }

  protected MessageT deserialize(final PreparedGossipMessage message) throws DecodingException {
    return getGossipEncoding().decodeMessage(message, getMessageType());
  }

  public OperationProcessor<MessageT> getProcessor() {
    return processor;
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
